// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {IPaymentSplitter} from "./interfaces/IPaymentSplitter.sol";

contract PaymentSplitter is AccessControl, ReentrancyGuard, IPaymentSplitter {
    using SafeERC20 for IERC20;

    bool private _initialized;
    uint256 private _totalShares;

    address[] private _payees;
    mapping(address => uint256) private _shares;
    mapping(address => mapping(address => uint256)) private _released;
    mapping(address => uint256) private _totalReleased;

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
    }

    receive() external payable {
        emit PaymentReceived(msg.sender, msg.value);
    }

    function initialize(address[] calldata payees, uint256[] calldata shares_)
        external
        onlyRole(DEFAULT_ADMIN_ROLE)
    {
        if (_initialized) revert ErrAlreadyInitialized();
        if (payees.length == 0) revert ErrInvalidPayee();
        if (payees.length != shares_.length) revert ErrArrayMismatch();

        _initialized = true;

        for (uint256 i; i < payees.length; i++) {
            address payee = payees[i];
            uint256 s = shares_[i];

            if (payee == address(0)) revert ErrInvalidPayee();
            if (s == 0) revert ErrZeroShares();
            if (_shares[payee] != 0) revert ErrInvalidPayee();

            _payees.push(payee);
            _shares[payee] = s;
            _totalShares += s;

            emit PayeeAdded(payee, s);
        }
    }

    function release(address token) external nonReentrant {
        address payee = msg.sender;
        uint256 share = _shares[payee];
        if (share == 0) revert ErrInvalidPayee();

        uint256 totalDue = _totalDue(token);
        uint256 alreadyReleased = _released[token][payee];
        uint256 payeeDue = (totalDue * share) / _totalShares;
        uint256 pending = payeeDue - alreadyReleased;

        if (pending == 0) revert ErrNoDuePayment();

        _released[token][payee] = payeeDue;
        _totalReleased[token] += pending;

        if (token == address(0)) {
            (bool sent, ) = payee.call{value: pending}("");
            if (!sent) revert ErrTransferFailed();
        } else {
            IERC20(token).safeTransfer(payee, pending);
        }

        emit PaymentReleased(token, payee, pending);
    }

    /// @notice Distribute available funds to ALL payees in one transaction (push pattern).
    /// @param token Address of the token (address(0) for native ETH).
    function receiveAndDistribute(address token) external nonReentrant {
        uint256 totalDue = _totalDue(token);
        uint256 releasedSoFar = _totalReleased[token];
        uint256 available = totalDue - releasedSoFar;
        if (available == 0) revert ErrNoDuePayment();

        for (uint256 i; i < _payees.length; i++) {
            address payee = _payees[i];
            uint256 share = _shares[payee];
            if (share == 0) continue;

            uint256 totalPayeeDue = (totalDue * share) / _totalShares;
            uint256 alreadyReleased = _released[token][payee];
            uint256 pending = totalPayeeDue - alreadyReleased;

            if (pending > 0) {
                _released[token][payee] = totalPayeeDue;
                _totalReleased[token] += pending;

                if (token == address(0)) {
                    (bool sent, ) = payee.call{value: pending}("");
                    if (!sent) revert ErrTransferFailed();
                } else {
                    IERC20(token).safeTransfer(payee, pending);
                }

                emit PaymentReleased(token, payee, pending);
            }
        }
    }

    function _totalDue(address token) internal view returns (uint256) {
        uint256 released_ = _totalReleased[token];
        if (token == address(0)) {
            return address(this).balance + released_;
        }
        return IERC20(token).balanceOf(address(this)) + released_;
    }

    function released(address token, address payee) external view returns (uint256) {
        return _released[token][payee];
    }

    function totalReleased(address token) external view returns (uint256) {
        return _totalReleased[token];
    }

    function shares(address payee) external view returns (uint256) {
        return _shares[payee];
    }

    function totalShares() external view returns (uint256) {
        return _totalShares;
    }

    function getPayees() external view returns (address[] memory) {
        return _payees;
    }
}
