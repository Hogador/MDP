// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

interface ITrustProvider {
    function verify(bytes32 intentHash, bytes calldata proof) external view returns (bool);
}
