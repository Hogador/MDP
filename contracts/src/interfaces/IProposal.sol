// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

interface IProposal {
    struct ProposalData {
        bytes32 allocId;
        address proposer;
        uint256 deadline;
        string description;
        uint256 forVotes;
        uint256 againstVotes;
        uint256 abstainVotes;
        bool executed;
        bool cancelled;
        uint8 retryCount; // F-146
    }

    event ProposalCreated(
        uint256 indexed proposalId,
        bytes32 indexed allocId,
        address indexed proposer,
        uint256 deadline,
        string description
    );

    event VoteCast(
        uint256 indexed proposalId,
        address indexed voter,
        uint8 support,
        uint256 weight,
        string reason
    );

    event ProposalExecuted(uint256 indexed proposalId);

    event ProposalCancelled(uint256 indexed proposalId);

    error ErrInvalidProposal();
    error ErrVotingEnded();
    error ErrAlreadyVoted();
    error ErrInvalidSupport();
    error ErrZeroVotes();
    error ErrVotingNotEnded();
    error ErrAlreadyExecuted();
    error ErrAlreadyCancelled();
    error ErrQuorumNotMet();
    error ErrProposalDefeated();
    error ErrInvalidAddress();
    error ErrUnauthorized();
    error ErrNotCancelled();

    function createProposal(bytes32 allocId, string calldata description) external returns (uint256 proposalId);

    function vote(uint256 proposalId, uint8 support) external;

    function executeProposal(uint256 proposalId) external;

    function cancelProposal(uint256 proposalId) external;

    function retryProposal(uint256 previousProposalId, string calldata description) external returns (uint256 proposalId);

    function getProposal(uint256 proposalId) external view returns (ProposalData memory);

    function hasVoted(uint256 proposalId, address voter) external view returns (bool);
}
