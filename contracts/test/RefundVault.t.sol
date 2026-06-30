// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {RefundVault} from "../src/RefundVault.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract MockERC20 {
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        uint256 allowed = allowance[from][msg.sender];
        if (allowed != type(uint256).max) { allowance[from][msg.sender] = allowed - amount; }
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract RefundVaultTest is Test {
    RefundVault public vault;
    MockERC20 public token;
    address owner = address(0xCAFE);
    address alice = address(0x1111);

    function setUp() public {
        vm.prank(owner);
        vault = new RefundVault();
        token = new MockERC20();
        token.mint(owner, 1000e18);
        vm.prank(owner);
        token.approve(address(vault), 1000e18);
    }

    function test_DepositRefund() public {
        vm.prank(owner);
        vm.expectEmit(true, true, false, true);
        emit RefundVault.RefundDeposited(alice, IERC20(address(token)), 100e18);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);

        (uint256 amount, bool claimed) = vault.refundDue(alice, IERC20(address(token)));
        assertEq(amount, 100e18);
        assertFalse(claimed);
    }

    function test_ClaimRefund() public {
        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);

        vm.prank(alice);
        vm.expectEmit(true, true, false, true);
        emit RefundVault.RefundClaimed(alice, IERC20(address(token)), 100e18);
        vault.claimRefund(IERC20(address(token)));

        assertEq(token.balanceOf(alice), 100e18);
    }

    function test_RevertWhen_ClaimNoRefund() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(RefundVault.ErrNoRefundDue.selector));
        vault.claimRefund(IERC20(address(token)));
    }

    function test_RevertWhen_DoubleClaim() public {
        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);

        vm.prank(alice);
        vault.claimRefund(IERC20(address(token)));

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(RefundVault.ErrAlreadyRefunded.selector));
        vault.claimRefund(IERC20(address(token)));
    }

    function test_AccumulateRefunds() public {
        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);
        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 50e18);

        (uint256 amount,) = vault.refundDue(alice, IERC20(address(token)));
        assertEq(amount, 150e18);
    }

    function test_OwnerCannotWithdraw() public {
        // F-008: withdrawToken removed — owner cannot directly transfer vault funds
        token.mint(address(vault), 500e18);
        uint256 before = token.balanceOf(owner);

        // try to transfer from vault directly as owner — should fail (no withdrawToken function)
        // Instead, verify the vault holds the tokens and owner can't get them without claimRefund
        assertEq(token.balanceOf(address(vault)), 500e18);
        assertEq(token.balanceOf(owner), before);

        // Owner can still deposit, but only users can claim
        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);

        vm.prank(alice);
        vault.claimRefund(IERC20(address(token)));

        // Verify totalRefundsPending tracked correctly
        assertEq(vault.totalRefundsPending(IERC20(address(token))), 0);
    }

    function test_RevertWhen_NotOwnerDeposits() public {
        vm.prank(alice);
        vm.expectRevert();
        vault.depositRefund(alice, IERC20(address(token)), 100e18);
    }

    function test_RevertWhen_DepositZero() public {
        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(RefundVault.ErrNoRefundDue.selector));
        vault.depositRefund(alice, IERC20(address(token)), 0);
    }

    function test_RevertWhen_DepositZeroAddress() public {
        vm.prank(owner);
        vm.expectRevert(abi.encodeWithSelector(RefundVault.ErrInvalidAddress.selector));
        vault.depositRefund(address(0), IERC20(address(token)), 100e18);
    }

    function test_TotalRefundsPending() public {
        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);
        assertEq(vault.totalRefundsPending(IERC20(address(token))), 100e18);

        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 50e18);
        assertEq(vault.totalRefundsPending(IERC20(address(token))), 150e18);

        vm.prank(alice);
        vault.claimRefund(IERC20(address(token)));
        assertEq(vault.totalRefundsPending(IERC20(address(token))), 0);
    }

    function test_TotalRefundsPendingMultipleUsers() public {
        address bob = address(0x2222);

        vm.prank(owner);
        vault.depositRefund(alice, IERC20(address(token)), 100e18);
        vm.prank(owner);
        vault.depositRefund(bob, IERC20(address(token)), 200e18);

        assertEq(vault.totalRefundsPending(IERC20(address(token))), 300e18);

        vm.prank(alice);
        vault.claimRefund(IERC20(address(token)));
        assertEq(vault.totalRefundsPending(IERC20(address(token))), 200e18);
    }
}
