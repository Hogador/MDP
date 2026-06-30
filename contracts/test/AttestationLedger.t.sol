// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {AttestationLedger} from "../src/AttestationLedger.sol";

contract AttestationLedgerTest is Test {
    AttestationLedger public ledger;
    address owner = address(0xCAFE);
    address alice = address(0x1111);
    address bob = address(0x2222);

    function setUp() public {
        vm.prank(owner);
        ledger = new AttestationLedger();
    }

    function test_Attest() public {
        bytes32 subject = keccak256("user123");
        bytes32 hash = keccak256("recovery:guardian_approved:tx_hash_abc");
        string memory meta = "guardian approval for recovery";

        vm.prank(owner);
        vm.expectEmit(true, true, true, true);
        emit AttestationLedger.AttestationRecorded(owner, subject, hash, block.timestamp, meta);
        ledger.attest(subject, hash, meta);

        assertTrue(ledger.verify(hash));
    }

    function test_VerifyNonExistent() public {
        bytes32 hash = keccak256("nothing");
        assertFalse(ledger.verify(hash));
    }

    function test_VerifyBatch() public {
        bytes32 hash1 = keccak256("event1");
        bytes32 hash2 = keccak256("event2");
        bytes32 hash3 = keccak256("event3");

        vm.prank(owner);
        ledger.attest(keccak256("subject"), hash1, "e1");
        vm.prank(owner);
        ledger.attest(keccak256("subject"), hash2, "e2");

        bytes32[] memory hashes = new bytes32[](3);
        hashes[0] = hash1;
        hashes[1] = hash2;
        hashes[2] = hash3;

        bool[] memory results = ledger.verifyBatch(hashes);
        assertTrue(results[0]);
        assertTrue(results[1]);
        assertFalse(results[2]);
    }

    function test_AttestSameHashTwice() public {
        bytes32 hash = keccak256("unique_event");
        vm.prank(owner);
        ledger.attest(keccak256("subj"), hash, "first");
        vm.prank(owner);
        ledger.attest(keccak256("subj2"), hash, "second");
        assertTrue(ledger.verify(hash));
    }

    // ─── F-050 regression: only owner can attest ─────────────────────

    function test_RevertWhen_NonOwnerAttests() public {
        bytes32 hash = keccak256("unauthorized");
        vm.prank(alice);
        vm.expectRevert();
        ledger.attest(keccak256("subj"), hash, "hack");
    }
}
