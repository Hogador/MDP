// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {DeadManSwitch} from "../src/DeadManSwitch.sol";

contract DeadManSwitchTest is Test {
    DeadManSwitch public dms;
    address public owner = address(0xCAFE);
    address public alice = address(0x1111);
    address public bob = address(0x2222);

    function setUp() public {
        vm.prank(owner);
        dms = new DeadManSwitch();
    }

    // ─── setSwitch ──────────────────────────────────────────────────

    function test_SetSwitch() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        (address beneficiary, uint256 inactivityPeriod,, bool active) = dms.switches(alice);
        assertEq(beneficiary, bob);
        assertEq(inactivityPeriod, 90 days);
        assertTrue(active);
    }

    function test_RevertWhen_BeneficiarySameAsOwner() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrBeneficiarySameAsOwner.selector));
        dms.setSwitch(alice, 90 days);
    }

    function test_MinInactivityFloor() public {
        vm.prank(alice);
        dms.setSwitch(bob, 1 days);

        (, uint256 inactivityPeriod,,) = dms.switches(alice);
        assertEq(inactivityPeriod, 90 days);
    }

    // ─── ping ───────────────────────────────────────────────────────

    function test_Ping() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 10 days);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.ActivityPinged(alice, block.timestamp);
        dms.ping();

        (, , uint256 lastActivity,) = dms.switches(alice);
        assertEq(lastActivity, block.timestamp);
    }

    function test_RevertWhen_PingNotActive() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);
        vm.prank(alice);
        dms.deactivate();

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrSwitchNotActive.selector));
        dms.ping();
    }

    // ─── changeBeneficiary ──────────────────────────────────────────

    function test_ChangeBeneficiary() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        address charlie = address(0x3333);
        vm.prank(alice);
        dms.changeBeneficiary(charlie);

        (address beneficiary,,, bool active) = dms.switches(alice);
        assertEq(beneficiary, charlie);
        assertTrue(active);
    }

    // ─── initiateClaim ──────────────────────────────────────────────

    function test_InitiateClaim() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.SwitchTriggered(alice, bob);
        dms.initiateClaim(alice);

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Triggered));
        assertEq(dms.triggerAt(alice), block.timestamp);
    }

    function test_RevertWhen_ClaimBeforeInactivity() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 89 days);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrInactivityNotMet.selector));
        dms.initiateClaim(alice);
    }

    function test_RevertWhen_ClaimByNonBeneficiary() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrNotBeneficiary.selector));
        dms.initiateClaim(alice);
    }

    function test_RevertWhen_ClaimTwice() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrAlreadyClaimed.selector));
        dms.initiateClaim(alice);
    }

    // ─── deactivate ─────────────────────────────────────────────────

    function test_Deactivate() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.SwitchDeactivated(alice);
        dms.deactivate();

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Active));
    }

    function test_RevertWhen_DeactivateNotActive() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);
        vm.prank(alice);
        dms.deactivate();

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrSwitchNotActive.selector));
        dms.deactivate();
    }

    // ─── executeClaim ───────────────────────────────────────────────

    function test_ExecuteClaim() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        // Advance past challenge period
        vm.warp(block.timestamp + 7 days);

        vm.prank(bob);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.OwnershipClaimTriggered(alice, bob);
        dms.executeClaim(alice);

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Executed));
    }

    function test_RevertWhen_ExecuteClaimNotTriggered() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrNotTriggered.selector));
        dms.executeClaim(alice);
    }

    function test_RevertWhen_ExecuteClaimBeforeChallengePeriod() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        // Try to execute immediately — challenge period not elapsed
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrChallengeNotExpired.selector));
        dms.executeClaim(alice);
    }

    function test_ExecuteClaimPerWalletIsolation() public {
        address charlie = address(0x3333);
        address dan = address(0x4444);

        // alice sets bob as beneficiary
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        // charlie sets dan as beneficiary
        vm.prank(charlie);
        dms.setSwitch(dan, 90 days);

        vm.warp(block.timestamp + 91 days);

        // Initiate both
        vm.prank(bob);
        dms.initiateClaim(alice);
        vm.prank(dan);
        dms.initiateClaim(charlie);

        // Advance past challenge period
        vm.warp(block.timestamp + 7 days);

        // bob claims alice
        vm.prank(bob);
        dms.executeClaim(alice);
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Executed));

        // dan claims charlie
        vm.prank(dan);
        dms.executeClaim(charlie);
        assertEq(uint256(dms.recoveryState(charlie)), uint256(DeadManSwitch.State.Executed));
    }

    // ─── challengeTrigger ───────────────────────────────────────────

    function test_ChallengeTrigger() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Triggered));

        // Alice challenges
        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.TriggerChallenged(alice);
        dms.challengeTrigger();

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Active));
        assertEq(dms.triggerAt(alice), 0);
    }

    function test_RevertWhen_ChallengeTriggerNotTriggered() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrNotTriggered.selector));
        dms.challengeTrigger();
    }

    // ─── Ping after challenge ───────────────────────────────────────

    function test_PingAfterChallenge() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        // Alice challenges
        vm.prank(alice);
        dms.challengeTrigger();

        // Ping should work again
        vm.warp(block.timestamp + 1 days);
        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.ActivityPinged(alice, block.timestamp);
        dms.ping();
    }

    // ─── Ping after initiateClaim (F-072 regression) ────────────────

    function test_RevertWhen_PingAfterTrigger() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrSwitchNotActive.selector));
        dms.ping();
    }

    // ─── Execute after challenge period boundary ────────────────────

    function test_ExecuteClaimAfterChallengePeriod() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        // Advance exactly to challenge period boundary
        vm.warp(block.timestamp + 7 days);

        vm.prank(bob);
        dms.executeClaim(alice);

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Executed));
    }

    // ─── Re-trigger after executeClaim ──────────────────────────────

    function test_RevertWhen_InitiateAfterExecuted() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);

        vm.warp(block.timestamp + 7 days);

        vm.prank(bob);
        dms.executeClaim(alice);

        // Second initiate should fail
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrAlreadyClaimed.selector));
        dms.initiateClaim(alice);
    }

    // ─── Constant ───────────────────────────────────────────────────

    function test_ConstantMinInactivity() public view {
        assertEq(dms.MIN_INACTIVITY(), 90 days);
    }

    // ─── IRecoveryHook: onRecoveryExecuted ──────────────────────────

    function test_OnRecoveryExecutedResetsTimer() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        // Advance time
        vm.warp(block.timestamp + 50 days);

        // Set recoveryCaller
        address recoveryModule = address(0x9999);
        vm.prank(owner);
        dms.setRecoveryCaller(recoveryModule);

        // Call onRecoveryExecuted as recovery module
        vm.prank(recoveryModule);
        dms.onRecoveryExecuted(alice);

        // lastActivity should be now
        (, , uint256 lastActivity,) = dms.switches(alice);
        assertEq(lastActivity, block.timestamp);
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Active));
    }

    function test_OnRecoveryExecutedCancelsTriggered() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.initiateClaim(alice);
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Triggered));

        // Set recoveryCaller and trigger recovery hook
        address recoveryModule = address(0x9999);
        vm.prank(owner);
        dms.setRecoveryCaller(recoveryModule);

        vm.prank(recoveryModule);
        dms.onRecoveryExecuted(alice);

        // Should reset to Active
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Active));
        assertEq(dms.triggerAt(alice), 0);

        // Timer should be reset — initiateClaim should fail again
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrInactivityNotMet.selector));
        dms.initiateClaim(alice);
    }

    function test_RevertWhen_OnRecoveryExecutedUnauthorized() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        // recoveryCaller not set yet — default is address(0)
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrUnauthorized.selector));
        dms.onRecoveryExecuted(alice);
    }

    // ─── setRecoveryCaller ──────────────────────────────────────────

    function test_SetRecoveryCaller() public {
        address recoveryModule = address(0x9999);
        vm.prank(owner);
        dms.setRecoveryCaller(recoveryModule);
        assertEq(dms.recoveryCaller(), recoveryModule);
    }

    function test_RevertWhen_SetRecoveryCallerNotOwner() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrUnauthorized.selector));
        dms.setRecoveryCaller(address(0x9999));
    }
}
