// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";

contract DeploySocialRecoveryModule is Script {
    function run() external {
        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));

        // EIP-7951 P256_VERIFY precompile (address 0x100)
        // Use vm.envAddress for testnets where mock is needed
        address p256Verifier = 0x0000000000000000000000000000000000000100;

        vm.startBroadcast(deployer);
        // MDAO token must be deployed first — pass its address here
        address mdaoToken = vm.envAddress("MDAO_TOKEN");
        SocialRecoveryModule module = new SocialRecoveryModule(mdaoToken, p256Verifier);
        vm.stopBroadcast();

        console.log("SocialRecoveryModule deployed at:", address(module));
        console.log("P256_VERIFIER:", p256Verifier);
    }
}
