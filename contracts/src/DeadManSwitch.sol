// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {IRecoveryHook} from "./interfaces/IRecoveryHook.sol";

/// @title DeadManSwitch
/// @notice Inactivity timer that signals when a wallet owner has been inactive too long.
///         NO deposits, NO funds, NO ownership transfer — pure event emitter.
///         Actual ownership transfer is triggered off-chain via SocialRecoveryModule.
/// @dev IRecoveryHook: onRecoveryExecuted resets timer when guardian recovery happens.
contract DeadManSwitch is IRecoveryHook {
    error ErrNotBeneficiary();
    error ErrInactivityNotMet();
    error ErrAlreadyClaimed();
    error ErrBeneficiarySameAsOwner();
    error ErrSwitchNotActive();
    error ErrNotTriggered();
    error ErrChallengeNotExpired();
    error ErrUnauthorized();

    uint256 public constant MIN_INACTIVITY = 90 days;
    uint256 public constant CHALLENGE_PERIOD = 7 days;

    enum State { Active, Triggered, Executed }

    struct SwitchConfig {
        address beneficiary;
        uint256 inactivityPeriod;
        uint256 lastActivity;
        bool active;
    }

    mapping(address wallet => SwitchConfig) public switches;
    mapping(address => State) public recoveryState;
    mapping(address => uint256) public triggerAt;

    address public owner;
    /// @notice Allowed caller of onRecoveryExecuted (SocialRecoveryModule).
    address public recoveryCaller;

    modifier onlyOwner() {
        if (msg.sender != owner) revert ErrUnauthorized();
        _;
    }

    event SwitchSet(address indexed wallet, address indexed beneficiary, uint256 inactivityPeriod);
    event ActivityPinged(address indexed wallet, uint256 timestamp);
    event BeneficiaryChanged(address indexed wallet, address indexed newBeneficiary);
    event SwitchTriggered(address indexed wallet, address indexed beneficiary);
    event SwitchDeactivated(address indexed wallet);
    event TriggerChallenged(address indexed wallet);
    /// @notice Emitted when executeClaim is called after challenge period expires.
    ///         Watchtower/relay picks this up to initiate guardian recovery.
    event OwnershipClaimTriggered(address indexed wallet, address indexed beneficiary);

    constructor() {
        owner = msg.sender;
    }

    // ─── Configuration ──────────────────────────────────────────────

    function setSwitch(address beneficiary, uint256 inactivityPeriod) external {
        if (beneficiary == msg.sender) revert ErrBeneficiarySameAsOwner();
        if (inactivityPeriod < MIN_INACTIVITY) inactivityPeriod = MIN_INACTIVITY;

        switches[msg.sender] = SwitchConfig({
            beneficiary: beneficiary,
            inactivityPeriod: inactivityPeriod,
            lastActivity: block.timestamp,
            active: true
        });

        emit SwitchSet(msg.sender, beneficiary, inactivityPeriod);
    }

    function changeBeneficiary(address newBeneficiary) external {
        SwitchConfig storage cfg = switches[msg.sender];
        if (newBeneficiary == msg.sender) revert ErrBeneficiarySameAsOwner();
        cfg.beneficiary = newBeneficiary;
        emit BeneficiaryChanged(msg.sender, newBeneficiary);
    }

    function deactivate() external {
        SwitchConfig storage cfg = switches[msg.sender];
        if (!cfg.active) revert ErrSwitchNotActive();
        cfg.active = false;
        emit SwitchDeactivated(msg.sender);
    }

    // ─── Heartbeat ──────────────────────────────────────────────────

    function ping() external {
        SwitchConfig storage cfg = switches[msg.sender];
        if (!cfg.active) revert ErrSwitchNotActive();
        if (recoveryState[msg.sender] != State.Active) revert ErrSwitchNotActive();
        cfg.lastActivity = block.timestamp;
        emit ActivityPinged(msg.sender, block.timestamp);
    }

    // ─── Inactivity flow ────────────────────────────────────────────

    /// @notice Start the challenge period. Only callable by beneficiary after inactivityPeriod.
    function initiateClaim(address wallet) external {
        SwitchConfig storage cfg = switches[wallet];
        if (msg.sender != cfg.beneficiary) revert ErrNotBeneficiary();
        if (!cfg.active) revert ErrSwitchNotActive();
        if (recoveryState[wallet] != State.Active) revert ErrAlreadyClaimed();
        if (block.timestamp < cfg.lastActivity + cfg.inactivityPeriod) revert ErrInactivityNotMet();

        recoveryState[wallet] = State.Triggered;
        triggerAt[wallet] = block.timestamp;
        emit SwitchTriggered(wallet, msg.sender);
    }

    /// @notice Cancel a triggered claim. Owner says "I'm alive".
    function challengeTrigger() external {
        if (recoveryState[msg.sender] != State.Triggered) revert ErrNotTriggered();
        recoveryState[msg.sender] = State.Active;
        triggerAt[msg.sender] = 0;
        emit TriggerChallenged(msg.sender);
    }

    /// @notice Finalize after challenge period. Emits event only — no funds or ownership transfer.
    ///         Watchtower/relay picks up OwnershipClaimTriggered to initiate guardian recovery.
    function executeClaim(address wallet) external {
        SwitchConfig storage cfg = switches[wallet];
        if (msg.sender != cfg.beneficiary) revert ErrNotBeneficiary();
        if (recoveryState[wallet] != State.Triggered) revert ErrNotTriggered();
        if (block.timestamp < triggerAt[wallet] + CHALLENGE_PERIOD) revert ErrChallengeNotExpired();

        recoveryState[wallet] = State.Executed;
        emit OwnershipClaimTriggered(wallet, msg.sender);
    }

    // ─── IRecoveryHook ──────────────────────────────────────────────

    /// @notice Reset timer and cancel any active challenge after guardian recovery.
    /// @dev Only callable by recoveryCaller (SocialRecoveryModule).
    function onRecoveryExecuted(address wallet) external {
        if (msg.sender != recoveryCaller) revert ErrUnauthorized();
        SwitchConfig storage cfg = switches[wallet];
        cfg.lastActivity = block.timestamp;
        recoveryState[wallet] = State.Active;
        triggerAt[wallet] = 0;
        emit ActivityPinged(wallet, block.timestamp);
    }

    // ─── Admin ──────────────────────────────────────────────────────

    /// @notice Set the allowed caller of onRecoveryExecuted (SocialRecoveryModule address).
    function setRecoveryCaller(address caller) external onlyOwner {
        recoveryCaller = caller;
    }
}
