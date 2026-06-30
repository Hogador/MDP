// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

/// @title RefundVault
/// @notice Holds user refunds. Owner can deposit, users can claim.
/// @dev Owner CANNOT withdraw — withdrawToken() intentionally removed (F-008).
contract RefundVault is Ownable {
    using SafeERC20 for IERC20;

    error ErrNoRefundDue();
    error ErrAlreadyRefunded();
    error ErrTransferFailed();
    error ErrInvalidAddress();

    struct RefundEntry {
        uint256 amount;
        bool claimed;
    }

    mapping(address user => mapping(IERC20 token => RefundEntry)) public refunds;

    // F-008: per-token pending refund total (owner cannot withdraw this)
    mapping(IERC20 token => uint256) public totalRefundsPending;

    event RefundDeposited(address indexed user, IERC20 indexed token, uint256 amount);
    event RefundClaimed(address indexed user, IERC20 indexed token, uint256 amount);

    constructor() Ownable(msg.sender) {}

    function depositRefund(address user, IERC20 token, uint256 amount) external onlyOwner {
        if (user == address(0)) revert ErrInvalidAddress();
        if (amount == 0) revert ErrNoRefundDue();

        RefundEntry storage entry = refunds[user][token];
        entry.amount += amount;
        totalRefundsPending[token] += amount;

        token.safeTransferFrom(msg.sender, address(this), amount);
        emit RefundDeposited(user, token, amount);
    }

    function claimRefund(IERC20 token) external {
        RefundEntry storage entry = refunds[msg.sender][token];
        if (entry.amount == 0) revert ErrNoRefundDue();
        if (entry.claimed) revert ErrAlreadyRefunded();

        uint256 amount = entry.amount;
        entry.claimed = true;
        // F-008: update total before transfer (CEI pattern)
        totalRefundsPending[token] -= amount;

        token.safeTransfer(msg.sender, amount);
        emit RefundClaimed(msg.sender, token, amount);
    }

    // ⚠️  withdrawToken() intentionally omitted — F-008: owner cannot withdraw user refunds

    function refundDue(address user, IERC20 token) external view returns (uint256, bool) {
        RefundEntry storage entry = refunds[user][token];
        return (entry.amount, entry.claimed);
    }
}
