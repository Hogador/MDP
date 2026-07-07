// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/// @title InsuranceFund
/// @notice Multi-auditor insurance fund. Claims require N-of-M auditor signatures
///         with strict deduplication (sorted signers) and EIP-712 replay protection.
/// @dev    S-01 fix: single auditor → multi-auditor with bitmap deduplication via
///         sorted address check (gas-efficient, no storage writes per signature).
contract InsuranceFund is Ownable, ReentrancyGuard, EIP712 {
    error ErrInsufficientFunds();
    error ErrClaimLimitExceeded();
    error ErrInvalidAuditApproval();
    error ErrNoFeeCollected();
    error ErrTransferFailed();
    error ErrEmptyAuditors();
    error ErrDuplicateAuditor();
    error ErrNotAuditor();

    uint256 public constant FEE_BPS = 50;
    uint256 public constant MAX_CLAIM_BPS = 1000;
    /// @dev Max auditors supported (gas bound for loop in submitClaim)
    uint256 public constant MAX_AUDITORS = 20;

    uint256 public totalFunds;
    uint256 public requiredSignatures;

    /// @dev victim → total amount claimed (for transparency)
    mapping(address => uint256) public claims;

    address[] public auditors;
    /// @dev address → isAuditor quick lookup (true for indices < auditors.length)
    mapping(address => bool) public isAuditor;

    /// @dev victim → nonce to prevent replay of the same claim hash
    mapping(address => uint256) public claimNonces;

    // EIP-712 typehash for structured claim
    bytes32 private constant CLAIM_TYPEHASH =
        keccak256("Claim(address victim,uint256 amount,bytes32 bugReportHash,uint256 nonce)");

    event FeeCollected(address indexed from, uint256 amount, uint256 fee);
    event ClaimSubmitted(address indexed victim, uint256 amount, bytes32 bugReportHash);
    event ClaimApproved(address indexed victim, uint256 amount, uint256 totalClaimed);
    event ClaimRejected(address indexed victim, bytes32 bugReportHash);
    event FundWithdrawn(address indexed to, uint256 amount);
    event AuditorAdded(address indexed auditor);
    event AuditorRemoved(address indexed auditor);
    event RequiredSignaturesUpdated(uint256 oldRequired, uint256 newRequired);

    constructor(address[] memory _auditors, uint256 _requiredSignatures)
        Ownable(msg.sender)
        EIP712("InsuranceFund", "1")
    {
        if (_auditors.length == 0) revert ErrEmptyAuditors();
        if (_requiredSignatures == 0 || _requiredSignatures > _auditors.length) {
            revert ErrInvalidAuditApproval();
        }
        for (uint256 i; i < _auditors.length; i++) {
            if (_auditors[i] == address(0)) revert ErrInvalidAuditApproval();
            if (isAuditor[_auditors[i]]) revert ErrDuplicateAuditor();
            auditors.push(_auditors[i]);
            isAuditor[_auditors[i]] = true;
            emit AuditorAdded(_auditors[i]);
        }
        requiredSignatures = _requiredSignatures;
        emit RequiredSignaturesUpdated(0, _requiredSignatures);
    }

    /// @notice Add a new auditor (owner only).
    function addAuditor(address auditor_) external onlyOwner {
        if (auditor_ == address(0)) revert ErrInvalidAuditApproval();
        if (isAuditor[auditor_]) revert ErrDuplicateAuditor();
        if (auditors.length >= MAX_AUDITORS) revert ErrInvalidAuditApproval();
        auditors.push(auditor_);
        isAuditor[auditor_] = true;
        emit AuditorAdded(auditor_);
    }

    /// @notice Remove an auditor (owner only).
    ///         Uses swap-and-pop pattern for O(1) removal.
    function removeAuditor(address auditor_) external onlyOwner {
        if (!isAuditor[auditor_]) revert ErrNotAuditor();
        if (auditors.length <= requiredSignatures) revert ErrInvalidAuditApproval();
        isAuditor[auditor_] = false;
        // Find and swap with last
        for (uint256 i; i < auditors.length; i++) {
            if (auditors[i] == auditor_) {
                auditors[i] = auditors[auditors.length - 1];
                auditors.pop();
                break;
            }
        }
        emit AuditorRemoved(auditor_);
    }

    /// @notice Update required signature count. Must be 1..auditors.length.
    function setRequiredSignatures(uint256 _required) external onlyOwner {
        if (_required == 0 || _required > auditors.length) revert ErrInvalidAuditApproval();
        uint256 old = requiredSignatures;
        requiredSignatures = _required;
        emit RequiredSignaturesUpdated(old, _required);
    }

    /// @notice Get the full list of current auditors.
    function getAuditors() external view returns (address[] memory) {
        return auditors;
    }

    // ─── Fees & Claims ─────────────────────────────────────

    function collectFee(address from, uint256 amount) external onlyOwner {
        if (amount == 0) revert ErrNoFeeCollected();
        uint256 fee = (amount * FEE_BPS) / 10000;
        if (fee == 0) return;
        totalFunds += fee;
        emit FeeCollected(from, amount, fee);
    }

    /// @notice Submit a bug claim with N-of-M auditor EIP-712 signatures.
    ///         Signatures MUST be sorted by signer address ascending (off-chain).
    ///         Deduplication via `signer > lastSigner` check — no storage writes per sig.
    function submitClaim(
        address victim,
        uint256 amount,
        bytes32 bugReportHash,
        bytes[] calldata auditorSignatures
    ) external {
        if (auditorSignatures.length < requiredSignatures) revert ErrInvalidAuditApproval();
        if (amount > totalFunds) revert ErrInsufficientFunds();
        uint256 maxClaim = (totalFunds * MAX_CLAIM_BPS) / 10000;
        if (amount > maxClaim) revert ErrClaimLimitExceeded();

        uint256 nonce = claimNonces[victim];
        bytes32 structHash = keccak256(
            abi.encode(CLAIM_TYPEHASH, victim, amount, bugReportHash, nonce)
        );
        bytes32 typedDigest = _hashTypedDataV4(structHash);

        uint256 validSigs;
        address lastSigner; // defaults to address(0)

        for (uint256 i; i < auditorSignatures.length; i++) {
            address signer = ECDSA.recover(typedDigest, auditorSignatures[i]);

            // Dedup: addresses MUST be strictly increasing
            if (signer <= lastSigner) revert ErrInvalidAuditApproval();
            lastSigner = signer;

            if (isAuditor[signer]) {
                validSigs++;
            }
        }

        if (validSigs < requiredSignatures) revert ErrInvalidAuditApproval();

        // CEI: consume nonce before emit
        claimNonces[victim] = nonce + 1;

        emit ClaimSubmitted(victim, amount, bugReportHash);
    }

    /// @notice Approve a claim (owner only, after successful auditor multi-sig).
    function approveClaim(
        address victim,
        uint256 amount
    ) external nonReentrant onlyOwner {
        if (amount > totalFunds) revert ErrInsufficientFunds();
        uint256 maxClaim = (totalFunds * MAX_CLAIM_BPS) / 10000;
        if (amount > maxClaim) revert ErrClaimLimitExceeded();

        // CEI: state before transfer
        totalFunds -= amount;
        claims[victim] += amount;

        (bool sent, ) = payable(victim).call{value: amount}("");
        if (!sent) revert ErrTransferFailed();

        emit ClaimApproved(victim, amount, claims[victim]);
    }

    function rejectClaim(bytes32 bugReportHash) external onlyOwner {
        emit ClaimRejected(address(0), bugReportHash);
    }

    function withdrawFunds(address to, uint256 amount) external nonReentrant onlyOwner {
        if (amount > totalFunds) revert ErrInsufficientFunds();
        // CEI: state before transfer
        totalFunds -= amount;
        (bool sent, ) = payable(to).call{value: amount}("");
        if (!sent) revert ErrTransferFailed();
        emit FundWithdrawn(to, amount);
    }

    /// @dev Public getter for domain separator (needed by tests to compute typed hashes).
    function domainSeparatorV4() external view returns (bytes32) {
        return _domainSeparatorV4();
    }

    receive() external payable {}
}
