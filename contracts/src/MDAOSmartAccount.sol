// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/* solhint-disable avoid-low-level-calls */
/* solhint-disable no-empty-blocks */

import {BaseAccount} from "account-abstraction/core/BaseAccount.sol";
import {IEntryPoint} from "account-abstraction/interfaces/IEntryPoint.sol";
import {UserOperation} from "account-abstraction/interfaces/UserOperation.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";

/// @notice ERC-4337 smart account with ECDSA ownership and social recovery support.
/// @dev Single EOA owner, EntryPoint-only validation, transferOwnership for recovery.
/// Inherits validateUserOp from BaseAccount (EntryPoint-only via _requireFromEntryPoint).
contract MDAOSmartAccount is BaseAccount {
    IEntryPoint private immutable _entryPoint;
    address public owner;
    /// @notice Allowed caller of transferOwnership for recovery (SocialRecoveryModule).
    address public recoveryCaller;

    error ErrCannotBeZero();
    error ErrNotOwner();
    error ErrUnauthorized();
    error ErrZeroAddress();

    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);
    event RecoveryCallerSet(address indexed caller);

    modifier onlyOwner() {
        if (msg.sender != owner && msg.sender != address(_entryPoint) && msg.sender != recoveryCaller) {
            revert ErrNotOwner();
        }
        _;
    }

    /// @param entryPoint_ EntryPoint contract address.
    /// @param owner_ Initial owner address (EOA).
    constructor(IEntryPoint entryPoint_, address owner_) {
        if (address(entryPoint_) == address(0)) revert ErrCannotBeZero();
        if (owner_ == address(0)) revert ErrCannotBeZero();
        _entryPoint = entryPoint_;
        owner = owner_;
    }

    /// @notice Set the recovery caller (SocialRecoveryModule address).
    /// @dev Only callable by current owner. Zero address rejected to prevent
    ///      permanently locking recovery.
    function setRecoveryCaller(address caller) external {
        if (msg.sender != owner) revert ErrUnauthorized();
        if (caller == address(0)) revert ErrZeroAddress();
        recoveryCaller = caller;
        emit RecoveryCallerSet(caller);
    }

    /// @inheritdoc BaseAccount
    function entryPoint() public view virtual override returns (IEntryPoint) {
        return _entryPoint;
    }

    /// @inheritdoc BaseAccount
    function _validateSignature(
        UserOperation calldata userOp,
        bytes32 userOpHash
    ) internal virtual override returns (uint256 validationData) {
        if (owner != ECDSA.recover(userOpHash, userOp.signature)) {
            return SIG_VALIDATION_FAILED;
        }
        return 0; // SIG_VALIDATION_SUCCESS
    }

    /// @notice Transfer ownership (for social recovery integration).
    /// @param newOwner New owner address (must be non-zero).
    /// @dev Called via EntryPoint (execute), by current owner, or by SocialRecoveryModule.
    function transferOwnership(address newOwner) external onlyOwner {
        if (newOwner == address(0)) revert ErrCannotBeZero();
        address previousOwner = owner;
        owner = newOwner;
        emit OwnershipTransferred(previousOwner, newOwner);
    }

    /// @notice Current deposit in EntryPoint.
    function getDeposit() public view returns (uint256) {
        return entryPoint().balanceOf(address(this));
    }

    /// @notice Deposit ETH to EntryPoint for gas prefunding.
    function addDeposit() external payable {
        entryPoint().depositTo{value: msg.value}(address(this));
    }

    /// @notice Withdraw deposit from EntryPoint.
    function withdrawDepositTo(address payable withdrawAddress, uint256 amount) external onlyOwner {
        entryPoint().withdrawTo(withdrawAddress, amount);
    }

    /// @dev Accept ETH from EntryPoint for _payPrefund.
    receive() external payable {}
}
