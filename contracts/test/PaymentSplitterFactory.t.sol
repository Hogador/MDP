// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {PaymentSplitterFactory} from "../src/PaymentSplitterFactory.sol";
import {PaymentSplitter} from "../src/PaymentSplitter.sol";
import {IPaymentSplitter} from "../src/interfaces/IPaymentSplitter.sol";

contract PaymentSplitterFactoryTest is Test {
    PaymentSplitterFactory public factory;
    bytes32 public constant DEFAULT_ADMIN_ROLE = 0x00;

    function setUp() public {
        factory = new PaymentSplitterFactory();
    }

    // ── 1. Deploy happy path ───────────────────────────────────────

    function test_Deploy() public {
        address[] memory payees = new address[](2);
        payees[0] = address(0xAA);
        payees[1] = address(0xBB);
        uint256[] memory shares = new uint256[](2);
        shares[0] = 60;
        shares[1] = 40;

        bytes32 salt = keccak256("test");
        address predicted = factory.getAddress(salt);

        vm.expectEmit(true, true, true, true);
        emit PaymentSplitterFactory.SplitterDeployed(predicted, salt, address(this));

        address splitter = factory.deploy(salt, payees, shares);

        assertEq(splitter, predicted);
        assertTrue(factory.deployedSplitters(splitter));
        assertTrue(splitter.code.length > 0);

        // Verify splitter was initialized
        address[] memory deployedPayees = IPaymentSplitter(payable(splitter)).getPayees();
        assertEq(deployedPayees.length, 2);
        assertEq(deployedPayees[0], address(0xAA));
        assertEq(deployedPayees[1], address(0xBB));

        uint256 totalShares = IPaymentSplitter(payable(splitter)).totalShares();
        assertEq(totalShares, 100);
    }

    // ── 2. Deterministic address via getAddress ────────────────────

    function test_DeterministicAddress() public {
        bytes32 salt = keccak256("unique");

        address predicted = factory.getAddress(salt);
        assertTrue(predicted != address(0));

        // After deploy, predicted == actual
        address[] memory payees = new address[](1);
        payees[0] = address(0xAA);
        uint256[] memory shares = new uint256[](1);
        shares[0] = 1;

        address actual = factory.deploy(salt, payees, shares);
        assertEq(predicted, actual);
    }

    // ── 3. Reverts on duplicate salt ────────────────────────────────

    function test_RevertDuplicateSalt() public {
        address[] memory payees = new address[](1);
        payees[0] = address(0xAA);
        uint256[] memory shares = new uint256[](1);
        shares[0] = 1;
        bytes32 salt = keccak256("dup");

        factory.deploy(salt, payees, shares);

        vm.expectRevert(PaymentSplitterFactory.ErrAlreadyDeployed.selector);
        factory.deploy(salt, payees, shares);
    }

    // ── 4. Reverts on empty payees ──────────────────────────────────

    function test_RevertEmptyPayees() public {
        address[] memory payees;
        uint256[] memory shares;

        vm.expectRevert(PaymentSplitterFactory.ErrInvalidParams.selector);
        factory.deploy(keccak256("empty"), payees, shares);
    }

    // ── 5. Reverts on array mismatch ────────────────────────────────

    function test_RevertArrayMismatch() public {
        address[] memory payees = new address[](1);
        payees[0] = address(0xAA);
        uint256[] memory shares = new uint256[](2);
        shares[0] = 50;
        shares[1] = 50;

        vm.expectRevert(PaymentSplitterFactory.ErrArrayMismatch.selector);
        factory.deploy(keccak256("mismatch"), payees, shares);
    }

    // ── 6. Deployer is factory owner (DEFAULT_ADMIN_ROLE) ──────────

    function test_FactoryRetainsAdminOnSplitter() public {
        address[] memory payees = new address[](1);
        payees[0] = address(0xCC);
        uint256[] memory shares = new uint256[](1);
        shares[0] = 100;

        bytes32 salt = keccak256("admin");
        address splitter = factory.deploy(salt, payees, shares);

        // Factory is DEFAULT_ADMIN_ROLE (0x00) on the splitter
        assertTrue(PaymentSplitter(payable(splitter)).hasRole(DEFAULT_ADMIN_ROLE, address(factory)));
    }
}
