// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOSmartAccount} from "../src/MDAOSmartAccount.sol";
import {MDAOSmartAccountFactory} from "../src/MDAOSmartAccountFactory.sol";
import {IEntryPoint} from "account-abstraction/interfaces/IEntryPoint.sol";
import {UserOperation} from "account-abstraction/interfaces/UserOperation.sol";

contract MDAOSmartAccountFactoryTest is Test {
    MDAOSmartAccountFactory public factory;
    IEntryPoint public ep;
    address public owner;

    function setUp() public {
        ep = IEntryPoint(address(new MockEntryPoint()));
        factory = new MDAOSmartAccountFactory(address(ep));
        owner = makeAddr("factoryOwner");
    }

    function test_CreateAccountMatchesGetAddress() public {
        uint256 salt = 42;

        address predicted = factory.getAddress(owner, salt);
        address deployed = factory.createAccount(owner, salt);

        assertEq(deployed, predicted, "deployed address must match counterfactual");
        assertEq(MDAOSmartAccount(payable(deployed)).owner(), owner);
        assertEq(address(MDAOSmartAccount(payable(deployed)).entryPoint()), address(ep));
    }

    function test_CreateAccountIdempotent() public {
        uint256 salt = 7;
        address first = factory.createAccount(owner, salt);

        // Second call must return the same address without redeploying
        address second = factory.createAccount(owner, salt);
        assertEq(first, second);
        assertTrue(first.code.length > 0);
    }

    function test_DifferentOwnersOrSaltsDiffer() public {
        assertTrue(
            factory.getAddress(owner, 1) != factory.getAddress(owner, 2)
                && factory.getAddress(owner, 1) != factory.getAddress(makeAddr("other"), 1)
        );
    }

    function test_ConstructorRevertsZeroEntryPoint() public {
        vm.expectRevert(MDAOSmartAccountFactory.ErrCannotBeZero.selector);
        new MDAOSmartAccountFactory(address(0));
    }
}

/// @notice Minimal EntryPoint mock (no-op for deposit/withdraw).
contract MockEntryPoint is IEntryPoint {
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
