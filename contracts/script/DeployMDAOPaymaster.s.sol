// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script} from "forge-std/Script.sol";
import {console} from "forge-std/console.sol";
import {MDAOPaymaster} from "../src/MDAOPaymaster.sol";

// Deploy to Sepolia:
//   forge script script/DeployMDAOPaymaster.s.sol \
//     --rpc-url https://rpc.sepolia.org \
//     --private-key <deployer-key> \
//     --broadcast \
//     --verify --etherscan-api-key <key>
//   env USDT=0x7169D38820dfd117C3FA1f22a697dBA58d90BA06
//
// Deploy to BSC:
//   forge script script/DeployMDAOPaymaster.s.sol \
//     --rpc-url https://bsc-dataseed1.binance.org \
//     --private-key <deployer-key> \
//     --broadcast \
//     --verify --etherscan-api-key <key>
//
// ENV:
//   ENTRY_POINT   (default: 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789)
//   MDAO          (BSC: 0x60322971a672B81BccE5947706D22c19dAeCf6Fb;
//                  Sepolia: deploy your own mock or leave default)
//   USDT          (BSC: 0x55d398326f99059fF775485246999027B3197955;
//                  Sepolia: 0x7169D38820dfd117C3FA1f22a697dBA58d90BA06)
contract DeployMDAOPaymaster is Script {
    address constant DEFAULT_ENTRY_POINT = 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789;
    address constant MDAO_BSC = 0x60322971a672B81BccE5947706D22c19dAeCf6Fb;
    address constant USDT_BSC = 0x55d398326f99059fF775485246999027B3197955;
    address constant USDT_SEPOLIA = 0x7169D38820dfd117C3FA1f22a697dBA58d90BA06;

    function run() external {
        address entryPoint = vm.envOr("ENTRY_POINT", DEFAULT_ENTRY_POINT);
        address mdao = vm.envOr("MDAO", MDAO_BSC);
        address usdt = vm.envOr("USDT", USDT_BSC);

        address trustedSigner = vm.envOr("TRUSTED_SIGNER", address(0));

        vm.startBroadcast();
        MDAOPaymaster pm = new MDAOPaymaster(entryPoint, mdao, usdt, trustedSigner);
        vm.stopBroadcast();

        console.log("MDAOPaymaster deployed at:", address(pm));
        console.log("EntryPoint:", entryPoint);
        console.log("MDAO:", mdao);
        console.log("USDT:", usdt);
        console.log("TrustedSigner:", trustedSigner);
    }
}
