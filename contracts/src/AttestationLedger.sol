// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

/// @title AttestationLedger
/// @notice On-chain attestation registry. Only owner can attest. Anyone can verify.
/// @dev F-050: attest() protected by onlyOwner
contract AttestationLedger is Ownable {
    error ErrNoAttestationPath();

    event AttestationRecorded(address indexed submitter, bytes32 indexed subject, bytes32 attestationHash, uint256 timestamp, string metadata);

    mapping(bytes32 attestationHash => bool exists) public attestations;

    constructor() Ownable(msg.sender) {}

    /// @notice Record an attestation. Only callable by owner (F-050).
    function attest(bytes32 subject, bytes32 attestationHash, string calldata metadata) external onlyOwner {
        attestations[attestationHash] = true;
        emit AttestationRecorded(msg.sender, subject, attestationHash, block.timestamp, metadata);
    }

    function verify(bytes32 attestationHash) external view returns (bool) {
        return attestations[attestationHash];
    }

    function verifyBatch(bytes32[] calldata hashes) external view returns (bool[] memory results) {
        results = new bool[](hashes.length);
        for (uint256 i = 0; i < hashes.length; i++) {
            results[i] = attestations[hashes[i]];
        }
    }
}
