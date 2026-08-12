// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";

contract DeploySocialRecoveryModule is Script {
    function run() external {
        // P-256 verifier is required on BSC (56/97) — no RIP-7212 precompile, no defaults.
        // vm.envAddress reverts if P256_VERIFIER is not set (fail-fast).
        address p256Verifier = vm.envAddress("P256_VERIFIER");
        require(p256Verifier != address(0), "P256_VERIFIER must be set (deployed P256Verifier address)");
        require(p256Verifier.code.length > 0, "P256_VERIFIER has no code (deployed P256Verifier required)");

        console.log("Using custom P256_VERIFIER:", p256Verifier);

        vm.startBroadcast();
        // MDAO token must be deployed first — pass its address here
        address mdaoToken = vm.envAddress("MDAO_TOKEN");
        SocialRecoveryModule module = new SocialRecoveryModule(mdaoToken, p256Verifier);
        vm.stopBroadcast();

        console.log("SocialRecoveryModule deployed at:", address(module));
        console.log("P256_VERIFIER:", p256Verifier);
    }
}
