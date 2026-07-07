// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOToken} from "../../src/MDAOToken.sol";
import {SocialRecoveryModule} from "../../src/SocialRecoveryModule.sol";
import {SessionKeyModule} from "../../src/SessionKeyModule.sol";
import {DeadManSwitch} from "../../src/DeadManSwitch.sol";
import {IRecoveryHook} from "../../src/interfaces/IRecoveryHook.sol";
import {MockP256} from "../mocks/MockP256.sol";

/// @title RecoveryHooksIntegrationTest
/// @notice Verifies the full hook chain: SocialRecoveryModule.executeRecovery →
///         SessionKeyModule.onRecoveryExecuted (invalidate keys) +
///         DeadManSwitch.onRecoveryExecuted (reset timer).
contract RecoveryHooksIntegrationTest is Test {
    MDAOToken public token;
    SocialRecoveryModule public recovery;
    SessionKeyModule public sessionKeys;
    DeadManSwitch public dms;
    MockP256 public mockP256;

    address public deployer = address(0xCAFE);
    address public alice = address(0x1111);
    address public guardian1 = address(0x2222);
    address public guardian2 = address(0x3333);
    address public beneficiary = address(0x4444);

    bytes public constant TEST_AUTH_DATA = hex"01";
    bytes public constant TEST_CLIENT_DATA = hex"02";
    // 64-byte dummy r||s signature (MockP256 accepts any)
    bytes public constant VALID_SIG = hex"00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";

    bytes public newKeyPubKey = abi.encodePacked(keccak256("new-key-x"), keccak256("new-key-y")); // 64 bytes

    function setUp() public {
        vm.prank(deployer);
        token = new MDAOToken(deployer);

        mockP256 = new MockP256();

        vm.prank(deployer);
        recovery = new SocialRecoveryModule(address(token), address(mockP256));

        sessionKeys = new SessionKeyModule();

        vm.prank(deployer);
        dms = new DeadManSwitch();

        // Wire: set recovery callers
        vm.prank(address(sessionKeys.owner()));
        sessionKeys.setRecoveryCaller(address(recovery));

        vm.prank(address(dms.owner()));
        dms.setRecoveryCaller(address(recovery));

        // Wire: register hooks on SocialRecoveryModule
        vm.prank(deployer);
        recovery.addRecoveryHook(IRecoveryHook(address(sessionKeys)));

        vm.prank(deployer);
        recovery.addRecoveryHook(IRecoveryHook(address(dms)));

        // Exempt SocialRecoveryModule from burn fee
        vm.prank(deployer);
        token.setExempt(address(recovery), true);

        // Give guardian1 MDAO tokens for recovery deposit
        vm.prank(deployer);
        token.transfer(guardian1, 100 ether);
        vm.prank(deployer);
        token.transfer(guardian2, 100 ether);
    }

    // ─── Test: recovery → session keys invalidated + DeadManSwitch timer reset ───

    function test_HooksExecuteOnRecovery() public {
        // 1. alice sets up DeadManSwitch
        vm.prank(alice);
        dms.setSwitch(beneficiary, 90 days);

        // 2. Advance time so alice appears inactive
        vm.warp(block.timestamp + 91 days);

        // DeadManSwitch should be in Active state
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Active));

        // 3. alice creates a session key
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = keccak256("payment");

        vm.prank(alice);
        bytes32 keyId = sessionKeys.createSessionKey(address(0x9999), block.timestamp + 365 days, perms, 1 ether, 0);

        // Verify key exists
        (address keyOwner,,,,,,, bool revoked,,,,) = sessionKeys.getSessionKey(keyId);
        assertEq(keyOwner, alice);
        assertFalse(revoked);

        // 4. Set up guardian recovery for alice
        bytes32 initialKeyX = keccak256("alice-old-key-x");
        bytes32 initialKeyY = keccak256("alice-old-key-y");
        vm.prank(alice);
        recovery.registerWallet(initialKeyX, initialKeyY);

        // Add guardians (identity hash = keccak256 of their address)
        bytes32 identityA = keccak256(abi.encodePacked(guardian1));
        bytes32 identityB = keccak256(abi.encodePacked(guardian2));

        vm.prank(alice);
        recovery.addGuardian(alice, identityA, keccak256("guardian1-pub-x"), keccak256("guardian1-pub-y"));
        vm.prank(alice);
        recovery.addGuardian(alice, identityB, keccak256("guardian2-pub-x"), keccak256("guardian2-pub-y"));

        // Guardians confirm their roles
        vm.prank(guardian1);
        recovery.confirmGuardian(alice);
        vm.prank(guardian2);
        recovery.confirmGuardian(alice);

        // Initiate recovery — guardian1 calls with new 64-byte passkey pub key
        vm.prank(guardian1);
        token.approve(address(recovery), 1 ether);

        assertEq(newKeyPubKey.length, 64, "newKeyPubKey must be 64 bytes");

        vm.prank(guardian1);
        recovery.initiateRecovery(alice, newKeyPubKey);

        // Approve with both guardians (MockP256 accepts any sig)
        vm.prank(guardian1);
        recovery.approveRecovery(alice, identityA, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        vm.prank(guardian2);
        recovery.approveRecovery(alice, identityB, TEST_AUTH_DATA, TEST_CLIENT_DATA, VALID_SIG);

        // Warp past timelock (48h) + into execution window
        vm.warp(block.timestamp + 49 hours);

        // 5. Execute recovery
        vm.prank(guardian1);
        recovery.executeRecovery(alice);

        // 6. Verify: session key revoked
        (,,,,,,, bool revokedAfter,,,,) = sessionKeys.getSessionKey(keyId);
        assertTrue(revokedAfter, "Session key should be revoked after recovery");

        // 7. Verify: DeadManSwitch timer reset
        (, , uint256 lastActivity,) = dms.switches(alice);
        assertEq(lastActivity, block.timestamp, "DeadManSwitch timer should reset to recovery time");

        // 8. Verify: DeadManSwitch state back to Active (trigger cleared)
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Active));
        assertEq(dms.triggerAt(alice), 0);
    }

    // ─── Test: hook registration ────────────────────────────────────

    function test_HookRegistration() public {
        assertEq(recovery.getRecoveryHookCount(), 2);
    }

    function test_SetRecoveryCaller() public {
        assertEq(address(sessionKeys.recoveryCaller()), address(recovery));
        assertEq(address(dms.recoveryCaller()), address(recovery));
    }
}
