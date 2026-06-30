// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOPaymaster, UserOperation, IPaymasterV06, IEntryPointView} from "../src/MDAOPaymaster.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";

// ── Mock ERC-20 with configurable decimals ───────────────────────────────
contract MockToken {
    string public name;
    string public symbol;
    uint8 public decimals;

    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;

    constructor(string memory _name, string memory _symbol, uint8 _decimals) {
        name = _name;
        symbol = _symbol;
        decimals = _decimals;
    }

    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }

    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        uint256 allowed = allowance[from][msg.sender];
        if (allowed != type(uint256).max) { allowance[from][msg.sender] = allowed - amount; }
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

// ── Mock EntryPoint that tracks nonces ──────────────────────────────────
contract MockEntryPoint {
    mapping(address => uint256) public nonces;
    mapping(address => uint256) public balances;

    function balanceOf(address account) external view returns (uint256) {
        return balances[account];
    }

    function validateNonce(address sender, uint256 nonce) external returns (bool) {
        if (nonce < nonces[sender]) return false;
        nonces[sender] = nonce + 1;
        return true;
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Smoke Scenarios
// ═══════════════════════════════════════════════════════════════════════════
contract SmokeScenarios is Test {
    uint256 constant ALICE_KEY = 0xA11CE;
    address alice = vm.addr(ALICE_KEY);
    address bob = makeAddr("bob");
    address constant ENTRY_POINT = address(0x1234);

    MockToken mdao;
    MockToken usdt;
    MockEntryPoint ep;
    MDAOPaymaster paymaster;

    function setUp() public {
        vm.etch(ENTRY_POINT, hex"00");

        mdao = new MockToken("MDAO", "MDAO", 18);
        usdt = new MockToken("USDT", "USDT", 18);
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), address(0));

        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(mdao), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSignature("withdrawTo(address,uint256)"),
            ""
        );
    }

    // ── helpers ──────────────────────────────────────────────────────

    function _op(address sender, bytes memory pm) internal pure returns (UserOperation memory) {
        return UserOperation({
            sender: sender,
            nonce: 0,
            initCode: "",
            callData: "",
            callGasLimit: 1_000_000,
            verificationGasLimit: 50000,
            preVerificationGas: 100_000,
            maxFeePerGas: 100 gwei,
            maxPriorityFeePerGas: 100 gwei,
            paymasterAndData: pm,
            signature: ""
        });
    }

    // ══════════════════════════════════════════════════════════════════
    // SEC-PHISHING-A: recipient validation (address(0) token rejected)
    // ══════════════════════════════════════════════════════════════════

    function test_SEC_PHISHING_A() public {
        // Phishing vector: craft paymaster data with address(0) as token
        // Contract must reject invalid token addresses
        bytes memory customData = abi.encodePacked(
            address(0), uint256(100e18), block.timestamp + 1 hours
        );
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidToken.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ══════════════════════════════════════════════════════════════════
    // ILL-INPUT-A: send 0 tokens — maxTokenAmount=0 passes validation
    //              but postOp transferFrom fails (allowance is 0)
    // ══════════════════════════════════════════════════════════════════

    function test_ILL_INPUT_A() public {
        // maxTokenAmount=0: validation passes (0 <= limit),
        // but postOp will try to charge actualGasCost which is > 0
        // and the user has no allowance, so transferFrom fails → PaymentFailed event
        usdt.mint(alice, 1000e18);
        vm.prank(alice);
        usdt.approve(address(paymaster), 0); // zero allowance

        bytes memory customData = abi.encodePacked(
            address(usdt), uint256(0), block.timestamp + 1 hours
        );
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // postOp: actualGasCost > 0 but maxTokenAmount = 0
        // computeAmountToCharge: actualTokenAmount = actualGasCost * price / 1e18
        // if actualTokenAmount >= maxTokenAmount (0), returns (maxTokenAmount, 0) = (0, 0)
        // So transferFrom(sender, paymaster, 0) — should succeed as ERC20 transfer of 0 is valid
        uint256 before = usdt.balanceOf(address(paymaster));
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);

        // With 0 tokens charged, balance should not change
        assertEq(usdt.balanceOf(address(paymaster)), before, "zero-amount transfer should not move balance");
    }

    // ══════════════════════════════════════════════════════════════════
    // ILL-INPUT-D: malformed paymasterData (too short to decode token)
    // ══════════════════════════════════════════════════════════════════

    function test_ILL_INPUT_D() public {
        // Pass truncated data: only 10 bytes instead of required 84+ for token+amount+deadline
        bytes memory badData = hex"deadbeef1234567890";
        bytes memory pmAndData = abi.encode(address(paymaster), badData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert();
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, badData);
    }

    // ══════════════════════════════════════════════════════════════════
    // ASYNC-RACE-A: 2 tx same nonce — EntryPoint-level nonce check
    // ══════════════════════════════════════════════════════════════════

    function test_ASYNC_RACE_A() public {
        // Simulate nonce ordering: two ops with nonce=0 from same sender.
        // The EntryPoint enforces monotonically increasing nonces.
        // First op validates successfully; second with same nonce would be
        // rejected by the EntryPoint before reaching the paymaster.
        usdt.mint(alice, 2000e18);
        vm.prank(alice);
        usdt.approve(address(paymaster), 2000e18);

        bytes memory customData = abi.encodePacked(
            address(usdt), uint256(1000e18), block.timestamp + 1 hours
        );
        bytes memory pmAndData = abi.encode(address(paymaster), customData);

        // First op with nonce=0
        UserOperation memory op1 = _op(alice, pmAndData);
        op1.nonce = 0;

        // Second op also with nonce=0 (race condition)
        UserOperation memory op2 = _op(alice, pmAndData);
        op2.nonce = 0;

        // Both would pass paymaster validation individually,
        // but EntryPoint nonce tracking prevents duplicate processing.
        // We verify paymaster validates both independently (race is at EP level).
        vm.prank(ENTRY_POINT);
        paymaster.validatePaymasterUserOp(op1, bytes32(0), 1e16, customData);

        vm.prank(ENTRY_POINT);
        paymaster.validatePaymasterUserOp(op2, bytes32(uint256(1)), 1e16, customData);

        // If EntryPoint were live, it would reject op2 due to stale nonce.
        // The race protection is at the EP layer, not paymaster.
    }

    // ══════════════════════════════════════════════════════════════════
    // EDGE-CHAIN-D: token decimals (USDT 6 vs DAI/MDAO 18)
    // ══════════════════════════════════════════════════════════════════

    function test_EDGE_CHAIN_D() public {
        // Deploy a 6-decimal token (like real USDT on mainnet)
        MockToken usdt6 = new MockToken("USDT6", "USDT6", 6);
        // Register it as usdtConfig by redeploying paymaster
        MockEntryPoint ep2 = new MockEntryPoint();
        vm.etch(address(ep2), hex"00");
        MDAOPaymaster pm2 = new MDAOPaymaster(address(ep2), address(mdao), address(usdt6), address(0));

        vm.prank(pm2.owner());
        pm2.setTokenPrice(address(usdt6), 1e24);
        vm.prank(pm2.owner());
        pm2.setMaxGasPrice(1000 gwei);

        vm.mockCall(
            address(ep2),
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(pm2)),
            abi.encode(uint256(10 ether))
        );

        // Mint 6-decimal tokens: 1000 USDT = 1000e6
        usdt6.mint(alice, 1000e6);
        vm.prank(alice);
        usdt6.approve(address(pm2), 1000e6);

        bytes memory customData = abi.encodePacked(
            address(usdt6), uint256(1000e6), block.timestamp + 1 hours
        );
        bytes memory pmAndData = abi.encode(address(pm2), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(address(ep2));
        (bytes memory ctx,) = pm2.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertGt(ctx.length, 0, "validation should succeed with 6-decimal token");

        // postOp: computeAmountToCharge divides by 1e18 regardless of token decimals.
        // actualTokenAmount = actualGasCost * price / 1e18 = 1e15 * 1e24 / 1e18 = 1e21
        // This is > maxTokenAmount (1000e6), so charge is capped at maxTokenAmount.
        uint256 before = usdt6.balanceOf(address(pm2));
        vm.prank(address(ep2));
        pm2.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        assertEq(usdt6.balanceOf(address(pm2)) - before, 1000e6, "should charge full 6-decimal amount");
    }

    // ══════════════════════════════════════════════════════════════════
    // PAY-ECONOMICS-A: paymaster deposit < maxCost → InsufficientDeposit
    // ══════════════════════════════════════════════════════════════════

    function test_PAY_ECONOMICS_A() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice);
        usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(
            address(usdt), uint256(1000e18), block.timestamp + 1 hours
        );
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        // Override mock: paymaster has only 0.001 ETH, but maxCost is 1e16 (0.01 ETH)
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(0.001 ether))
        );

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InsufficientDeposit.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ══════════════════════════════════════════════════════════════════
    // PAY-ECONOMICS-B: price moves > 20% — setTokenPrice rejects
    // ══════════════════════════════════════════════════════════════════

    function test_PAY_ECONOMICS_B() public {
        address token = makeAddr("volatileToken");

        vm.startPrank(paymaster.owner());
        // Set initial price
        paymaster.setTokenPrice(token, 1000e18);

        // Try to change by 25% (> MAX_PRICE_CHANGE_BPS = 200 = 2%)
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PriceChangeTooHigh.selector));
        vm.warp(block.timestamp + 15 minutes);
        paymaster.setTokenPrice(token, 1250e18);

        // 2% change should succeed
        vm.warp(block.timestamp + 15 minutes);
        paymaster.setTokenPrice(token, 1020e18);

        // 2.1% change from new price should fail
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PriceChangeTooHigh.selector));
        vm.warp(block.timestamp + 15 minutes);
        paymaster.setTokenPrice(token, 1042e18);
        vm.stopPrank();
    }
}
