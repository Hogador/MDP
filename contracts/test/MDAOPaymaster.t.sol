// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOPaymaster, UserOperation, IPaymasterV06, IEntryPointView} from "../src/MDAOPaymaster.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {TimelockController} from "@openzeppelin/contracts/governance/TimelockController.sol";
import {TrustProviderRegistry} from "../src/TrustProviderRegistry.sol";
import {EcdsaVerifier} from "../src/EcdsaVerifier.sol";

// ── Mock ERC-20 with EIP-2612 permit ─────────────────────────────────────
contract MockPermitToken {
    string public name;
    string public symbol;
    uint8 public constant decimals = 18;

    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    mapping(address => uint256) public nonces;

    bytes32 public constant PERMIT_TYPEHASH = keccak256(
        "Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)"
    );

    bytes32 public immutable DOMAIN_SEPARATOR;

    constructor(string memory _name, string memory _symbol) {
        name = _name;
        symbol = _symbol;
        DOMAIN_SEPARATOR = keccak256(
            abi.encode(
                keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"),
                keccak256(bytes(name)),
                keccak256(bytes("1")),
                block.chainid,
                address(this)
            )
        );
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
    function permit(address owner, address spender, uint256 value, uint256 deadline, uint8 v, bytes32 r, bytes32 s) external {
        require(deadline >= block.timestamp, "expired");
        bytes32 structHash = keccak256(
            abi.encode(PERMIT_TYPEHASH, owner, spender, value, nonces[owner]++, deadline)
        );
        bytes32 digest = keccak256(abi.encodePacked("\x19\x01", DOMAIN_SEPARATOR, structHash));
        address recovered = ecrecover(digest, v, r, s);
        require(recovered == owner, "bad sig");
        allowance[owner][spender] = value;
    }
}

// ── Mock ERC-20 without permit (like USDT) ──────────────────────────────
contract MockLegacyToken {
    string public name;
    string public symbol;
    uint8 public immutable decimals; // H-06: parametrized (6/8/18) — was constant 18
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    // F-004: simulate USDT false-return (not revert) on next transferFrom
    bool public failNextTransferFrom;

    constructor(string memory _name, string memory _symbol, uint8 _decimals) {
        name = _name;
        symbol = _symbol;
        decimals = _decimals;
    }
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function setFailNextTransferFrom(bool f) external { failNextTransferFrom = f; }
    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }
    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        // F-004: simulate USDT-style false return (not revert)
        if (failNextTransferFrom) {
            failNextTransferFrom = false;
            return false;
        }
        uint256 allowed = allowance[from][msg.sender];
        if (allowed != type(uint256).max) { allowance[from][msg.sender] = allowed - amount; }
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

// ── Tests ────────────────────────────────────────────────────────────────
contract MDAOPaymasterTest is Test {
    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);
    uint256 constant ALICE_KEY = 0xA11CE;
    address alice = vm.addr(ALICE_KEY);
    address bob = makeAddr("bob");
    address constant ENTRY_POINT = address(0x1234);

    MockPermitToken mdao;
    MockLegacyToken usdt;
    MDAOPaymaster paymaster;

    function setUp() public {
        vm.etch(ENTRY_POINT, hex"00"); // STOP — allows value receipts + passes extcodesize check
        mdao = new MockPermitToken("MDAO", "MDAO");
        usdt = new MockLegacyToken("USDTv1", "USDTv1", 18);
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), address(0));

        // Set token prices so amountToCharge >= maxTokenAmount → no refund (paymaster has no tokens)
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(mdao), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        // Mock entrypoint view methods
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

    // Build the custom paymasterData (what goes in the `bytes` part of abi.encode(address, bytes)):
    // token(20) || maxTokenAmount(32) || quoteDeadline(32) || [permitDeadline(32) || v(1) || r(32) || s(32)]
    function _encodeCustomData(address token, uint256 maxAmt) internal view returns (bytes memory) {
        return abi.encodePacked(token, maxAmt, block.timestamp + 1 hours);
    }

    function _encodeCustomDataWithPermit(address token, uint256 maxAmt, uint256 deadline, uint8 v, bytes32 r, bytes32 s)
        internal view returns (bytes memory)
    {
        return abi.encodePacked(token, maxAmt, block.timestamp + 1 hours, deadline, v, r, s);
    }

    // Build the full paymasterAndData = abi.encode(paymasterAddress, customData)
    function _encodePM(address token, uint256 maxAmt) internal view returns (bytes memory) {
        bytes memory customData = _encodeCustomData(token, maxAmt);
        return abi.encode(address(paymaster), customData);
    }

    function _encodePMWithPermit(address token, uint256 maxAmt, uint256 deadline, uint8 v, bytes32 r, bytes32 s)
        internal view returns (bytes memory)
    {
        bytes memory customData = _encodeCustomDataWithPermit(token, maxAmt, deadline, v, r, s);
        return abi.encode(address(paymaster), customData);
    }

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

    function _signPermit(address owner, uint256 pk, address spender, uint256 val, uint256 deadline)
        internal view returns (uint8 v, bytes32 r, bytes32 s)
    {
        bytes32 structHash = keccak256(abi.encode(
            mdao.PERMIT_TYPEHASH(), owner, spender, val, mdao.nonces(owner), deadline
        ));
        bytes32 digest = keccak256(abi.encodePacked("\x19\x01", mdao.DOMAIN_SEPARATOR(), structHash));
        return vm.sign(pk, digest);
    }

    // ── Quote deadline ──────────────────────────────────────────────

    function test_RevertWhen_QuoteExpired() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        // Build custom data with deadline in the past
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp - 1);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.QuoteExpired.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ── USDT (approval model) ────────────────────────────────────────

    function test_ValidateWithApproval() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertGt(ctx.length, 0);
    }

    function test_RevertWhen_InsufficientBalance() public {
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InsufficientBalance.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_RevertWhen_InsufficientAllowance() public {
        usdt.mint(alice, 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InsufficientAllowance.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_PostOpTransfer() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        uint256 before = usdt.balanceOf(address(paymaster));
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        assertGt(usdt.balanceOf(address(paymaster)), before);
    }

    function test_PostOpTransfersMaxTokenAmount() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(500e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        uint256 before = usdt.balanceOf(address(paymaster));
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        uint256 transferred = usdt.balanceOf(address(paymaster)) - before;
        assertEq(transferred, 500e18);
    }

    // ── MDAO (permit model) ──────────────────────────────────────────

    function test_ValidateWithPermit() public {
        mdao.mint(alice, 1000e18);
        (uint8 v, bytes32 r, bytes32 s) = _signPermit(alice, ALICE_KEY, address(paymaster), 1000e18, block.timestamp + 1 hours);

        bytes memory customData = _encodeCustomDataWithPermit(address(mdao), 1000e18, block.timestamp + 1 hours, v, r, s);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertGt(ctx.length, 0);
    }

    function test_PostOpWithPermit() public {
        mdao.mint(alice, 1000e18);
        (uint8 v, bytes32 r, bytes32 s) = _signPermit(alice, ALICE_KEY, address(paymaster), 1000e18, block.timestamp + 1 hours);

        bytes memory customData = _encodeCustomDataWithPermit(address(mdao), 1000e18, block.timestamp + 1 hours, v, r, s);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // Permit was executed during validation — allowance is now set
        assertEq(mdao.allowance(alice, address(paymaster)), 1000e18);

        uint256 before = mdao.balanceOf(address(paymaster));
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        assertGt(mdao.balanceOf(address(paymaster)), before);
    }

    // ── Permit nonce burn fix — allowance already sufficient ───────

    function test_PermitSkippedWhenAllowanceSufficient() public {
        mdao.mint(alice, 1000e18);
        vm.prank(alice); mdao.approve(address(paymaster), 1000e18);

        uint256 nonceBefore = mdao.nonces(alice);

        // Sign permit anyway (will be ignored by contract since allowance is enough)
        (uint8 v, bytes32 r, bytes32 s) = _signPermit(alice, ALICE_KEY, address(paymaster), 1000e18, block.timestamp + 1 hours);

        bytes memory customData = _encodeCustomDataWithPermit(address(mdao), 1000e18, block.timestamp + 1 hours, v, r, s);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertGt(ctx.length, 0);

        // Nonce should NOT have been incremented (permit was skipped)
        assertEq(mdao.nonces(alice), nonceBefore, "permit nonce was burned despite sufficient allowance");
    }

    function test_PermitRevertsWhenSignatureInvalid() public {
        mdao.mint(alice, 1000e18);

        // invalid v,r,s (random values)
        bytes memory customData = _encodeCustomDataWithPermit(
            address(mdao), 1000e18, block.timestamp + 1 hours,
            0x1b, bytes32(uint256(1)), bytes32(uint256(2))
        );
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PermitFailed.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ── Access control ───────────────────────────────────────────────

    function test_RevertWhen_NotEntryPoint() public {
        bytes memory customData = abi.encodePacked(address(usdt), uint256(0), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.Unauthorized.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 0, customData);
    }

    function test_RevertWhen_PostOpNotEntryPoint() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.Unauthorized.selector));
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 0, 0);
    }

    function test_RevertWhen_InvalidToken() public {
        bytes memory customData = abi.encodePacked(address(0xdead), uint256(0), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidToken.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 0, customData);
    }

    // ── Admin ────────────────────────────────────────────────────────

    function test_DepositForwardsToEntryPoint() public {
        vm.deal(alice, 1 ether);
        vm.prank(alice);
        vm.expectCall(ENTRY_POINT, 0.5 ether, "");
        paymaster.deposit{value: 0.5 ether}();
    }

    // F-009a/F-039: MIN_PRICE_BUFFER_BPS = 100 reverts below
    function test_SetPriceBufferBpsBelowMinimumReverts() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(MDAOPaymaster.PriceBufferTooLow.selector);
        paymaster.setPriceBufferBps(99);
    }

    function test_SetPriceBufferBpsAtMinimumSucceeds() public {
        vm.prank(paymaster.owner());
        paymaster.setPriceBufferBps(100);
        assertEq(paymaster.priceBufferBps(), 100);
    }

    // F-051: bound checks on setCooldownPeriod
    function test_SetCooldownPeriodBelowMinimumReverts() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(MDAOPaymaster.CooldownOutOfBounds.selector);
        paymaster.setCooldownPeriod(30); // 30 seconds < 1 minute
    }

    function test_SetCooldownPeriodAboveMaximumReverts() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(MDAOPaymaster.CooldownOutOfBounds.selector);
        paymaster.setCooldownPeriod(8 days); // 8 days > 7 days
    }

    function test_SetCooldownPeriodAtMinimumSucceeds() public {
        vm.prank(paymaster.owner());
        paymaster.setCooldownPeriod(1 minutes);
        assertEq(paymaster.cooldownPeriod(), 1 minutes);
    }

    function test_SetPermitSupport() public {
        mdao.mint(alice, 1000e18);
        (uint8 v, bytes32 r, bytes32 s) = _signPermit(alice, ALICE_KEY, address(paymaster), 1000e18, block.timestamp + 1 hours);

        vm.prank(paymaster.owner());
        paymaster.setPermitSupport(address(mdao), false);

        bytes memory customData = _encodeCustomDataWithPermit(address(mdao), 1000e18, block.timestamp + 1 hours, v, r, s);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PermitFailed.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ── Additional edge cases ─────────────────────────────────────

    function test_PostOpRevertedDoesNotThrow() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // postOpReverted mode should still transfer tokens
        uint256 before = usdt.balanceOf(address(paymaster));
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.postOpReverted, ctx, 1e15, 1e12);
        assertGt(usdt.balanceOf(address(paymaster)), before);
    }

    function test_DecodePaymasterDataExactBoundary() public {
        // customData at exactly offset + 97 bytes (= permit boundary)
        // Use MDAO which supports permit
        mdao.mint(alice, 1000e18);
        (uint8 v, bytes32 r, bytes32 s) = _signPermit(alice, ALICE_KEY, address(paymaster), 1000e18, block.timestamp + 1 hours);

        bytes memory customData = abi.encodePacked(
            address(mdao), uint256(1000e18), block.timestamp + 1 hours,
            uint256(block.timestamp + 1 hours), v, r, s
        );
        assertEq(customData.length, 84 + 97);

        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        vm.prank(ENTRY_POINT);
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_RevertWhen_NotOwnerSetsPermitSupport() public {
        vm.prank(bob);
        vm.expectRevert();
        paymaster.setPermitSupport(address(mdao), false);
    }

    // ══════════════════════════════════════════════════════════════════
    // Step 1a: withdrawTo replaces scheduleWithdraw
    // ══════════════════════════════════════════════════════════════════

    function test_WithdrawToTokens() public {
        usdt.mint(address(paymaster), 1000e18);
        vm.prank(paymaster.owner());
        paymaster.setDailyWithdrawalCapBps(10000);

        uint256 before = usdt.balanceOf(alice);
        vm.prank(paymaster.owner());
        paymaster.withdrawTo(address(usdt), alice, 500e18);
        assertEq(usdt.balanceOf(alice) - before, 500e18);
    }

    function test_RevertWhen_WithdrawToExceedsDailyCap() public {
        usdt.mint(address(paymaster), 1000e18);
        // 50% of 1000e18 = 500e18 per day (default cap)

        vm.prank(paymaster.owner());
        paymaster.withdrawTo(address(usdt), alice, 500e18);
        assertEq(paymaster.dailyWithdrawnToday(), 500e18);

        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.DailyCapExceeded.selector));
        paymaster.withdrawTo(address(usdt), alice, 1);
    }

    // ── Deprecation & Exit Window ──

    function test_InitiateDeprecation() public {
        vm.prank(paymaster.owner());
        vm.expectEmit(true, false, false, true);
        emit MDAOPaymaster.DeprecationInitiated(block.timestamp, block.timestamp + 7 days);
        paymaster.initiateDeprecation();

        assertTrue(paymaster.isDeprecated());
        assertEq(paymaster.exitWindowEnd(), block.timestamp + 7 days);
    }

    function test_NormalOpsStillWorkDuringExitWindow() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));

        vm.prank(paymaster.owner());
        paymaster.initiateDeprecation();

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertGt(ctx.length, 0);
    }

    function test_RevertWhen_DoubleDeprecation() public {
        vm.prank(paymaster.owner());
        paymaster.initiateDeprecation();

        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AlreadyDeprecated.selector));
        paymaster.initiateDeprecation();
    }

    function test_RevertWhen_RevertWhenNotDeprecatedExitWindowEnd() public {
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.NotDeprecated.selector));
        paymaster.exitWindowEnd();
    }

    function test_FinalizeDeprecationAfterWindow() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));

        vm.prank(paymaster.owner());
        paymaster.initiateDeprecation();

        vm.warp(block.timestamp + 8 days);

        vm.prank(paymaster.owner());
        paymaster.finalizeDeprecation();

        assertTrue(paymaster.isDeprecated());

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.Unauthorized.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_RevertWhen_FinalizeBeforeWindowEnds() public {
        vm.prank(paymaster.owner());
        paymaster.initiateDeprecation();

        vm.warp(block.timestamp + 3 days);

        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.ExitWindowActive.selector));
        paymaster.finalizeDeprecation();
    }

    function test_RevertWhen_FinalizeNotDeprecated() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.NotDeprecated.selector));
        paymaster.finalizeDeprecation();
    }

    // ══════════════════════════════════════════════════════════════════
    // C-1: Trusted signer EIP-712 verification (immutable)
    // ══════════════════════════════════════════════════════════════════

    uint256 constant SIGNER_KEY = 0xBEEF;
    address signer = vm.addr(SIGNER_KEY);

    function _encodeQuoteDigest(
        address sender,
        address token,
        uint256 maxTokenAmount,
        uint256 maxGasPrice,
        uint256 quoteDeadline,
        uint256 nonce
    ) internal view returns (bytes32) {
        bytes32 structHash = keccak256(abi.encode(
            paymaster.QUOTE_TYPEHASH(),
            sender,
            token,
            maxTokenAmount,
            maxGasPrice,
            quoteDeadline,
            nonce
        ));
        return keccak256(abi.encodePacked(
            "\x19\x01",
            paymaster.DOMAIN_SEPARATOR(),
            structHash
        ));
    }

    // C-8: build paymasterData with EIP-712 sig suffix
    function _packWithSig(bytes memory base, bytes memory sig) internal pure returns (bytes memory) {
        bytes8 magic = hex"22e325a297439656";
        bytes2 lenHex = bytes2(uint16(sig.length));
        return abi.encodePacked(base, sig, lenHex, magic);
    }

    function test_ValidateWithTrustedSigner_EIP712() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 maxGas = 100 gwei;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, maxGas, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        // C-8: EIP-712 sig in paymasterData suffix, not userOp.signature
        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = maxGas;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertGt(ctx.length, 0);
        assertEq(paymaster.nextQuoteNonce(alice), 1);
    }

    function test_RevertWhen_InvalidTrustedSigner_EIP712() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(ALICE_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidSigner.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_SkipsSignerCheckWhenZero() public {
        assertEq(paymaster.trustedSigner(), address(0));

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, customData);

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
    }

    function test_RevertWhen_ReplayNonce() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        // First call succeeds
        vm.prank(ENTRY_POINT);
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // Replay with same nonce → InvalidSigner (nonce consumed, digest mismatch)
        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidSigner.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_RevertWhen_TamperedQuote() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        // Sign for 1000e18 but send 2000e18 in customData
        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), uint256(2000e18), deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidSigner.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ══════════════════════════════════════════════════════════════════
    // F-04: MAX_BLOCK_FAILURE_THRESHOLD cap
    // ══════════════════════════════════════════════════════════════════

    function test_SetBlockFailureThresholdWithinCap() public {
        vm.prank(paymaster.owner());
        vm.expectEmit(false, false, false, true);
        emit MDAOPaymaster.BlockFailureThresholdUpdated(5, 8);
        paymaster.setBlockFailureThreshold(8);
        assertEq(paymaster.blockFailureThreshold(), 8);
    }

    function test_SetBlockFailureThresholdAtCap() public {
        vm.prank(paymaster.owner());
        paymaster.setBlockFailureThreshold(10);
        assertEq(paymaster.blockFailureThreshold(), 10);
    }

    function test_RevertWhen_BlockFailureThresholdExceedsCap() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AmountTooHigh.selector));
        paymaster.setBlockFailureThreshold(11);
    }

    // ══════════════════════════════════════════════════════════════════
    // F-07: setTokenPrice first-price bypass fix
    // ══════════════════════════════════════════════════════════════════

    function test_RevertWhen_SetTokenPriceToZero() public {
        address newToken = makeAddr("newToken");
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PriceChangeTooHigh.selector));
        paymaster.setTokenPrice(newToken, 0);
    }

    function test_SetTokenPriceFirstTime() public {
        address newToken = makeAddr("newToken");
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(newToken, 1e18);
        assertEq(paymaster.tokenPrice(newToken), 1e18);
    }

    // ══════════════════════════════════════════════════════════════════
    // F-08: 2-step ownership transfer
    // ══════════════════════════════════════════════════════════════════

    function test_TransferOwnershipInitiates() public {
        vm.prank(paymaster.owner());
        vm.expectEmit(true, true, false, false);
        emit MDAOPaymaster.OwnershipTransferStarted(paymaster.owner(), bob);
        paymaster.transferOwnership(bob);
        assertEq(paymaster.pendingOwner(), bob);
        assertEq(paymaster.owner(), paymaster.owner()); // owner unchanged
    }

    function test_AcceptOwnership() public {
        vm.startPrank(paymaster.owner());
        paymaster.transferOwnership(bob);
        vm.stopPrank();

        vm.expectEmit(true, true, false, false);
        emit OwnershipTransferred(paymaster.owner(), bob);
        vm.prank(bob);
        paymaster.acceptOwnership();
        assertEq(paymaster.owner(), bob);
        assertEq(paymaster.pendingOwner(), address(0));
    }

    function test_RevertWhen_AcceptOwnershipNotPending() public {
        vm.startPrank(paymaster.owner());
        paymaster.transferOwnership(bob);
        vm.stopPrank();

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.Unauthorized.selector));
        paymaster.acceptOwnership();
    }

    function test_RevertWhen_TransferOwnershipToZero() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.NoTransferToZeroAddress.selector));
        paymaster.transferOwnership(address(0));
    }

    function test_RevertWhen_TransferOwnershipNotOwner() public {
        vm.prank(bob);
        vm.expectRevert();
        paymaster.transferOwnership(bob);
    }

    // ══════════════════════════════════════════════════════════════════
    // F-12: PriceUpdated event includes oldPrice
    // ══════════════════════════════════════════════════════════════════

    function test_PriceUpdatedEmitsOldPrice() public {
        address newToken = makeAddr("evtToken");
        vm.startPrank(paymaster.owner());
        paymaster.setTokenPrice(newToken, 100e18);

        vm.expectEmit(true, false, false, true);
        emit MDAOPaymaster.PriceUpdated(newToken, 100e18, 101e18);
        vm.warp(block.timestamp + 15 minutes);
        paymaster.setTokenPrice(newToken, 101e18);
        vm.stopPrank();
    }

    // ── Branch Coverage Tests ───────────────────────────────────────────

    function test_PostOp_OpReverted_NoCharge() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        uint256 aliceBefore = usdt.balanceOf(alice);
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opReverted, ctx, 1e15, 1e12);
        assertEq(usdt.balanceOf(alice), aliceBefore, "opReverted should not charge");
    }

    function test_PostOp_TransferFails_EmitsPaymentFailed() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // Revoke approval after validation so transferFrom fails in postOp
        vm.prank(alice); usdt.approve(address(paymaster), 0);

        vm.prank(ENTRY_POINT);
        vm.expectEmit(true, true, false, false);
        emit MDAOPaymaster.PaymentFailed(alice, IERC20(address(usdt)), 1000e18, MDAOPaymaster.FailureReason.InsufficientAllowance);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);

        assertEq(usdt.balanceOf(alice), 1000e18, "no tokens taken on failed transfer");
        // F-004: user-fixable error resets counter, doesn't block
        assertEq(paymaster.failedPaymentCount(alice), 0);
    }

    function test_ValidatePaymasterUserOp_PriceZero_RejectsNonZero() public {
        // Cover the branch: tokenPrice[token] == 0 && maxTokenAmount > 0 → AmountTooHigh.
        // _getTokenConfig only recognizes mdao/usdt, so use a valid token and a huge
        // maxTokenAmount that exceeds the oracle-based limit (AmountTooHigh on line 340).
        usdt.mint(alice, 2e27);
        vm.prank(alice); usdt.approve(address(paymaster), 2e27);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(2e27), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AmountTooHigh.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_ValidatePaymasterUserOp_GasPriceTooHigh() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        // maxGasPrice is 1000 gwei in setUp; use 1001 gwei
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));
        op.maxFeePerGas = 1001 gwei;

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.GasPriceTooHigh.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_ValidatePaymasterUserOp_InsufficientDeposit() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));

        // Mock EP balance to 0 (override setUp's 10 ether)
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(0))
        );

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InsufficientDeposit.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ══════════════════════════════════════════════════════════════════
    // FP-AUTH-001: EIP-712 quote verification — regression tests
    // ══════════════════════════════════════════════════════════════════

    function testRejectsQuoteWithoutSignature() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        // PaymasterData without signature suffix (length < _SUFFIX_LEN)
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        // OZ ECDSA.recover reverts with ECDSAInvalidSignature() for random bytes
        vm.expectRevert();
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function testRejectsTamperedMaxTokenAmount() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        // Sign for 1000e18
        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        // Tamper: use 2000e18 in data but signed 1000e18
        bytes memory base = abi.encodePacked(address(usdt), uint256(2000e18), deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidSigner.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function testRejectsReplayedQuote() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        // First use succeeds
        vm.prank(ENTRY_POINT);
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // Replay with same nonce → digest mismatch → InvalidSigner
        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidSigner.selector));
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function testFuzzRejectsMalleableSignature(uint256 s) public {
        // Bound s to the malleable range (> secp256k1n/2)
        // Use bound instead of assume to avoid rejection overflow
        uint256 SECP256K1N_DIV_2 = 0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0;
        s = bound(s, SECP256K1N_DIV_2 + 1, type(uint256).max);

        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(
            ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether))
        );
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, ) = vm.sign(SIGNER_KEY, digest);
        // Replace s with a malleable value — OZ ECDSA.recover rejects s > n/2
        bytes32 malleableS = bytes32(s);
        bytes memory sig = abi.encodePacked(r, malleableS, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;
        vm.prank(ENTRY_POINT);
        vm.expectRevert();
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // ══════════════════════════════════════════════════════════════════
    // F-004: Anti-griefing tests
    // ══════════════════════════════════════════════════════════════════

    // Helper: approve, validate, then revoke allowance to cause postOp failure
    function _causePostOpFailure(address sender, uint256 pk) internal returns (bytes memory ctx) {
        usdt.mint(sender, 1000e18);
        vm.prank(sender); usdt.approve(address(paymaster), 1000e18);
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(sender, abi.encode(address(paymaster), customData));
        vm.prank(ENTRY_POINT);
        (ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        // Revoke approval so transferFrom fails
        vm.prank(sender); usdt.approve(address(paymaster), 0);
    }

    function testRepeatedFailuresTriggerBlock() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        for (uint256 i = 0; i < 5; i++) {
            // Re-mint tokens and re-approve for each iteration
            if (i > 0) {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);
            }

            bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
            UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));
            vm.prank(ENTRY_POINT);
            (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

            // Set token to return false (like USDT) even though balance+allowance sufficient
            usdt.setFailNextTransferFrom(true);

            vm.prank(ENTRY_POINT);
            paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        }

        // After 5 TokenRevert failures, alice should be blocked
        assertTrue(paymaster.blockedUntil(alice) > block.timestamp, "alice should be blocked");
        assertEq(paymaster.failedPaymentCount(alice), 5);
    }

    function testInsufficientAllowanceDoesNotBlock() public {
        bytes memory ctx = _causePostOpFailure(alice, ALICE_KEY);

        // First failure: InsufficientAllowance → should not increment counter
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);

        assertEq(paymaster.failedPaymentCount(alice), 0, "counter reset on user-fixable error");
        assertEq(paymaster.blockedUntil(alice), 0, "not blocked on user-fixable error");
    }

    function testInsufficientBalanceDoesNotBlock() public {
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));
        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        // Drain alice's balance after validation (use startPrank to cover both calls)
        vm.startPrank(alice);
        uint256 bal = usdt.balanceOf(alice);
        usdt.transfer(bob, bal);
        vm.stopPrank();

        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);

        assertEq(paymaster.failedPaymentCount(alice), 0, "counter reset on user-fixable error");
        assertEq(paymaster.blockedUntil(alice), 0, "not blocked on user-fixable error");
    }

    function testSuccessDoesNotResetCounterWithinCooldown() public {
        // Cause a TokenRevert failure
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 1 hours);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));
        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        usdt.setFailNextTransferFrom(true);
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);

        assertEq(paymaster.failedPaymentCount(alice), 1, "counter incremented on TokenRevert");

        // Succeed immediately (within BLOCK_COOLDOWN=1h) — counter should NOT reset
        UserOperation memory op2 = _op(alice, abi.encode(address(paymaster), customData));
        vm.prank(ENTRY_POINT);
        (bytes memory ctx2,) = paymaster.validatePaymasterUserOp(op2, bytes32(0), 1e16, customData);
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx2, 1e15, 1e12);

        assertEq(paymaster.failedPaymentCount(alice), 1, "counter NOT reset within BLOCK_COOLDOWN");
    }

    function testSuccessResetsFailureCounterAfterCooldown() public {
        // Cause a TokenRevert failure
        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);
        // Use a generous deadline so it doesn't expire after warp
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), block.timestamp + 2 days);
        UserOperation memory op = _op(alice, abi.encode(address(paymaster), customData));
        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        usdt.setFailNextTransferFrom(true);
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);

        assertEq(paymaster.failedPaymentCount(alice), 1, "counter incremented on TokenRevert");

        // Warp past BLOCK_COOLDOWN (1 hour) so counter can reset on success
        vm.warp(block.timestamp + paymaster.BLOCK_COOLDOWN() + 1);

        // Now succeed: counter should reset (deadline is still 2 days - ~1h = far in future)
        UserOperation memory op2 = _op(alice, abi.encode(address(paymaster), customData));
        vm.prank(ENTRY_POINT);
        (bytes memory ctx2,) = paymaster.validatePaymasterUserOp(op2, bytes32(0), 1e16, customData);
        vm.prank(ENTRY_POINT);
        paymaster.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx2, 1e15, 1e12);

        assertEq(paymaster.failedPaymentCount(alice), 0, "counter reset on success after BLOCK_COOLDOWN");
    }

    // ══════════════════════════════════════════════════════════════════
    // F-005: maxDeviationBps price change guard
    // ══════════════════════════════════════════════════════════════════

    function test_RevertWhen_PriceDeviationExceedsMax() public {
        address newToken = makeAddr("volatileToken");
        uint256 initialPrice = 1000e18;
        uint256 maxDev = paymaster.maxDeviationBps(); // 1000 = 10%

        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(newToken, initialPrice);
        assertEq(paymaster.tokenPrice(newToken), initialPrice);

        // A change >10% should revert (caught by maxDeviationBps, though MAX_PRICE_CHANGE_BPS=200 is stricter)
        uint256 bigPrice = initialPrice + initialPrice * (maxDev + 1) / 10000 + 1;
        vm.warp(block.timestamp + paymaster.PRICE_COOLDOWN() + 1);
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PriceChangeTooHigh.selector));
        paymaster.setTokenPrice(newToken, bigPrice);
    }

    // F-005 regression: cooldown prevents compound price manipulation
    function test_RevertWhen_PriceUpdateCooldownActive() public {
        address tk = makeAddr("testToken");
        uint256 p = 1000e18;

        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(tk, p);
        assertEq(paymaster.tokenPrice(tk), p);

        // Immediate second update should revert (cooldown not elapsed)
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PriceCooldownActive.selector));
        paymaster.setTokenPrice(tk, p + 1);

        // After cooldown, update succeeds
        vm.warp(block.timestamp + paymaster.PRICE_COOLDOWN() + 1);
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(tk, p + 1);
        assertEq(paymaster.tokenPrice(tk), p + 1);
    }

    // F-040 regression: price cooldown enforced (exact name match)
    function testPriceUpdateCooldownEnforced() public {
        address tk = makeAddr("cooldownToken");
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(tk, 1000e18);
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.PriceCooldownActive.selector));
        paymaster.setTokenPrice(tk, 1000e18 + 1);
    }

    // F-040 regression: compound price change per day
    function testCompoundPriceChangePerDay() public {
        address tk = makeAddr("compoundTest");
        uint256 p = 1000e18;
        vm.startPrank(paymaster.owner());
        paymaster.setTokenPrice(tk, p);
        uint256 maxChangePerUpdate = p * paymaster.MAX_PRICE_CHANGE_BPS() / 10000;
        uint256 maxDay = maxChangePerUpdate;
        for (uint256 i = 0; i < 24; i++) {
            vm.warp(block.timestamp + paymaster.PRICE_COOLDOWN() + 1);
            paymaster.setTokenPrice(tk, paymaster.tokenPrice(tk) + maxChangePerUpdate);
        }
        // After 24 cooldown cycles (6h total), price should be bounded
        assertLe(paymaster.tokenPrice(tk), p + 24 * maxDay, "compound price growth bounded");
        vm.stopPrank();
    }

    // ══════════════════════════════════════════════════════════════════
    // F-018: Daily withdrawal cap tests (via withdrawTo)
    // ══════════════════════════════════════════════════════════════════

    function test_RevertWhen_DailyCapExceeded() public {
        usdt.mint(address(paymaster), 1000e18);
        // 50% of 1000e18 = 500e18 per day (default cap)

        vm.prank(paymaster.owner());
        paymaster.withdrawTo(address(usdt), alice, 500e18);
        assertEq(paymaster.dailyWithdrawnToday(), 500e18);

        // Second withdrawal of 1 wei exceeds cap
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.DailyCapExceeded.selector));
        paymaster.withdrawTo(address(usdt), alice, 1);
    }

    function test_DailyCapResetsAfter24Hours() public {
        usdt.mint(address(paymaster), 1000e18);
        vm.prank(paymaster.owner());
        paymaster.setDailyWithdrawalCapBps(10000);

        vm.prank(paymaster.owner());
        paymaster.withdrawTo(address(usdt), alice, 500e18);

        // Warp past reset time
        vm.warp(paymaster.dailyWithdrawalResetAt() + 1);

        vm.prank(paymaster.owner());
        paymaster.withdrawTo(address(usdt), alice, 500e18);

        assertEq(paymaster.dailyWithdrawnToday(), 500e18);
    }

    function test_SetDailyWithdrawalCap() public {
        vm.prank(paymaster.owner());
        paymaster.setDailyWithdrawalCapBps(1000); // 10%
        assertEq(paymaster.dailyWithdrawalCapBps(), 1000);
    }

    function test_RevertWhen_DailyCapExceedsMax() public {
        vm.prank(paymaster.owner());
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AmountTooHigh.selector));
        paymaster.setDailyWithdrawalCapBps(10001);
    }

    // ══════════════════════════════════════════════════════════════════
    // TimelockController integration tests
    // ══════════════════════════════════════════════════════════════════

    function test_TimelockAdminCanSetMaxGasPrice() public {
        TimelockController timelock = _deployTimelockAndTransferOwnership();

        // Call admin function through timelock
        bytes memory setData = abi.encodeWithSelector(paymaster.setMaxGasPrice.selector, uint256(500 gwei));
        timelock.schedule(address(paymaster), 0, setData, bytes32(0), bytes32("test2"), 2 days);
        vm.warp(block.timestamp + 2 days);
        timelock.execute(address(paymaster), 0, setData, bytes32(0), bytes32("test2"));

        assertEq(paymaster.maxGasPrice(), 500 gwei);
    }

    function test_TimelockAdminCanWithdrawTo() public {
        TimelockController timelock = _deployTimelockAndTransferOwnership();

        usdt.mint(address(paymaster), 1000e18);
        vm.prank(address(timelock));
        paymaster.setDailyWithdrawalCapBps(10000);

        bytes memory withdrawData = abi.encodeWithSelector(paymaster.withdrawTo.selector, address(usdt), alice, uint256(500e18));
        timelock.schedule(address(paymaster), 0, withdrawData, bytes32(0), bytes32("withdraw"), 2 days);
        vm.warp(block.timestamp + 2 days);
        timelock.execute(address(paymaster), 0, withdrawData, bytes32(0), bytes32("withdraw"));

        assertEq(usdt.balanceOf(alice), 500e18);
    }

    function test_RevertWhen_NonOwnerCallsAdminDirectly() public {
        TimelockController timelock = _deployTimelockAndTransferOwnership();

        // this contract (test) is no longer owner
        vm.expectRevert();
        paymaster.setMaxGasPrice(100 gwei);
    }

    function _deployTimelockAndTransferOwnership() internal returns (TimelockController) {
        address[] memory admins = new address[](1);
        admins[0] = address(this);
        TimelockController timelock = new TimelockController(2 days, admins, admins, address(0));

        // 2-step transfer
        paymaster.transferOwnership(address(timelock));

        bytes memory acceptData = abi.encodeWithSelector(paymaster.acceptOwnership.selector);
        timelock.schedule(address(paymaster), 0, acceptData, bytes32(0), bytes32("test"), 2 days);
        vm.warp(block.timestamp + 2 days);
        timelock.execute(address(paymaster), 0, acceptData, bytes32(0), bytes32("test"));

        assertEq(paymaster.owner(), address(timelock));
        return timelock;
    }

    // ══════════════════════════════════════════════════
    // Step 1b: TrustProviderRegistry integration
    // ══════════════════════════════════════════════════

    function test_PaymasterUsesRegistry() public {
        EcdsaVerifier verifier = new EcdsaVerifier(signer);
        TrustProviderRegistry reg = new TrustProviderRegistry();
        bytes32 providerId = bytes32(uint256(uint160(signer)));
        reg.registerProvider(providerId, address(verifier));

        // Replace class-level paymaster with one using registry
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether)));
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);
        vm.prank(paymaster.owner());
        paymaster.setRegistry(address(reg));

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertEq(paymaster.nextQuoteNonce(alice), 1);
    }

    function test_RevertWhen_RegistryRejectsInactiveProvider() public {
        EcdsaVerifier verifier = new EcdsaVerifier(signer);
        TrustProviderRegistry reg = new TrustProviderRegistry();
        bytes32 providerId = bytes32(uint256(uint160(signer)));
        reg.registerProvider(providerId, address(verifier));
        reg.setProviderStatus(providerId, TrustProviderRegistry.ProviderStatus.DEPRECATED);

        // Replace class-level paymaster with one using registry
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.prank(paymaster.owner());
        paymaster.setRegistry(address(reg));

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        vm.expectRevert();
        paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    function test_RegistryFallbackToTrustedSigner() public {
        // registry = address(0) — must use existing trustedSigner flow
        // Replace class-level paymaster with one that has trustedSigner set
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt), signer);
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(paymaster)),
            abi.encode(uint256(10 ether)));
        vm.prank(paymaster.owner());
        paymaster.setTokenPrice(address(usdt), 1e24);
        vm.prank(paymaster.owner());
        paymaster.setMaxGasPrice(1000 gwei);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 maxAmt = 1000e18;
        uint256 deadline = block.timestamp + 1 hours;
        uint256 nonce = paymaster.nextQuoteNonce(alice);

        bytes32 digest = _encodeQuoteDigest(alice, address(usdt), maxAmt, 100 gwei, deadline, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(SIGNER_KEY, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        bytes memory base = abi.encodePacked(address(usdt), maxAmt, deadline);
        bytes memory customData = _packWithSig(base, sig);
        UserOperation memory op = _op(alice, customData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertEq(paymaster.nextQuoteNonce(alice), 1);
    }

    // ── H-06: decimals handling ─────────────────────────────────────

    // 6-dec token: charge must be computed in base units (not 18-dec notation)
    function test_H06_SixDecimalsChargeInBaseUnits() public {
        MockLegacyToken usdt6 = new MockLegacyToken("USDT6", "USDT6", 6);
        MDAOPaymaster pm6 = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt6), address(0));
        vm.etch(ENTRY_POINT, hex"00"); // re-etch after new contract creation (extcodesize check at deploy)
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(pm6)),
            abi.encode(uint256(10 ether)));
        vm.prank(pm6.owner());
        pm6.setTokenPrice(address(usdt6), 1e24);
        vm.prank(pm6.owner());
        pm6.setMaxGasPrice(1000 gwei);

        usdt6.mint(alice, 1000e6);
        vm.prank(alice); usdt6.approve(address(pm6), 1000e6);

        // maxAmt = 500 USDT6 in base units (500 * 1e6)
        bytes memory customData = abi.encodePacked(address(usdt6), uint256(500e6), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(pm6), customData);
        UserOperation memory op = _op(alice, pmAndData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = pm6.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        uint256 before = usdt6.balanceOf(address(pm6));
        vm.prank(ENTRY_POINT);
        pm6.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        uint256 transferred = usdt6.balanceOf(address(pm6)) - before;
        // computeAmountToCharge(actualGasCost=1e15): 1e15*1e24/1e18 = 1e21 (18-dec) → 6-dec base = 1e15 units.
        // Clamped to maxTokenAmount = 500e6 base units.
        assertEq(transferred, 500e6, "6-dec charge must be clamped to maxTokenAmount in base units");
    }

    // 8-dec token: same flow, base units = 8-decimals
    function test_H06_EightDecimalsChargeInBaseUnits() public {
        MockLegacyToken usdt8 = new MockLegacyToken("USDT8", "USDT8", 8);
        MDAOPaymaster pm8 = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt8), address(0));
        vm.etch(ENTRY_POINT, hex"00");
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(pm8)),
            abi.encode(uint256(10 ether)));
        vm.prank(pm8.owner());
        pm8.setTokenPrice(address(usdt8), 1e24);
        vm.prank(pm8.owner());
        pm8.setMaxGasPrice(1000 gwei);

        usdt8.mint(alice, 1000e8);
        vm.prank(alice); usdt8.approve(address(pm8), 1000e8);

        bytes memory customData = abi.encodePacked(address(usdt8), uint256(500e8), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(pm8), customData);
        UserOperation memory op = _op(alice, pmAndData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = pm8.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        uint256 before = usdt8.balanceOf(address(pm8));
        vm.prank(ENTRY_POINT);
        pm8.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
        uint256 transferred = usdt8.balanceOf(address(pm8)) - before;
        assertEq(transferred, 500e8, "8-dec charge must be in base units");
    }

    // Rounding UP integration: 18-dec amount with fractional base units charges ceil
    function test_H06_PostOpChargesCeilForFractionalBaseUnits() public {
        MockLegacyToken usdt6 = new MockLegacyToken("USDT6", "USDT6", 6);
        MDAOPaymaster pm6 = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt6), address(0));
        vm.etch(ENTRY_POINT, hex"00");
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(pm6)),
            abi.encode(uint256(10 ether)));
        vm.prank(pm6.owner());
        pm6.setTokenPrice(address(usdt6), 1e24);
        vm.prank(pm6.owner());
        pm6.setMaxGasPrice(1000 gwei);

        usdt6.mint(alice, 1000e6);
        vm.prank(alice); usdt6.approve(address(pm6), 1000e6);

        // maxAmt large enough not to clamp: 900e6 base units.
        bytes memory customData = abi.encodePacked(address(usdt6), uint256(900e6), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(pm6), customData);
        UserOperation memory op = _op(alice, pmAndData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = pm6.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        uint256 before = usdt6.balanceOf(address(pm6));
        vm.prank(ENTRY_POINT);
        // actualGasCost = 1234567890123 wei → 18-dec amount = 1234567890123 * 1e24 / 1e18
        // = 1.234567890123e18 → base6 = 1.234567890123e18 * 1e6 / 1e18 = 1234567.890123
        // → ceil = 1234568 (< maxAmt 900e6, no clamp)
        pm6.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1234567890123, 1e12);
        uint256 transferred = usdt6.balanceOf(address(pm6)) - before;
        assertEq(transferred, 1234568, "fractional base units must round UP");
    }

    // Charge that rounds to 0 base units must revert AmountTooLow
    function test_H06_RevertWhen_AmountTooLow() public {
        MockLegacyToken usdt6 = new MockLegacyToken("USDT6", "USDT6", 6);
        MDAOPaymaster pm6 = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt6), address(0));
        vm.etch(ENTRY_POINT, hex"00");
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(pm6)),
            abi.encode(uint256(10 ether)));
        // price=100 (set once, no cooldown/deviation issue): actualGasCost=1e15 →
        // 18-dec amount = 1e15*100/1e18 = 0.1 → 0 base units → AmountTooLow.
        // maxTokenAmount=1 passes maxAllowed (maxAllowed = ceil(1.05) = 1).
        vm.prank(pm6.owner());
        pm6.setTokenPrice(address(usdt6), 100);
        vm.prank(pm6.owner());
        pm6.setMaxGasPrice(1000 gwei);

        usdt6.mint(alice, 1000e6);
        vm.prank(alice); usdt6.approve(address(pm6), 1000e6);

        bytes memory customData = abi.encodePacked(address(usdt6), uint256(1), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(pm6), customData);
        UserOperation memory op = _op(alice, pmAndData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx,) = pm6.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AmountTooLow.selector));
        pm6.postOp(IPaymasterV06.PostOpMode.opSucceeded, ctx, 1e15, 1e12);
    }

    // setTokenDecimals: only 6/8/18 accepted; registry updated; limit recalculated
    function test_H06_SetTokenDecimalsValidationAndLimit() public {
        vm.prank(paymaster.owner());
        paymaster.setTokenDecimals(address(usdt), 6);
        (,, uint8 d6) = paymaster.usdtConfig();
        assertEq(d6, 6);
        assertEq(paymaster.maxTokenAmountLimit(address(usdt)), 10_000 * 1e6, "limit must follow decimals");

        vm.prank(paymaster.owner());
        paymaster.setTokenDecimals(address(usdt), 8);
        (,, uint8 d8) = paymaster.usdtConfig();
        assertEq(d8, 8);
        assertEq(paymaster.maxTokenAmountLimit(address(usdt)), 10_000 * 1e8);

        vm.prank(paymaster.owner());
        paymaster.setTokenDecimals(address(usdt), 18);
        (,, uint8 d18) = paymaster.usdtConfig();
        assertEq(d18, 18);

        address own = paymaster.owner(); // owner() before expectRevert — else it consumes the revert expectation
        vm.prank(own);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidToken.selector));
        paymaster.setTokenDecimals(address(usdt), 0);
        vm.prank(own);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidToken.selector));
        paymaster.setTokenDecimals(address(usdt), 4);
    }

    // constructor must reject tokens with unsupported decimals (e.g. 0)
    function test_H06_RevertWhen_ConstructorUnsupportedDecimals() public {
        MockLegacyToken usdt0 = new MockLegacyToken("USDT0", "USDT0", 0);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.InvalidToken.selector));
        new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt0), address(0));
    }

    // maxAllowed uses decimals: for 6-dec token a large 18-dec maxTokenAmount is rejected
    function test_H06_MaxAllowedDecimalsAware() public {
        MockLegacyToken usdt6 = new MockLegacyToken("USDT6", "USDT6", 6);
        MDAOPaymaster pm6 = new MDAOPaymaster(ENTRY_POINT, address(mdao), address(usdt6), address(0));
        vm.etch(ENTRY_POINT, hex"00");
        vm.mockCall(ENTRY_POINT,
            abi.encodeWithSelector(IEntryPointView.balanceOf.selector, address(pm6)),
            abi.encode(uint256(10 ether)));
        vm.prank(pm6.owner());
        pm6.setTokenPrice(address(usdt6), 1e24);
        vm.prank(pm6.owner());
        pm6.setMaxGasPrice(1000 gwei);

        usdt6.mint(alice, 1000e6);
        vm.prank(alice); usdt6.approve(address(pm6), 1000e6);

        // maxCost=1e16, price=1e24, buffer 500bps → maxAllowed18 = 1e16*1e24*1.05/1e18 = 1.05e22
        // base6 = ceil(1.05e22 * 1e6 / 1e18) = 1.05e10. maxTokenAmount = 2e10 > 1.05e10 → revert
        bytes memory customData = abi.encodePacked(address(usdt6), uint256(2e10), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(pm6), customData);
        UserOperation memory op = _op(alice, pmAndData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AmountTooHigh.selector));
        pm6.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
    }

    // 18-dec token (default config): maxAllowed stays in 18-dec notation — regression
    function test_H06_EighteenDecimalsMaxAllowedUnchanged() public {
        usdt.mint(alice, 2e22);
        vm.prank(alice); usdt.approve(address(paymaster), 2e22);

        // maxCost=1e16, price=1e24, buffer 500bps → maxAllowed18 = 1.05e22.
        // maxTokenAmount = 1e22 < 1.05e22 → accepted (18-dec notation, no conversion).
        bytes memory customData = abi.encodePacked(address(usdt), uint256(1e22), block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);
        op.maxFeePerGas = 100 gwei;

        vm.prank(ENTRY_POINT);
        (bytes memory ctx, uint256 vd) = paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        assertEq(vd, 0);
        assertGt(ctx.length, 0);
    }
}
