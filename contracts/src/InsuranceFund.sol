// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";

contract InsuranceFund is Ownable {
    error ErrInsufficientFunds();
    error ErrClaimLimitExceeded();
    error ErrInvalidAuditApproval();
    error ErrNoFeeCollected();
    error ErrTransferFailed();

    uint256 public constant FEE_BPS = 50;
    uint256 public constant MAX_CLAIM_BPS = 1000;
    uint256 public totalFunds;

    address public immutable auditor;

    mapping(address => uint256) public claims;

    event FeeCollected(address indexed from, uint256 amount, uint256 fee);
    event ClaimSubmitted(address indexed victim, uint256 amount, bytes32 bugReportHash);
    event ClaimApproved(address indexed victim, uint256 amount, uint256 totalClaimed);
    event ClaimRejected(address indexed victim, bytes32 bugReportHash);
    event FundWithdrawn(address indexed to, uint256 amount);

    constructor(address _auditor) Ownable(msg.sender) {
        if (_auditor == address(0)) revert ErrInvalidAuditApproval();
        auditor = _auditor;
    }

    function collectFee(address from, uint256 amount) external onlyOwner {
        if (amount == 0) revert ErrNoFeeCollected();
        uint256 fee = (amount * FEE_BPS) / 10000;
        if (fee == 0) return;
        totalFunds += fee;
        emit FeeCollected(from, amount, fee);
    }

    function submitClaim(
        address victim,
        uint256 amount,
        bytes32 bugReportHash,
        bytes[] calldata auditorSignatures
    ) external {
        if (auditorSignatures.length < 3) revert ErrInvalidAuditApproval();
        if (amount > totalFunds) revert ErrInsufficientFunds();
        uint256 maxClaim = (totalFunds * MAX_CLAIM_BPS) / 10000;
        if (amount > maxClaim) revert ErrClaimLimitExceeded();

        bytes32 digest = keccak256(abi.encode(victim, amount, bugReportHash));
        bytes32 ethSignedDigest = keccak256(abi.encodePacked("\x19Ethereum Signed Message:\n32", digest));
        bool verified;
        for (uint256 i; i < auditorSignatures.length; i++) {
            if (ECDSA.recover(ethSignedDigest, auditorSignatures[i]) == auditor) {
                verified = true;
                break;
            }
        }
        if (!verified) revert ErrInvalidAuditApproval();

        emit ClaimSubmitted(victim, amount, bugReportHash);
    }

    function approveClaim(
        address victim,
        uint256 amount
    ) external onlyOwner {
        if (amount > totalFunds) revert ErrInsufficientFunds();
        uint256 maxClaim = (totalFunds * MAX_CLAIM_BPS) / 10000;
        if (amount > maxClaim) revert ErrClaimLimitExceeded();

        totalFunds -= amount;
        claims[victim] += amount;

        (bool sent, ) = payable(victim).call{value: amount}("");
        if (!sent) revert ErrTransferFailed();

        emit ClaimApproved(victim, amount, claims[victim]);
    }

    function rejectClaim(bytes32 bugReportHash) external onlyOwner {
        emit ClaimRejected(address(0), bugReportHash);
    }

    function withdrawFunds(address to, uint256 amount) external onlyOwner {
        if (amount > totalFunds) revert ErrInsufficientFunds();
        totalFunds -= amount;
        (bool sent, ) = payable(to).call{value: amount}("");
        if (!sent) revert ErrTransferFailed();
        emit FundWithdrawn(to, amount);
    }

    receive() external payable {}
}
