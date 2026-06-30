// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOPaymaster} from "../../src/MDAOPaymaster.sol";

contract PaymasterInvariant is Test {
    MDAOPaymaster public paymaster;
    address public entryPoint;
    address public mdao;
    address public usdt;

    function setUp() public {
        entryPoint = address(0x1234);
        vm.etch(entryPoint, hex"00");
        mdao = address(0xDEAD);
        usdt = address(0xBEEF);
        paymaster = new MDAOPaymaster(entryPoint, mdao, usdt, address(0xBACE));
    }

    // Invariant: Trusted signer cannot be address(0)
    function invariant_trustedSignerNotZero() public {
        assertTrue(paymaster.trustedSigner() != address(0), "Trusted signer is zero");
    }
}
