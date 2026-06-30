// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title MockP256
/// @notice Mock P-256 verifier for testing. Returns success for any input.
contract MockP256 {
    fallback(bytes calldata) external returns (bytes memory) {
        require(block.chainid != 56, "MockP256: mainnet");
        return abi.encode(uint256(1));
    }

    // F-04: chain ID check prevents mock deployment on mainnet
    function verify(bytes32, bytes32, bytes32, bytes32, bytes32) external view returns (uint256) {
        require(block.chainid != 56, "MockP256: mainnet");
        return 1;
    }
}
