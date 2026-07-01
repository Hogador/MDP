// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOPaymaster, UserOperation, IPaymasterV06, IEntryPointView} from "../../src/MDAOPaymaster.sol";

contract MDAOPaymasterFuzz is Test {
    uint256 constant ALICE_KEY = 0xA11CE;
    address alice = vm.addr(ALICE_KEY);
    address constant ENTRY_POINT = address(0x1234);

    MockPermitToken mdao;
    MockLegacyToken usdt;
    MDAOPaymaster paymaster;

    function setUp() public {
        vm.etch(ENTRY_POINT, hex"00");
        mdao = new MockPermitToken("MDAO", "MDAO");
        usdt = new MockLegacyToken("USDTv1", "USDTv1");
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
    }

    function testFuzz_ComputeAmountToCharge(uint256 maxTokenAmount, uint256 actualGasCost) public {
        maxTokenAmount = bound(maxTokenAmount, 1, type(uint128).max);
        actualGasCost = bound(actualGasCost, 1, type(uint128).max);

        (uint256 amountToCharge, uint256 refund) = paymaster.computeAmountToCharge(maxTokenAmount, actualGasCost, address(usdt));

        assertLe(amountToCharge, maxTokenAmount);
        assertEq(amountToCharge + refund, maxTokenAmount, "charge + refund must equal maxTokenAmount");
    }

    function testFuzz_QuoteDeadlineValidation(uint256 offset) public {
        offset = bound(offset, 1, 10000);

        usdt.mint(alice, 1000e18);
        vm.prank(alice); usdt.approve(address(paymaster), 1000e18);

        uint256 deadline = block.timestamp + offset;

        bytes memory customData = abi.encodePacked(address(usdt), uint256(1000e18), deadline);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        if (offset < 300) {
            vm.prank(ENTRY_POINT);
            vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.DeadlineTooSoon.selector));
            paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        } else {
            vm.prank(ENTRY_POINT);
            paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        }
    }

    function testFuzz_MaxTokenAmountLimit(uint256 amount) public {
        vm.assume(amount > 0);

        usdt.mint(alice, amount);
        vm.prank(alice); usdt.approve(address(paymaster), amount);

        bytes memory customData = abi.encodePacked(address(usdt), amount, block.timestamp + 1 hours);
        bytes memory pmAndData = abi.encode(address(paymaster), customData);
        UserOperation memory op = _op(alice, pmAndData);

        if (amount > paymaster.maxTokenAmountLimit(address(usdt))) {
            vm.prank(ENTRY_POINT);
            vm.expectRevert(abi.encodeWithSelector(MDAOPaymaster.AmountTooHigh.selector));
            paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        } else {
            vm.prank(ENTRY_POINT);
            paymaster.validatePaymasterUserOp(op, bytes32(0), 1e16, customData);
        }
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
}

contract MockPermitToken {
    string public name;
    string public symbol;
    uint8 public constant decimals = 18;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    mapping(address => uint256) public nonces;
    bytes32 public constant PERMIT_TYPEHASH = keccak256("Permit(address owner,address spender,uint256 value,uint256 nonce,uint256 deadline)");
    bytes32 public immutable DOMAIN_SEPARATOR;
    constructor(string memory _name, string memory _symbol) {
        name = _name; symbol = _symbol;
        DOMAIN_SEPARATOR = keccak256(abi.encode(keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"), keccak256(bytes(name)), keccak256(bytes("1")), block.chainid, address(this)));
    }
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) { allowance[msg.sender][spender] = amount; return true; }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        uint256 allowed = allowance[from][msg.sender];
        if (allowed != type(uint256).max) { allowance[from][msg.sender] = allowed - amount; }
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract MockLegacyToken {
    string public name;
    string public symbol;
    uint8 public constant decimals = 18;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    constructor(string memory _name, string memory _symbol) { name = _name; symbol = _symbol; }
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) { allowance[msg.sender][spender] = amount; return true; }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        uint256 allowed = allowance[from][msg.sender];
        if (allowed != type(uint256).max) { allowance[from][msg.sender] = allowed - amount; }
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}
