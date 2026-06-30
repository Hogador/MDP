// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract DeadManSwitch is Ownable, ReentrancyGuard {
    error ErrNotBeneficiary();
    error ErrInactivityNotMet();
    error ErrAlreadyClaimed();
    error ErrBeneficiarySameAsOwner();
    error ErrTransferFailed();
    error ErrNotExpired();
    error ErrSwitchNotActive();
    error ErrNoDeposits();
    error ErrNotTriggered();

    uint256 public constant MIN_INACTIVITY = 90 days;
    uint256 public constant CHALLENGE_PERIOD = 7 days;

    enum State { Active, Triggered, Claimable }

    struct SwitchConfig {
        address beneficiary;
        uint256 inactivityPeriod;
        uint256 lastActivity;
        bool active;
        bool claimed;
    }

    mapping(address wallet => SwitchConfig) public switches;
    mapping(address => uint256) public deposits;
    mapping(address => State) public recoveryState;
    mapping(address => uint256) public triggerAt;

    event SwitchSet(address indexed wallet, address indexed beneficiary, uint256 inactivityPeriod);
    event ActivityPinged(address indexed wallet, uint256 timestamp);
    event BeneficiaryChanged(address indexed wallet, address indexed newBeneficiary);
    event SwitchTriggered(address indexed wallet, address indexed beneficiary);
    event SwitchDeactivated(address indexed wallet);
    event FundsClaimed(address indexed wallet, address indexed beneficiary, uint256 amount);
    event TriggerChallenged(address indexed wallet);

    constructor() Ownable(msg.sender) {}

    function setSwitch(address beneficiary, uint256 inactivityPeriod) external {
        if (beneficiary == msg.sender) revert ErrBeneficiarySameAsOwner();
        if (inactivityPeriod < MIN_INACTIVITY) inactivityPeriod = MIN_INACTIVITY;

        switches[msg.sender] = SwitchConfig({
            beneficiary: beneficiary,
            inactivityPeriod: inactivityPeriod,
            lastActivity: block.timestamp,
            active: true,
            claimed: false
        });

        emit SwitchSet(msg.sender, beneficiary, inactivityPeriod);
    }

    function ping() external {
        SwitchConfig storage cfg = switches[msg.sender];
        if (!cfg.active) revert ErrSwitchNotActive();
        if (recoveryState[msg.sender] != State.Active) revert ErrSwitchNotActive();
        cfg.lastActivity = block.timestamp;
        emit ActivityPinged(msg.sender, block.timestamp);
    }

    function changeBeneficiary(address newBeneficiary) external {
        SwitchConfig storage cfg = switches[msg.sender];
        if (newBeneficiary == msg.sender) revert ErrBeneficiarySameAsOwner();
        cfg.beneficiary = newBeneficiary;
        emit BeneficiaryChanged(msg.sender, newBeneficiary);
    }

    function triggerRecovery(address wallet) external {
        SwitchConfig storage cfg = switches[wallet];
        if (msg.sender != cfg.beneficiary) revert ErrNotBeneficiary();
        if (!cfg.active) revert ErrSwitchNotActive();
        if (recoveryState[wallet] != State.Active) revert ErrAlreadyClaimed();
        if (block.timestamp < cfg.lastActivity + cfg.inactivityPeriod) revert ErrInactivityNotMet();

        recoveryState[wallet] = State.Triggered;
        triggerAt[wallet] = block.timestamp;
        emit SwitchTriggered(wallet, msg.sender);
    }

    function challengeTrigger() external {
        if (recoveryState[msg.sender] != State.Triggered) revert ErrNotTriggered();
        recoveryState[msg.sender] = State.Active;
        triggerAt[msg.sender] = 0;
        emit TriggerChallenged(msg.sender);
    }

    function deactivate() external {
        SwitchConfig storage cfg = switches[msg.sender];
        if (!cfg.active) revert ErrSwitchNotActive();
        cfg.active = false;
        emit SwitchDeactivated(msg.sender);
    }

    function claimFunds(address wallet) external nonReentrant {
        SwitchConfig storage cfg = switches[wallet];
        if (msg.sender != cfg.beneficiary) revert ErrNotBeneficiary();
        if (recoveryState[wallet] != State.Triggered) revert ErrSwitchNotActive();
        if (block.timestamp < triggerAt[wallet] + CHALLENGE_PERIOD) revert ErrNotExpired();

        recoveryState[wallet] = State.Claimable;

        uint256 amount = deposits[wallet];
        if (amount == 0) revert ErrNoDeposits();

        // CEI: zero out before send
        deposits[wallet] = 0;

        (bool sent, ) = payable(msg.sender).call{value: amount}("");
        if (!sent) revert ErrTransferFailed();
        emit FundsClaimed(wallet, msg.sender, amount);
    }

    receive() external payable {
        deposits[msg.sender] += msg.value;
    }
}
