// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {TrustProviderRegistry} from "../src/TrustProviderRegistry.sol";
import {EcdsaVerifier} from "../src/EcdsaVerifier.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";

contract TrustProviderRegistryTest is Test {
    using ECDSA for bytes32;

    TrustProviderRegistry registry;
    EcdsaVerifier verifier;

    uint256 signerKey = 0xBEEF;
    address signer = vm.addr(signerKey);
    bytes32 providerId = bytes32(uint256(uint160(signer)));
    bytes32 intentHash = keccak256("test intent");

    function setUp() public {
        verifier = new EcdsaVerifier(signer);
        registry = new TrustProviderRegistry();
    }

    function test_RegisterProvider() public {
        registry.registerProvider(providerId, address(verifier));
        (address v, TrustProviderRegistry.ProviderStatus s) = registry.getProvider(providerId);
        assertEq(v, address(verifier));
        assertEq(uint8(s), uint8(TrustProviderRegistry.ProviderStatus.ACTIVE));
    }

    function test_VerifyWithActiveProvider() public {
        registry.registerProvider(providerId, address(verifier));
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(signerKey, intentHash);
        bytes memory proof = abi.encodePacked(r, s, v);
        assertTrue(registry.verify(providerId, intentHash, proof));
    }

    function test_RevertWhen_VerifyWithUnregisteredProvider() public {
        bytes memory proof = abi.encodePacked(bytes32(0), bytes32(0), uint8(0));
        vm.expectRevert(abi.encodeWithSelector(TrustProviderRegistry.ProviderNotActive.selector));
        registry.verify(providerId, intentHash, proof);
    }

    function test_RevertWhen_VerifyWithDeprecatedProvider() public {
        registry.registerProvider(providerId, address(verifier));
        registry.setProviderStatus(providerId, TrustProviderRegistry.ProviderStatus.DEPRECATED);
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(signerKey, intentHash);
        bytes memory proof = abi.encodePacked(r, s, v);
        vm.expectRevert(abi.encodeWithSelector(TrustProviderRegistry.ProviderNotActive.selector));
        registry.verify(providerId, intentHash, proof);
    }

    function test_RevertWhen_RegisterTwice() public {
        registry.registerProvider(providerId, address(verifier));
        vm.expectRevert(abi.encodeWithSelector(TrustProviderRegistry.ProviderAlreadyRegistered.selector));
        registry.registerProvider(providerId, address(verifier));
    }

    function test_RevertWhen_RegisterZeroAddress() public {
        vm.expectRevert(abi.encodeWithSelector(TrustProviderRegistry.InvalidVerifier.selector));
        registry.registerProvider(providerId, address(0));
    }

    function test_RevertWhen_NotOwnerRegisters() public {
        vm.prank(address(0xdead));
        vm.expectRevert();
        registry.registerProvider(providerId, address(verifier));
    }

    function test_AcceptOwnership() public {
        address newOwner = address(0xDEAD);
        registry.transferOwnership(newOwner);

        vm.prank(newOwner);
        registry.acceptOwnership();

        assertEq(registry.owner(), newOwner);
    }

    function test_RevertWhen_NotPendingOwnerAccepts() public {
        registry.transferOwnership(address(0xDEAD));

        vm.prank(address(0xBEEF));
        vm.expectRevert(abi.encodeWithSelector(TrustProviderRegistry.NotPendingOwner.selector));
        registry.acceptOwnership();
    }

    function test_SetProviderStatus() public {
        registry.registerProvider(providerId, address(verifier));
        registry.setProviderStatus(providerId, TrustProviderRegistry.ProviderStatus.DEPRECATED);
        (address v, TrustProviderRegistry.ProviderStatus s) = registry.getProvider(providerId);
        assertEq(uint8(s), uint8(TrustProviderRegistry.ProviderStatus.DEPRECATED));
    }

    function test_RevertWhen_SetStatusForUnregistered() public {
        vm.expectRevert(abi.encodeWithSelector(TrustProviderRegistry.ProviderNotRegistered.selector));
        registry.setProviderStatus(providerId, TrustProviderRegistry.ProviderStatus.SUNSET);
    }

    function test_EcdsaVerifier() public {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(signerKey, intentHash);
        bytes memory proof = abi.encodePacked(r, s, v);
        assertTrue(verifier.verify(intentHash, proof));
    }

    function test_EcdsaVerifierWrongSigner() public {
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(0xDEAD, intentHash);
        bytes memory proof = abi.encodePacked(r, s, v);
        assertFalse(verifier.verify(intentHash, proof));
    }

    // --- Proxy detection tests (SC-005 advisory) ---
    // If registry is ever replaced with a proxy, staticcall assumptions break.
    // These tests fail-fast if that happens.

    function test_Registry_IsNotTransparentProxy() public {
        // EIP-1967 implementation slot
        bytes32 IMPL_SLOT = 0x360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc;
        bytes32 implAddr = vm.load(address(registry), IMPL_SLOT);
        assertEq(implAddr, bytes32(0), "Registry has EIP-1967 impl slot - is a proxy");
    }

    function test_Registry_IsNotUUPSProxy() public {
        // EIP-1822 implementation slot (ERC1967 alternate)
        bytes32 UUPS_SLOT = 0xa3f0ad74e5423aebfd80d3ef4346578335a9a72aeaee59ff6cb3582b35133d50;
        bytes32 implAddr = vm.load(address(registry), UUPS_SLOT);
        assertEq(implAddr, bytes32(0), "Registry has UUPS impl slot - is a proxy");
    }

    function test_Registry_IsNotBeaconProxy() public {
        // EIP-1967 beacon slot
        bytes32 BEACON_SLOT = 0xa3f0ad74e5423aebfd80d3ef4346578335a9a72aeaee59ff6cb3582b35133d50;
        bytes32 beaconAddr = vm.load(address(registry), BEACON_SLOT);
        assertEq(beaconAddr, bytes32(0), "Registry has beacon slot - is a proxy");
    }
}
