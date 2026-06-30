// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOToken} from "../src/MDAOToken.sol";

contract MDAOTokenTest is Test {
    MDAOToken public token;
    address public owner;
    address public alice;
    address public bob;

    function setUp() public {
        owner = makeAddr("owner");
        alice = makeAddr("alice");
        bob = makeAddr("bob");
        token = new MDAOToken(owner);
        vm.prank(owner);
        token.mint(alice, 10_000 * 10 ** 18);
    }

    function test_SetBurnFeeBps_Success() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(100);
        assertEq(token.burnFeeBps(), 100);
        assertEq(token.lastBurnFeeUpdate(), block.timestamp);
        vm.stopPrank();
    }

    function test_RevertWhen_FeeTooHigh() public {
        vm.prank(owner);
        vm.expectRevert(MDAOToken.FeeTooHigh.selector);
        token.setBurnFeeBps(1001);
    }

    function test_RevertWhen_UpdateTooSoon() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(100);
        // Warp only 30 minutes (less than MIN_UPDATE_DELAY = 1 hour)
        vm.warp(block.timestamp + 30 minutes);
        vm.expectRevert(MDAOToken.UpdateTooSoon.selector);
        token.setBurnFeeBps(150);
        vm.stopPrank();
    }

    function test_SetBurnFeeBps_AfterDelay() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(100);
        // Warp exactly 1 hour
        vm.warp(block.timestamp + 1 hours);
        token.setBurnFeeBps(150);
        assertEq(token.burnFeeBps(), 150);
        vm.stopPrank();
    }

    function test_RevertWhen_FeeChangeTooHigh_Up() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(50);
        vm.warp(block.timestamp + 1 hours);
        // Try to increase by 51 bps (max is 50)
        vm.expectRevert(MDAOToken.FeeChangeTooHigh.selector);
        token.setBurnFeeBps(101);
        vm.stopPrank();
    }

    function test_RevertWhen_FeeChangeTooHigh_Down() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(100);
        vm.warp(block.timestamp + 1 hours);
        // Try to decrease by 51 bps (max is 50)
        vm.expectRevert(MDAOToken.FeeChangeTooHigh.selector);
        token.setBurnFeeBps(49);
        vm.stopPrank();
    }

    function test_SetBurnFeeBps_MaxChangeAllowed() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(50);
        vm.warp(block.timestamp + 1 hours);
        // Increase by exactly 50 bps
        token.setBurnFeeBps(100);
        assertEq(token.burnFeeBps(), 100);
        vm.stopPrank();
    }

    function test_SetBurnFeeBps_DecreaseByMaxAllowed() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(100);
        vm.warp(block.timestamp + 1 hours);
        // Decrease by exactly 50 bps
        token.setBurnFeeBps(50);
        assertEq(token.burnFeeBps(), 50);
        vm.stopPrank();
    }

    function test_SetBurnFeeBps_MultipleUpdates() public {
        vm.startPrank(owner);
        token.setBurnFeeBps(50);
        assertEq(token.burnFeeBps(), 50);

        vm.warp(block.timestamp + 1 hours);
        token.setBurnFeeBps(100);
        assertEq(token.burnFeeBps(), 100);

        vm.warp(block.timestamp + 1 hours);
        token.setBurnFeeBps(75);
        assertEq(token.burnFeeBps(), 75);

        vm.warp(block.timestamp + 1 hours);
        token.setBurnFeeBps(25);
        assertEq(token.burnFeeBps(), 25);
        vm.stopPrank();
    }

    function test_RevertWhen_NotOwner() public {
        vm.prank(alice);
        vm.expectRevert();
        token.setBurnFeeBps(100);
    }

    function test_BurnFeeAppliedOnTransfer() public {
        vm.prank(owner);
        token.setBurnFeeBps(50); // 0.5%
        vm.prank(alice);
        token.transfer(bob, 1000 * 10 ** 18);

        uint256 expectedFee = (1000 * 10 ** 18) * 50 / 10000;
        uint256 expectedReceived = (1000 * 10 ** 18) - expectedFee;
        assertEq(token.balanceOf(bob), expectedReceived);
        assertEq(token.balanceOf(address(0x000000000000000000000000000000000000dEaD)), expectedFee);
    }

    // ─── F-029 regression: minimum 1 wei burn for dust transfers ────

    function test_BurnFeePrecisionLoss_Minimum1Wei() public {
        // With 50 bps (0.5%), any value < 200 wei would round to 0 fee
        vm.prank(owner);
        token.setBurnFeeBps(50);
        vm.prank(owner);
        token.mint(alice, 1000 ether);

        // Transfer tiny amount that would give fee=0 without the fix
        uint256 dustAmount = 1 wei;
        vm.prank(alice);
        token.transfer(bob, dustAmount);

        // Fee should be 1 wei (minimum), bob receives 0
        assertEq(token.balanceOf(bob), 0);
        assertEq(token.balanceOf(address(0xdEaD)), 1 wei);
    }

    function test_BurnFeePrecisionLoss_SmallTransfer() public {
        vm.prank(owner);
        token.setBurnFeeBps(50);
        vm.prank(owner);
        token.mint(alice, 1000 ether);

        // Transfer 199 wei — fee = 199*50/10000 = 0.995 → 0 without fix
        uint256 amount = 199 wei;
        vm.prank(alice);
        token.transfer(bob, amount);

        // With fix: fee = 1 wei, bob receives 198 wei
        assertEq(token.balanceOf(bob), 198 wei);
        assertEq(token.balanceOf(address(0xdEaD)), 1 wei);
    }

    function test_BurnFeePrecisionLoss_ZeroFeeWhenBurnFeeBpsZero() public {
        // When burnFeeBps = 0, no fee should be taken even for tiny amounts
        vm.prank(owner);
        token.setBurnFeeBps(0);
        vm.prank(owner);
        token.mint(alice, 1000 ether);

        vm.prank(alice);
        token.transfer(bob, 1 wei);

        assertEq(token.balanceOf(bob), 1 wei);
        assertEq(token.balanceOf(address(0xdEaD)), 0);
    }
}
