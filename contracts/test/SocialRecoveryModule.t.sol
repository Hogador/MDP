// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {MockP256} from "./mocks/MockP256.sol";

contract SocialRecoveryModuleTest is Test {
    SocialRecoveryModule public recovery;
    MDAOToken public mdaoToken;
    MockP256 public mockP256;

    address public alice = makeAddr("alice");
    address public guardianA = makeAddr("guardianA");
    address public guardianB = makeAddr("guardianB");
    address public guardianC = makeAddr("guardianC");

    bytes32 constant PUBKEY_X_A = bytes32(uint256(1));
    bytes32 constant PUBKEY_Y_A = bytes32(uint256(2));
    bytes32 constant PUBKEY_X_B = bytes32(uint256(3));
    bytes32 constant PUBKEY_Y_B = bytes32(uint256(4));
    bytes32 constant PUBKEY_X_C = bytes32(uint256(5));
    bytes32 constant PUBKEY_Y_C = bytes32(uint256(6));

    bytes32 IDENTITY_A;
    bytes32 IDENTITY_B;
    bytes32 IDENTITY_C;

    bytes constant NEW_PUBKEY = hex"00000000000000000000000000000000000000000000000000000000000000050000000000000000000000000000000000000000000000000000000000000006";

    bytes constant VALID_SIG = abi.encode(bytes32(uint256(0xdead)), bytes32(uint256(0xbeef)));

    // WebAuthn test data
    bytes constant TEST_AUTH_DATA = hex"000000000000000000000000000000000000000000000000000000000000000000000000000000"; // 38 bytes
    bytes constant TEST_CLIENT_DATA = bytes('{"type":"webauthn.get","challenge":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","origin":"https://mdaopay.app"}');

    uint256 constant DEPOSIT = 0.01 ether;
    // MDAOToken has 50 bps burn fee, so actual deposit received is DEPOSIT minus fee
    uint256 constant BURN_FEE_BPS = 50;
    // Deposit received by contract (after burn fee on transferFrom)
    uint256 constant EXPECTED_DEPOSIT = DEPOSIT - (DEPOSIT * BURN_FEE_BPS / 10000);
    // Net refund to initiator (after burn fee on the refund transfer too)
    uint256 constant EXPECTED_REFUND = EXPECTED_DEPOSIT - (EXPECTED_DEPOSIT * BURN_FEE_BPS / 10000);

    function setUp() public {
        IDENTITY_A = keccak256(abi.encodePacked(guardianA));
        IDENTITY_B = keccak256(abi.encodePacked(guardianB));
        IDENTITY_C = keccak256(abi.encodePacked(guardianC));

        mockP256 = new MockP256();
        mdaoToken = new MDAOToken(address(this));
        recovery = new SocialRecoveryModule(address(mdaoToken), address(mockP256));

        // Mint MDAO tokens to test accounts
        mdaoToken.mint(alice, 1000 ether);
        mdaoToken.mint(guardianA, 1000 ether);
        mdaoToken.mint(guardianB, 1000 ether);
        mdaoToken.mint(guardianC, 1000 ether);

        // Approve recovery contract to spend tokens
        vm.prank(alice);
        mdaoToken.approve(address(recovery), type(uint256).max);
        vm.prank(guardianA);
        mdaoToken.approve(address(recovery), type(uint256).max);
        vm.prank(guardianB);
        mdaoToken.approve(address(recovery), type(uint256).max);
        vm.prank(guardianC);
        mdaoToken.approve(address(recovery), type(uint256).max);

        vm.prank(alice);
        recovery.registerWallet(bytes32(uint256(0xa1)), bytes32(uint256(0xa2)));
    }

    // ──────────────────────────────────────────────
    //  Guardian Management
    // ──────────────────────────────────────────────

    function test_AddGuardian() public {
        vm.prank(alice);
        recovery.addGuardian(alice, IDENTITY_A, PUBKEY_X_A, PUBKEY_Y_A);

        assertTrue(recovery.isGuardian(alice, IDENTITY_A));
        assertEq(recovery.guardianCount(alice), 1);

        SocialRecoveryModule.Guardian[] memory gs = recovery.getGuardians(alice);
        assertEq(gs.length, 1);
        assertEq(gs[0].identityHash, IDENTITY_A);
        assertEq(gs[0].pubKeyX, PUBKEY_X_A);
        assertEq(gs[0].pubKeyY, PUBKEY_Y_A);
    }

    function test_RevertWhen_AddExistingGuardian() public {
        vm.prank(alice);
        recovery.addGuardian(alice, IDENTITY_A, PUBKEY_X_A, PUBKEY_Y_A);

        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrAlreadyGuardian.selector);
        recovery.addGuardian(alice, IDENTITY_A, PUBKEY_X_A, PUBKEY_Y_A);
    }

    function test_RevertWhen_AddMaxGuardians() public {
        for (uint256 i = 0; i < 3; i++) {
            bytes32 id = keccak256(abi.encode(i));
            vm.prank(alice);
            recovery.addGuardian(alice, id, bytes32(uint256(i * 10 + 1)), bytes32(uint256(i * 10 + 2)));
        }

        bytes32 extra = keccak256("extra");
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrMaxGuardians.selector);
        recovery.addGuardian(alice, extra, PUBKEY_X_A, PUBKEY_Y_A);
    }

    function test_RevertWhen_AddInvalidKey() public {
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrInvalidPublicKey.selector);
        // Point at infinity (0, 0) — should be rejected
        recovery.addGuardian(alice, IDENTITY_A, bytes32(0), bytes32(0));
    }

    function test_RemoveGuardian() public {
        _addGuardiansAndConfirm(3);

        vm.prank(alice);
        recovery.removeGuardian(alice, IDENTITY_A);

        assertFalse(recovery.isGuardian(alice, IDENTITY_A));
        assertEq(recovery.guardianCount(alice), 2);
    }

    function test_RevertWhen_RemoveNonexistentGuardian() public {
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrNotGuardian.selector);
        recovery.removeGuardian(alice, IDENTITY_A);
    }

    function test_RevertWhen_RemoveBelowMinGuardians() public {
        // Add only 2 guardians — can't remove any
        _addGuardiansAndConfirm(2);

        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrCannotRemove.selector);
        recovery.removeGuardian(alice, IDENTITY_A);
    }

    function test_RevertWhen_AddGuardianByNonOwner() public {
        vm.prank(guardianA);
        vm.expectRevert(SocialRecoveryModule.ErrUnauthorized.selector);
        recovery.addGuardian(alice, IDENTITY_A, PUBKEY_X_A, PUBKEY_Y_A);
    }

    function test_RevertWhen_RemoveGuardianByNonOwner() public {
        vm.prank(alice);
        recovery.addGuardian(alice, IDENTITY_A, PUBKEY_X_A, PUBKEY_Y_A);

        vm.prank(guardianA);
        vm.expectRevert(SocialRecoveryModule.ErrUnauthorized.selector);
        recovery.removeGuardian(alice, IDENTITY_A);
    }

    function test_GuardianCanInitiateRecovery() public {
        _addGuardians();
        vm.prank(guardianA);
        recovery.initiateRecovery(alice, NEW_PUBKEY);
        (, uint256 startedAt,,,,,,) = recovery.getRecoveryRequest(alice);
        assertGt(startedAt, 0);
    }

    function test_StrangerCanInitiateRecoveryWithDeposit() public {
        _addGuardians();
        address stranger = makeAddr("stranger");
        mdaoToken.mint(stranger, 1000 ether);
        vm.prank(stranger);
        mdaoToken.approve(address(recovery), type(uint256).max);
        vm.prank(stranger);
        recovery.initiateRecovery(alice, NEW_PUBKEY);
        (, uint256 startedAt,,,,,,) = recovery.getRecoveryRequest(alice);
        assertGt(startedAt, 0);
        // Actual deposit accounts for MDAOToken burn fee (50 bps)
        assertEq(recovery.recoveryDeposit(alice), EXPECTED_DEPOSIT);
    }

    function test_RevertWhen_InitiateWithInsufficientDeposit() public {
        _addGuardians();
        // Use a account with no approval — OZ ERC20 transferFrom reverts
        address poor = makeAddr("poor");
        mdaoToken.mint(poor, 1000 ether);
        // No approval — transferFrom will revert with ERC20InsufficientAllowance
        vm.prank(poor);
        vm.expectRevert();
        recovery.initiateRecovery(alice, NEW_PUBKEY);
    }

    // ──────────────────────────────────────────────
    //  Wallet Registration
    // ──────────────────────────────────────────────

    function test_RevertWhen_AlreadyRegistered() public {
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrAlreadyRegistered.selector);
        recovery.registerWallet(bytes32(uint256(0xb1)), bytes32(uint256(0xb2)));
    }

    function test_RevertWhen_RegisterWithZeroKey() public {
        vm.prank(makeAddr("bob"));
        vm.expectRevert(SocialRecoveryModule.ErrInvalidPublicKey.selector);
        recovery.registerWallet(bytes32(0), bytes32(uint256(0xb2)));
    }

    // ──────────────────────────────────────────────
    //  Recovery Flow
    // ──────────────────────────────────────────────

    function test_InitiateRecovery() public {
        _addGuardians();

        bytes32 oldKeyHash = recovery.ownerPasskeyHash(alice);
        vm.prank(alice);
        vm.expectEmit(true, true, true, true);
        emit SocialRecoveryModule.RecoveryInitiated(alice, oldKeyHash, keccak256(NEW_PUBKEY), alice, block.timestamp + 48 hours, 1);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (bytes memory pubKey, uint256 started, uint256 approvals, uint256 vetoes, bool vetoed, bool executed, uint256 deadline, uint256 nonce) =
            recovery.getRecoveryRequest(alice);

        assertEq(pubKey, NEW_PUBKEY);
        assertEq(started, block.timestamp);
        assertEq(approvals, 0);
        assertEq(vetoes, 0);
        assertFalse(vetoed);
        assertFalse(executed);
        assertEq(deadline, block.timestamp + 48 hours);
        assertEq(nonce, 1);
    }

    function test_RevertWhen_InitiateWithoutGuardians() public {
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrInsufficientApprovals.selector);
        recovery.initiateRecovery(alice, NEW_PUBKEY);
    }

    function test_RevertWhen_InitiateWithInvalidPubKey() public {
        _addGuardians();
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrInvalidPublicKey.selector);
        recovery.initiateRecovery(alice, hex"dead");
    }

    function test_RevertWhen_InitiateWhenAlreadyActive() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrRecoveryAlreadyActive.selector);
        recovery.initiateRecovery(alice, NEW_PUBKEY);
    }

    function test_ApproveRecovery() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceA) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceA);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        (, , uint256 approvals, , , , , ) = recovery.getRecoveryRequest(alice);
        assertEq(approvals, 1);
    }

    function test_RevertWhen_ApproveTwice() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceTw) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceTw);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.prank(guardianA);
        vm.expectRevert(SocialRecoveryModule.ErrAlreadyApproved.selector);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
    }

    function test_RevertWhen_ApproveWithDifferentCallerSameIdentity() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceDc) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceDc);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.prank(makeAddr("stranger"));
        vm.expectRevert(SocialRecoveryModule.ErrAlreadyApproved.selector);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
    }

    function test_RevertWhen_ApproveByNonGuardian() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        bytes32 unknownId = keccak256("unknown");

        vm.prank(makeAddr("stranger"));
        vm.expectRevert(SocialRecoveryModule.ErrNotGuardian.selector);
        recovery.approveRecovery(alice, unknownId, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
    }

    function test_RevertWhen_ApproveWithInvalidSig() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        bytes memory shortSig = abi.encode(bytes32(uint256(0xdead)));
        vm.prank(guardianA);
        vm.expectRevert(SocialRecoveryModule.ErrInvalidSignature.selector);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, shortSig);
    }

    function test_ExecuteRecoveryAfterTimelock() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceEx) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceEx);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.approveRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.warp(block.timestamp + 48 hours + 1);

        uint256 initiatorBalBefore = mdaoToken.balanceOf(alice);
        vm.prank(alice);
        recovery.executeRecovery(alice);

        bytes32 expectedHash = keccak256(NEW_PUBKEY);
        assertEq(recovery.ownerPasskeyHash(alice), expectedHash);
        // Deposit refunded to initiator (minus burn fees on deposit + refund)
        assertEq(recovery.recoveryDeposit(alice), 0);
        assertEq(mdaoToken.balanceOf(alice), initiatorBalBefore + EXPECTED_REFUND);
    }

    function test_RevertWhen_ExecuteBeforeTimelock() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceEb) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceEb);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.approveRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrTimelockNotPassed.selector);
        recovery.executeRecovery(alice);
    }

    function test_RevertWhen_ExecuteWithOnlyOneApproval() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceEw) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceEw);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        // Only 1 approval — need 2 (GUARDIAN_THRESHOLD)

        vm.warp(block.timestamp + 48 hours + 1);

        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrInsufficientApprovals.selector);
        recovery.executeRecovery(alice);
    }

    function test_VetoRecovery() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceV) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceV);

        uint256 initiatorBalBefore = mdaoToken.balanceOf(alice);
        uint256 totalSupplyBefore = mdaoToken.totalSupply();

        vm.prank(guardianA);
        recovery.vetoRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.vetoRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        (, , , uint256 vetoes, bool vetoed, , , ) = recovery.getRecoveryRequest(alice);
        assertTrue(vetoed);
        // Deposit burned after veto via burn()
        assertEq(recovery.recoveryDeposit(alice), 0);
        assertEq(mdaoToken.balanceOf(alice), initiatorBalBefore);
        // Total supply decreased by the burned deposit
        assertEq(mdaoToken.totalSupply(), totalSupplyBefore - EXPECTED_DEPOSIT);
    }

    function test_RevertWhen_VetoByNonGuardian() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        bytes32 unknownId = keccak256("unknown");
        vm.prank(makeAddr("stranger"));
        vm.expectRevert(SocialRecoveryModule.ErrNotGuardian.selector);
        recovery.vetoRecovery(alice, unknownId, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
    }

    function test_RevertWhen_ExecuteVetoedRecovery() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceEv) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceEv);

        vm.prank(guardianA);
        recovery.vetoRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.vetoRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.warp(block.timestamp + 48 hours + 1);
        vm.prank(alice);
        vm.expectRevert(SocialRecoveryModule.ErrRecoveryVetoed.selector);
        recovery.executeRecovery(alice);
    }

    function test_ReinitiateAfterVeto() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceRv1) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceRv1);

        vm.prank(guardianA);
        recovery.vetoRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.vetoRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        bytes memory newPubKey2 = hex"00000000000000000000000000000000000000000000000000000000000000070000000000000000000000000000000000000000000000000000000000000008";
        vm.prank(alice);
        recovery.initiateRecovery(alice, newPubKey2);

        (,,,,,,, uint256 nonceRv2) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, newPubKey2, nonceRv2);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.approveRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.warp(block.timestamp + 48 hours + 1);
        vm.prank(alice);
        recovery.executeRecovery(alice);

        bytes32 expectedHash = keccak256(newPubKey2);
        assertEq(recovery.ownerPasskeyHash(alice), expectedHash);
    }

    function test_ReinitiateAfterExecution() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceRe1) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceRe1);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.approveRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.warp(block.timestamp + 48 hours + 1);
        vm.prank(alice);
        recovery.executeRecovery(alice);

        bytes memory newPubKey3 = hex"0000000000000000000000000000000000000000000000000000000000000009000000000000000000000000000000000000000000000000000000000000000a";
        vm.prank(alice);
        recovery.initiateRecovery(alice, newPubKey3);

        (bytes memory pubKey, , , , , , , uint256 nonce) = recovery.getRecoveryRequest(alice);
        assertEq(pubKey, newPubKey3);
        assertEq(nonce, 2);
    }

    // ──────────────────────────────────────────────
    //  C-3: Expiry & Cleanup
    // ──────────────────────────────────────────────

    function test_CleanupExpiredRecoveryBurnsDeposit() public {
        _addGuardians();

        // F-131: cleanup burns deposit instead of returning to initiator
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        // Fast forward past timelock + execution window
        vm.warp(block.timestamp + 48 hours + 48 hours + 1);

        // Capture state BEFORE cleanup
        uint256 initiatorBalBefore = mdaoToken.balanceOf(alice);
        uint256 actualDeposit = recovery.recoveryDeposit(alice); // may differ from EXPECTED_DEPOSIT due to burn fee
        uint256 totalSupplyBefore = mdaoToken.totalSupply();

        vm.prank(makeAddr("anyone"));
        vm.expectEmit(true, true, true, true);
        emit SocialRecoveryModule.RecoveryCleanedUp(alice, actualDeposit);
        recovery.cleanupExpiredRecovery(alice);

        // State reset
        (, uint256 started,,,,,,) = recovery.getRecoveryRequest(alice);
        assertEq(started, 0);
        assertEq(recovery.recoveryDeposit(alice), 0);

        // Deposit burned on expiry (F-131: anti-spam)
        assertEq(mdaoToken.balanceOf(alice), initiatorBalBefore, "initiator should NOT get deposit back");
        assertEq(recovery.recoveryDeposit(alice), 0, "deposit cleared");
        // Total supply unchanged (tokens at address(0), not _burn())
        assertEq(mdaoToken.totalSupply(), totalSupplyBefore, "total supply unchanged");
    }

    function test_RevertWhen_CleanupNotExpired() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        vm.prank(makeAddr("anyone"));
        vm.expectRevert(SocialRecoveryModule.ErrNoExpiredRecovery.selector);
        recovery.cleanupExpiredRecovery(alice);
    }

    function test_RevertWhen_CleanupAfterExecution() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceEx2) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceEx2);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.approveRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.warp(block.timestamp + 48 hours + 1);
        vm.prank(alice);
        recovery.executeRecovery(alice);

        vm.warp(block.timestamp + 48 hours + 48 hours + 1);
        vm.prank(makeAddr("anyone"));
        vm.expectRevert(SocialRecoveryModule.ErrRecoveryExecuted.selector);
        recovery.cleanupExpiredRecovery(alice);
    }

    function test_CleanupAnyoneCanCall() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        vm.warp(block.timestamp + 48 hours + 48 hours + 1);

        // Even a stranger can call cleanup
        address stranger = makeAddr("cleanupStranger");
        vm.prank(stranger);
        recovery.cleanupExpiredRecovery(alice);
        assertEq(recovery.recoveryDeposit(alice), 0);
    }

    // ──────────────────────────────────────────────
    //  Edge Cases
    // ──────────────────────────────────────────────

    function test_GuardianCountIsCorrectAfterMultipleOps() public {
        _addGuardiansAndConfirm(3);
        assertEq(recovery.guardianCount(alice), 3);

        vm.prank(alice);
        recovery.removeGuardian(alice, IDENTITY_A);
        assertEq(recovery.guardianCount(alice), 2);
    }

    function test_DepositReturnedToInitiatorOnExecute() public {
        _addGuardians();

        // Initiate as guardianA (initiator = guardianA)
        vm.prank(guardianA);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        (,,,,,,, uint256 nonceDr) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonceDr);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);
        vm.prank(guardianB);
        recovery.approveRecovery(alice, IDENTITY_B, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.warp(block.timestamp + 48 hours + 1);

        uint256 initiatorBalBefore = mdaoToken.balanceOf(guardianA);
        // Execute as alice, not initiator — deposit still goes to initiator
        vm.prank(alice);
        recovery.executeRecovery(alice);

        // Deposit returned minus burn fees
        assertEq(mdaoToken.balanceOf(guardianA), initiatorBalBefore + EXPECTED_REFUND);
    }

    // ──────────────────────────────────────────────
    //  F-109: DER → raw signature conversion
    // ──────────────────────────────────────────────

    function test_DerToRS_Valid() public {
        bytes32 expectedR = bytes32(uint256(0xdead));
        bytes32 expectedS = bytes32(uint256(0xbeef));

        bytes memory der = abi.encodePacked(
            hex"30440220", expectedR, hex"0220", expectedS
        );

        DerToRSTestHelper helper = new DerToRSTestHelper(address(mdaoToken), address(mockP256));
        (bytes32 r, bytes32 s) = helper.exposeDerToRS(der);
        assertEq(r, expectedR);
        assertEq(s, expectedS);
    }

    function test_DerToRS_ValidWithLeadingZeroR() public {
        // r with high bit set → 33 bytes in DER with leading 0x00
        bytes32 expectedR = bytes32(uint256(0x8000000000000000000000000000000000000000000000000000000000000000));
        bytes32 expectedS = bytes32(uint256(0xbeef));

        bytes memory der = abi.encodePacked(
            hex"3045022100", expectedR, hex"0220", expectedS
        );

        DerToRSTestHelper helper = new DerToRSTestHelper(address(mdaoToken), address(mockP256));
        (bytes32 r, bytes32 s) = helper.exposeDerToRS(der);
        assertEq(r, expectedR);
        assertEq(s, expectedS);
    }

    function test_DerToRS_InvalidTooShort() public {
        DerToRSTestHelper helper = new DerToRSTestHelper(address(mdaoToken), address(mockP256));
        vm.expectRevert(SocialRecoveryModule.ErrDerParsing.selector);
        helper.exposeDerToRS(hex"3000");
    }

    function test_DerToRS_InvalidBadTag() public {
        DerToRSTestHelper helper = new DerToRSTestHelper(address(mdaoToken), address(mockP256));
        vm.expectRevert(SocialRecoveryModule.ErrDerParsing.selector);
        helper.exposeDerToRS(hex"000000000000000000000000000000000000");
    }

    function test_ApproveRecoveryWithDerSignature() public {
        _addGuardians();
        vm.prank(alice);
        recovery.initiateRecovery(alice, NEW_PUBKEY);

        // DER-encoded version of VALID_SIG
        bytes memory derSig = abi.encodePacked(
            hex"30440220",
            bytes32(uint256(0xdead)),
            hex"0220",
            bytes32(uint256(0xbeef))
        );

        (,,,,,,, uint256 nonce) = recovery.getRecoveryRequest(alice);
        _mockAllApprovals(alice, NEW_PUBKEY, nonce);

        vm.prank(guardianA);
        recovery.approveRecovery(alice, IDENTITY_A, TEST_AUTH_DATA, TEST_CLIENT_DATA, derSig);

        (, , uint256 approvals, , , , , ) = recovery.getRecoveryRequest(alice);
        assertEq(approvals, 1);
    }

    // ──────────────────────────────────────────────
    //  F-108: P256_VERIFIER setter
    // ──────────────────────────────────────────────

    function test_SetP256Verifier() public {
        address newVerifier = makeAddr("newVerifier");
        vm.expectEmit(true, true, true, true);
        emit SocialRecoveryModule.P256VerifierUpdated(address(mockP256), newVerifier);
        recovery.setP256Verifier(newVerifier);
        assertEq(recovery.P256_VERIFIER(), newVerifier);
    }

    function test_RevertWhen_SetP256VerifierByNonOwner() public {
        vm.prank(makeAddr("stranger"));
        vm.expectRevert();
        recovery.setP256Verifier(makeAddr("hacker"));
    }

    function test_RevertWhen_SetP256VerifierZero() public {
        vm.expectRevert("Invalid verifier");
        recovery.setP256Verifier(address(0));
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    function _addGuardians() internal {
        _addGuardiansAndConfirm(3);
    }

    function _mockWebAuthnCall(address, bytes memory, uint256, bytes32, bytes32) internal {
        // MockP256 handles all verification calls — returns 1 for any input via fallback
        // No vm.mockCall needed when MockP256 address is passed as P256_VERIFIER
    }

    function _mockAllApprovals(address wallet, bytes memory newPubKey, uint256 nonce) internal {
        bytes32[3] memory xs = [PUBKEY_X_A, PUBKEY_X_B, PUBKEY_X_C];
        bytes32[3] memory ys = [PUBKEY_Y_A, PUBKEY_Y_B, PUBKEY_Y_C];
        for (uint256 i = 0; i < 3; i++) {
            _mockWebAuthnCall(wallet, newPubKey, nonce, xs[i], ys[i]);
        }
    }

    function _addGuardiansAndConfirm(uint256 count) internal {
        bytes32[3] memory hashes = [IDENTITY_A, IDENTITY_B, IDENTITY_C];
        bytes32[3] memory xs = [PUBKEY_X_A, PUBKEY_X_B, PUBKEY_X_C];
        bytes32[3] memory ys = [PUBKEY_Y_A, PUBKEY_Y_B, PUBKEY_Y_C];
        address[3] memory addrs = [guardianA, guardianB, guardianC];

        for (uint256 i = 0; i < count; i++) {
            vm.prank(alice);
            recovery.addGuardian(alice, hashes[i], xs[i], ys[i]);
            vm.prank(addrs[i]);
            recovery.confirmGuardian(alice);
        }
    }
}

/// @dev Helper to expose internal derToRS for testing.
contract DerToRSTestHelper is SocialRecoveryModule {
    constructor(address token, address p256Verifier) SocialRecoveryModule(token, p256Verifier) {}

    function exposeDerToRS(bytes memory der) external pure returns (bytes32 r, bytes32 s) {
        return derToRS(der);
    }
}
