// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

interface IPaymentSplitter {
    event PayeeAdded(address indexed payee, uint256 shares);
    event PaymentReleased(address indexed token, address indexed payee, uint256 amount);
    event PaymentReceived(address indexed from, uint256 amount);

    error ErrAlreadyInitialized();
    error ErrInvalidPayee();
    error ErrZeroShares();
    error ErrNoDuePayment();
    error ErrArrayMismatch();
    error ErrTransferFailed();

    function initialize(address[] calldata payees, uint256[] calldata shares_) external;
    function release(address token) external;
    function receiveAndDistribute(address token) external;
    function released(address token, address payee) external view returns (uint256);
    function totalReleased(address token) external view returns (uint256);
    function shares(address payee) external view returns (uint256);
    function totalShares() external view returns (uint256);
    function getPayees() external view returns (address[] memory);
}
