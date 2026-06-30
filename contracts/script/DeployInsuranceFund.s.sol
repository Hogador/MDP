// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {InsuranceFund} from "../src/InsuranceFund.sol";

contract DeployInsuranceFund is Script {
    function run() external {
        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));

        vm.startBroadcast(deployer);
        InsuranceFund fund = new InsuranceFund(address(0));
        vm.stopBroadcast();

        console.log("InsuranceFund deployed at:", address(fund));
    }
}
