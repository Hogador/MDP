// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title MockP256
/// @notice Mock P-256 verifier for testing and testnets without RIP-7212 support.
///         Returns success for any valid input (always returns 1).
///         Includes chain ID check to prevent deployment on mainnet (BSC mainnet = 56).
contract MockP256 {
    /// @dev Fallback function that accepts any calldata and returns success (1)
    ///      Only works on testnets (chainId != 56)
    fallback(bytes calldata) external returns (bytes memory) {
        require(block.chainid != 56, "MockP256: cannot deploy on mainnet");
        return abi.encode(uint256(1));
    }

    /// @dev Verify function matching P-256 verifier interface
    ///      Always returns 1 (success) for any input on testnets
    function verify(bytes32, bytes32, bytes32, bytes32, bytes32) external view returns (uint256) {
        require(block.chainid != 56, "MockP256: cannot use on mainnet");
        return 1;
    }
    
    /// @dev Helper function to check if mock is being used on allowed network
    function isAllowedNetwork() external view returns (bool) {
        return block.chainid != 56;
    }
}
