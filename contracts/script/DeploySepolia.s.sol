// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {MDAOPaymaster} from "../src/MDAOPaymaster.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {NicknameRegistry} from "../src/NicknameRegistry.sol";
import {SessionKeyModule} from "../src/SessionKeyModule.sol";

/// @title DeploySepolia — Unified Sepolia deployment script
/// @notice Deploys all 5 MDAOPay contracts in one broadcast.
///         Verifies on Etherscan if ETHERSCAN_API_KEY is set.
///         Run:
///           forge script script/DeploySepolia.s.sol \
///             --rpc-url https://ethereum-sepolia.publicnode.com \
///             --broadcast --verify --etherscan-api-key $ETHERSCAN_API_KEY -vvv
contract DeploySepolia is Script {
    uint256 constant SEPOLIA_CHAIN_ID = 11155111;
    address constant DEFAULT_ENTRY_POINT = 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789;
    address constant USDT_SEPOLIA = 0x7169D38820dfd117C3FA1f22a697dBA58d90BA06;

    function run() external {
        require(block.chainid == SEPOLIA_CHAIN_ID, "Wrong chain");

        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));
        address entryPoint = vm.envOr("ENTRY_POINT", DEFAULT_ENTRY_POINT);
        address usdt = vm.envOr("USDT", USDT_SEPOLIA);

        vm.startBroadcast(deployer);

        // 1. MDAOToken — deployer gets 100M tokens
        MDAOToken token = new MDAOToken(deployer);

        // 2. NicknameRegistry — stateless identity registry
        NicknameRegistry nickReg = new NicknameRegistry();

        // 3. SocialRecoveryModule — passkey-based guardian recovery with MDAO deposit
        SocialRecoveryModule recovery = new SocialRecoveryModule(address(token), 0x0000000000000000000000000000000000000100);

        // 4. SessionKeyModule — scoped dApp session keys
        SessionKeyModule sessionKeys = new SessionKeyModule();

        // 5. MDAOPaymaster — ERC-4337 paymaster (needs entryPoint + tokens)
        address trustedSigner = vm.envOr("TRUSTED_SIGNER", address(0));
        MDAOPaymaster paymaster = new MDAOPaymaster(entryPoint, address(token), usdt, trustedSigner);

        vm.stopBroadcast();

        console.log("=== Sepolia Deployment ===");
        console.log("MDAOToken:          ", address(token));
        console.log("NicknameRegistry:   ", address(nickReg));
        console.log("SocialRecoveryModule:", address(recovery));
        console.log("SessionKeyModule:   ", address(sessionKeys));
        console.log("MDAOPaymaster:      ", address(paymaster));
    }
}
