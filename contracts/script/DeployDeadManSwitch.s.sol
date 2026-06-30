// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {DeadManSwitch} from "../src/DeadManSwitch.sol";

contract DeployDeadManSwitch is Script {
    function run() external {
        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));

        vm.startBroadcast(deployer);
        DeadManSwitch dms = new DeadManSwitch();
        vm.stopBroadcast();

        console.log("DeadManSwitch deployed at:", address(dms));
    }
}
