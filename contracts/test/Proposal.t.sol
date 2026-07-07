// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {Treasury} from "../src/Treasury.sol";
import {Proposal} from "../src/Proposal.sol";
import {IProposal} from "../src/interfaces/IProposal.sol";
import {ITreasury} from "../src/interfaces/ITreasury.sol";

contract ProposalTest is Test {
    MDAOToken token;
    Treasury treasury;
    Proposal proposal;

    address admin = address(0xCAFE);
    address finance = address(0xBABE);
    address alice = address(0x1111);
    address bob = address(0x2222);

    bytes32 constant ALLOC_ID = keccak256("alloc-1");

    function setUp() public {
        vm.startPrank(admin);
        token = new MDAOToken(admin);
        treasury = new Treasury(admin, address(0));
        treasury.grantRole(treasury.FINANCE_ROLE(), finance);
        vm.stopPrank();

        vm.startPrank(admin);
        proposal = new Proposal(address(treasury), address(token), admin);
        proposal.grantRole(proposal.FINANCE_ROLE(), finance);
        vm.stopPrank();

        vm.startPrank(admin);
        treasury.grantRole(treasury.FINANCE_ROLE(), address(proposal));
        treasury.setProposalContract(address(proposal));
        vm.stopPrank();

        address[] memory recipients = new address[](1);
        recipients[0] = alice;
        uint256[] memory amounts = new uint256[](1);
        amounts[0] = 10 ether;
        vm.prank(finance);
        treasury.createAllocation(ALLOC_ID, recipients, amounts, address(0));

        vm.deal(address(treasury), 100 ether);
    }

    // ───── createProposal ─────

    function test_CreateProposal() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Send 10 ETH to alice");

        assertEq(propId, 1);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertEq(p.allocId, ALLOC_ID);
        assertEq(p.proposer, finance);
        assertEq(p.deadline, block.timestamp + 3 days);
        assertEq(p.description, "Send 10 ETH to alice");
        assertEq(p.forVotes, 0);
        assertEq(p.againstVotes, 0);
        assertEq(p.abstainVotes, 0);
        assertFalse(p.executed);
        assertFalse(p.cancelled);
    }

    function test_CreateProposal_IncrementsId() public {
        vm.startPrank(finance);
        uint256 id1 = proposal.createProposal(ALLOC_ID, "First");
        uint256 id2 = proposal.createProposal(ALLOC_ID, "Second");
        vm.stopPrank();

        assertEq(id1, 1);
        assertEq(id2, 2);
        assertEq(proposal.proposalCount(), 2);
    }

    function test_CreateProposal_RevertWhen_NotFinance() public {
        vm.prank(alice);
        vm.expectRevert();
        proposal.createProposal(ALLOC_ID, "Rogue proposal");
    }

    function test_CreateProposal_EmitsEvent() public {
        vm.prank(finance);
        vm.expectEmit(true, true, true, true);
        emit IProposal.ProposalCreated(1, ALLOC_ID, finance, block.timestamp + 3 days, "Test emit");
        proposal.createProposal(ALLOC_ID, "Test emit");
    }

    // ───── vote ─────

    function test_Vote_For() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        uint256 weight = token.balanceOf(admin);
        vm.startPrank(admin);
        vm.expectEmit(true, true, false, true);
        emit IProposal.VoteCast(propId, admin, 1, weight, "");
        proposal.vote(propId, 1);
        vm.stopPrank();

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertEq(p.forVotes, weight);
        assertEq(p.againstVotes, 0);
        assertEq(p.abstainVotes, 0);
        assertTrue(proposal.hasVoted(propId, admin));
    }

    function test_Vote_Against() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 0);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertEq(p.forVotes, 0);
        assertEq(p.againstVotes, token.balanceOf(admin));
        assertEq(p.abstainVotes, 0);
    }

    function test_Vote_Abstain() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 2);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertEq(p.forVotes, 0);
        assertEq(p.againstVotes, 0);
        assertEq(p.abstainVotes, token.balanceOf(admin));
    }

    function test_Vote_MultipleVoters() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        uint256 aliceWeight = _transferAndGetAmount(admin, alice, 1000e18);
        uint256 adminWeight = token.balanceOf(admin);

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.prank(alice);
        proposal.vote(propId, 0);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertEq(p.forVotes, adminWeight);
        assertEq(p.againstVotes, aliceWeight);
    }

    function test_Vote_RevertWhen_InvalidProposal() public {
        vm.expectRevert(IProposal.ErrInvalidProposal.selector);
        proposal.vote(999, 1);
    }

    function test_Vote_RevertWhen_VotingEnded() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.warp(block.timestamp + 3 days + 1);

        vm.expectRevert(IProposal.ErrVotingEnded.selector);
        proposal.vote(propId, 1);
    }

    function test_Vote_RevertWhen_DoubleVote() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.prank(admin);
        vm.expectRevert(IProposal.ErrAlreadyVoted.selector);
        proposal.vote(propId, 1);
    }

    function test_Vote_RevertWhen_InvalidSupport() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        vm.expectRevert(IProposal.ErrInvalidSupport.selector);
        proposal.vote(propId, 3);
    }

    function test_Vote_RevertWhen_ZeroVotes() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(address(0x9999));
        vm.expectRevert(IProposal.ErrZeroVotes.selector);
        proposal.vote(propId, 1);
    }

    // ───── executeProposal ─────

    function test_ExecuteProposal() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Send 10 ETH");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);

        vm.expectEmit(true, false, false, true);
        emit IProposal.ProposalExecuted(propId);
        proposal.executeProposal(propId);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertTrue(p.executed);

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertTrue(alloc.executed);
    }

    function test_ExecuteProposal_SendsEthToRecipient() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Send 10 ETH");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);

        uint256 aliceBefore = alice.balance;
        proposal.executeProposal(propId);
        assertEq(alice.balance - aliceBefore, 10 ether);
    }

    function test_ExecuteProposal_RevertWhen_VotingNotEnded() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.expectRevert(IProposal.ErrVotingNotEnded.selector);
        proposal.executeProposal(propId);
    }

    function test_ExecuteProposal_RevertWhen_AlreadyExecuted() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);
        proposal.executeProposal(propId);

        vm.expectRevert(IProposal.ErrAlreadyExecuted.selector);
        proposal.executeProposal(propId);
    }

    function test_ExecuteProposal_RevertWhen_QuorumNotMet() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        _transferAndGetAmount(admin, bob, 1 ether);

        vm.prank(bob);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);

        vm.expectRevert(IProposal.ErrQuorumNotMet.selector);
        proposal.executeProposal(propId);
    }

    function test_ExecuteProposal_RevertWhen_Defeated() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 0);

        vm.warp(block.timestamp + 3 days + 1);

        vm.expectRevert(IProposal.ErrProposalDefeated.selector);
        proposal.executeProposal(propId);
    }

    function test_ExecuteProposal_RevertWhen_Cancelled() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(finance);
        proposal.cancelProposal(propId);

        vm.warp(block.timestamp + 3 days + 1);

        vm.expectRevert(IProposal.ErrAlreadyCancelled.selector);
        proposal.executeProposal(propId);
    }

    function test_ExecuteProposal_RevertWhen_InvalidProposal() public {
        vm.expectRevert(IProposal.ErrInvalidProposal.selector);
        proposal.executeProposal(999);
    }

    // ───── cancelProposal ─────

    function test_CancelProposal() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(finance);
        vm.expectEmit(true, false, false, true);
        emit IProposal.ProposalCancelled(propId);
        proposal.cancelProposal(propId);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertTrue(p.cancelled);
    }

    function test_CancelProposal_RevertWhen_NotFinance() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(alice);
        vm.expectRevert();
        proposal.cancelProposal(propId);
    }

    function test_CancelProposal_RevertWhen_AfterDeadline() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.warp(block.timestamp + 3 days + 1);

        vm.prank(finance);
        vm.expectRevert(IProposal.ErrVotingEnded.selector);
        proposal.cancelProposal(propId);
    }

    function test_CancelProposal_RevertWhen_AlreadyCancelled() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(finance);
        proposal.cancelProposal(propId);

        vm.prank(finance);
        vm.expectRevert(IProposal.ErrAlreadyCancelled.selector);
        proposal.cancelProposal(propId);
    }

    function test_CancelProposal_RevertWhen_AlreadyExecuted() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);
        proposal.executeProposal(propId);

        vm.prank(finance);
        vm.expectRevert(IProposal.ErrAlreadyExecuted.selector);
        proposal.cancelProposal(propId);
    }

    function test_CancelProposal_CascadesToTreasury() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Test cascade");

        vm.prank(finance);
        proposal.cancelProposal(propId);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertTrue(p.cancelled);

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertTrue(alloc.cancelled);
    }

    function test_CancelProposal_ByProposer() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Proposer cancel");

        vm.prank(finance);
        vm.expectEmit(true, false, false, true);
        emit IProposal.ProposalCancelled(propId);
        proposal.cancelProposal(propId);
        assertTrue(proposal.getProposal(propId).cancelled);
    }

    // ───── retryProposal ─────

    function test_RetryProposal() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Original");
        vm.prank(finance);
        proposal.cancelProposal(propId);

        vm.prank(finance);
        uint256 newId = proposal.retryProposal(propId, "Retry");
        assertEq(newId, 2);
        IProposal.ProposalData memory p = proposal.getProposal(newId);
        assertEq(p.allocId, ALLOC_ID);
        assertEq(p.proposer, finance);
        assertEq(p.deadline, block.timestamp + 3 days);
        assertFalse(p.executed);
        assertFalse(p.cancelled);
    }

    function test_RetryProposal_RevertWhen_NotCancelled() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Active");
        vm.expectRevert(IProposal.ErrNotCancelled.selector);
        proposal.retryProposal(propId, "Fail");
    }

    // ───── S-135: retry failed (passed but not executed) ─────

    function test_RetryProposal_FailedProposal() public {
        // Create allocation WITHOUT funding Treasury — execute will fail
        vm.deal(address(treasury), 0 ether);

        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Will fail");

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);

        // Execution fails — insufficient funds
        vm.expectRevert();
        proposal.executeProposal(propId);

        // Retry as new proposal with same allocId
        vm.prank(finance);
        uint256 newId = proposal.retryProposal(propId, "Retry after failure");
        assertEq(newId, 2);

        IProposal.ProposalData memory p = proposal.getProposal(newId);
        assertEq(p.allocId, ALLOC_ID);
        assertEq(p.proposer, finance);
        assertFalse(p.executed);
        assertFalse(p.cancelled);
    }

    function test_RetryProposal_RevertWhen_InvalidProposal() public {
        vm.expectRevert(IProposal.ErrInvalidProposal.selector);
        proposal.retryProposal(999, "Fail");
    }

    function test_RetryProposal_RevertWhen_NotAuthorized() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Original");
        vm.prank(finance);
        proposal.cancelProposal(propId);
        vm.prank(alice);
        vm.expectRevert(IProposal.ErrUnauthorized.selector);
        proposal.retryProposal(propId, "Rogue");
    }

    // ───── integration: full cycle ─────

    function test_FullCycle_CreateVoteExecute() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Full cycle test");

        uint256 aliceTokens = _transferAndGetAmount(admin, alice, 500_000e18);

        vm.prank(alice);
        proposal.vote(propId, 1);

        vm.prank(admin);
        proposal.vote(propId, 1);

        vm.warp(block.timestamp + 3 days + 1);

        uint256 aliceBefore = alice.balance;
        proposal.executeProposal(propId);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertTrue(p.executed);
        assertEq(p.forVotes, aliceTokens + token.balanceOf(admin));

        ITreasury.Allocation memory alloc = treasury.getAllocation(ALLOC_ID);
        assertTrue(alloc.executed);

        assertEq(alice.balance - aliceBefore, 10 ether);
    }

    function test_FullCycle_AgainstWins() public {
        vm.prank(finance);
        uint256 propId = proposal.createProposal(ALLOC_ID, "Against should win");

        vm.prank(admin);
        proposal.vote(propId, 0);

        _transferAndGetAmount(admin, bob, 100_000e18);

        vm.prank(bob);
        proposal.vote(propId, 0);

        vm.warp(block.timestamp + 3 days + 1);

        vm.expectRevert(IProposal.ErrProposalDefeated.selector);
        proposal.executeProposal(propId);

        IProposal.ProposalData memory p = proposal.getProposal(propId);
        assertFalse(p.executed);
        assertFalse(treasury.getAllocation(ALLOC_ID).executed);
    }

    // ───── helpers ─────

    function _transferAndGetAmount(address from, address to, uint256 amount) internal returns (uint256 received) {
        uint256 before = token.balanceOf(to);
        vm.prank(from);
        token.transfer(to, amount);
        received = token.balanceOf(to) - before;
    }
}
