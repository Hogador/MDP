// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";

contract DeploySocialRecoveryModule is Script {
    function run() external {
        // P-256 verifier: override via env or default to RIP-7212 precompile (0x100)
        // BSC (56/97) does NOT have RIP-7212; deploy P256Verifier and pass its address
        address p256Verifier = vm.envOr("P256_VERIFIER", address(0x100));

        // Warn if using precompile on BSC (RIP-7212 confirmed absent)
        uint256 chainId = block.chainid;
        if (chainId == 56 || chainId == 97) {
            if (p256Verifier == address(0x100)) {
                console.log("WARNING: BSC chain detected, RIP-7212 precompile is NOT available.");
                console.log("Set P256_VERIFIER env to deployed P256Verifier address.");
            } else {
                console.log("Using custom P256_VERIFIER:", p256Verifier);
            }
        }

        vm.startBroadcast();
        // MDAO token must be deployed first — pass its address here
        address mdaoToken = vm.envAddress("MDAO_TOKEN");
        SocialRecoveryModule module = new SocialRecoveryModule(mdaoToken, p256Verifier);
        vm.stopBroadcast();

        console.log("SocialRecoveryModule deployed at:", address(module));
        console.log("P256_VERIFIER:", p256Verifier);
    }
}
