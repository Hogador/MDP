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

    function test_SetSwitch() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        (address beneficiary, uint256 inactivityPeriod,, bool active, bool claimed) = dms.switches(alice);
        assertEq(beneficiary, bob);
        assertEq(inactivityPeriod, 90 days);
        assertTrue(active);
        assertFalse(claimed);
    }

    function test_RevertWhen_BeneficiarySameAsOwner() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrBeneficiarySameAsOwner.selector));
        dms.setSwitch(alice, 90 days);
    }

    function test_MinInactivityFloor() public {
        vm.prank(alice);
        dms.setSwitch(bob, 1 days);

        (, uint256 inactivityPeriod,,,) = dms.switches(alice);
        assertEq(inactivityPeriod, 90 days);
    }

    function test_Ping() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 10 days);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.ActivityPinged(alice, block.timestamp);
        dms.ping();

        (, , uint256 lastActivity,,) = dms.switches(alice);
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

    function test_ChangeBeneficiary() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        address charlie = address(0x3333);
        vm.prank(alice);
        dms.changeBeneficiary(charlie);

        (address beneficiary,,, bool active,) = dms.switches(alice);
        assertEq(beneficiary, charlie);
        assertTrue(active);
    }

    function test_TriggerRecovery() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.SwitchTriggered(alice, bob);
        dms.triggerRecovery(alice);

        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Triggered));
        assertEq(dms.triggerAt(alice), block.timestamp);
    }

    function test_RevertWhen_TriggerBeforeInactivity() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 89 days);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrInactivityNotMet.selector));
        dms.triggerRecovery(alice);
    }

    function test_RevertWhen_TriggerByNonBeneficiary() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrNotBeneficiary.selector));
        dms.triggerRecovery(alice);
    }

    function test_RevertWhen_TriggerTwice() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrAlreadyClaimed.selector));
        dms.triggerRecovery(alice);
    }

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

    function test_ClaimFunds() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        // Advance past challenge period
        vm.warp(block.timestamp + 7 days + 1);

        // alice sends ETH through receive() so deposit is tracked
        vm.deal(alice, 10 ether);
        vm.prank(alice);
        (bool ok,) = payable(address(dms)).call{value: 10 ether}("");
        assertTrue(ok);

        assertEq(dms.deposits(alice), 10 ether);

        vm.prank(bob);
        vm.expectEmit(true, true, false, true);
        emit DeadManSwitch.FundsClaimed(alice, bob, 10 ether);
        dms.claimFunds(alice);

        assertEq(address(bob).balance, 10 ether);
        assertEq(dms.deposits(alice), 0);
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Claimable));
    }

    function test_RevertWhen_ClaimFundsNotTriggered() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrSwitchNotActive.selector));
        dms.claimFunds(alice);
    }

    function test_RevertWhen_ClaimFundsNoDeposit() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        // Advance past challenge period
        vm.warp(block.timestamp + 7 days + 1);

        // No deposit made — should revert
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrNoDeposits.selector));
        dms.claimFunds(alice);
    }

    function test_ClaimFundsPerWalletIsolation() public {
        address charlie = address(0x3333);
        address dan = address(0x4444);

        // alice sets bob as beneficiary
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        // charlie sets dan as beneficiary
        vm.prank(charlie);
        dms.setSwitch(dan, 90 days);

        vm.warp(block.timestamp + 91 days);

        // Trigger both
        vm.prank(bob);
        dms.triggerRecovery(alice);
        vm.prank(dan);
        dms.triggerRecovery(charlie);

        // Advance past challenge period
        vm.warp(block.timestamp + 7 days + 1);

        // alice deposits 5 ETH, charlie deposits 7 ETH
        vm.deal(alice, 5 ether);
        vm.prank(alice);
        (bool ok1,) = payable(address(dms)).call{value: 5 ether}("");
        assertTrue(ok1);

        vm.deal(charlie, 7 ether);
        vm.prank(charlie);
        (bool ok2,) = payable(address(dms)).call{value: 7 ether}("");
        assertTrue(ok2);

        assertEq(dms.deposits(alice), 5 ether);
        assertEq(dms.deposits(charlie), 7 ether);

        // bob claims only alice's deposit (5 ETH)
        vm.prank(bob);
        dms.claimFunds(alice);
        assertEq(address(bob).balance, 5 ether);
        assertEq(dms.deposits(alice), 0);
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Claimable));

        // dan claims charlie's deposit (7 ETH)
        vm.prank(dan);
        dms.claimFunds(charlie);
        assertEq(address(dan).balance, 7 ether);
        assertEq(dms.deposits(charlie), 0);
        assertEq(uint256(dms.recoveryState(charlie)), uint256(DeadManSwitch.State.Claimable));
    }

    function test_ConstantMinInactivity() public view {
        assertEq(dms.MIN_INACTIVITY(), 90 days);
    }

    // ─── F-072 regression tests ─────────────────────────────────────

    function test_RevertWhen_PingAfterTrigger() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrSwitchNotActive.selector));
        dms.ping();
    }

    function test_RevertWhen_ClaimBeforeChallengePeriod() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        // Try to claim immediately — should fail (CHALLENGE_PERIOD not elapsed)
        vm.deal(alice, 1 ether);
        vm.prank(alice);
        (bool ok,) = payable(address(dms)).call{value: 1 ether}("");
        assertTrue(ok);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrNotExpired.selector));
        dms.claimFunds(alice);
    }

    function test_ChallengeTrigger() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

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

    function test_PingAfterChallenge() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

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

    function test_ClaimFundsAfterChallengePeriod() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        // Advance exactly to challenge period boundary
        vm.warp(block.timestamp + 7 days);

        vm.deal(alice, 2 ether);
        vm.prank(alice);
        (bool ok,) = payable(address(dms)).call{value: 2 ether}("");
        assertTrue(ok);

        // At exact boundary (triggerAt + 7 days) should work
        vm.prank(bob);
        dms.claimFunds(alice);

        assertEq(address(bob).balance, 2 ether);
        assertEq(dms.deposits(alice), 0);
        assertEq(uint256(dms.recoveryState(alice)), uint256(DeadManSwitch.State.Claimable));
    }

    function test_RevertWhen_TriggerAfterClaimed() public {
        vm.prank(alice);
        dms.setSwitch(bob, 90 days);

        vm.warp(block.timestamp + 91 days);

        vm.prank(bob);
        dms.triggerRecovery(alice);

        vm.warp(block.timestamp + 7 days + 1);

        vm.deal(alice, 1 ether);
        vm.prank(alice);
        (bool ok,) = payable(address(dms)).call{value: 1 ether}("");
        assertTrue(ok);

        vm.prank(bob);
        dms.claimFunds(alice);

        // Second trigger attempt should fail
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(DeadManSwitch.ErrAlreadyClaimed.selector));
        dms.triggerRecovery(alice);
    }
}
