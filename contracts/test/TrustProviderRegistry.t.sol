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
}
