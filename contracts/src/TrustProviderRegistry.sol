// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {ITrustProvider} from "./ITrustProvider.sol";

contract TrustProviderRegistry is Ownable {
    enum ProviderStatus { UNREGISTERED, ACTIVE, DEPRECATED, SUNSET }

    struct ProviderInfo {
        address verifier;
        ProviderStatus status;
    }

    mapping(bytes32 providerId => ProviderInfo) public providers;

    error ProviderAlreadyRegistered();
    error ProviderNotRegistered();
    error ProviderNotActive();
    error InvalidVerifier();
    error NoTransferToZeroAddress();
    error NotPendingOwner();

    event ProviderRegistered(bytes32 indexed providerId, address indexed verifier);
    event ProviderStatusUpdated(bytes32 indexed providerId, ProviderStatus oldStatus, ProviderStatus newStatus);

    // 2-step ownership
    address public pendingOwner;
    event OwnershipTransferStarted(address indexed previousOwner, address indexed newOwner);

    constructor() Ownable(msg.sender) {}

    function registerProvider(bytes32 providerId, address verifierContract) external onlyOwner {
        if (verifierContract == address(0)) revert InvalidVerifier();
        if (providers[providerId].verifier != address(0)) revert ProviderAlreadyRegistered();
        providers[providerId] = ProviderInfo({verifier: verifierContract, status: ProviderStatus.ACTIVE});
        emit ProviderRegistered(providerId, verifierContract);
    }

    function setProviderStatus(bytes32 providerId, ProviderStatus status) external onlyOwner {
        if (providers[providerId].verifier == address(0)) revert ProviderNotRegistered();
        ProviderStatus oldStatus = providers[providerId].status;
        providers[providerId].status = status;
        emit ProviderStatusUpdated(providerId, oldStatus, status);
    }

    function verify(bytes32 providerId, bytes32 intentHash, bytes calldata proof) external view returns (bool) {
        ProviderInfo storage info = providers[providerId];
        if (info.status != ProviderStatus.ACTIVE) revert ProviderNotActive();
        return ITrustProvider(info.verifier).verify(intentHash, proof);
    }

    function getProvider(bytes32 providerId) external view returns (address verifier, ProviderStatus status) {
        ProviderInfo storage info = providers[providerId];
        return (info.verifier, info.status);
    }

    // ── 2-step ownership transfer ──

    function transferOwnership(address newOwner) public override onlyOwner {
        if (newOwner == address(0)) revert NoTransferToZeroAddress();
        pendingOwner = newOwner;
        emit OwnershipTransferStarted(owner(), newOwner);
    }

    function acceptOwnership() external {
        if (msg.sender != pendingOwner) revert NotPendingOwner();
        address previousOwner = owner();
        delete pendingOwner;
        _transferOwnership(msg.sender);
        emit OwnershipTransferred(previousOwner, msg.sender);
    }
}
