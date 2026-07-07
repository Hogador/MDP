// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {PaymentSplitter} from "../src/PaymentSplitter.sol";
import {IPaymentSplitter} from "../src/interfaces/IPaymentSplitter.sol";
import {Treasury} from "../src/Treasury.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {Proposal} from "../src/Proposal.sol";

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

contract PaymentSplitterTest is Test {
    PaymentSplitter public splitter;
    MockERC20 public token;

    address admin = address(0xCAFE);
    address alice = address(0x1111);
    address bob = address(0x2222);
    address charlie = address(0x3333);

    function setUp() public {
        vm.prank(admin);
        splitter = new PaymentSplitter();
        token = new MockERC20();
    }

    function _initTwoPayees() internal {
        address[] memory payees = new address[](2);
        payees[0] = alice;
        payees[1] = bob;
        uint256[] memory shares_ = new uint256[](2);
        shares_[0] = 30;
        shares_[1] = 70;
        vm.prank(admin);
        splitter.initialize(payees, shares_);
    }

    // ───── initialize ─────

    function test_Initialize() public {
        _initTwoPayees();
        assertEq(splitter.totalShares(), 100);
        assertEq(splitter.shares(alice), 30);
        assertEq(splitter.shares(bob), 70);
        address[] memory payees = splitter.getPayees();
        assertEq(payees.length, 2);
        assertEq(payees[0], alice);
        assertEq(payees[1], bob);
    }

    function test_Initialize_RevertWhen_NotAdmin() public {
        address[] memory payees = new address[](1);
        payees[0] = alice;
        uint256[] memory shares_ = new uint256[](1);
        shares_[0] = 100;
        vm.prank(alice);
        vm.expectRevert();
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_RevertWhen_Twice() public {
        _initTwoPayees();
        address[] memory payees = new address[](1);
        payees[0] = charlie;
        uint256[] memory shares_ = new uint256[](1);
        shares_[0] = 100;
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrAlreadyInitialized.selector));
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_RevertWhen_EmptyPayees() public {
        address[] memory payees = new address[](0);
        uint256[] memory shares_ = new uint256[](0);
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrInvalidPayee.selector));
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_RevertWhen_ArrayMismatch() public {
        address[] memory payees = new address[](1);
        payees[0] = alice;
        uint256[] memory shares_ = new uint256[](2);
        shares_[0] = 50;
        shares_[1] = 50;
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrArrayMismatch.selector));
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_RevertWhen_ZeroAddress() public {
        address[] memory payees = new address[](1);
        payees[0] = address(0);
        uint256[] memory shares_ = new uint256[](1);
        shares_[0] = 100;
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrInvalidPayee.selector));
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_RevertWhen_ZeroShares() public {
        address[] memory payees = new address[](1);
        payees[0] = alice;
        uint256[] memory shares_ = new uint256[](1);
        shares_[0] = 0;
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrZeroShares.selector));
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_RevertWhen_DuplicatePayee() public {
        address[] memory payees = new address[](2);
        payees[0] = alice;
        payees[1] = alice;
        uint256[] memory shares_ = new uint256[](2);
        shares_[0] = 50;
        shares_[1] = 50;
        vm.prank(admin);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrInvalidPayee.selector));
        splitter.initialize(payees, shares_);
    }

    function test_Initialize_EmitsEvents() public {
        address[] memory payees = new address[](2);
        payees[0] = alice;
        payees[1] = bob;
        uint256[] memory shares_ = new uint256[](2);
        shares_[0] = 30;
        shares_[1] = 70;

        vm.prank(admin);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PayeeAdded(alice, 30);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PayeeAdded(bob, 70);
        splitter.initialize(payees, shares_);
    }

    // ───── release ETH ─────

    function test_ReleaseETH() public {
        _initTwoPayees();
        vm.deal(address(splitter), 100 ether);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PaymentReleased(address(0), alice, 30 ether);
        splitter.release(address(0));

        assertEq(alice.balance, 30 ether);
        assertEq(splitter.released(address(0), alice), 30 ether);
        assertEq(splitter.totalReleased(address(0)), 30 ether);

        vm.prank(bob);
        splitter.release(address(0));

        assertEq(bob.balance, 70 ether);
        assertEq(splitter.released(address(0), bob), 70 ether);
        assertEq(splitter.totalReleased(address(0)), 100 ether);
        assertEq(address(splitter).balance, 0);
    }

    function test_ReleaseETH_MultiplePayees() public {
        address[] memory payees = new address[](3);
        payees[0] = alice;
        payees[1] = bob;
        payees[2] = charlie;
        uint256[] memory shares_ = new uint256[](3);
        shares_[0] = 50;
        shares_[1] = 30;
        shares_[2] = 20;

        vm.prank(admin);
        splitter.initialize(payees, shares_);

        vm.deal(address(splitter), 100 ether);

        vm.prank(alice);
        splitter.release(address(0));
        assertEq(alice.balance, 50 ether);

        vm.prank(bob);
        splitter.release(address(0));
        assertEq(bob.balance, 30 ether);

        vm.prank(charlie);
        splitter.release(address(0));
        assertEq(charlie.balance, 20 ether);
    }

    function test_ReleaseETH_RevertWhen_NoShares() public {
        _initTwoPayees();
        vm.deal(address(splitter), 100 ether);

        vm.prank(charlie);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrInvalidPayee.selector));
        splitter.release(address(0));
    }

    function test_ReleaseETH_RevertWhen_NoDuePayment() public {
        _initTwoPayees();
        vm.deal(address(splitter), 0 ether);

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrNoDuePayment.selector));
        splitter.release(address(0));
    }

    // ───── release ERC20 ─────

    function test_ReleaseERC20() public {
        _initTwoPayees();
        token.mint(address(splitter), 100e18);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PaymentReleased(address(token), alice, 30e18);
        splitter.release(address(token));

        assertEq(token.balanceOf(alice), 30e18);
        assertEq(splitter.released(address(token), alice), 30e18);
        assertEq(splitter.totalReleased(address(token)), 30e18);

        vm.prank(bob);
        splitter.release(address(token));

        assertEq(token.balanceOf(bob), 70e18);
        assertEq(splitter.released(address(token), bob), 70e18);
        assertEq(splitter.totalReleased(address(token)), 100e18);
        assertEq(token.balanceOf(address(splitter)), 0);
    }

    function test_ReleaseERC20_RevertWhen_NoDuePayment() public {
        _initTwoPayees();

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrNoDuePayment.selector));
        splitter.release(address(token));
    }

    // ───── released / totalReleased accounting ─────

    function test_Accounting_PartialReleaseThenMoreFunds() public {
        _initTwoPayees();
        vm.deal(address(splitter), 100 ether);

        vm.prank(alice);
        splitter.release(address(0));
        assertEq(alice.balance, 30 ether);
        assertEq(splitter.released(address(0), alice), 30 ether);
        assertEq(splitter.totalReleased(address(0)), 30 ether);

        vm.deal(address(splitter), 200 ether);
        assertEq(address(splitter).balance, 200 ether);

        vm.prank(alice);
        splitter.release(address(0));
        assertEq(alice.balance, 30 ether + 39 ether);
        assertEq(splitter.released(address(0), alice), 69 ether);
    }

    function test_Accounting_MixedTokens() public {
        _initTwoPayees();
        vm.deal(address(splitter), 100 ether);
        token.mint(address(splitter), 200e18);

        vm.prank(alice);
        splitter.release(address(0));
        assertEq(alice.balance, 30 ether);

        vm.prank(alice);
        splitter.release(address(token));
        assertEq(token.balanceOf(alice), 60e18);

        assertEq(splitter.totalReleased(address(0)), 30 ether);
        assertEq(splitter.totalReleased(address(token)), 60e18);
    }

    // ───── receive ─────

    function test_ReceiveETH() public {
        _initTwoPayees();

        vm.deal(alice, 10 ether);
        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PaymentReceived(alice, 5 ether);
        (bool sent,) = address(splitter).call{value: 5 ether}("");
        assertTrue(sent);

        assertEq(address(splitter).balance, 5 ether);
    }

    function test_ReceiveETHThenRelease() public {
        _initTwoPayees();

        vm.deal(alice, 10 ether);
        vm.prank(alice);
        (bool sent,) = address(splitter).call{value: 10 ether}("");
        assertTrue(sent);

        vm.prank(alice);
        splitter.release(address(0));
        assertEq(alice.balance, 10 ether - 10 ether + 3 ether);

        vm.prank(bob);
        splitter.release(address(0));
        assertEq(bob.balance, 7 ether);
    }

    // ───── receiveAndDistribute ─────

    function test_ReceiveAndDistribute_ETH() public {
        _initTwoPayees();
        vm.deal(address(splitter), 100 ether);

        splitter.receiveAndDistribute(address(0));

        assertEq(alice.balance, 30 ether);
        assertEq(bob.balance, 70 ether);
        assertEq(address(splitter).balance, 0);
        assertEq(splitter.released(address(0), alice), 30 ether);
        assertEq(splitter.released(address(0), bob), 70 ether);
        assertEq(splitter.totalReleased(address(0)), 100 ether);
    }

    function test_ReceiveAndDistribute_ETH_ThreePayees() public {
        address[] memory payees = new address[](3);
        payees[0] = alice;
        payees[1] = bob;
        payees[2] = charlie;
        uint256[] memory shares_ = new uint256[](3);
        shares_[0] = 50;
        shares_[1] = 30;
        shares_[2] = 20;
        vm.prank(admin);
        splitter.initialize(payees, shares_);
        vm.deal(address(splitter), 100 ether);
        splitter.receiveAndDistribute(address(0));
        assertEq(alice.balance, 50 ether);
        assertEq(bob.balance, 30 ether);
        assertEq(charlie.balance, 20 ether);
    }

    function test_ReceiveAndDistribute_ERC20() public {
        _initTwoPayees();
        token.mint(address(splitter), 100e18);
        splitter.receiveAndDistribute(address(token));
        assertEq(token.balanceOf(alice), 30e18);
        assertEq(token.balanceOf(bob), 70e18);
        assertEq(token.balanceOf(address(splitter)), 0);
    }

    function test_ReceiveAndDistribute_RevertWhen_NoFunds() public {
        _initTwoPayees();
        vm.expectRevert(abi.encodeWithSelector(IPaymentSplitter.ErrNoDuePayment.selector));
        splitter.receiveAndDistribute(address(0));
    }

    function test_ReceiveAndDistribute_EmitsEvents() public {
        _initTwoPayees();
        vm.deal(address(splitter), 100 ether);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PaymentReleased(address(0), alice, 30 ether);
        vm.expectEmit(true, true, false, true);
        emit IPaymentSplitter.PaymentReleased(address(0), bob, 70 ether);
        splitter.receiveAndDistribute(address(0));
    }

    // ───── views ─────

    function test_Views_BeforeInitialize() public {
        assertEq(splitter.totalShares(), 0);
        assertEq(splitter.shares(alice), 0);
        assertEq(splitter.getPayees().length, 0);
        assertEq(splitter.released(address(0), alice), 0);
        assertEq(splitter.totalReleased(address(0)), 0);
    }

    // ───── full cycle with Proposal + Treasury ─────

    function test_FullCycle_WithTreasuryAndProposal() public {
        address finance = address(0xBABE);

        vm.startPrank(admin);
        MDAOToken mdao = new MDAOToken(admin);
        Treasury treasury = new Treasury(admin, address(0));
        treasury.grantRole(treasury.FINANCE_ROLE(), finance);

        Proposal proposal = new Proposal(address(treasury), address(mdao), admin);
        treasury.setProposalContract(address(proposal));
        proposal.grantRole(proposal.FINANCE_ROLE(), finance);
        treasury.grantRole(treasury.FINANCE_ROLE(), address(proposal));
        vm.stopPrank();

        _initTwoPayees();
        assertEq(splitter.totalShares(), 100);

        bytes32 allocId = keccak256("splitter-payment");
        address[] memory recipients = new address[](1);
        recipients[0] = address(splitter);
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 100 ether;

        vm.prank(finance);
        treasury.createAllocation(allocId, recipients, amounts, address(0));

        vm.deal(address(treasury), 200 ether);

        vm.prank(finance);
        uint256 propId = proposal.createProposal(allocId, "Fund PaymentSplitter");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);
        proposal.executeProposal(propId);

        assertEq(address(splitter).balance, 100 ether);

        vm.prank(alice);
        splitter.release(address(0));
        assertEq(alice.balance, 30 ether);

        vm.prank(bob);
        splitter.release(address(0));
        assertEq(bob.balance, 70 ether);

        assertEq(address(splitter).balance, 0);
    }

    function test_FullCycle_ERC20_WithTreasuryAndProposal() public {
        address finance = address(0xBABE);

        vm.startPrank(admin);
        MDAOToken mdao = new MDAOToken(admin);
        Treasury treasury = new Treasury(admin, address(0));
        treasury.grantRole(treasury.FINANCE_ROLE(), finance);

        Proposal proposal = new Proposal(address(treasury), address(mdao), admin);
        treasury.setProposalContract(address(proposal));
        proposal.grantRole(proposal.FINANCE_ROLE(), finance);
        treasury.grantRole(treasury.FINANCE_ROLE(), address(proposal));
        vm.stopPrank();

        _initTwoPayees();

        bytes32 allocId = keccak256("erc20-splitter");
        address[] memory recipients = new address[](1);
        recipients[0] = address(splitter);
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 100e18;

        vm.prank(finance);
        treasury.createAllocation(allocId, recipients, amounts, address(token));

        token.mint(address(treasury), 100e18);

        vm.prank(finance);
        uint256 propId = proposal.createProposal(allocId, "Fund PaymentSplitter ERC20");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);
        proposal.executeProposal(propId);

        assertEq(token.balanceOf(address(splitter)), 100e18);

        vm.prank(alice);
        splitter.release(address(token));
        assertEq(token.balanceOf(alice), 30e18);

        vm.prank(bob);
        splitter.release(address(token));
        assertEq(token.balanceOf(bob), 70e18);

        assertEq(token.balanceOf(address(splitter)), 0);
    }
}
