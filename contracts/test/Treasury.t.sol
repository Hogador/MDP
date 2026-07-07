// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {Treasury} from "../src/Treasury.sol";
import {ITreasury} from "../src/interfaces/ITreasury.sol";
import {PaymentSplitterFactory} from "../src/PaymentSplitterFactory.sol";
import {IPaymentSplitter} from "../src/interfaces/IPaymentSplitter.sol";

contract MockERC20 {
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
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

contract TreasuryTest is Test {
    Treasury public treasury;
    MockERC20 public token;

    address admin = address(0xCAFE);
    address finance = address(0xBABE);
    address emergency = address(0xDEAD);
    address alice = address(0x1111);
    address bob = address(0x2222);

    bytes32 constant ALLOC_ID = keccak256("alloc-1");

    function setUp() public {
        vm.startPrank(admin);
        treasury = new Treasury(admin, address(0));
        treasury.grantRole(treasury.FINANCE_ROLE(), finance);
        treasury.grantRole(treasury.FINANCE_ROLE(), address(this));
        treasury.grantRole(treasury.EMERGENCY_ROLE(), emergency);
        treasury.setProposalContract(address(this));
        vm.stopPrank();

        token = new MockERC20();
        token.mint(admin, 1000e18);
        vm.prank(admin);
        token.approve(address(treasury), 1000e18);
    }

    // ───── deposit ETH ─────

    function test_DepositETH() public {
        vm.deal(alice, 10 ether);
        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit ITreasury.Deposited(alice, 5 ether);
        treasury.deposit{value: 5 ether}();

        assertEq(address(treasury).balance, 5 ether);
    }

    // ───── receive ETH ─────

    function test_ReceiveETH() public {
        vm.deal(alice, 10 ether);
        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit ITreasury.Deposited(alice, 3 ether);
        (bool sent,) = address(treasury).call{value: 3 ether}("");
        assertTrue(sent);

        assertEq(address(treasury).balance, 3 ether);
    }

    // ───── deposit ERC20 ─────

    function test_DepositERC20() public {
        vm.prank(admin);
        vm.expectEmit(true, true, false, true);
        emit ITreasury.ERC20Deposited(admin, address(token), 100e18);
        treasury.depositERC20(address(token), 100e18);

        assertEq(token.balanceOf(address(treasury)), 100e18);
    }

    // ───── createAllocation ─────

    function test_CreateAllocation() public {
        address[] memory recipients = new address[](2);
        recipients[0] = alice;
        recipients[1] = bob;
        uint256[] memory amounts = new uint256[](2);
        amounts[0] = 10e18;
        amounts[1] = 20e18;

        vm.prank(finance);
        vm.expectEmit(true, false, false, true);
        emit ITreasury.AllocationCreated(ALLOC_ID, address(0), 30e18, 2);
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertEq(alloc.recipients.length, 2);
        assertEq(alloc.amounts[0], 10e18);
        assertFalse(alloc.executed);
        assertFalse(alloc.cancelled);
    }

    function test_CreateAllocation_RevertWhen_NonFinance() public {
        address[] memory recipients = new address[](1);
        recipients[0] = alice;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 1 ether;

        vm.prank(alice);
        vm.expectRevert();
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));
    }

    function test_CreateAllocation_RevertWhen_AlreadyExists() public {
        _setupAlloc(alice, 5 ether);

        address[] memory recipients = new address[](1);
        recipients[0] = bob;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 5 ether;

        vm.prank(finance);
        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrAllocationExists.selector));
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));
    }

    // ───── executeAllocation ─────

    function test_ExecuteAllocation() public {
        _setupAlloc(alice, 5 ether);

        vm.deal(address(treasury), 10 ether);

        vm.expectEmit(true, false, false, true);
        emit ITreasury.AllocationExecuted(ALLOC_ID);
        treasury.executeAllocation(ALLOC_ID);

        assertEq(alice.balance, 5 ether);
        assertEq(address(treasury).balance, 5 ether);

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertTrue(alloc.executed);
    }

    function test_ExecuteAllocation_RevertWhen_Cancelled() public {
        _setupAlloc(alice, 5 ether);

        vm.prank(finance);
        treasury.cancelAllocation(ALLOC_ID);

        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrAlreadyCancelled.selector));
        treasury.executeAllocation(ALLOC_ID);
    }

    function test_ExecuteAllocation_RevertWhen_InsufficientFunds() public {
        _setupAlloc(alice, 5 ether);
        // Не отправляем ETH в treasury — баланс 0, а нужно 5 ether

        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrInsufficientFunds.selector));
        treasury.executeAllocation(ALLOC_ID);
    }

    function test_ExecuteAllocation_RevertWhen_AlreadyExecuted() public {
        _setupAlloc(alice, 5 ether);
        vm.deal(address(treasury), 10 ether);

        treasury.executeAllocation(ALLOC_ID);

        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrAlreadyExecuted.selector));
        treasury.executeAllocation(ALLOC_ID);
    }

    function test_ExecuteAllocation_RevertWhen_NotFinance() public {
        _setupAlloc(alice, 5 ether);
        vm.deal(address(treasury), 10 ether);

        vm.prank(alice);
        vm.expectRevert();
        treasury.executeAllocation(ALLOC_ID);
    }

    function test_ExecuteAllocation_RevertWhen_NotProposalContract() public {
        _setupAlloc(alice, 5 ether);
        vm.deal(address(treasury), 10 ether);

        vm.prank(bob);
        vm.expectRevert();
        treasury.executeAllocation(ALLOC_ID);
    }

    function test_SetProposalContract() public {
        address newProposal = address(0x1234);
        vm.prank(admin);
        treasury.setProposalContract(newProposal);
        assertEq(treasury.proposalContract(), newProposal);
    }

    function test_SetProposalContract_RevertWhen_NotAdmin() public {
        vm.prank(alice);
        vm.expectRevert();
        treasury.setProposalContract(address(0x1234));
    }

    function test_SetProposalContract_RevertWhen_ZeroAddress() public {
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrInvalidAddress.selector));
        treasury.setProposalContract(address(0));
    }

    function test_ExecuteAllocation_MultipleRecipients() public {
        address[] memory recipients = new address[](2);
        recipients[0] = alice;
        recipients[1] = bob;
        uint256[] memory amounts = new uint256[](2);
        amounts[0] = 3 ether;
        amounts[1] = 7 ether;

        vm.prank(finance);
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));

        vm.deal(address(treasury), 10 ether);

        treasury.executeAllocation(ALLOC_ID);

        assertEq(alice.balance, 3 ether);
        assertEq(bob.balance, 7 ether);
        assertEq(address(treasury).balance, 0);
    }

    function test_ExecuteAllocation_ERC20() public {
        address[] memory recipients = new address[](1);
        recipients[0] = alice;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 50e18;

        vm.prank(finance);
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(token));

        token.mint(address(treasury), 100e18);

        treasury.executeAllocation(ALLOC_ID);

        assertEq(token.balanceOf(alice), 50e18);
        assertEq(token.balanceOf(address(treasury)), 50e18);
    }

    // ───── S-133: double-spend protection ─────

    function test_ExecuteAllocation_ZeroesAmounts() public {
        address[] memory recipients = new address[](2);
        recipients[0] = alice;
        recipients[1] = bob;
        uint256[] memory amounts = new uint256[](2);
        amounts[0] = 3 ether;
        amounts[1] = 7 ether;

        vm.prank(finance);
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));

        vm.deal(address(treasury), 10 ether);
        treasury.executeAllocation(ALLOC_ID);

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertEq(alloc.amounts.length, 0, "amounts should be cleared");
    }

    // ───── cancelAllocation ─────

    function test_CancelAllocation() public {
        _setupAlloc(alice, 5 ether);

        vm.prank(finance);
        vm.expectEmit(true, false, false, true);
        emit ITreasury.AllocationCancelled(ALLOC_ID);
        treasury.cancelAllocation(ALLOC_ID);

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertTrue(alloc.cancelled);
    }

    function test_CancelAllocation_RevertWhen_NotFinance() public {
        _setupAlloc(alice, 5 ether);

        vm.prank(alice);
        vm.expectRevert();
        treasury.cancelAllocation(ALLOC_ID);
    }

    function test_CancelAllocation_ByProposalContract() public {
        _setupAlloc(alice, 5 ether);

        // proposalContract = address(this), call without FINANCE_ROLE
        treasury.cancelAllocation(ALLOC_ID);

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertTrue(alloc.cancelled);
    }

    function test_CancelAllocation_RevertWhen_AlreadyCancelled() public {
        _setupAlloc(alice, 5 ether);

        vm.prank(finance);
        treasury.cancelAllocation(ALLOC_ID);

        vm.prank(finance);
        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrAlreadyCancelled.selector));
        treasury.cancelAllocation(ALLOC_ID);
    }

    function test_CancelAllocation_RevertWhen_AlreadyExecuted() public {
        _setupAlloc(alice, 5 ether);
        vm.deal(address(treasury), 10 ether);
        treasury.executeAllocation(ALLOC_ID);

        vm.prank(finance);
        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrAlreadyExecuted.selector));
        treasury.cancelAllocation(ALLOC_ID);
    }

    // ───── pause / unpause ─────

    function test_Pause() public {
        vm.prank(emergency);
        treasury.pause();
        assertTrue(treasury.paused());
    }

    function test_Pause_RevertWhen_NotEmergency() public {
        vm.prank(alice);
        vm.expectRevert();
        treasury.pause();
    }

    function test_Unpause() public {
        vm.prank(emergency);
        treasury.pause();

        vm.prank(admin);
        treasury.unpause();
        assertFalse(treasury.paused());
    }

    function test_Unpause_RevertWhen_NotAdmin() public {
        vm.prank(emergency);
        treasury.pause();

        vm.prank(alice);
        vm.expectRevert();
        treasury.unpause();
    }

    function test_Deposit_RevertWhen_Paused() public {
        vm.prank(emergency);
        treasury.pause();

        vm.deal(alice, 1 ether);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrPaused.selector));
        treasury.deposit{value: 1 ether}();
    }

    function test_Execute_RevertWhen_Paused() public {
        _setupAlloc(alice, 5 ether);
        vm.deal(address(treasury), 10 ether);

        vm.prank(emergency);
        treasury.pause();

        vm.expectRevert(abi.encodeWithSelector(ITreasury.ErrPaused.selector));
        treasury.executeAllocation(ALLOC_ID);
    }

    // ───── withdrawStuckTokens ─────

    function test_WithdrawStuckTokens_ERC20() public {
        token.mint(address(treasury), 100e18);

        vm.prank(admin);
        treasury.withdrawStuckTokens(address(token), 50e18);

        assertEq(token.balanceOf(admin), 1000e18 + 50e18);
        assertEq(token.balanceOf(address(treasury)), 50e18);
    }

    function test_WithdrawStuckTokens_ETH() public {
        vm.deal(address(treasury), 10 ether);

        vm.prank(admin);
        treasury.withdrawStuckTokens(address(0), 4 ether);

        assertEq(admin.balance, 4 ether);
        assertEq(address(treasury).balance, 6 ether);
    }

    function test_WithdrawStuckTokens_RevertWhen_NonAdmin() public {
        token.mint(address(treasury), 100e18);

        vm.prank(alice);
        vm.expectRevert();
        treasury.withdrawStuckTokens(address(token), 50e18);
    }

    // ───── splitterFactory validation ─────

    function test_SetSplitterFactory() public {
        PaymentSplitterFactory f = new PaymentSplitterFactory();
        vm.prank(admin);
        treasury.setSplitterFactory(address(f));
        assertEq(treasury.splitterFactory(), address(f));
    }

    function test_SetSplitterFactory_RevertWhen_NotAdmin() public {
        vm.prank(alice);
        vm.expectRevert();
        treasury.setSplitterFactory(address(1));
    }

    function test_CreateAllocation_RevertWhen_ContractNotVerified() public {
        // Deploy factory and set it
        PaymentSplitterFactory f = new PaymentSplitterFactory();
        vm.prank(admin);
        treasury.setSplitterFactory(address(f));

        // Deploy a dummy contract that is NOT a verified splitter
        DummyContract dummy = new DummyContract();

        address[] memory recipients = new address[](1);
        recipients[0] = address(dummy);
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 1 ether;

        vm.prank(finance);
        vm.expectRevert(ITreasury.ErrNotVerifiedSplitter.selector);
        treasury.createAllocation(keccak256("bad"), recipients, amounts, address(0));
    }

    function test_CreateAllocation_SucceedsWithVerifiedSplitter() public {
        PaymentSplitterFactory f = new PaymentSplitterFactory();
        vm.prank(admin);
        treasury.setSplitterFactory(address(f));

        // Deploy a real splitter via factory
        address[] memory payees = new address[](2);
        payees[0] = alice;
        payees[1] = bob;
        uint256[] memory shares = new uint256[](2);
        shares[0] = 50;
        shares[1] = 50;

        address splitter = f.deploy(keccak256("split1"), payees, shares);

        // Use splitter as recipient in allocation
        address[] memory recipients = new address[](1);
        recipients[0] = splitter;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 1 ether;

        vm.prank(finance);
        treasury.createAllocation(keccak256("good"), recipients, amounts, address(0));
    }

    function test_CreateAllocation_SucceedsWithEOAWhenFactorySet() public {
        PaymentSplitterFactory f = new PaymentSplitterFactory();
        vm.prank(admin);
        treasury.setSplitterFactory(address(f));

        // EOA recipients always pass
        address[] memory recipients = new address[](1);
        recipients[0] = alice; // EOA
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 1 ether;

        vm.prank(finance);
        treasury.createAllocation(keccak256("eoa"), recipients, amounts, address(0));
    }

    // ───── helpers ─────

    function _setupAlloc(address recipient, uint256 amount) internal {
        address[] memory recipients = new address[](1);
        recipients[0] = recipient;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = amount;

        vm.prank(finance);
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));
    }
}

/// @notice Minimal contract with code (used to test non-verified splitter rejection).
contract DummyContract {}
