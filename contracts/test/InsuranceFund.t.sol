// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {InsuranceFund} from "../src/InsuranceFund.sol";

contract InsuranceFundTest is Test {
    InsuranceFund public fund;
    address public owner = address(0xCAFE);
    address public user = address(0x1234);
    address public victim = address(0xBEEF);

    uint256 internal key1 = 0xA1D17;
    uint256 internal key2 = 0xB2E18;
    uint256 internal key3 = 0xC3F19;
    uint256 internal key4 = 0xD4E20;

    address internal auditor1 = vm.addr(key1);
    address internal auditor2 = vm.addr(key2);
    address internal auditor3 = vm.addr(key3);
    address internal auditor4 = vm.addr(key4);

    function setUp() public {
        address[] memory auditors = new address[](4);
        auditors[0] = auditor1;
        auditors[1] = auditor2;
        auditors[2] = auditor3;
        auditors[3] = auditor4;

        vm.prank(owner);
        fund = new InsuranceFund(auditors, 3);
    }

    // ─── Fee tests ──────────────────────────────────────────

    function test_CollectFee() public {
        vm.prank(owner);
        fund.collectFee(user, 100_000 ether);
        uint256 expectedFee = (100_000 ether * 50) / 10000;
        assertEq(fund.totalFunds(), expectedFee);
    }

    function test_RevertWhen_CollectFeeZero() public {
        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrNoFeeCollected.selector));
        fund.collectFee(user, 0);
    }

    function test_RevertWhen_NotOwnerCollectsFee() public {
        vm.prank(user);
        vm.expectRevert();
        fund.collectFee(user, 100 ether);
    }

    // ─── Submit claim (3-of-4) ──────────────────────────────

    function test_SubmitClaim() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();

        bytes[] memory sigs = _threeValidSigs(total / 100, bugHash);

        vm.prank(user);
        vm.expectEmit(true, true, false, true);
        emit InsuranceFund.ClaimSubmitted(victim, total / 100, bugHash);
        fund.submitClaim(victim, total / 100, bugHash, sigs);
    }

    function test_RevertWhen_DuplicateSigner() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();

        // All 3 sigs from the SAME auditor — should be detected as duplicate
        bytes[] memory sigs = _threeSigSameAuditor(total / 100, bugHash, key1);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInvalidAuditApproval.selector));
        fund.submitClaim(victim, total / 100, bugHash, sigs);
    }

    function test_RevertWhen_Only2Of4Valid() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();

        // 2 valid + 1 invalid = not enough (need 3)
        bytes[] memory sigs = _twoValidOneInvalid(total / 100, bugHash);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInvalidAuditApproval.selector));
        fund.submitClaim(victim, total / 100, bugHash, sigs);
    }

    function test_RevertWhen_ClaimExceedsMax() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();
        uint256 maxClaim = (total * 1000) / 10000;
        bytes[] memory sigs = _threeValidSigs(maxClaim + 1, bugHash);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrClaimLimitExceeded.selector));
        fund.submitClaim(victim, maxClaim + 1, bugHash, sigs);
    }

    function test_RevertWhen_SubmitInsufficientFunds() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();
        bytes[] memory sigs = _threeValidSigs(total + 1, bugHash);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInsufficientFunds.selector));
        fund.submitClaim(victim, total + 1, bugHash, sigs);
    }

    function test_RevertWhen_SubmitClaimInvalidAuditorSignature() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();

        bytes[] memory sigs = _wrongSigs(total / 100, bugHash);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInvalidAuditApproval.selector));
        fund.submitClaim(victim, total / 100, bugHash, sigs);
    }

    function test_RevertWhen_ReplayClaim() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();

        bytes[] memory sigs = _threeValidSigs(total / 100, bugHash);

        vm.prank(user);
        fund.submitClaim(victim, total / 100, bugHash, sigs);

        // Replay: same sigs with same nonce (now consumed) should fail
        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInvalidAuditApproval.selector));
        fund.submitClaim(victim, total / 100, bugHash, sigs);
    }

    // ─── Approve ────────────────────────────────────────────

    function test_ApproveClaim() public {
        _fundInsurance(10_000 ether);
        deal(victim, 0);

        uint256 total = fund.totalFunds();
        uint256 claimAmount = total / 10;

        // approveClaim requires claimNonces[victim] > 0 — submitClaim must run first
        bytes32 bugHash = keccak256("bug-approve");
        bytes[] memory sigs = _threeValidSigs(claimAmount, bugHash);
        fund.submitClaim(victim, claimAmount, bugHash, sigs);

        vm.prank(owner);
        vm.expectEmit(true, true, false, true);
        emit InsuranceFund.ClaimApproved(victim, claimAmount, claimAmount);
        fund.approveClaim(victim, claimAmount);

        assertEq(fund.claims(victim), claimAmount);
        assertEq(address(victim).balance, claimAmount);
    }

    function test_RevertWhen_NotOwnerApproves() public {
        _fundInsurance(10_000 ether);
        vm.prank(user);
        vm.expectRevert();
        fund.approveClaim(victim, 0.01 ether);
    }

    function test_RevertWhen_ApproveInsufficientFunds() public {
        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInsufficientFunds.selector));
        fund.approveClaim(victim, 1 ether);
    }

    function test_RevertWhen_ApproveExceedsMax() public {
        _fundInsurance(10_000 ether);
        uint256 total = fund.totalFunds();
        uint256 maxClaim = (total * 1000) / 10000;

        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrClaimLimitExceeded.selector));
        fund.approveClaim(victim, maxClaim + 1);
    }

    // ─── Withdraw ───────────────────────────────────────────

    function test_WithdrawFunds() public {
        _fundInsurance(10_000 ether);
        uint256 total = fund.totalFunds();

        vm.prank(owner);
        fund.withdrawFunds(owner, total / 2);

        assertEq(fund.totalFunds(), total - total / 2);
    }

    function test_RevertWhen_NotOwnerWithdraws() public {
        _fundInsurance(10_000 ether);
        vm.prank(user);
        vm.expectRevert();
        fund.withdrawFunds(user, 1 wei);
    }

    // ─── Auditor management ─────────────────────────────────

    function test_AddAndRemoveAuditor() public {
        assertEq(fund.getAuditors().length, 4);

        // Remove one auditor (now 3 auditors, required=3 still valid)
        vm.prank(owner);
        fund.removeAuditor(auditor4);

        assertEq(fund.getAuditors().length, 3);
        assertFalse(fund.isAuditor(auditor4));

        // Add back
        vm.prank(owner);
        fund.addAuditor(auditor4);

        assertEq(fund.getAuditors().length, 4);
        assertTrue(fund.isAuditor(auditor4));
    }

    function test_RevertWhen_RemoveBelowRequired() public {
        // Remove 2 auditors → only 2 left but required=3
        vm.prank(owner);
        fund.removeAuditor(auditor4);
        // Now 3 auditors left, can't remove (3 <= 3)
        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInvalidAuditApproval.selector));
        fund.removeAuditor(auditor3);
    }

    // ─── Reject ─────────────────────────────────────────────

    function test_RejectClaim() public {
        bytes32 bugHash = keccak256("bug-002");
        vm.prank(owner);
        vm.expectEmit(true, true, false, true);
        emit InsuranceFund.ClaimRejected(address(0), bugHash);
        fund.rejectClaim(bugHash);
    }

    // ─── Constants ──────────────────────────────────────────

    function test_FeeBpsConstant() public view {
        assertEq(fund.FEE_BPS(), 50);
    }

    function test_MaxClaimBpsConstant() public view {
        assertEq(fund.MAX_CLAIM_BPS(), 1000);
    }

    // ─── Helpers ────────────────────────────────────────────

    function _fundInsurance(uint256 amount) internal {
        uint256 fee = (amount * 50) / 10000;
        vm.prank(owner);
        fund.collectFee(user, amount);
        deal(address(fund), fee * 3);
    }

    /// @dev Build EIP-712 typed claim digest matching InsuranceFund._hashTypedDataV4
    function _claimDigest(address claimVictim, uint256 amount, bytes32 bugHash, uint256 nonce)
        internal view returns (bytes32)
    {
        bytes32 structHash = keccak256(
            abi.encode(
                keccak256("Claim(address victim,uint256 amount,bytes32 bugReportHash,uint256 nonce)"),
                claimVictim,
                amount,
                bugHash,
                nonce
            )
        );
        return _hashTypedDataV4(structHash);
    }

    /// @dev Wrapper to match EIP712._hashTypedDataV4 calculation using domain separator from contract
    function _hashTypedDataV4(bytes32 structHash) internal view returns (bytes32) {
        return keccak256(
            abi.encodePacked("\x19\x01", fund.domainSeparatorV4(), structHash)
        );
    }

    /// @dev Sign a claim with a given private key (returns 65-byte sig)
    function _signClaim(address claimVictim, uint256 amount, bytes32 bugHash, uint256 signerKey)
        internal view returns (bytes memory)
    {
        uint256 nonce = fund.claimNonces(claimVictim);
        bytes32 digest = _claimDigest(claimVictim, amount, bugHash, nonce);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(signerKey, digest);
        return abi.encodePacked(r, s, v);
    }

    /// @dev 3 valid sigs from different auditors (sorted ascending by address)
    function _threeValidSigs(uint256 amount, bytes32 bugHash)
        internal view returns (bytes[] memory sigs)
    {
        uint256 nonce = fund.claimNonces(victim);
        bytes32 digest = _claimDigest(victim, amount, bugHash, nonce);

        address[3] memory signers = [auditor1, auditor2, auditor3];
        bytes[3] memory rawSigs;

        for (uint256 i; i < 3; i++) {
            (uint8 v, bytes32 r, bytes32 s) = vm.sign(
                i == 0 ? key1 : (i == 1 ? key2 : key3),
                digest
            );
            rawSigs[i] = abi.encodePacked(r, s, v);
        }

        // Bubble sort by signer address (ascending)
        for (uint256 i; i < 2; i++) {
            for (uint256 j; j < 2 - i; j++) {
                if (signers[j] > signers[j + 1]) {
                    (signers[j], signers[j + 1]) = (signers[j + 1], signers[j]);
                    (rawSigs[j], rawSigs[j + 1]) = (rawSigs[j + 1], rawSigs[j]);
                }
            }
        }

        sigs = new bytes[](3);
        sigs[0] = rawSigs[0];
        sigs[1] = rawSigs[1];
        sigs[2] = rawSigs[2];
    }

    /// @dev 3 sigs all from the SAME auditor (should fail duplicate check)
    function _threeSigSameAuditor(uint256 amount, bytes32 bugHash, uint256 singleKey)
        internal view returns (bytes[] memory sigs)
    {
        uint256 nonce = fund.claimNonces(victim);
        bytes32 digest = _claimDigest(victim, amount, bugHash, nonce);

        (uint8 v, bytes32 r, bytes32 s) = vm.sign(singleKey, digest);
        bytes memory sig = abi.encodePacked(r, s, v);

        sigs = new bytes[](3);
        sigs[0] = sig;
        sigs[1] = sig;
        sigs[2] = sig;
    }

    /// @dev 2 valid + 1 invalid (not enough valid sigs)
    function _twoValidOneInvalid(uint256 amount, bytes32 bugHash)
        internal view returns (bytes[] memory sigs)
    {
        uint256 nonce = fund.claimNonces(victim);
        bytes32 digest = _claimDigest(victim, amount, bugHash, nonce);

        (uint8 v1, bytes32 r1, bytes32 s1) = vm.sign(key1, digest);
        (uint8 v2, bytes32 r2, bytes32 s2) = vm.sign(key2, digest);
        // invalid sig from random address (not an auditor)
        (uint8 v3, bytes32 r3, bytes32 s3) = vm.sign(0xDEAD, digest);

        sigs = new bytes[](3);
        // Sort: auditor1 < auditor2 < 0xDEAD...? Check order
        // vm.addr(0xDEAD) > auditor2 usually, but let's verify
        address random = vm.addr(0xDEAD);
        if (auditor1 < auditor2 && auditor2 < random) {
            sigs[0] = abi.encodePacked(r1, s1, v1);
            sigs[1] = abi.encodePacked(r2, s2, v2);
            sigs[2] = abi.encodePacked(r3, s3, v3);
        } else {
            // Just in case order differs
            sigs[0] = abi.encodePacked(r1, s1, v1);
            sigs[1] = abi.encodePacked(r2, s2, v2);
            sigs[2] = abi.encodePacked(r3, s3, v3);
        }
    }

    /// @dev 3 sigs all from non-auditor keys
    function _wrongSigs(uint256 amount, bytes32 bugHash)
        internal view returns (bytes[] memory sigs)
    {
        uint256 nonce = fund.claimNonces(victim);
        bytes32 digest = _claimDigest(victim, amount, bugHash, nonce);

        (uint8 v1, bytes32 r1, bytes32 s1) = vm.sign(0xAAAA, digest);
        (uint8 v2, bytes32 r2, bytes32 s2) = vm.sign(0xBBBB, digest);
        (uint8 v3, bytes32 r3, bytes32 s3) = vm.sign(0xCCCC, digest);

        sigs = new bytes[](3);
        sigs[0] = abi.encodePacked(r1, s1, v1);
        sigs[1] = abi.encodePacked(r2, s2, v2);
        sigs[2] = abi.encodePacked(r3, s3, v3);
    }
}
