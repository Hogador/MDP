// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {InsuranceFund} from "../src/InsuranceFund.sol";

contract InsuranceFundTest is Test {
    InsuranceFund public fund;
    address public owner = address(0xCAFE);
    address public user = address(0x1234);
    address public victim = address(0xBEEF);

    uint256 internal auditorKey = 0xA1D17;
    address internal auditor = vm.addr(auditorKey);

    function setUp() public {
        vm.prank(owner);
        fund = new InsuranceFund(auditor);
    }

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

    function test_CollectFee_RevertWhen_NotOwner() public {
        vm.prank(user);
        vm.expectRevert();
        fund.collectFee(user, 100 ether);
    }

    function test_SubmitClaim() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");

        uint256 total = fund.totalFunds();
        uint256 claimAmount = total / 100;

        bytes[] memory sigs = _threeSigs(bugHash, claimAmount);

        vm.prank(user);
        vm.expectEmit(true, true, false, true);
        emit InsuranceFund.ClaimSubmitted(victim, claimAmount, bugHash);
        fund.submitClaim(victim, claimAmount, bugHash, sigs);
    }

    function test_RevertWhen_ClaimExceedsMax() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();
        uint256 claimAmount = total / 100;
        uint256 maxClaim = (total * 1000) / 10000;
        bytes[] memory sigs = _threeSigs(bugHash, maxClaim + 1);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrClaimLimitExceeded.selector));
        fund.submitClaim(victim, maxClaim + 1, bugHash, sigs);
    }

    function test_RevertWhen_SubmitInsufficientFunds() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();
        uint256 badAmount = total + 1;
        bytes[] memory sigs = _threeSigs(bugHash, badAmount);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInsufficientFunds.selector));
        fund.submitClaim(victim, badAmount, bugHash, sigs);
    }

    function test_RevertWhen_SubmitClaimInvalidAuditorSignature() public {
        _fundInsurance(10_000 ether);
        bytes32 bugHash = keccak256("bug-001");
        uint256 total = fund.totalFunds();
        uint256 claimAmount = total / 100;

        // All 3 sigs are valid-format (65 bytes) but signed by wrong keys
        bytes[] memory sigs = _wrongSigs(bugHash, claimAmount);

        vm.prank(user);
        vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ErrInvalidAuditApproval.selector));
        fund.submitClaim(victim, claimAmount, bugHash, sigs);
    }

    function test_ApproveClaim() public {
        _fundInsurance(10_000 ether);
        deal(victim, 0);

        uint256 total = fund.totalFunds();
        uint256 claimAmount = total / 10;

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

    function test_RejectClaim() public {
        bytes32 bugHash = keccak256("bug-002");
        vm.prank(owner);
        vm.expectEmit(true, true, false, true);
        emit InsuranceFund.ClaimRejected(address(0), bugHash);
        fund.rejectClaim(bugHash);
    }

    function test_FeeBpsConstant() public view {
        assertEq(fund.FEE_BPS(), 50);
    }

    function test_MaxClaimBpsConstant() public view {
        assertEq(fund.MAX_CLAIM_BPS(), 1000);
    }

    function _fundInsurance(uint256 amount) internal {
        uint256 fee = (amount * 50) / 10000;
        vm.prank(owner);
        fund.collectFee(user, amount);
        deal(address(fund), fee * 3);
    }

    function _ethSignedDigest(address claimVictim, uint256 amount, bytes32 bugHash) internal pure returns (bytes32) {
        bytes32 digest = keccak256(abi.encode(claimVictim, amount, bugHash));
        return keccak256(abi.encodePacked("\x19Ethereum Signed Message:\n32", digest));
    }

    function _signClaim(address claimVictim, uint256 amount, bytes32 bugHash) internal view returns (bytes memory) {
        bytes32 ethSignedDigest = _ethSignedDigest(claimVictim, amount, bugHash);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(auditorKey, ethSignedDigest);
        return abi.encodePacked(r, s, v);
    }

    /// @dev Create a valid-format (65 bytes) signature from a random key
    function _signRandom(bytes32 ethSignedDigest) internal view returns (bytes memory) {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(0xBAD, ethSignedDigest);
        return abi.encodePacked(r, s, v);
    }

    /// @dev Returns 3 sigs: [auditor, random, random] — all valid format 65 bytes
    function _threeSigs(bytes32 bugHash, uint256 amount) internal view returns (bytes[] memory sigs) {
        bytes32 ethSignedDigest = _ethSignedDigest(victim, amount, bugHash);
        sigs = new bytes[](3);
        sigs[0] = _signClaim(victim, amount, bugHash);
        sigs[1] = _signRandom(ethSignedDigest);
        sigs[2] = _signRandom(ethSignedDigest);
    }

    /// @dev Returns 3 sigs all signed by random keys (for invalid-auditor test)
    function _wrongSigs(bytes32 bugHash, uint256 amount) internal view returns (bytes[] memory sigs) {
        bytes32 ethSignedDigest = _ethSignedDigest(victim, amount, bugHash);
        sigs = new bytes[](3);
        sigs[0] = _signRandom(ethSignedDigest);
        sigs[1] = _signRandom(ethSignedDigest);
        sigs[2] = _signRandom(ethSignedDigest);
    }
}
