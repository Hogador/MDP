// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

interface ITreasury {
    struct Allocation {
        address[] recipients;
        uint256[] amounts;
        address token;
        bool executed;
        bool cancelled;
    }

    event Deposited(address indexed from, uint256 amount);
    event ERC20Deposited(address indexed from, address indexed token, uint256 amount);
    event AllocationCreated(bytes32 indexed id, address token, uint256 totalAmount, uint256 recipientCount);
    event AllocationExecuted(bytes32 indexed id);
    event AllocationCancelled(bytes32 indexed id);

    error ErrInvalidAllocation();
    error ErrAllocationExists();
    error ErrAlreadyExecuted();
    error ErrAlreadyCancelled();
    error ErrInsufficientFunds();
    error ErrTransferFailed();
    error ErrInvalidAddress();
    error ErrInvalidAmount();
    error ErrPaused();
    error ErrNotVerifiedSplitter();

    function deposit() external payable;
    function depositERC20(address token, uint256 amount) external;
    function createAllocation(bytes32 id, address[] calldata recipients, uint256[] calldata amounts, address token) external;
    function executeAllocation(bytes32 id) external;
    function cancelAllocation(bytes32 id) external;
    function pause() external;
    function unpause() external;
    function withdrawStuckTokens(address token, uint256 amount) external;
}
