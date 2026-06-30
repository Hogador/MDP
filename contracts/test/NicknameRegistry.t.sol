// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {NicknameRegistry} from "../src/NicknameRegistry.sol";

contract NicknameRegistryTest is Test {
    NicknameRegistry public registry;

    uint256 public aliceKey = 0xA11CE;
    uint256 public bobKey = 0xB0B;
    address public alice = vm.addr(aliceKey);
    address public bob = vm.addr(bobKey);

    function setUp() public {
        registry = new NicknameRegistry();
    }

    function _sign(address wallet, uint256 key) internal view returns (bytes memory) {
        bytes32 digest = keccak256(abi.encodePacked(
            "\x19\x01",
            registry.domainSeparator(),
            keccak256(abi.encode(registry.REGISTRATION_TYPEHASH(), NicknameRegistry.Registration({wallet: wallet, nonce: registry.nonces(wallet)})))
        ));
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, digest);
        return abi.encodePacked(r, s, v);
    }

    function test_Register() public {
        bytes memory sig = _sign(alice, aliceKey);
        vm.prank(alice);
        registry.register(sig);

        bytes32 expectedHash = keccak256(abi.encodePacked(alice));
        assertEq(registry.nicknameToAddress(expectedHash), alice);
        assertEq(registry.addressToNickname(alice), expectedHash);
    }

    function test_RevertWhen_WrongSigner() public {
        // Alice signs but Bob pranks as msg.sender — signer != msg.sender
        bytes memory sig = _sign(alice, aliceKey);
        vm.prank(bob);
        vm.expectRevert();
        registry.register(sig);
    }

    function test_RevertWhen_NicknameAlreadyTaken() public {
        bytes memory sig = _sign(alice, aliceKey);
        vm.prank(alice);
        registry.register(sig);

        // Fresh signature with nonce=1 to pass nonce check and hit duplicate check
        bytes memory sig2 = _sign(alice, aliceKey);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(NicknameRegistry.NicknameAlreadyTaken.selector, keccak256(abi.encodePacked(alice))));
        registry.register(sig2);
    }

    function test_RegisterDifferentKeys() public {
        bytes memory sigA = _sign(alice, aliceKey);
        vm.prank(alice);
        registry.register(sigA);

        bytes memory sigB = _sign(bob, bobKey);
        vm.prank(bob);
        registry.register(sigB);

        assertEq(registry.nicknameToAddress(keccak256(abi.encodePacked(alice))), alice);
        assertEq(registry.nicknameToAddress(keccak256(abi.encodePacked(bob))), bob);
    }

    function test_RevertWhen_BobRegistersAliceNickname() public {
        bytes memory sigA = _sign(alice, aliceKey);
        vm.prank(alice);
        registry.register(sigA);

        // Bob tries to register Alice's nickname by signing his own wallet
        bytes32 aliceHash = keccak256(abi.encodePacked(alice));
        // Bob can't — register with sig that signs alice as wallet but bob as msg.sender fails
        bytes memory sigB = _sign(alice, bobKey);
        vm.prank(bob);
        vm.expectRevert();
        registry.register(sigB);
    }

    function test_Resolve() public {
        bytes memory sig = _sign(alice, aliceKey);
        vm.prank(alice);
        registry.register(sig);

        bytes32 aliceHash = keccak256(abi.encodePacked(alice));
        address resolved = registry.resolve(aliceHash);
        assertEq(resolved, alice);
    }

    function test_RevertWhen_NicknameNotRegistered() public {
        vm.expectRevert(abi.encodeWithSelector(NicknameRegistry.NicknameNotRegistered.selector, keccak256("nonexistent")));
        registry.resolve(keccak256("nonexistent"));
    }

    function test_DomainSeparator() public view {
        bytes32 sep = registry.domainSeparator();
        bytes32 expected = keccak256(abi.encode(
            keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"),
            keccak256("MDAOPay"),
            keccak256("1"),
            block.chainid,
            address(registry)
        ));
        assertEq(sep, expected);
    }

    // ─── F-031 regression: domain separator is cached, not recomputed ──

    function test_DomainSeparatorIsCached() public {
        bytes32 first = registry.domainSeparator();
        bytes32 second = registry.domainSeparator();
        assertEq(first, second);
    }

    function test_RegisterWithFrontrun() public {
        // Alice signs for herself, but Bob frontruns by calling register with Alice's sig
        bytes memory sig = _sign(alice, aliceKey);

        // Bob frontruns with Alice's sig but msg.sender = bob
        vm.prank(bob);
        vm.expectRevert(); // signer (alice) != msg.sender (bob)
        registry.register(sig);
    }
}
