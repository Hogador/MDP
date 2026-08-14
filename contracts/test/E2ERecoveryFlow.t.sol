// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {MDAOSmartAccount} from "../src/MDAOSmartAccount.sol";
import {MDAOSmartAccountFactory} from "../src/MDAOSmartAccountFactory.sol";
import {P256Verifier} from "../src/helpers/P256Verifier.sol";
import {IEntryPoint} from "account-abstraction/interfaces/IEntryPoint.sol";
import {UserOperation} from "account-abstraction/interfaces/UserOperation.sol";

/// @title E2E social recovery flow
/// @notice Full loop: deploy MDAOSmartAccount via factory → configure
///         SocialRecoveryModule as recoveryCaller → register wallet → add 2
///         guardians → initiateRecovery → approveRecovery with REAL P-256
///         (secp256r1) WebAuthn signatures → executeRecovery → ownership of the
///         smart account switches to the new EOA.
/// @dev Uses the real pure-Solidity P256Verifier (not MockP256) so that
///      signatures are genuinely validated: a garbage r/s must revert
///      (ErrInvalidSignature), which MockP256 (returns 1 for any input) can't do.
///      Signatures below were generated offline with fixed private keys over
///      messageHash = SHA-256(authenticatorData || SHA-256(clientDataJSON)).
contract E2ERecoveryFlowTest is Test {
    MDAOToken public token;
    P256Verifier public verifier;
    SocialRecoveryModule public srm;
    MDAOSmartAccountFactory public factory;
    MDAOSmartAccount public account;

    address public owner = makeAddr("owner");
    address public newOwner = makeAddr("newOwner");
    address public guardianA = makeAddr("guardianA");
    address public guardianB = makeAddr("guardianB");

    bytes32 public IDENTITY_A;
    bytes32 public IDENTITY_B;

    // Same WebAuthn inputs as the rest of the test suite.
    bytes constant AUTH_DATA = hex"000000000000000000000000000000000000000000000000000000000000000000000000000000"; // 38 bytes
    bytes constant CLIENT_DATA = bytes('{"type":"webauthn.get","challenge":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","origin":"https://mdaopay.app"}');

    // Guardian A — fixed secp256r1 keypair (generated offline, priv = 0xc2ac...f3)
    bytes32 constant PUBKEY_X_A = hex"186335ec9efa5e6b8b4496f6e8eb4ff32a8111e61d4d964d6ef4e97270e8f971";
    bytes32 constant PUBKEY_Y_A = hex"a001c698e474b982f40b712391b8a838f343c377f4cc1a024628d09b9a13223e";
    bytes32 constant SIG_R_A = hex"7d2bd871728a6fe31478dfa0693860fe99e85c2004266744725ed4d533de3d9e";
    bytes32 constant SIG_S_A = hex"50676cfc2ddd08e6293aee181e48690d6c20df7888bc802ab78bc17f150708da";

    // Guardian B — fixed secp256r1 keypair (priv = 0x1a2b...809)
    bytes32 constant PUBKEY_X_B = hex"b5a104b6caadfa15a6fb9eb3939237284d404e9d9486b706411457a16f12e84a";
    bytes32 constant PUBKEY_Y_B = hex"18bbebd97e525867075442bd530ea4d29ee7ad873a82a62e804be3c08e34c9d6";
    bytes32 constant SIG_R_B = hex"01e4c076a3cecd6e84852cc21141bf69e5254a7641290e96c0038714ad9ff5d9";
    bytes32 constant SIG_S_B = hex"448fcdab33620dff16a05f15cc04e18ce3a0b2d486f5d58668c73112fee4ec38";

    // 64-byte raw r||s signatures for the two guardians.
    bytes constant SIG_A = abi.encode(SIG_R_A, SIG_S_A);
    bytes constant SIG_B = abi.encode(SIG_R_B, SIG_S_B);

    bytes constant NEW_PUBKEY = hex"00000000000000000000000000000000000000000000000000000000000000050000000000000000000000000000000000000000000000000000000000000006";

    function setUp() public {
        IDENTITY_A = keccak256(abi.encodePacked(guardianA));
        IDENTITY_B = keccak256(abi.encodePacked(guardianB));

        token = new MDAOToken(address(this));
        verifier = new P256Verifier();
        // Real verifier passes the RFC 6979 A.2.5 probe → used as P256_VERIFIER.
        srm = new SocialRecoveryModule(address(token), address(verifier));
        factory = new MDAOSmartAccountFactory(address(new E2EMockEntryPoint()));

        token.mint(owner, 100 ether);
        vm.prank(owner);
        token.approve(address(srm), type(uint256).max);
    }

    // ──────────────────────────────────────────────
    //  Positive E2E flow
    // ──────────────────────────────────────────────

    function test_EndToEndRecoveryFlow() public {
        _setupAccountAndRecovery();

        // Real P-256 verifier is in use (not the MockP256 fallback).
        assertTrue(srm.p256VerifierWorking(), "real verifier should be active");
        assertEq(address(srm.P256_VERIFIER()), address(verifier));

        // Owner is the smart account owner and SRM is the recoveryCaller.
        assertEq(account.owner(), owner);
        assertEq(account.recoveryCaller(), address(srm));

        // GUARDIAN_THRESHOLD = 2 → exactly 2 real P-256 WebAuthn approvals.
        vm.prank(guardianA);
        srm.approveRecovery(owner, IDENTITY_A, AUTH_DATA, CLIENT_DATA, SIG_A);
        vm.prank(guardianB);
        srm.approveRecovery(owner, IDENTITY_B, AUTH_DATA, CLIENT_DATA, SIG_B);

        (, , uint256 approvals, , , , , ) = srm.getRecoveryRequest(owner);
        assertEq(approvals, 2);

        // Timelock is 48h.
        vm.warp(block.timestamp + 48 hours + 1);
        vm.prank(owner);
        srm.executeRecovery(owner);

        // Smart account ownership transferred to the new EOA.
        assertEq(account.owner(), newOwner, "smart account owner must switch");
        // SRM passkey rotated and transfer config consumed.
        assertEq(srm.ownerPasskeyHash(owner), keccak256(NEW_PUBKEY));
        assertEq(srm.recoverySmartAccount(owner), address(0), "recoverySmartAccount consumed");
        assertEq(srm.recoveryEOAOwner(owner), address(0), "recoveryEOAOwner consumed");

        // Old EOA no longer controls the account...
        vm.prank(owner);
        vm.expectRevert(MDAOSmartAccount.ErrNotOwner.selector);
        account.transferOwnership(makeAddr("hacker"));

        // ...the new EOA does.
        address postOwner = makeAddr("postRecoveryOwner");
        vm.prank(newOwner);
        account.transferOwnership(postOwner);
        assertEq(account.owner(), postOwner);
    }

    // ──────────────────────────────────────────────
    //  Negative cases
    // ──────────────────────────────────────────────

    function test_RevertWhen_NonGuardianApproves() public {
        _setupAccountAndRecovery();

        address stranger = makeAddr("stranger");
        bytes32 strangerId = keccak256(abi.encodePacked(stranger));

        vm.prank(stranger);
        vm.expectRevert(SocialRecoveryModule.ErrNotGuardian.selector);
        srm.approveRecovery(owner, strangerId, AUTH_DATA, CLIENT_DATA, SIG_A);
    }

    function test_RevertWhen_GarbageSignature() public {
        _setupAccountAndRecovery();

        // 64-byte raw r||s, but not a valid P-256 signature → real verifier
        // returns 0 → ErrInvalidSignature.
        bytes memory garbageSig = abi.encode(bytes32(uint256(0xdead)), bytes32(uint256(0xbeef)));

        vm.prank(guardianA);
        vm.expectRevert(SocialRecoveryModule.ErrInvalidSignature.selector);
        srm.approveRecovery(owner, IDENTITY_A, AUTH_DATA, CLIENT_DATA, garbageSig);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    function _setupAccountAndRecovery() internal {
        // Deploy the smart account through the factory.
        vm.prank(owner);
        account = MDAOSmartAccount(payable(factory.createAccount(owner, 0)));

        // Owner wires SRM as the recovery caller.
        vm.prank(owner);
        account.setRecoveryCaller(address(srm));

        // Register wallet + add/confirm 2 guardians.
        vm.prank(owner);
        srm.registerWallet(bytes32(uint256(0xa1)), bytes32(uint256(0xa2)));

        vm.prank(owner);
        srm.addGuardian(owner, IDENTITY_A, PUBKEY_X_A, PUBKEY_Y_A);
        vm.prank(owner);
        srm.addGuardian(owner, IDENTITY_B, PUBKEY_X_B, PUBKEY_Y_B);
        vm.prank(guardianA);
        srm.confirmGuardian(owner);
        vm.prank(guardianB);
        srm.confirmGuardian(owner);

        // Configure smart account + new EOA for the ownership transfer.
        vm.prank(owner);
        srm.setRecoveryTransfer(address(account), newOwner);

        // Initiate recovery (owner pays the 0.01 MDAO deposit).
        vm.prank(owner);
        srm.initiateRecovery(owner, NEW_PUBKEY);
    }
}

/// @dev Minimal EntryPoint implementing IEntryPoint (+ IStakeManager, INonceManager)
///      so MDAOSmartAccount / MDAOSmartAccountFactory can be deployed in tests.
contract E2EMockEntryPoint is IEntryPoint {
    function getUserOpHash(UserOperation calldata) external pure returns (bytes32) {
        return keccak256("test");
    }
    function simulateValidation(UserOperation calldata) external {}
    function simulateHandleOp(UserOperation calldata, address, bytes calldata) external {}
    function handleOps(UserOperation[] calldata, address payable) external {}
    function handleAggregatedOps(UserOpsPerAggregator[] calldata, address payable) external {}
    function getSenderAddress(bytes calldata) external pure { revert SenderAddressResult(address(0)); }
    // IStakeManager
    function getDepositInfo(address) external pure returns (DepositInfo memory) { return DepositInfo(0, false, 0, 0, 0); }
    function balanceOf(address) external pure returns (uint256) { return 0; }
    function depositTo(address) external payable {}
    function addStake(uint32) external payable {}
    function unlockStake() external {}
    function withdrawStake(address payable) external {}
    function withdrawTo(address payable, uint256) external {}
    // INonceManager
    function getNonce(address, uint192) external pure returns (uint256) { return 0; }
    function incrementNonce(uint192) external {}
}
