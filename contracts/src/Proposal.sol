// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {ITreasury} from "./interfaces/ITreasury.sol";
import {IProposal} from "./interfaces/IProposal.sol";

/// @notice DAO proposal contract for treasury allocation voting.
/// @dev Uses MDAOToken for voting weight, Treasury for execution.
contract Proposal is AccessControl, ReentrancyGuard, IProposal {
    bytes32 public constant FINANCE_ROLE = keccak256("FINANCE_ROLE");

    /// @notice The treasury contract that holds and executes allocations.
    ITreasury public treasury;

    /// @notice The token used for voting weight.
    IERC20 public votingToken;

    /// @notice Duration of the voting period in seconds (3 days).
    uint256 public constant VOTING_PERIOD = 3 days;

    /// @notice Quorum basis points (400 = 4%).
    uint256 public constant QUORUM_BPS = 400;

    /// @notice Total number of proposals created.
    uint256 public proposalCount;

    mapping(uint256 id => ProposalData) private _proposals;
    mapping(uint256 id => mapping(address voter => bool)) private _hasVoted;

    /// @notice Initializes the Proposal contract.
    /// @param _treasury Address of the Treasury contract.
    /// @param _votingToken Address of the voting token (MDAOToken).
    /// @param admin Address to be granted DEFAULT_ADMIN_ROLE.
    constructor(address _treasury, address _votingToken, address admin) {
        if (_treasury == address(0)) revert ErrInvalidAddress();
        if (_votingToken == address(0)) revert ErrInvalidAddress();
        if (admin == address(0)) revert ErrInvalidAddress();
        treasury = ITreasury(_treasury);
        votingToken = IERC20(_votingToken);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    /// @notice Creates a new proposal linked to a Treasury allocation.
    /// @param allocId The allocation ID in Treasury.
    /// @param description A human-readable description of the proposal.
    /// @return proposalId The ID of the newly created proposal.
    function createProposal(bytes32 allocId, string calldata description)
        external
        onlyRole(FINANCE_ROLE)
        returns (uint256 proposalId)
    {
        proposalId = ++proposalCount;
        ProposalData storage p = _proposals[proposalId];
        p.allocId = allocId;
        p.proposer = msg.sender;
        p.deadline = block.timestamp + VOTING_PERIOD;
        p.description = description;

        emit ProposalCreated(proposalId, allocId, msg.sender, p.deadline, description);
    }

    /// @notice Casts a vote on a proposal.
    /// @param proposalId The ID of the proposal.
    /// @param support 0 = Against, 1 = For, 2 = Abstain.
    function vote(uint256 proposalId, uint8 support) external {
        ProposalData storage p = _proposals[proposalId];
        if (p.deadline == 0) revert ErrInvalidProposal();
        if (block.timestamp > p.deadline) revert ErrVotingEnded();
        if (_hasVoted[proposalId][msg.sender]) revert ErrAlreadyVoted();
        if (support > 2) revert ErrInvalidSupport();

        uint256 weight = votingToken.balanceOf(msg.sender);
        if (weight == 0) revert ErrZeroVotes();

        _hasVoted[proposalId][msg.sender] = true;

        if (support == 0) {
            p.againstVotes += weight;
        } else if (support == 1) {
            p.forVotes += weight;
        } else {
            p.abstainVotes += weight;
        }

        emit VoteCast(proposalId, msg.sender, support, weight, "");
    }

    /// @notice Executes a proposal after the voting period has ended.
    /// @dev ReentrancyGuard prevents reentrant calls to Treasury.
    /// @param proposalId The ID of the proposal.
    function executeProposal(uint256 proposalId) external nonReentrant {
        ProposalData storage p = _proposals[proposalId];
        if (p.deadline == 0) revert ErrInvalidProposal();
        if (block.timestamp <= p.deadline) revert ErrVotingNotEnded();
        if (p.executed) revert ErrAlreadyExecuted();
        if (p.cancelled) revert ErrAlreadyCancelled();

        uint256 totalSupply = votingToken.totalSupply();
        uint256 quorum = (totalSupply * QUORUM_BPS) / 10000;
        if (p.forVotes + p.againstVotes < quorum) revert ErrQuorumNotMet();
        if (p.forVotes <= p.againstVotes) revert ErrProposalDefeated();

        p.executed = true;

        treasury.executeAllocation(p.allocId);

        emit ProposalExecuted(proposalId);
    }

    /// @notice Retries a cancelled or failed proposal with the same allocId, creating a fresh proposal.
    /// @param previousProposalId The ID of the cancelled/failed proposal.
    /// @param description A new description for the retried proposal.
    /// @return proposalId The ID of the new proposal.
    function retryProposal(uint256 previousProposalId, string calldata description)
        external
        returns (uint256 proposalId)
    {
        ProposalData storage prev = _proposals[previousProposalId];
        if (prev.deadline == 0) revert ErrInvalidProposal();

        // Retry allowed if: cancelled OR (passed voting but execution failed)
        bool isFailed = !prev.executed && !prev.cancelled
            && block.timestamp > prev.deadline
            && prev.forVotes > prev.againstVotes;
        if (!prev.cancelled && !isFailed) revert ErrNotCancelled();

        if (!hasRole(FINANCE_ROLE, msg.sender) && msg.sender != prev.proposer) revert ErrUnauthorized();

        proposalId = ++proposalCount;
        ProposalData storage p = _proposals[proposalId];
        p.allocId = prev.allocId;
        p.proposer = msg.sender;
        p.deadline = block.timestamp + VOTING_PERIOD;
        p.description = description;

        emit ProposalCreated(proposalId, prev.allocId, msg.sender, p.deadline, description);
    }

    /// @notice Cancels a proposal before the voting period ends.
    /// @param proposalId The ID of the proposal.
    function cancelProposal(uint256 proposalId) external {
        ProposalData storage p = _proposals[proposalId];
        if (p.deadline == 0) revert ErrInvalidProposal();
        if (p.executed) revert ErrAlreadyExecuted();
        if (p.cancelled) revert ErrAlreadyCancelled();
        if (block.timestamp > p.deadline) revert ErrVotingEnded();
        if (!hasRole(FINANCE_ROLE, msg.sender) && msg.sender != p.proposer) revert ErrUnauthorized();

        p.cancelled = true;

        treasury.cancelAllocation(p.allocId);

        emit ProposalCancelled(proposalId);
    }

    /// @notice Returns the full proposal data.
    /// @param proposalId The ID of the proposal.
    function getProposal(uint256 proposalId) external view returns (ProposalData memory) {
        return _proposals[proposalId];
    }

    /// @notice Returns whether an address has voted on a proposal.
    /// @param proposalId The ID of the proposal.
    /// @param voter The address to check.
    function hasVoted(uint256 proposalId, address voter) external view returns (bool) {
        return _hasVoted[proposalId][voter];
    }
}
