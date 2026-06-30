// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Permit.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Pausable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract MDAOToken is ERC20, ERC20Burnable, ERC20Permit, ERC20Pausable, Ownable {
    uint256 public constant MAX_SUPPLY = 1_000_000_000 * 10**18;
    uint256 public constant MAX_BURN_FEE_BPS = 300;
    uint256 public constant MAX_BURN_FEE_CHANGE_BPS = 50;
    uint256 public constant MIN_UPDATE_DELAY = 1 hours;
    address public constant BURN_ADDRESS = 0x000000000000000000000000000000000000dEaD;

    uint256 public burnFeeBps = 50;
    uint256 public lastBurnFeeUpdate;

    mapping(address => bool) public isExempt;

    event BurnFeeUpdated(uint256 oldFee, uint256 newFee);
    event ExemptUpdated(address indexed account, bool exempt);

    error MaxSupplyExceeded();
    error FeeTooHigh();
    error FeeChangeTooHigh();
    error UpdateTooSoon();

    constructor(
        address initialOwner
    )
        ERC20("MDAO", "MDAO")
        ERC20Permit("MDAO")
        Ownable(initialOwner)
    {
        _mint(initialOwner, 100_000_000 * 10**18);
        isExempt[address(this)] = true;
    }

    function _update(address from, address to, uint256 value) internal override(ERC20, ERC20Pausable) {
        if (from != address(0) && to != address(0) && !isExempt[from] && !isExempt[to] && burnFeeBps > 0) {
            uint256 fee = value * burnFeeBps / 10000;
            if (fee == 0 && value > 0) fee = 1; // ponytail: minimum 1 wei burn to prevent dust transfer (F-029)
            super._update(from, BURN_ADDRESS, fee);
            value -= fee;
        }
        super._update(from, to, value);
    }

    function mint(address to, uint256 amount) external onlyOwner {
        if (totalSupply() + amount > MAX_SUPPLY) revert MaxSupplyExceeded();
        _mint(to, amount);
    }

    function setBurnFeeBps(uint256 newFee) external onlyOwner {
        if (newFee > MAX_BURN_FEE_BPS) revert FeeTooHigh();
        if (lastBurnFeeUpdate != 0 && block.timestamp < lastBurnFeeUpdate + MIN_UPDATE_DELAY) {
            revert UpdateTooSoon();
        }
        uint256 currentFee = burnFeeBps;
        if (newFee > currentFee) {
            if (newFee - currentFee > MAX_BURN_FEE_CHANGE_BPS) revert FeeChangeTooHigh();
        } else {
            if (currentFee - newFee > MAX_BURN_FEE_CHANGE_BPS) revert FeeChangeTooHigh();
        }
        lastBurnFeeUpdate = block.timestamp;
        emit BurnFeeUpdated(currentFee, newFee);
        burnFeeBps = newFee;
    }

    function setExempt(address account, bool exempt) external onlyOwner {
        isExempt[account] = exempt;
        emit ExemptUpdated(account, exempt);
    }

    function pause() external onlyOwner {
        _pause();
    }

    function unpause() external onlyOwner {
        _unpause();
    }
}
