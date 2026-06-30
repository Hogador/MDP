// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title SessionKeyModule
/// @notice Identity Connect: scoped session keys with permissions, spending limits, and expiry.
/// @dev Standalone contract (no inheritance). Keys are owned by wallet addresses.
///      Only the owner can create, revoke keys. External callers validate + use.
contract SessionKeyModule {
    error SessionKeyExpired();
    error SessionKeyRevoked();
    error PermissionDenied();
    error SpendingLimitExceeded();
    error Unauthorized();
    error InvalidKey();

    struct SessionKey {
        address owner;
        address dapp;
        uint256 validAfter;
        uint256 validUntil;
        bytes32[] permissions;
        uint256 spendingLimit;
        uint256 spent;
        bool revoked;
        uint8 riskTier;        // 0=LOW, 1=MEDIUM, 2=HIGH
        uint256 successCount;   // successful operations (for dynamic limit)
        uint256 lastAmount;     // last operation amount (spike detection)
        uint256 lastUsedAt;     // timestamp of last use (time-decay)
    }

    mapping(bytes32 => SessionKey) public sessionKeys;
    // owner => key count (for future enumeration if needed)
    mapping(address => uint256) public keyCount;

    event SessionKeyCreated(
        bytes32 indexed keyId,
        address indexed owner,
        address indexed dapp,
        uint256 validUntil,
        uint256 spendingLimit,
        uint8 riskTier
    );
    event SessionKeyRevokedEv(bytes32 indexed keyId, address indexed owner);
    event SessionKeyUsed(bytes32 indexed keyId, bytes32 indexed permission, uint256 amount);

    /// @notice Create a new session key. Only callable by the wallet owner (msg.sender).
    /// @param dapp         The dApp this key grants access to.
    /// @param validUntil   Expiry timestamp (seconds). Key is valid from block.timestamp.
    /// @param permissions  Array of allowed permission identifiers (bytes32).
    /// @param spendingLimit  Maximum total spending allowed (0 = unlimited).
    /// @param riskTier     Risk tier: 0=LOW, 1=MEDIUM, 2=HIGH.
    /// @return keyId       The unique identifier for this session key.
    function createSessionKey(
        address dapp,
        uint256 validUntil,
        bytes32[] calldata permissions,
        uint256 spendingLimit,
        uint8 riskTier
    ) external returns (bytes32 keyId) {
        if (dapp == address(0) || validUntil <= block.timestamp) revert InvalidKey();
        if (riskTier > 2) revert InvalidKey();

        keyCount[msg.sender]++;

        keyId = keccak256(abi.encodePacked(msg.sender, dapp, block.timestamp, keyCount[msg.sender]));

        SessionKey storage key = sessionKeys[keyId];
        key.owner = msg.sender;
        key.dapp = dapp;
        key.validAfter = block.timestamp;
        key.validUntil = validUntil;
        key.permissions = permissions;
        key.spendingLimit = spendingLimit;
        key.riskTier = riskTier;

        emit SessionKeyCreated(keyId, msg.sender, dapp, validUntil, spendingLimit, riskTier);
    }

    /// @notice Revoke a session key. Only the owner can revoke.
    /// @param keyId  The key to revoke.
    function revokeSessionKey(bytes32 keyId) external {
        SessionKey storage key = sessionKeys[keyId];
        if (key.owner == address(0)) revert InvalidKey();
        if (msg.sender != key.owner) revert Unauthorized();
        if (key.revoked) revert SessionKeyRevoked();

        key.revoked = true;
        emit SessionKeyRevokedEv(keyId, msg.sender);
    }

    /// @notice Validate a session key: not expired, not revoked, has permission, within dynamic limit.
    /// @param keyId      The key to validate.
    /// @param permission The permission being checked.
    /// @param amount     The spending amount being attempted.
    function validateSessionKey(bytes32 keyId, bytes32 permission, uint256 amount) external view {
        SessionKey storage key = sessionKeys[keyId];
        if (key.owner == address(0)) revert InvalidKey();
        if (key.revoked) revert SessionKeyRevoked();
        if (block.timestamp < key.validAfter || block.timestamp > key.validUntil) revert SessionKeyExpired();
        if (!_hasPermission(key, permission)) revert PermissionDenied();

        uint256 dynamicLimit = _computeDynamicLimit(key);
        if (dynamicLimit > 0 && key.spent + amount > dynamicLimit) revert SpendingLimitExceeded();
    }

    /// @notice Record usage of a session key. Increments spent, updates risk metrics, emits event.
    /// @dev Call validateSessionKey separately before calling this.
    /// @param keyId      The key being used.
    /// @param permission The permission exercised.
    /// @param amount     The spending amount.
    function useSessionKey(bytes32 keyId, bytes32 permission, uint256 amount) external {
        SessionKey storage key = sessionKeys[keyId];
        if (key.owner == address(0)) revert InvalidKey();
        if (key.revoked) revert SessionKeyRevoked();
        if (block.timestamp < key.validAfter || block.timestamp > key.validUntil) revert SessionKeyExpired();
        if (!_hasPermission(key, permission)) revert PermissionDenied();

        uint256 dynamicLimit = _computeDynamicLimit(key);
        if (dynamicLimit > 0 && key.spent + amount > dynamicLimit) revert SpendingLimitExceeded();

        key.spent += amount;

        // Spike detection: amount > 2x lastAmount → reset trust
        if (key.lastAmount > 0 && amount > key.lastAmount * 2) {
            key.successCount = 0;
        } else {
            key.successCount++;
        }

        key.lastAmount = amount;
        key.lastUsedAt = block.timestamp;

        emit SessionKeyUsed(keyId, permission, amount);
    }

    /// @notice Get session key details.
    function getSessionKey(bytes32 keyId) external view returns (
        address owner,
        address dapp,
        uint256 validAfter,
        uint256 validUntil,
        bytes32[] memory permissions,
        uint256 spendingLimit,
        uint256 spent,
        bool revoked,
        uint8 riskTier,
        uint256 successCount,
        uint256 lastAmount,
        uint256 lastUsedAt
    ) {
        SessionKey storage key = sessionKeys[keyId];
        return (key.owner, key.dapp, key.validAfter, key.validUntil, key.permissions, key.spendingLimit, key.spent, key.revoked, key.riskTier, key.successCount, key.lastAmount, key.lastUsedAt);
    }

    /// @notice Compute effective successCount with time-decay bonus.
    /// @dev Time-decay: +1 per day since last use, cap 30 total.
    function _effectiveSuccessCount(SessionKey storage key) internal view returns (uint256) {
        if (key.lastUsedAt == 0) return key.successCount;
        uint256 hoursSince = (block.timestamp - key.lastUsedAt) / 1 hours;
        if (hoursSince < 24) return key.successCount;
        uint256 daysSince = hoursSince / 24;
        uint256 total = key.successCount + daysSince;
        return total > 30 ? 30 : total;
    }

    /// @notice Compute dynamic spending limit based on risk tier and effective successCount.
    /// @dev LOW risk + successCount > 10 → +20%. MEDIUM/HIGH → no bonus.
    function _computeDynamicLimit(SessionKey storage key) internal view returns (uint256) {
        if (key.spendingLimit == 0) return 0; // unlimited
        if (key.riskTier == 0) { // LOW
            uint256 effective = _effectiveSuccessCount(key);
            if (effective > 10) return key.spendingLimit * 120 / 100;
        }
        // MEDIUM (1) and HIGH (2): no bonus
        return key.spendingLimit;
    }

    function _hasPermission(SessionKey storage key, bytes32 permission) internal view returns (bool) {
        bytes32[] storage perms = key.permissions;
        for (uint256 i = 0; i < perms.length; i++) {
            if (perms[i] == permission) return true;
        }
        return false;
    }
}
