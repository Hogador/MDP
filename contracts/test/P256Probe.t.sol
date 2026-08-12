// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import "forge-std/Test.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {P256Verifier} from "../src/helpers/P256Verifier.sol";

contract P256ProbeTest is Test {
    SocialRecoveryModule m;
    P256Verifier p256Verifier;

    bytes32 private constant P256_PROBE_HASH = 0xaf2bdbe1aa9b6ec1e2ade1d694f41fc71a831d0268e9891562113d8a62add1bf;
    bytes32 private constant P256_PROBE_R = 0xefd48b2aacb6a8fd1140dd9cd45e81d69d2c877b56aaf991c34d0ea84eaf3716;
    bytes32 private constant P256_PROBE_S = 0x0834e36ad29a83bf2bc9385e491d6099c8fdf9d1ed67aa7ea5f51f93782857a9;
    bytes32 private constant P256_PROBE_X = 0x60fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6;
    bytes32 private constant P256_PROBE_Y = 0x7903fe1008b8bc99a41ae9e95628bc64f2f1b20c2d7e9f5177a3c294d4462299;

    function setUp() public {
        p256Verifier = new P256Verifier();
    }

    function test_Probe_ValidVerifierIsUsed() public {
        m = new SocialRecoveryModule(address(0xdead), address(p256Verifier));
        assertTrue(m.p256VerifierWorking());
        assertEq(address(m.P256_VERIFIER()), address(p256Verifier));
    }

    function test_Probe_DeadVerifierFallsBackToMock() public {
        m = new SocialRecoveryModule(address(0xdead), address(0x0000000000000000000000000000000000000001));
        assertFalse(m.p256VerifierWorking());
        assertNotEq(address(m.P256_VERIFIER()), address(0));
    }

    function test_P256Verifier_ValidVector() public {
        bytes memory input = abi.encodePacked(
            P256_PROBE_HASH,
            P256_PROBE_R,
            P256_PROBE_S,
            P256_PROBE_X,
            P256_PROBE_Y
        );
        (bool success, bytes memory result) = address(p256Verifier).staticcall(input);
        assertTrue(success);
        assertEq(abi.decode(result, (uint256)), 1);

        // Zero input
        bytes memory zeroInput = new bytes(160);
        (success, result) = address(p256Verifier).staticcall(zeroInput);
        assertTrue(success);
        assertEq(abi.decode(result, (uint256)), 0);
    }
}