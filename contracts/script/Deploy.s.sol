// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {MDAOPaymaster} from "../src/MDAOPaymaster.sol";
import {InsuranceFund} from "../src/InsuranceFund.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {NicknameRegistry} from "../src/NicknameRegistry.sol";
import {DeadManSwitch} from "../src/DeadManSwitch.sol";
import {AttestationLedger} from "../src/AttestationLedger.sol";
import {RefundVault} from "../src/RefundVault.sol";
import {SessionKeyModule} from "../src/SessionKeyModule.sol";
import {TimelockController} from "@openzeppelin/contracts/governance/TimelockController.sol";
import {TrustProviderRegistry} from "../src/TrustProviderRegistry.sol";
import {EcdsaVerifier} from "../src/EcdsaVerifier.sol";
import {MockP256} from "../test/mocks/MockP256.sol";

contract Deploy is Script {
    function run() external {
        address deployer = vm.rememberKey(vm.envUint("DEPLOYER_PRIVATE_KEY"));

        // ── Chain ID validation (F-117) ──
        uint256 chainId = block.chainid;
        require(chainId == 56 || chainId == 97, "Unsupported chain ID (56=BSC, 97=BSC Testnet)");
        console.log("Chain ID:", chainId);

        // ── P-256 Precompile check (F-108) ──
        // RIP-7212 may not be available on BSC Testnet (97); fallback to MockP256
        (bool precompileOk, bytes memory precompileData) = address(0x100).staticcall(
            abi.encodePacked(bytes32(0), bytes32(0), bytes32(0), bytes32(0), bytes32(0))
        );
        bool rip7212Available = precompileOk && precompileData.length >= 32;

        address p256Verifier;
        if (rip7212Available) {
            p256Verifier = address(0x100);
            console.log("Using RIP-7212 P-256 precompile at 0x100");
        } else {
            vm.startBroadcast(deployer);
            MockP256 mockP256 = new MockP256();
            vm.stopBroadcast();
            p256Verifier = address(mockP256);
            console.log("RIP-7212 not available, deployed MockP256 at:", address(mockP256));
        }

        // ── MDAOToken ──
        vm.startBroadcast(deployer);
        MDAOToken token = new MDAOToken(deployer);
        vm.stopBroadcast();
        console.log("MDAOToken:", address(token));

        // ── MDAOPaymaster (needs token address) ──
        vm.startBroadcast(deployer);
        MDAOPaymaster paymaster = new MDAOPaymaster(
            vm.envAddress("ENTRY_POINT"),
            address(token),
            vm.envAddress("USDT_ADDRESS"),
            vm.envAddress("TRUSTED_SIGNER")
        );
        vm.stopBroadcast();
        console.log("MDAOPaymaster:", address(paymaster));

        // ── InsuranceFund (after paymaster, needs paymaster for fee collection) ──
        vm.startBroadcast(deployer);
        InsuranceFund insuranceFund = new InsuranceFund(address(0));
        vm.stopBroadcast();
        console.log("InsuranceFund:", address(insuranceFund));
        // ponytail: InsuranceFund needs setPaymaster(address(paymaster)) once contract supports it

        // ── SocialRecoveryModule (needs mdaoToken + p256Verifier) ──
        vm.startBroadcast(deployer);
        SocialRecoveryModule socialRecovery = new SocialRecoveryModule(address(token), p256Verifier);
        vm.stopBroadcast();
        console.log("SocialRecoveryModule:", address(socialRecovery));

        // ── NicknameRegistry ──
        vm.startBroadcast(deployer);
        NicknameRegistry nicknameRegistry = new NicknameRegistry();
        vm.stopBroadcast();
        console.log("NicknameRegistry:", address(nicknameRegistry));

        // ── DeadManSwitch ──
        vm.startBroadcast(deployer);
        DeadManSwitch deadManSwitch = new DeadManSwitch();
        vm.stopBroadcast();
        console.log("DeadManSwitch:", address(deadManSwitch));
        // ponytail: DeadManSwitch uses MIN_INACTIVITY=90d constant; add setter if variable needed

        // ── AttestationLedger ──
        vm.startBroadcast(deployer);
        AttestationLedger attestationLedger = new AttestationLedger();
        vm.stopBroadcast();
        console.log("AttestationLedger:", address(attestationLedger));
        // ponytail: AttestationLedger needs attester role setup once contract supports it

        // ── RefundVault (needs paymaster address) ──
        vm.startBroadcast(deployer);
        RefundVault refundVault = new RefundVault();
        vm.stopBroadcast();
        console.log("RefundVault:", address(refundVault));
        // ponytail: RefundVault needs setPaymaster(address(paymaster)) once contract supports it

        // ── SessionKeyModule ──
        vm.startBroadcast(deployer);
        SessionKeyModule sessionKeys = new SessionKeyModule();
        vm.stopBroadcast();
        console.log("SessionKeyModule:", address(sessionKeys));

        // ── TimelockController ──
        // F-107: proposers = [gnosisSafe], не [deployer]; admin = address(0)
        address gnosisSafe = vm.envOr("GNOSIS_SAFE", deployer);
        vm.startBroadcast(deployer);
        address[] memory proposers = new address[](1);
        proposers[0] = gnosisSafe;
        address[] memory executors = new address[](1);
        executors[0] = gnosisSafe;
        TimelockController timelock = new TimelockController(2 days, proposers, executors, address(0));
        vm.stopBroadcast();
        console.log("TimelockController:", address(timelock));
        console.log("Timelock proposer:", gnosisSafe);

        // Transfer paymaster ownership to timelock (2-step)
        vm.startBroadcast(deployer);
        paymaster.transferOwnership(address(timelock));
        vm.stopBroadcast();
        console.log("Paymaster pendingOwner set to TimelockController");

        // Accept ownership through timelock (requires 2-day delay in real deploy)
        // In script, we use vm.warp for demonstration
        bytes memory acceptData = abi.encodeWithSelector(paymaster.acceptOwnership.selector);
        vm.startBroadcast(deployer);
        timelock.schedule(address(paymaster), 0, acceptData, bytes32(0), bytes32("accept-paymaster-ownership"), 2 days);
        vm.stopBroadcast();
        // Note: In production, deployer must wait 2 days and call execute manually
        // vm.warp(block.timestamp + 2 days);
        // timelock.execute(address(paymaster), 0, acceptData, bytes32(0), bytes32("accept-paymaster-ownership"));
        console.log("Timelock: scheduled paymaster.acceptOwnership (execute after 2 days)");

        // ── EcdsaVerifier ──
        vm.startBroadcast(deployer);
        EcdsaVerifier ecdsaVerifier = new EcdsaVerifier(vm.envAddress("TRUSTED_SIGNER"));
        vm.stopBroadcast();
        console.log("EcdsaVerifier:", address(ecdsaVerifier));

        // ── TrustProviderRegistry ──
        vm.startBroadcast(deployer);
        TrustProviderRegistry providerRegistry = new TrustProviderRegistry();
        vm.stopBroadcast();
        console.log("TrustProviderRegistry:", address(providerRegistry));

        // Register default provider
        bytes32 providerId = bytes32(uint256(uint160(vm.envAddress("TRUSTED_SIGNER"))));
        vm.startBroadcast(deployer);
        providerRegistry.registerProvider(providerId, address(ecdsaVerifier));
        vm.stopBroadcast();
        console.log("Registered provider:", vm.toString(providerId));

        // Set registry on paymaster (owner is still deployer at this point)
        vm.startBroadcast(deployer);
        paymaster.setRegistry(address(providerRegistry));
        vm.stopBroadcast();
        console.log("Paymaster registry set to:", address(providerRegistry));

        // Transfer registry ownership to timelock
        vm.startBroadcast(deployer);
        providerRegistry.transferOwnership(address(timelock));
        vm.stopBroadcast();
        console.log("Registry pendingOwner set to TimelockController");

        // Schedule registry.acceptOwnership through timelock
        bytes memory acceptRegistryData = abi.encodeWithSelector(providerRegistry.acceptOwnership.selector);
        vm.startBroadcast(deployer);
        timelock.schedule(address(providerRegistry), 0, acceptRegistryData, bytes32(0), bytes32("accept-registry-ownership"), 2 days);
        vm.stopBroadcast();
        console.log("Timelock: scheduled registry.acceptOwnership (execute after 2 days)");

        // Post-deploy verification (F-107): registry owner must be timelock after execute
        // In forge script simulation: warp+execute to verify
        // In production: run `cast call TrustProviderRegistry owner() --rpc-url <RPC>`
        //   expected: address of TimelockController above
        vm.warp(block.timestamp + 2 days + 1 hours);
        timelock.execute(address(providerRegistry), 0, acceptRegistryData, bytes32(0), bytes32("accept-registry-ownership"));
        console.log("Registry owner:", vm.toString(providerRegistry.owner()));
        require(providerRegistry.owner() == address(timelock), "Registry not owned by timelock");
        console.log("[OK] Registry owned by TimelockController");

        // Also execute paymaster ownership transfer for verification
        timelock.execute(address(paymaster), 0, acceptData, bytes32(0), bytes32("accept-paymaster-ownership"));
        console.log("Paymaster owner:", vm.toString(paymaster.owner()));
        require(paymaster.owner() == address(timelock), "Paymaster not owned by timelock");
        console.log("[OK] Paymaster owned by TimelockController");
    }
}
