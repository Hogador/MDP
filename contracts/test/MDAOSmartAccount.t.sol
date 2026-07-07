// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Test} from "forge-std/Test.sol";
import {MDAOSmartAccount} from "../src/MDAOSmartAccount.sol";
import {SocialRecoveryModule} from "../src/SocialRecoveryModule.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
import {MockP256} from "./mocks/MockP256.sol";
import {IEntryPoint} from "account-abstraction/interfaces/IEntryPoint.sol";
import {PackedUserOperation} from "account-abstraction/interfaces/PackedUserOperation.sol";
import {ISenderCreator} from "account-abstraction/interfaces/ISenderCreator.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";

contract MDAOSmartAccountTest is Test {
    MDAOSmartAccount public account;
    IEntryPoint public ep;
    address public owner;
    uint256 public ownerKey;

    event OwnershipTransferred(address indexed previousOwner, address indexed newOwner);

    function setUp() public {
        // Deploy a minimal EntryPoint so it can be called
        ep = IEntryPoint(address(new MockEntryPoint()));
        (owner, ownerKey) = makeAddrAndKey("smartAccountOwner");

        vm.prank(owner);
        account = new MDAOSmartAccount(ep, owner);
    }

    // ── 1. Constructor ─────────────────────────────────────────────

    function test_ConstructorSetsOwnerAndEntryPoint() public {
        assertEq(address(account.entryPoint()), address(ep));
        assertEq(account.owner(), owner);
    }

    function test_ConstructorRevertsZeroEntryPoint() public {
        vm.expectRevert(MDAOSmartAccount.ErrCannotBeZero.selector);
        new MDAOSmartAccount(IEntryPoint(address(0)), owner);
    }

    function test_ConstructorRevertsZeroOwner() public {
        vm.expectRevert(MDAOSmartAccount.ErrCannotBeZero.selector);
        new MDAOSmartAccount(ep, address(0));
    }

    // ── 2. Signature validation ────────────────────────────────────

    function test_ValidateUserOpValidSignature() public {
        PackedUserOperation memory userOp = _dummyUserOp();
        bytes32 userOpHash = _hashUserOp(userOp);

        (uint8 v, bytes32 r, bytes32 s) = vm.sign(ownerKey, userOpHash);
        userOp.signature = abi.encodePacked(r, s, v);

        vm.prank(address(ep));
        uint256 result = account.validateUserOp(userOp, userOpHash, 0);
        assertEq(result, 0); // SIG_VALIDATION_SUCCESS
    }

    function test_ValidateUserOpInvalidSignature() public {
        PackedUserOperation memory userOp = _dummyUserOp();
        bytes32 userOpHash = _hashUserOp(userOp);

        // Sign with a wrong key
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(ownerKey + 1, userOpHash);
        userOp.signature = abi.encodePacked(r, s, v);

        vm.prank(address(ep));
        uint256 result = account.validateUserOp(userOp, userOpHash, 0);
        assertEq(result, 1); // SIG_VALIDATION_FAILED
    }

    // ── 3. transferOwnership ───────────────────────────────────────

    function test_TransferOwnership() public {
        address newOwner = makeAddr("newOwner");

        vm.expectEmit(true, true, true, true);
        emit OwnershipTransferred(owner, newOwner);

        vm.prank(owner);
        account.transferOwnership(newOwner);

        assertEq(account.owner(), newOwner);
    }

    function test_TransferOwnershipRevertsZero() public {
        vm.prank(owner);
        vm.expectRevert(MDAOSmartAccount.ErrCannotBeZero.selector);
        account.transferOwnership(address(0));
    }

    function test_TransferOwnershipRevertsNotOwner() public {
        vm.prank(makeAddr("stranger"));
        vm.expectRevert(MDAOSmartAccount.ErrNotOwner.selector);
        account.transferOwnership(makeAddr("newOwner"));
    }

    // ── 3b. Recovery caller ─────────────────────────────────────────

    function test_SetRecoveryCaller() public {
        address recoveryModule = makeAddr("recoveryModule");
        vm.prank(owner);
        account.setRecoveryCaller(recoveryModule);
        assertEq(account.recoveryCaller(), recoveryModule);
    }

    function test_RevertWhen_SetRecoveryCallerNotOwner() public {
        vm.prank(makeAddr("stranger"));
        vm.expectRevert(MDAOSmartAccount.ErrUnauthorized.selector);
        account.setRecoveryCaller(makeAddr("recoveryModule"));
    }

    function test_RevertWhen_SetRecoveryCallerZero() public {
        vm.prank(owner);
        vm.expectRevert(MDAOSmartAccount.ErrZeroAddress.selector);
        account.setRecoveryCaller(address(0));
    }

    function test_TransferOwnershipByRecoveryCaller() public {
        address recoveryModule = makeAddr("recoveryModule");
        address newOwner = makeAddr("newOwnerAfterRecovery");

        vm.prank(owner);
        account.setRecoveryCaller(recoveryModule);

        vm.prank(recoveryModule);
        account.transferOwnership(newOwner);

        assertEq(account.owner(), newOwner);
    }

    function test_EntryPointCanStillTransferOwnership() public {
        address newOwner = makeAddr("newOwner");

        vm.prank(address(ep));
        account.transferOwnership(newOwner);

        assertEq(account.owner(), newOwner);
    }

    // ── 4. execute (reverts if not EntryPoint) ─────────────────────

    function test_ExecuteRevertsNotEntryPoint() public {
        vm.prank(makeAddr("stranger"));
        vm.expectRevert();
        account.execute(makeAddr("to"), 0, "");
    }

    // ── 5. addDeposit / withdrawDepositTo ───────────────────────────

    function test_AddDeposit() public {
        // addDeposit calls entryPoint().depositTo{value: msg.value}(address(this))
        // MockEntryPoint.depositTo is a no-op, so this should just not revert
        // Anyone can call addDeposit (no access control — EntryPoint allows anyone to deposit for any account)
        account.addDeposit{value: 0.5 ether}();
    }

    function test_WithdrawDepositToRevertsNotOwner() public {
        vm.prank(makeAddr("stranger"));
        vm.expectRevert(MDAOSmartAccount.ErrNotOwner.selector);
        account.withdrawDepositTo(payable(makeAddr("to")), 0);
    }

    // ── Helpers ────────────────────────────────────────────────────

    function _dummyUserOp() internal view returns (PackedUserOperation memory) {
        return PackedUserOperation({
            sender: address(account),
            nonce: 0,
            initCode: new bytes(0),
            callData: new bytes(0),
            accountGasLimits: bytes32(uint256(50000) << 128 | uint256(21000)),
            preVerificationGas: 21000,
            gasFees: bytes32(uint256(1 ether) << 128 | uint256(1 gwei)),
            paymasterAndData: new bytes(0),
            signature: new bytes(65)
        });
    }

    function _hashUserOp(PackedUserOperation memory userOp) internal view returns (bytes32) {
        return keccak256(
            abi.encode(
                userOp.sender,
                userOp.nonce,
                keccak256(userOp.initCode),
                keccak256(userOp.callData),
                userOp.accountGasLimits,
                userOp.preVerificationGas,
                userOp.gasFees,
                keccak256(userOp.paymasterAndData)
            )
        );
    }

    // ── S-137: SocialRecoveryModule → SmartAccount ownership transfer ──

    function test_RecoveryTransfersSmartAccountOwnership() public {
        // Deploy dependencies
        MDAOToken mdao = new MDAOToken(makeAddr("tokenAdmin"));
        MockP256 mockP256 = new MockP256();
        SocialRecoveryModule recoveryModule = new SocialRecoveryModule(address(mdao), address(mockP256));

        address guardian1 = makeAddr("guardian1");
        address guardian2 = makeAddr("guardian2");
        (address newOwner, uint256 newOwnerKey) = makeAddrAndKey("newOwnerAfterRecovery");

        // Register wallet (current EOA owner sets up SocialRecoveryModule)
        vm.startPrank(owner);
        recoveryModule.registerWallet(
            bytes32(uint256(1)), // pubKeyX placeholder
            bytes32(uint256(2))  // pubKeyY placeholder
        );

        // Add guardians
        bytes32 id1 = keccak256(abi.encodePacked(guardian1));
        bytes32 id2 = keccak256(abi.encodePacked(guardian2));
        recoveryModule.addGuardian(owner, id1, bytes32(uint256(3)), bytes32(uint256(4)));
        recoveryModule.addGuardian(owner, id2, bytes32(uint256(5)), bytes32(uint256(6)));

        // Confirm guardians
        vm.stopPrank();

        vm.prank(guardian1);
        recoveryModule.confirmGuardian(owner);

        vm.prank(guardian2);
        recoveryModule.confirmGuardian(owner);

        // Set recoveryCaller on SmartAccount to point to SocialRecoveryModule
        vm.prank(owner);
        account.setRecoveryCaller(address(recoveryModule));

        // Configure SmartAccount + new owner for recovery transfer
        vm.prank(owner);
        recoveryModule.setRecoveryTransfer(address(account), newOwner);

        // Fund owner with MDAO for deposit (transfer from tokenAdmin)
        vm.prank(makeAddr("tokenAdmin"));
        mdao.transfer(owner, 1 ether);
        vm.prank(owner);
        mdao.approve(address(recoveryModule), 0.01 ether);

        // Initiate recovery
        bytes memory newPubKey = abi.encodePacked(bytes32(uint256(7)), bytes32(uint256(8)));
        vm.prank(owner);
        recoveryModule.initiateRecovery(owner, newPubKey);

        // Approve recovery (2 guardians = threshold)
        bytes memory validSig = abi.encode(bytes32(uint256(0xdead)), bytes32(uint256(0xbeef)));
        bytes memory authData = hex"000000000000000000000000000000000000000000000000000000000000000000000000000000";
        bytes memory clientData = bytes('{"type":"webauthn.get"}');

        (,,,,,,, uint256 nonce) = recoveryModule.getRecoveryRequest(owner);

        vm.prank(guardian1);
        recoveryModule.approveRecovery(owner, id1, authData, clientData, validSig);

        vm.prank(guardian2);
        recoveryModule.approveRecovery(owner, id2, authData, clientData, validSig);

        // Warp past timelock
        vm.warp(block.timestamp + 48 hours + 1);

        // Execute recovery
        vm.prank(owner);
        recoveryModule.executeRecovery(owner);

        // Verify: SmartAccount owner changed
        assertEq(account.owner(), newOwner, "SmartAccount owner should be the new EOA");

        // Verify: recovery mappings consumed
        assertEq(recoveryModule.recoveryEOAOwner(owner), address(0), "recoveryEOAOwner should be consumed");
        assertEq(recoveryModule.recoverySmartAccount(owner), address(0), "recoverySmartAccount should be consumed");

        // ── S-130.2: execute UserOp with new key after recovery ──

        // Old owner signature should FAIL
        bytes32 userOpHash = _hashUserOp(_dummyUserOp());
        (uint8 vOld, bytes32 rOld, bytes32 sOld) = vm.sign(ownerKey, userOpHash);
        bytes memory oldSig = abi.encodePacked(rOld, sOld, vOld);

        PackedUserOperation memory userOpOld = _dummyUserOp();
        userOpOld.signature = oldSig;
        vm.prank(address(ep));
        uint256 resultOld = account.validateUserOp(userOpOld, userOpHash, 0);
        assertEq(resultOld, 1, "Old owner sig should FAIL after recovery");

        // New owner signature should PASS
        (uint8 vNew, bytes32 rNew, bytes32 sNew) = vm.sign(newOwnerKey, userOpHash);
        bytes memory newSig = abi.encodePacked(rNew, sNew, vNew);

        PackedUserOperation memory userOpNew = _dummyUserOp();
        userOpNew.signature = newSig;
        vm.prank(address(ep));
        uint256 resultNew = account.validateUserOp(userOpNew, userOpHash, 0);
        assertEq(resultNew, 0, "New owner sig should PASS after recovery");
    }
}

