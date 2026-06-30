// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {MDAOToken} from "../src/MDAOToken.sol";

contract DeployMDAOToken is Script {
    function run() external {
        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));
        vm.startBroadcast(deployer);
        MDAOToken token = new MDAOToken(deployer);
        vm.stopBroadcast();
        console.log("MDAO deployed at:", address(token));
    }
}
