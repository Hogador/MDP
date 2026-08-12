// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {MDAOPaymaster} from "../src/MDAOPaymaster.sol";
import {InsuranceFund} from "../src/InsuranceFund.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {NicknameRegistry} from "../src/NicknameRegistry.sol";
import {DeadManSwitch} from "../src/DeadManSwitch.sol";
import {IRecoveryHook} from "../src/interfaces/IRecoveryHook.sol";
import {AttestationLedger} from "../src/AttestationLedger.sol";
import {RefundVault} from "../src/RefundVault.sol";
import {SessionKeyModule} from "../src/SessionKeyModule.sol";
import {TimelockController} from "@openzeppelin/contracts/governance/TimelockController.sol";
import {TrustProviderRegistry} from "../src/TrustProviderRegistry.sol";
import {EcdsaVerifier} from "../src/EcdsaVerifier.sol";
import {P256Verifier} from "../src/helpers/P256Verifier.sol";
import {Treasury} from "../src/Treasury.sol";
import {Proposal} from "../src/Proposal.sol";
import {PaymentSplitterFactory} from "../src/PaymentSplitterFactory.sol";
import {MDAOSmartAccountFactory} from "../src/MDAOSmartAccountFactory.sol";

contract Deploy is Script {
    function run() external {
        address deployer = msg.sender;

        // ── Chain ID validation (F-117) ──
        uint256 chainId = block.chainid;
        require(chainId == 56 || chainId == 97, "Unsupported chain ID (56=BSC, 97=BSC Testnet)");
        console.log("Chain ID:", chainId);

        // ── P-256 Precompile check (F-108, F-138) ──
        // RIP-7212 is NOT available on BSC (56/97); fallback to Daimo P256Verifier (pure-Solidity P-256)
        (bool precompileOk, bytes memory precompileData) = address(0x100).staticcall(
            abi.encodePacked(bytes32(0), bytes32(0), bytes32(0), bytes32(0), bytes32(0))
        );
        bool rip7212Available = precompileOk && precompileData.length >= 32;

        address p256Verifier;
        if (rip7212Available) {
            p256Verifier = address(0x100);
            console.log("Using RIP-7212 P-256 precompile at 0x100");
        } else {
            vm.startBroadcast();
            P256Verifier verifier = new P256Verifier();
            vm.stopBroadcast();
            p256Verifier = address(verifier);
            console.log("RIP-7212 not available, deployed P256Verifier at:", address(verifier));
        }

        // ── MDAOToken ──
        vm.startBroadcast();
        MDAOToken token = new MDAOToken(deployer);
        vm.stopBroadcast();
        console.log("MDAOToken:", address(token));

        // ── MDAOPaymaster (needs token address) ──
        vm.startBroadcast();
        MDAOPaymaster paymaster = new MDAOPaymaster(
            vm.envAddress("ENTRY_POINT"),
            address(token),
            vm.envAddress("USDT_ADDRESS"),
            vm.envAddress("TRUSTED_SIGNER")
        );
        vm.stopBroadcast();
        console.log("MDAOPaymaster:", address(paymaster));

        // ── MDAOSmartAccountFactory (v0.6, same EntryPoint as paymaster) ──
        vm.startBroadcast();
        MDAOSmartAccountFactory smartAccountFactory = new MDAOSmartAccountFactory(vm.envAddress("ENTRY_POINT"));
        vm.stopBroadcast();
        console.log("MDAOSmartAccountFactory:", address(smartAccountFactory));

        // ── InsuranceFund (after paymaster, needs paymaster for fee collection) ──
        address insuranceAuditor = vm.envAddress("INSURANCE_AUDITOR_ADDRESS");
        require(insuranceAuditor != address(0), "InsuranceFund auditor not set (INSURANCE_AUDITOR_ADDRESS)");
        require(insuranceAuditor != deployer, "Auditor cannot be deployer");
        vm.startBroadcast();
        address[] memory insuranceAuditors = new address[](1);
        insuranceAuditors[0] = insuranceAuditor;
        InsuranceFund insuranceFund = new InsuranceFund(insuranceAuditors, 1);
        vm.stopBroadcast();
        console.log("InsuranceFund:", address(insuranceFund));

        // ── SocialRecoveryModule (needs mdaoToken + p256Verifier) ──
        vm.startBroadcast();
        SocialRecoveryModule socialRecovery = new SocialRecoveryModule(address(token), p256Verifier);
        vm.stopBroadcast();
        console.log("SocialRecoveryModule:", address(socialRecovery));

        // S-146: Exempt SocialRecoveryModule from burn fee to prevent double-burn on deposits
        vm.startBroadcast();
        token.setExempt(address(socialRecovery), true);
        vm.stopBroadcast();
        console.log("MDAOToken: SocialRecoveryModule exempted from burn fee");

        // ── NicknameRegistry ──
        vm.startBroadcast();
        NicknameRegistry nicknameRegistry = new NicknameRegistry();
        vm.stopBroadcast();
        console.log("NicknameRegistry:", address(nicknameRegistry));

        // ── DeadManSwitch ──
        vm.startBroadcast();
        DeadManSwitch deadManSwitch = new DeadManSwitch();
        vm.stopBroadcast();
        console.log("DeadManSwitch:", address(deadManSwitch));
        // ponytail: DeadManSwitch uses MIN_INACTIVITY=90d constant; add setter if variable needed

        // Wire DeadManSwitch ↔ SocialRecoveryModule (4.3)
        vm.startBroadcast();
        deadManSwitch.setRecoveryCaller(address(socialRecovery));
        socialRecovery.addRecoveryHook(IRecoveryHook(address(deadManSwitch)));
        vm.stopBroadcast();
        console.log("DeadManSwitch: recoveryCaller set, hook registered on SocialRecoveryModule");

        // ── AttestationLedger ──
        vm.startBroadcast();
        AttestationLedger attestationLedger = new AttestationLedger();
        vm.stopBroadcast();
        console.log("AttestationLedger:", address(attestationLedger));
        // ponytail: AttestationLedger needs attester role setup once contract supports it

        // ── RefundVault (needs paymaster address) ──
        vm.startBroadcast();
        RefundVault refundVault = new RefundVault();
        vm.stopBroadcast();
        console.log("RefundVault:", address(refundVault));
        // ponytail: RefundVault needs setPaymaster(address(paymaster)) once contract supports it

        // ── Governance: Treasury → Proposal → PaymentSplitterFactory ──
        // Treasury: admin = deployer (later transferred to timelock), proposalContract set after Proposal deploy
        address treasuryAdmin = vm.envOr("TREASURY_ADMIN", deployer);
        vm.startBroadcast();
        Treasury treasury = new Treasury(treasuryAdmin, address(0));
        vm.stopBroadcast();
        console.log("Treasury:", address(treasury));
        console.log("Treasury admin:", treasuryAdmin);

        // Proposal: needs treasury + voting token (MDAOToken) + admin
        // admin = deployer (later transferred to timelock or gnosis safe)
        address proposalAdmin = vm.envOr("PROPOSAL_ADMIN", deployer);
        vm.startBroadcast();
        Proposal proposal = new Proposal(address(treasury), address(token), proposalAdmin);
        vm.stopBroadcast();
        console.log("Proposal:", address(proposal));

        // Wire circular dependency: Treasury → Proposal (for executeAllocation access control)
        // Grant FINANCE_ROLE on Treasury to Proposal (so Proposal.executeProposal can call Treasury.executeAllocation)
        vm.startBroadcast();
        treasury.grantRole(treasury.FINANCE_ROLE(), address(proposal));
        treasury.setProposalContract(address(proposal));
        vm.stopBroadcast();
        console.log("Treasury: FINANCE_ROLE granted to Proposal, proposalContract set");

        // Grant FINANCE_ROLE to gnosis safe (for off-chain allocation creation)
        address gnosisFinance = vm.envOr("GNOSIS_FINANCE", deployer);
        vm.startBroadcast();
        treasury.grantRole(treasury.FINANCE_ROLE(), gnosisFinance);
        vm.stopBroadcast();
        console.log("Treasury: FINANCE_ROLE granted to:", gnosisFinance);

        // PaymentSplitterFactory
        vm.startBroadcast();
        PaymentSplitterFactory splitterFactory = new PaymentSplitterFactory();
        vm.stopBroadcast();
        console.log("PaymentSplitterFactory:", address(splitterFactory));

        // Wire Treasury → PaymentSplitterFactory (for recipient validation)
        vm.startBroadcast();
        treasury.setSplitterFactory(address(splitterFactory));
        vm.stopBroadcast();
        console.log("Treasury: splitterFactory set to PaymentSplitterFactory");

        // ── SessionKeyModule ──
        vm.startBroadcast();
        SessionKeyModule sessionKeys = new SessionKeyModule();
        vm.stopBroadcast();
        console.log("SessionKeyModule:", address(sessionKeys));

        // ── TimelockController ──
        // F-107: proposers = [gnosisSafe], не [deployer]; admin = address(0)
        address gnosisSafe = vm.envOr("GNOSIS_SAFE", deployer);
        vm.startBroadcast();
        address[] memory proposers = new address[](1);
        proposers[0] = gnosisSafe;
        address[] memory executors = new address[](1);
        executors[0] = gnosisSafe;
        TimelockController timelock = new TimelockController(2 days, proposers, executors, address(0));
        vm.stopBroadcast();
        console.log("TimelockController:", address(timelock));
        console.log("Timelock proposer:", gnosisSafe);

        // Transfer paymaster ownership to timelock (2-step)
        vm.startBroadcast();
        paymaster.transferOwnership(address(timelock));
        vm.stopBroadcast();
        console.log("Paymaster pendingOwner set to TimelockController");

        // Accept ownership through timelock (requires 2-day delay in real deploy)
        // In script, we use vm.warp for demonstration
        bytes memory acceptData = abi.encodeWithSelector(paymaster.acceptOwnership.selector);
        vm.startBroadcast();
        timelock.schedule(address(paymaster), 0, acceptData, bytes32(0), bytes32("accept-paymaster-ownership"), 2 days);
        vm.stopBroadcast();
        // Note: In production, deployer must wait 2 days and call execute manually
        // vm.warp(block.timestamp + 2 days);
        // timelock.execute(address(paymaster), 0, acceptData, bytes32(0), bytes32("accept-paymaster-ownership"));
        console.log("Timelock: scheduled paymaster.acceptOwnership (execute after 2 days)");

        // ── EcdsaVerifier ──
        vm.startBroadcast();
        EcdsaVerifier ecdsaVerifier = new EcdsaVerifier(vm.envAddress("TRUSTED_SIGNER"));
        vm.stopBroadcast();
        console.log("EcdsaVerifier:", address(ecdsaVerifier));

        // ── TrustProviderRegistry ──
        vm.startBroadcast();
        TrustProviderRegistry providerRegistry = new TrustProviderRegistry();
        vm.stopBroadcast();
        console.log("TrustProviderRegistry:", address(providerRegistry));

        // Register default provider
        bytes32 providerId = bytes32(uint256(uint160(vm.envAddress("TRUSTED_SIGNER"))));
        vm.startBroadcast();
        providerRegistry.registerProvider(providerId, address(ecdsaVerifier));
        vm.stopBroadcast();
        console.log("Registered provider:", vm.toString(providerId));

        // Set registry on paymaster (owner is still deployer at this point)
        vm.startBroadcast();
        paymaster.setRegistry(address(providerRegistry));
        vm.stopBroadcast();
        console.log("Paymaster registry set to:", address(providerRegistry));

        // Transfer registry ownership to timelock
        vm.startBroadcast();
        providerRegistry.transferOwnership(address(timelock));
        vm.stopBroadcast();
        console.log("Registry pendingOwner set to TimelockController");

        // Schedule registry.acceptOwnership through timelock
        bytes memory acceptRegistryData = abi.encodeWithSelector(providerRegistry.acceptOwnership.selector);
        vm.startBroadcast();
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