/// @notice Minimal EntryPoint mock implementing IEntryPoint + parents (IStakeManager, INonceManager).
contract MockEntryPoint is IEntryPoint {
    function getUserOpHash(PackedUserOperation calldata) external pure returns (bytes32) {
        return keccak256("test");
    }
    function getCurrentUserOpHash() external pure returns (bytes32) { return keccak256("test"); }
    function simulateValidation(PackedUserOperation calldata) external {}
    function simulateHandleOp(PackedUserOperation calldata, address, bytes calldata) external {}
    function handleOps(PackedUserOperation[] calldata, address payable) external {}
    function handleOp(PackedUserOperation calldata, address payable) external {}
    function handleAggregatedOps(UserOpsPerAggregator[] calldata, address payable) external {}
    function getSenderAddress(bytes calldata) external pure { revert SenderAddressResult(address(0)); }
    function delegateAndRevert(address, bytes calldata) external pure { revert DelegateAndRevert(false, ""); }
    function senderCreator() external view returns (ISenderCreator) { return ISenderCreator(address(0)); }
    // IStakeManager
    function getDepositInfo(address) external pure returns (DepositInfo memory) { return DepositInfo(0, false, 0, 0, 0); }
    function balanceOf(address) external pure returns (uint256) { return 0; }
    function depositTo(address) external payable {}
    function addStake(uint32) external payable {}
    function unlockStake() external {}
    function withdrawStake(address payable) external {}
    function withdrawTo(address payable, uint256) external {}
    // INonceManager
    function getNonce(address, uint192) external pure returns (uint256) { return 0; }
    function incrementNonce(uint192) external {}
}
