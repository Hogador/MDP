// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {SessionKeyModule} from "../src/SessionKeyModule.sol";

contract SessionKeyModuleTest is Test {
    SessionKeyModule public module;

    address public owner = makeAddr("owner");
    address public alice = makeAddr("alice");
    address public dApp = makeAddr("dApp");
    address public dApp2 = makeAddr("dApp2");

    bytes32 constant PERM_PAY = keccak256("payments.send");
    bytes32 constant PERM_READ = keccak256("address.read");
    bytes32 constant PERM_NFT = keccak256("nft.transfer");

    function setUp() public {
        vm.prank(owner);
        module = new SessionKeyModule();
    }

    // ── Helpers ──────────────────────────────────────────────────

    function _createKey(
        address _dapp,
        bytes32[] memory perms,
        uint256 limit,
        uint256 expiry,
        uint8 riskTier
    ) internal returns (bytes32 keyId) {
        vm.prank(owner);
        keyId = module.createSessionKey(_dapp, expiry, perms, limit, riskTier);
    }

    // ── 1. Create session key — happy path ────────────────────────

    function test_CreateSessionKey() public {
        bytes32[] memory perms = new bytes32[](2);
        perms[0] = PERM_PAY;
        perms[1] = PERM_READ;

        uint256 expiry = block.timestamp + 1 days;
        vm.prank(owner);
        bytes32 keyId = module.createSessionKey(dApp, expiry, perms, 5 ether, 0);

        (
            address o, address d, , uint256 eu, bytes32[] memory p, uint256 lim, uint256 spent, bool revoked,,,,
        ) = module.getSessionKey(keyId);

        assertEq(o, owner);
        assertEq(d, dApp);
        assertEq(eu, expiry);
        assertEq(p.length, 2);
        assertEq(p[0], PERM_PAY);
        assertEq(p[1], PERM_READ);
        assertEq(lim, 5 ether);
        assertEq(spent, 0);
        assertFalse(revoked);
    }

    // ── 2. Caller becomes owner of created key ───────────────────
    // Note: contract has no owner-gate on create; caller is the key owner.

    function test_CallerIsKeyOwner() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        vm.prank(alice);
        bytes32 keyId = module.createSessionKey(dApp, block.timestamp + 1 hours, perms, 0, 0);

        (address keyOwner,,,,,,,,,,,) = module.getSessionKey(keyId);
        assertEq(keyOwner, alice);
    }

    // ── 3. Revoke session key — happy path ───────────────────────

    function test_RevokeSessionKey() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 1 ether, block.timestamp + 1 days, 0);

        vm.prank(owner);
        module.revokeSessionKey(keyId);

        (, , , , , , , bool revoked,,,,) = module.getSessionKey(keyId);
        assertTrue(revoked);
    }

    // ── 4. Revert on revoke nonexistent key ──────────────────────

    function test_RevertWhen_RevokeNonexistentKey() public {
        bytes32 fakeId = keccak256("fake");
        vm.prank(owner);
        vm.expectRevert(SessionKeyModule.InvalidKey.selector);
        module.revokeSessionKey(fakeId);
    }

    // ── 5. Revert on revoke by non-owner ────────────────────────

    function test_RevertWhen_RevokeByNonOwner() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;
        bytes32 keyId = _createKey(dApp, perms, 1 ether, block.timestamp + 1 days, 0);

        vm.prank(alice);
        vm.expectRevert(SessionKeyModule.Unauthorized.selector);
        module.revokeSessionKey(keyId);
    }

    // ── 6. Validate session key — valid ──────────────────────────

    function test_ValidateSessionKey_Valid() public {
        bytes32[] memory perms = new bytes32[](2);
        perms[0] = PERM_PAY;
        perms[1] = PERM_NFT;

        bytes32 keyId = _createKey(dApp, perms, 10 ether, block.timestamp + 1 hours, 0);

        module.validateSessionKey(keyId, PERM_PAY, 1 ether);
        module.validateSessionKey(keyId, PERM_NFT, 5 ether);
    }

    // ── 7. Revert on expired key ────────────────────────────────

    function test_RevertWhen_SessionKeyExpired() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 1 ether, block.timestamp + 1 hours, 0);

        vm.warp(block.timestamp + 1 hours + 1);

        vm.expectRevert(SessionKeyModule.SessionKeyExpired.selector);
        module.validateSessionKey(keyId, PERM_PAY, 0.1 ether);
    }

    // ── 8. Revert on revoked key ────────────────────────────────

    function test_RevertWhen_SessionKeyRevoked() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 1 ether, block.timestamp + 1 days, 0);

        vm.prank(owner);
        module.revokeSessionKey(keyId);

        vm.expectRevert(SessionKeyModule.SessionKeyRevoked.selector);
        module.validateSessionKey(keyId, PERM_PAY, 0.1 ether);
    }

    // ── 9. Revert on permission denied ──────────────────────────

    function test_RevertWhen_PermissionDenied() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_READ;

        bytes32 keyId = _createKey(dApp, perms, 0, block.timestamp + 1 hours, 0);

        vm.expectRevert(SessionKeyModule.PermissionDenied.selector);
        module.validateSessionKey(keyId, PERM_PAY, 0);
    }

    // ── 10. Revert on spending limit exceeded ───────────────────

    function test_RevertWhen_SpendingLimitExceeded() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 1 ether, block.timestamp + 1 days, 0);

        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        module.validateSessionKey(keyId, PERM_PAY, 1.1 ether);
    }

    // ── 11. Use session key — increments spent ──────────────────

    function test_UseSessionKey() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 2 ether, block.timestamp + 1 days, 0);

        module.useSessionKey(keyId, PERM_PAY, 0.3 ether);
        (, , , , , , uint256 spent,,,,,) = module.getSessionKey(keyId);
        assertEq(spent, 0.3 ether);

        module.useSessionKey(keyId, PERM_PAY, 0.2 ether);
        (, , , , , , spent,,,,,) = module.getSessionKey(keyId);
        assertEq(spent, 0.5 ether);
    }

    // ── 12. Use session key — revert on limit ───────────────────

    function test_RevertWhen_UseExceedsLimit() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 1 ether, block.timestamp + 1 days, 0);

        module.useSessionKey(keyId, PERM_PAY, 0.8 ether);

        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        module.useSessionKey(keyId, PERM_PAY, 0.3 ether);
    }

    // ── 13. Unlimited spending (limit = 0) ─────────────────────

    function test_UnlimitedSpending() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 0, block.timestamp + 1 days, 0);

        module.useSessionKey(keyId, PERM_PAY, 1000 ether);
        (, , , , , , uint256 spent,,,,,) = module.getSessionKey(keyId);
        assertEq(spent, 1000 ether);
    }

    // ── 14. Multiple keys for different dApps ──────────────────

    function test_MultipleKeys() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        uint256 expiry = block.timestamp + 1 days;
        bytes32 key1 = _createKey(dApp, perms, 1 ether, expiry, 0);
        bytes32 key2 = _createKey(dApp2, perms, 5 ether, expiry, 0);

        (, address d1, , , , uint256 l1,,,,,,) = module.getSessionKey(key1);
        (, address d2, , , , uint256 l2,,,,,,) = module.getSessionKey(key2);

        assertEq(d1, dApp);
        assertEq(d2, dApp2);
        assertEq(l1, 1 ether);
        assertEq(l2, 5 ether);
    }

    // ── 15. Same-dapp keys in same block have unique keyIds ────

    function test_NoKeyIdCollisionSameDappSameBlock() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        uint256 expiry = block.timestamp + 1 days;
        bytes32 key1 = _createKey(dApp, perms, 1 ether, expiry, 0);
        bytes32 key2 = _createKey(dApp, perms, 2 ether, expiry, 0);

        assertTrue(key1 != key2, "keyId collision");

        (, address d1, , , , uint256 l1,,,,,,) = module.getSessionKey(key1);
        (, address d2, , , , uint256 l2,,,,,,) = module.getSessionKey(key2);

        assertEq(d1, dApp);
        assertEq(d2, dApp);
        assertEq(l1, 1 ether);
        assertEq(l2, 2 ether);
    }

    // ── 16. Revert on invalid key (zero owner) ─────────────────

    function test_RevertWhen_InvalidKey() public {
        bytes32 fakeId = keccak256("nonexistent");

        vm.expectRevert(SessionKeyModule.InvalidKey.selector);
        module.validateSessionKey(fakeId, PERM_PAY, 0);
    }

    // ── 17. Revert on create with past expiry ──────────────────

    function test_RevertWhen_PastExpiry() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        vm.expectRevert(SessionKeyModule.InvalidKey.selector);
        module.createSessionKey(dApp, block.timestamp - 1, perms, 0, 0);
    }

    // ── 18. Revert on create with zero dapp ────────────────────

    function test_RevertWhen_ZeroDapp() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        vm.expectRevert(SessionKeyModule.InvalidKey.selector);
        module.createSessionKey(address(0), block.timestamp + 1 hours, perms, 0, 0);
    }

    // ── 19. LOW risk tier gets +20% limit after 10+ successes ─────

    function test_RiskTierLowGetsHigherLimit() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        // LOW risk (0), spending limit = 100
        bytes32 keyId = _createKey(dApp, perms, 100, block.timestamp + 1 days, 0);

        // Use 11 times to build successCount to 11
        for (uint256 i = 0; i < 11; i++) {
            module.useSessionKey(keyId, PERM_PAY, 1);
        }

        // successCount = 11 (> 10), LOW → dynamicLimit = 100 * 120/100 = 120
        // spent = 11, so max additional = 120 - 11 = 109 → 109 passes
        module.validateSessionKey(keyId, PERM_PAY, 109);

        // 11 + 110 = 121 > 120 → revert
        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        module.validateSessionKey(keyId, PERM_PAY, 110);
    }

    // ── 20. Spike detection resets successCount ──────────────────

    function test_SpikeResetsSuccessCount() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 100 ether, block.timestamp + 1 days, 0);

        // Use normally: amounts 10, 20, 15
        module.useSessionKey(keyId, PERM_PAY, 10);
        module.useSessionKey(keyId, PERM_PAY, 20);
        module.useSessionKey(keyId, PERM_PAY, 15);

        // successCount should be 3
        (, , , , , , , , , uint256 successCount,,) = module.getSessionKey(keyId);
        assertEq(successCount, 3);

        // Spike: 40 > 15 * 2 = 30
        module.useSessionKey(keyId, PERM_PAY, 40);

        // successCount reset to 0
        (, , , , , , , , , successCount,,) = module.getSessionKey(keyId);
        assertEq(successCount, 0);
    }

    // ── 22. Permission whitelist (F-118) — empty whitelist allows all ─

    function test_PermissionWhitelist_EmptyAllowsAll() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;
        _createKey(dApp, perms, 1 ether, block.timestamp + 1 days, 0);
        // whitelist empty (allowedPermissionCount == 0), create should pass
    }

    // ── 23. Permission whitelist (F-118) — non-empty whitelist rejects unlisted ─

    function test_PermissionWhitelist_RevertWhenNotAllowed() public {
        vm.prank(owner);
        module.setPermissionAllowed(PERM_PAY, true);

        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_READ; // not in whitelist

        vm.prank(owner);
        vm.expectRevert(SessionKeyModule.PermissionNotAllowed.selector);
        module.createSessionKey(dApp, block.timestamp + 1 days, perms, 1 ether, 0);
    }

    // ── 24. Permission whitelist (F-118) — allowed permissions succeed ─

    function test_PermissionWhitelist_AllowedSucceeds() public {
        vm.prank(owner);
        module.setPermissionAllowed(PERM_PAY, true);
        vm.prank(owner);
        module.setPermissionAllowed(PERM_READ, true);

        bytes32[] memory perms = new bytes32[](2);
        perms[0] = PERM_PAY;
        perms[1] = PERM_READ;

        vm.prank(owner);
        bytes32 keyId = module.createSessionKey(dApp, block.timestamp + 1 days, perms, 1 ether, 0);
        (address o,,,,,,,,,,,) = module.getSessionKey(keyId);
        assertEq(o, owner);
    }

    // ── 25. Permission whitelist (F-118) — owner can add/remove ─────

    function test_PermissionWhitelist_OwnerCanToggle() public {
        assertFalse(module.allowedPermissions(PERM_PAY));

        vm.prank(owner);
        module.setPermissionAllowed(PERM_PAY, true);
        assertTrue(module.allowedPermissions(PERM_PAY));

        vm.prank(owner);
        module.setPermissionAllowed(PERM_PAY, false);
        assertFalse(module.allowedPermissions(PERM_PAY));
    }

    // ── 26. Permission whitelist (F-118) — non-owner can't modify ───

    function test_PermissionWhitelist_NonOwnerCannotModify() public {
        vm.prank(alice);
        vm.expectRevert(SessionKeyModule.Unauthorized.selector);
        module.setPermissionAllowed(PERM_PAY, true);
    }

    // ── 21. Time-decay increments effective successCount daily ───

    function test_TimeDecayIncrementsDaily() public {
        bytes32[] memory perms = new bytes32[](1);
        perms[0] = PERM_PAY;

        bytes32 keyId = _createKey(dApp, perms, 100, block.timestamp + 30 days, 0); // LOW

        // Use 10 times → successCount = 10 (no bonus yet, need > 10)
        for (uint256 i = 0; i < 10; i++) {
            module.useSessionKey(keyId, PERM_PAY, 1);
        }
        // spent = 10, dynamicLimit = 100 (no bonus), 10 + 91 = 101 > 100 → revert
        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        module.validateSessionKey(keyId, PERM_PAY, 91);

        // Warp 25 hours → +1 time-decay day → effective successCount = 11
        vm.warp(block.timestamp + 25 hours);

        // 11 > 10 → dynamicLimit = 120
        // spent(10) + 110 = 120 <= 120 → passes
        module.validateSessionKey(keyId, PERM_PAY, 110);

        // spent(10) + 111 = 121 > 120 → revert
        vm.expectRevert(SessionKeyModule.SpendingLimitExceeded.selector);
        module.validateSessionKey(keyId, PERM_PAY, 111);
    }
}
