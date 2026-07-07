// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {PaymentSplitter} from "./PaymentSplitter.sol";
import {ISplitterFactory} from "./interfaces/ISplitterFactory.sol";

/// @title PaymentSplitterFactory
/// @notice Deploy PaymentSplitter contracts via CREATE2 with deterministic addresses.
/// @dev Each splitter is initialized immediately after deployment.
///      Factory retains DEFAULT_ADMIN_ROLE on deployed splitters unless renounced.
contract PaymentSplitterFactory is ISplitterFactory {
    error ErrInvalidParams();
    error ErrArrayMismatch();
    error ErrAlreadyDeployed();

    event SplitterDeployed(address indexed splitter, bytes32 indexed salt, address indexed deployer);

    /// @inheritdoc ISplitterFactory
    mapping(address => bool) public deployedSplitters;

    /// @inheritdoc ISplitterFactory
    function getAddress(bytes32 salt) external view returns (address) {
        return _predictAddress(salt);
    }

    /// @inheritdoc ISplitterFactory
    function deploy(bytes32 salt, address[] calldata payees, uint256[] calldata shares)
        external
        returns (address splitter)
    {
        if (payees.length == 0) revert ErrInvalidParams();
        if (payees.length != shares.length) revert ErrArrayMismatch();

        address predicted = _predictAddress(salt);
        if (predicted.code.length > 0) revert ErrAlreadyDeployed();

        splitter = address(new PaymentSplitter{salt: salt}());
        PaymentSplitter(payable(splitter)).initialize(payees, shares);

        deployedSplitters[splitter] = true;
        emit SplitterDeployed(splitter, salt, msg.sender);
    }

    function _predictAddress(bytes32 salt) internal view returns (address) {
        return address(uint160(uint256(
            keccak256(abi.encodePacked(
                bytes1(0xff),
                address(this),
                salt,
                keccak256(type(PaymentSplitter).creationCode)
            ))
        )));
    }
}
