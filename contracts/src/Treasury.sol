// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {ITreasury} from "./interfaces/ITreasury.sol";
import {ISplitterFactory} from "./interfaces/ISplitterFactory.sol";

contract Treasury is AccessControl, ReentrancyGuard, ITreasury {
    using SafeERC20 for IERC20;

    bytes32 public constant FINANCE_ROLE = keccak256("FINANCE_ROLE");
    bytes32 public constant EMERGENCY_ROLE = keccak256("EMERGENCY_ROLE");

    bool private _paused;
    address public proposalContract;
    /// @notice Splitter factory for verifying recipients. Zero = skip validation.
    address public splitterFactory;

    error ErrUnauthorized();

    mapping(bytes32 id => Allocation) private _allocations;

    modifier whenNotPaused() {
        if (_paused) revert ErrPaused();
        _;
    }

    constructor(address admin, address _proposalContract) {
        if (admin == address(0)) revert ErrInvalidAddress();
        proposalContract = _proposalContract;
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    /// @notice Set the Proposal contract address (admin only, for deployment circular dependency)
    function setProposalContract(address _proposalContract) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (_proposalContract == address(0)) revert ErrInvalidAddress();
        proposalContract = _proposalContract;
    }

    /// @notice Set the SplitterFactory address for recipient validation.
    /// @param _splitterFactory Address of PaymentSplitterFactory. Set to address(0) to disable validation.
    function setSplitterFactory(address _splitterFactory) external onlyRole(DEFAULT_ADMIN_ROLE) {
        splitterFactory = _splitterFactory;
    }

    receive() external payable whenNotPaused {
        emit Deposited(msg.sender, msg.value);
    }

    fallback() external payable whenNotPaused {
        emit Deposited(msg.sender, msg.value);
    }

    function deposit() external payable whenNotPaused {
        emit Deposited(msg.sender, msg.value);
    }

    function depositERC20(address token, uint256 amount) external whenNotPaused {
        if (token == address(0)) revert ErrInvalidAddress();
        if (amount == 0) revert ErrInvalidAmount();
        IERC20(token).safeTransferFrom(msg.sender, address(this), amount);
        emit ERC20Deposited(msg.sender, token, amount);
    }

    function createAllocation(
        bytes32 id,
        address[] calldata recipients,
        uint256[] calldata amounts,
        address token
    ) external onlyRole(FINANCE_ROLE) {
        if (id == bytes32(0)) revert ErrInvalidAllocation();
        if (recipients.length == 0) revert ErrInvalidAllocation();
        if (recipients.length != amounts.length) revert ErrInvalidAllocation();
        if (_allocations[id].executed || _allocations[id].cancelled) revert ErrInvalidAllocation();
        if (_allocations[id].recipients.length > 0) revert ErrAllocationExists();

        // Validate recipients: each must be EOA or a Factory-deployed splitter
        address sf = splitterFactory;
        if (sf != address(0)) {
            for (uint256 i; i < recipients.length; i++) {
                if (recipients[i].code.length > 0 && !ISplitterFactory(sf).deployedSplitters(recipients[i])) {
                    revert ErrNotVerifiedSplitter();
                }
            }
        }

        Allocation storage alloc = _allocations[id];
        alloc.recipients = recipients;
        alloc.amounts = amounts;
        alloc.token = token;

        uint256 total;
        for (uint256 i; i < amounts.length; i++) {
            total += amounts[i];
        }

        emit AllocationCreated(id, token, total, recipients.length);
    }

    function executeAllocation(bytes32 id) external nonReentrant whenNotPaused {
        if (msg.sender != proposalContract) revert ErrUnauthorized();

        Allocation storage alloc = _allocations[id];
        if (alloc.executed) revert ErrAlreadyExecuted();
        if (alloc.cancelled) revert ErrAlreadyCancelled();

        uint256 total;
        for (uint256 i; i < alloc.amounts.length; i++) {
            total += alloc.amounts[i];
        }

        if (alloc.token == address(0)) {
            if (address(this).balance < total) revert ErrInsufficientFunds();
        } else {
            if (IERC20(alloc.token).balanceOf(address(this)) < total) revert ErrInsufficientFunds();
        }

        alloc.executed = true;

        // ponytail: zero amounts inline during transfers = double-spend defense-in-depth
        address[] memory recipients = alloc.recipients;
        uint256[] memory amounts = alloc.amounts;
        address token = alloc.token;

        for (uint256 i; i < recipients.length; i++) {
            alloc.amounts[i] = 0; // zero storage — double-spend protection
            if (token == address(0)) {
                (bool sent, ) = recipients[i].call{value: amounts[i]}("");
                if (!sent) revert ErrTransferFailed();
            } else {
                IERC20(token).safeTransfer(recipients[i], amounts[i]);
            }
        }

        delete alloc.recipients;
        delete alloc.amounts;

        emit AllocationExecuted(id);
    }

    function cancelAllocation(bytes32 id) external {
        if (msg.sender != proposalContract && !hasRole(FINANCE_ROLE, msg.sender)) revert ErrUnauthorized();
        Allocation storage alloc = _allocations[id];
        if (alloc.executed) revert ErrAlreadyExecuted();
        if (alloc.cancelled) revert ErrAlreadyCancelled();
        alloc.cancelled = true;
        emit AllocationCancelled(id);
    }

    function pause() external onlyRole(EMERGENCY_ROLE) {
        _paused = true;
    }

    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _paused = false;
    }

    function withdrawStuckTokens(address token, uint256 amount) external onlyRole(DEFAULT_ADMIN_ROLE) nonReentrant {
        if (amount == 0) revert ErrInvalidAmount();
        if (token == address(0)) {
            (bool sent, ) = msg.sender.call{value: amount}("");
            if (!sent) revert ErrTransferFailed();
        } else {
            IERC20(token).safeTransfer(msg.sender, amount);
        }
    }

    function paused() external view returns (bool) {
        return _paused;
    }

    function getAllocation(bytes32 id) external view returns (Allocation memory) {
        return _allocations[id];
    }
}
