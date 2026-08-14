// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {InsuranceFund} from "../src/InsuranceFund.sol";

contract DeployInsuranceFund is Script {
    function run() external {
        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));

        address insuranceAuditor = vm.envAddress("INSURANCE_AUDITOR_ADDRESS");
        require(insuranceAuditor != address(0), "INSURANCE_AUDITOR_ADDRESS not set");
        require(insuranceAuditor != deployer, "Auditor cannot be deployer");
        vm.startBroadcast(deployer);
        address[] memory auditors = new address[](1);
        auditors[0] = insuranceAuditor;
        InsuranceFund fund = new InsuranceFund(auditors, 1);
        vm.stopBroadcast();

        console.log("InsuranceFund deployed at:", address(fund));
    }
}
