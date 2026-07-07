// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @notice Interface for PaymentSplitterFactory.
/// @dev Enables Treasury to verify recipients are Factory-deployed splitters.
interface ISplitterFactory {
    /// @notice Check if a splitter was deployed by this factory.
    function deployedSplitters(address splitter) external view returns (bool);

    /// @notice Predict the CREATE2 address for a given salt and payees/shares.
    function getAddress(bytes32 salt) external view returns (address);

    /// @notice Deploy a new PaymentSplitter via CREATE2.
    /// @param salt  Unique salt for deterministic address.
    /// @param payees  Array of payee addresses.
    /// @param shares  Array of share amounts (must match payees length).
    /// @return splitter  Address of the deployed PaymentSplitter.
    function deploy(bytes32 salt, address[] calldata payees, uint256[] calldata shares)
        external
        returns (address splitter);
}
