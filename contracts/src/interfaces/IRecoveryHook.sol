// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @notice Hook interface called by SocialRecoveryModule after recovery execution.
/// @dev Modules (SessionKeyModule, DeadManSwitch) implement this to react to ownership changes.
interface IRecoveryHook {
    /// @notice Called after a successful recovery.
    /// @param wallet The wallet address that was recovered (owner changed).
    function onRecoveryExecuted(address wallet) external;
}
