// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script} from "forge-std/Script.sol";
import {NicknameRegistry} from "../src/NicknameRegistry.sol";

contract DeployNicknameRegistry is Script {
    function run() external {
        vm.startBroadcast();
        new NicknameRegistry();
        vm.stopBroadcast();
    }
}
