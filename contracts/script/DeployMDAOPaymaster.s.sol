// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script} from "forge-std/Script.sol";
import {console} from "forge-std/console.sol";
import {MDAOPaymaster} from "../src/MDAOPaymaster.sol";

// Deploy to BSC Testnet:
//   forge script script/DeployMDAOPaymaster.s.sol \
//     --rpc-url https://data-seed-prebsc-1-s1.binance.org:8545 \
//     --private-key <deployer-key> \
//     --broadcast \
//     --verify --verifier-url https://api-testnet.bscscan.com/api --etherscan-api-key <key>
//
// Deploy to BSC Mainnet:
//   forge script script/DeployMDAOPaymaster.s.sol \
//     --rpc-url https://bsc-dataseed1.binance.org \
//     --private-key <deployer-key> \
//     --broadcast \
//     --verify --etherscan-api-key <key>
//
// ENV:
//   ENTRY_POINT   (default: 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789)
//   MDAO          (BSC mainnet: 0x60322971a672B81BccE5947706D22c19dAeCf6Fb;
//                  BSC testnet: deploy your own)
//   USDT          (BSC mainnet: 0x55d398326f99059fF775485246999027B3197955;
//                  BSC testnet: 0x337610d27c682E347C9cD60BD4b3b107C9d34dDD)
contract DeployMDAOPaymaster is Script {
    address constant DEFAULT_ENTRY_POINT = 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789;
    address constant MDAO_BSC = 0x60322971a672B81BccE5947706D22c19dAeCf6Fb;
    address constant USDT_BSC = 0x55d398326f99059fF775485246999027B3197955;
    address constant USDT_BSC_TESTNET = 0x337610d27c682E347C9cD60BD4b3b107C9d34dDd;

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
