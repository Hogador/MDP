// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {MDAOSmartAccount} from "./MDAOSmartAccount.sol";
import {IEntryPoint} from "account-abstraction/interfaces/IEntryPoint.sol";

/// @title MDAOSmartAccountFactory
/// @notice Deploy MDAOSmartAccount via CREATE2 with deterministic addresses.
/// @dev Mirrors v0.6 SimpleAccountFactory: createAccount returns the address
///      even if already deployed, so EntryPoint.getSenderAddress() works.
contract MDAOSmartAccountFactory {
    IEntryPoint public immutable entryPoint;

    error ErrCannotBeZero();

    event AccountDeployed(address indexed account, address indexed owner, uint256 salt);

    constructor(address entryPoint_) {
        if (entryPoint_ == address(0)) revert ErrCannotBeZero();
        entryPoint = IEntryPoint(entryPoint_);
    }

    /// @notice Create (or return existing) account for owner.
    function createAccount(address owner, uint256 salt) external returns (address account) {
        account = getAddress(owner, salt);
        if (account.code.length > 0) return account;
        account = address(new MDAOSmartAccount{salt: bytes32(salt)}(entryPoint, owner));
        emit AccountDeployed(account, owner, salt);
    }

    /// @notice Counterfactual address of the account.
    function getAddress(address owner, uint256 salt) public view returns (address) {
        return address(uint160(uint256(
            keccak256(abi.encodePacked(
                bytes1(0xff),
                address(this),
                bytes32(salt),
                keccak256(abi.encodePacked(type(MDAOSmartAccount).creationCode, abi.encode(entryPoint, owner)))
            ))
        )));
    }
}