// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {MDAOToken} from "./MDAOToken.sol";

contract SocialRecoveryModule is Ownable {
    error ErrAlreadyRegistered();
    error ErrAlreadyGuardian();
    error ErrNotGuardian();
    error ErrMaxGuardians();
    error ErrInvalidIdentity();
    error ErrInvalidPublicKey();
    error ErrNoActiveRecovery();
    error ErrRecoveryAlreadyActive();
    error ErrAlreadyApproved();
    error ErrRecoveryVetoed();
    error ErrRecoveryExecuted();
    error ErrTimelockNotPassed();
    error ErrRecoveryExpired();
    error ErrInvalidSignature();
    error ErrAlreadyVetoed();
    error ErrInsufficientApprovals();
    error ErrUnauthorized();
    error ErrCannotRemove();
    error ErrInvalidNewPasskey();
    error ErrGuardianAlreadySet();
    error ErrNoExpiredRecovery();
    error ErrDerParsing();

    /// @notice P-256 verifier address. Defaults to RIP-7212 precompile (0x100).
    ///         Can be overridden via constructor or setP256Verifier() for chains without RIP-7212.
    address public P256_VERIFIER;
    // SHA-256 precompile
    address public constant SHA256_PRECOMPILE = 0x0000000000000000000000000000000000000002;
    address public constant BURN_ADDRESS = 0x000000000000000000000000000000000000dEaD;
    // P-256 field prime
    uint256 public constant P256_P = 0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff;
    uint256 public constant TIMELOCK = 48 hours;
    uint256 public constant EXECUTION_WINDOW = 48 hours;
    uint256 public constant MAX_GUARDIANS = 3;
    uint256 public constant GUARDIAN_THRESHOLD = 2;
    uint256 public constant VETO_THRESHOLD = 2;
    uint256 public constant MIN_GUARDIANS_FOR_RECOVERY = 2;
    uint256 public constant RECOVERY_DEPOSIT = 10_000_000_000_000_000; // 0.01 MDAO (18 decimals)

    IERC20 public immutable mdaoToken;

    struct Guardian {
        bytes32 identityHash;
        bytes32 pubKeyX;
        bytes32 pubKeyY;
        uint256 addedAt;
        bool confirmed;
    }

    struct RecoveryRequest {
        address initiator;
        bytes newPasskeyPubKey;
        uint256 startedAt;
        bool vetoed;
        bool executed;
        uint256 nonce;
    }

    mapping(address wallet => Guardian[MAX_GUARDIANS]) public guardians;
    mapping(address wallet => uint256) public guardianCount;
    mapping(address wallet => mapping(bytes32 identityHash => bool)) public isGuardian;
    mapping(address wallet => bytes32) public ownerPasskeyHash;
    mapping(address wallet => RecoveryRequest) public pendingRecovery;
    mapping(address wallet => mapping(uint256 nonce => mapping(bytes32 guardianIdentityHash => bool))) public recoveryApprovals;
    mapping(address wallet => mapping(uint256 nonce => mapping(bytes32 guardianIdentityHash => bool))) public recoveryVetoes;
    mapping(address wallet => mapping(uint256 nonce => uint256)) public approvalCount;
    mapping(address wallet => mapping(uint256 nonce => uint256)) public vetoCount;
    mapping(address wallet => uint256) public recoveryDeposit;

    event WalletRegistered(address indexed wallet, bytes32 passkeyHash);
    event GuardianAdded(address indexed wallet, bytes32 indexed identityHash, uint256 index);
    event GuardianConfirmed(address indexed wallet, bytes32 indexed identityHash);
    event GuardianRemoved(address indexed wallet, bytes32 indexed identityHash, uint256 index);
    event RecoveryInitiated(address indexed wallet, bytes32 indexed oldPasskeyHash, bytes32 indexed newPasskeyHash, address initiator, uint256 deadline, uint256 nonce);
    event ApprovalSubmitted(address indexed wallet, address indexed guardian, uint256 nonce, uint256 approvals);
    event VetoSubmitted(address indexed wallet, address indexed guardian, uint256 nonce, uint256 vetoes);
    event RecoveryExecutedEv(address indexed wallet, bytes32 indexed newPasskeyHash);
    event RecoveryCleanedUp(address indexed wallet, uint256 depositBurned);
    event DepositBurned(address indexed wallet, uint256 amount);
    event P256VerifierUpdated(address indexed oldVerifier, address indexed newVerifier);

    constructor(address _mdaoToken, address _p256Verifier) Ownable(msg.sender) {
        mdaoToken = IERC20(_mdaoToken);
        require(_p256Verifier != address(0), "Invalid P-256 verifier");
        P256_VERIFIER = _p256Verifier;
    }

    /// @notice Update the P-256 verifier address (e.g., when deploying MockP256 for testnet).
    function setP256Verifier(address _p256Verifier) external onlyOwner {
        require(_p256Verifier != address(0), "Invalid verifier");
        address old = P256_VERIFIER;
        P256_VERIFIER = _p256Verifier;
        emit P256VerifierUpdated(old, _p256Verifier);
    }

    // ──────────────────────────────────────────────
    //  Wallet Registration
    // ──────────────────────────────────────────────

    function registerWallet(bytes32 passkeyPubKeyX, bytes32 passkeyPubKeyY) external {
        if (ownerPasskeyHash[msg.sender] != bytes32(0)) revert ErrAlreadyRegistered();
        if (passkeyPubKeyX == bytes32(0) || passkeyPubKeyY == bytes32(0)) revert ErrInvalidPublicKey();
        bytes32 hash = keccak256(abi.encodePacked(passkeyPubKeyX, passkeyPubKeyY));
        ownerPasskeyHash[msg.sender] = hash;
        emit WalletRegistered(msg.sender, hash);
    }

    // ──────────────────────────────────────────────
    //  Guardian Management
    // ──────────────────────────────────────────────

    modifier onlyWalletOwner(address wallet) {
        if (msg.sender != wallet) revert ErrUnauthorized();
        _;
    }

    modifier onlyWalletOrGuardian(address wallet) {
        if (msg.sender == wallet) {
            _;
            return;
        }
        bytes32 identityHash = keccak256(abi.encodePacked(msg.sender));
        if (!isGuardian[wallet][identityHash]) revert ErrUnauthorized();
        Guardian memory g = _findGuardian(wallet, identityHash);
        if (g.addedAt == 0 || !g.confirmed) revert ErrUnauthorized();
        _;
    }

    function addGuardian(
        address wallet,
        bytes32 identityHash,
        bytes32 pubKeyX,
        bytes32 pubKeyY
    ) external onlyWalletOwner(wallet) {
        if (isGuardian[wallet][identityHash]) revert ErrAlreadyGuardian();
        if (guardianCount[wallet] >= MAX_GUARDIANS) revert ErrMaxGuardians();
        if (identityHash == bytes32(0)) revert ErrInvalidIdentity();
        // P-256 coordinate validation: within field range and not point at infinity
        if (pubKeyX >= bytes32(P256_P) || pubKeyY >= bytes32(P256_P)) revert ErrInvalidPublicKey();
        if (pubKeyX == bytes32(0) && pubKeyY == bytes32(0)) revert ErrInvalidPublicKey();

        uint256 idx = guardianCount[wallet];
        guardians[wallet][idx] = Guardian(identityHash, pubKeyX, pubKeyY, block.timestamp, false);
        guardianCount[wallet] = idx + 1;
        isGuardian[wallet][identityHash] = true;

        emit GuardianAdded(wallet, identityHash, idx);
    }

    function confirmGuardian(address wallet) external {
        bytes32 identityHash = keccak256(abi.encodePacked(msg.sender));
        if (!isGuardian[wallet][identityHash]) revert ErrNotGuardian();
        uint256 count = guardianCount[wallet];
        for (uint256 i = 0; i < count; i++) {
            if (guardians[wallet][i].identityHash == identityHash) {
                if (guardians[wallet][i].confirmed) revert ErrGuardianAlreadySet();
                guardians[wallet][i].confirmed = true;
                emit GuardianConfirmed(wallet, identityHash);
                return;
            }
        }
    }

    function removeGuardian(address wallet, bytes32 identityHash) external onlyWalletOwner(wallet) {
        if (!isGuardian[wallet][identityHash]) revert ErrNotGuardian();
        if (guardianCount[wallet] - 1 < MIN_GUARDIANS_FOR_RECOVERY) revert ErrCannotRemove();

        uint256 count = guardianCount[wallet];
        Guardian[MAX_GUARDIANS] storage gs = guardians[wallet];
        for (uint256 i = 0; i < count; i++) {
            if (gs[i].identityHash == identityHash) {
                uint256 lastIdx = count - 1;
                if (i < lastIdx) {
                    gs[i] = gs[lastIdx];
                }
                delete gs[lastIdx];
                guardianCount[wallet] = lastIdx;
                isGuardian[wallet][identityHash] = false;
                emit GuardianRemoved(wallet, identityHash, i);
                return;
            }
        }
    }

    function getGuardians(address wallet) external view returns (Guardian[] memory) {
        uint256 count = guardianCount[wallet];
        Guardian[] memory result = new Guardian[](count);
        for (uint256 i = 0; i < count; i++) {
            result[i] = guardians[wallet][i];
        }
        return result;
    }

    // ──────────────────────────────────────────────
    //  Recovery Flow
    // ──────────────────────────────────────────────

    function initiateRecovery(address wallet, bytes calldata newPasskeyPubKey) external {
        RecoveryRequest storage req = pendingRecovery[wallet];
        if (req.startedAt != 0 && !req.executed && !req.vetoed) revert ErrRecoveryAlreadyActive();
        if (newPasskeyPubKey.length != 64) revert ErrInvalidPublicKey();
        if (guardianCount[wallet] < MIN_GUARDIANS_FOR_RECOVERY) revert ErrInsufficientApprovals();

        // Track actual deposit received (accounts for token burn fees)
        uint256 balBefore = mdaoToken.balanceOf(address(this));
        mdaoToken.transferFrom(msg.sender, address(this), RECOVERY_DEPOSIT);
        uint256 actualDeposit = mdaoToken.balanceOf(address(this)) - balBefore;

        bytes32 oldKeyHash = ownerPasskeyHash[wallet];
        bytes32 newKeyHash = keccak256(newPasskeyPubKey);
        if (newKeyHash == oldKeyHash) revert ErrInvalidNewPasskey();

        uint256 newNonce = req.nonce + 1;
        req.initiator = msg.sender;
        req.newPasskeyPubKey = newPasskeyPubKey;
        req.startedAt = block.timestamp;
        req.vetoed = false;
        req.executed = false;
        req.nonce = newNonce;
        recoveryDeposit[wallet] = actualDeposit;

        emit RecoveryInitiated(wallet, oldKeyHash, newKeyHash, msg.sender, block.timestamp + TIMELOCK, newNonce);
    }

    function approveRecovery(
        address wallet,
        bytes32 guardianIdentityHash,
        bytes calldata authenticatorData,
        bytes calldata clientDataJSON,
        bytes calldata p256Signature
    ) external {
        RecoveryRequest storage req = pendingRecovery[wallet];
        uint256 nonce = req.nonce;
        if (req.startedAt == 0) revert ErrNoActiveRecovery();
        if (req.vetoed) revert ErrRecoveryVetoed();
        if (req.executed) revert ErrRecoveryExecuted();
        if (recoveryApprovals[wallet][nonce][guardianIdentityHash]) revert ErrAlreadyApproved();

        Guardian memory g = _findGuardian(wallet, guardianIdentityHash);
        if (g.addedAt == 0 || !g.confirmed) revert ErrNotGuardian();

        if (!_verifyWebAuthn(authenticatorData, clientDataJSON, p256Signature, g.pubKeyX, g.pubKeyY)) {
            revert ErrInvalidSignature();
        }

        recoveryApprovals[wallet][nonce][guardianIdentityHash] = true;
        approvalCount[wallet][nonce]++;

        emit ApprovalSubmitted(wallet, msg.sender, nonce, approvalCount[wallet][nonce]);
    }

    function vetoRecovery(
        address wallet,
        bytes32 guardianIdentityHash,
        bytes calldata authenticatorData,
        bytes calldata clientDataJSON,
        bytes calldata p256Signature
    ) external {
        RecoveryRequest storage req = pendingRecovery[wallet];
        if (req.startedAt == 0) revert ErrNoActiveRecovery();
        if (req.executed) revert ErrRecoveryExecuted();
        if (req.vetoed) revert ErrRecoveryVetoed();
        if (recoveryApprovals[wallet][req.nonce][guardianIdentityHash]) revert ErrAlreadyApproved();
        if (recoveryVetoes[wallet][req.nonce][guardianIdentityHash]) revert ErrAlreadyVetoed();

        Guardian memory g = _findGuardian(wallet, guardianIdentityHash);
        if (g.addedAt == 0 || !g.confirmed) revert ErrNotGuardian();

        if (!_verifyWebAuthn(authenticatorData, clientDataJSON, p256Signature, g.pubKeyX, g.pubKeyY)) {
            revert ErrInvalidSignature();
        }

        recoveryVetoes[wallet][req.nonce][guardianIdentityHash] = true;
        uint256 currentVetoes = vetoCount[wallet][req.nonce] + 1;
        vetoCount[wallet][req.nonce] = currentVetoes;

        if (currentVetoes >= VETO_THRESHOLD) {
            req.vetoed = true;
            // Burn deposit when recovery is vetoed
            uint256 deposit = recoveryDeposit[wallet];
            if (deposit > 0) {
                recoveryDeposit[wallet] = 0;
                MDAOToken(address(mdaoToken)).burn(deposit);
                emit DepositBurned(wallet, deposit);
            }
        }

        emit VetoSubmitted(wallet, msg.sender, req.nonce, currentVetoes);
    }

    function executeRecovery(address wallet) external {
        RecoveryRequest storage req = pendingRecovery[wallet];
        uint256 nonce = req.nonce;
        if (req.startedAt == 0) revert ErrNoActiveRecovery();
        if (req.vetoed) revert ErrRecoveryVetoed();
        if (req.executed) revert ErrRecoveryExecuted();
        if (approvalCount[wallet][nonce] < GUARDIAN_THRESHOLD) revert ErrInsufficientApprovals();
        if (block.timestamp < req.startedAt + TIMELOCK) revert ErrTimelockNotPassed();
        if (block.timestamp > req.startedAt + TIMELOCK + EXECUTION_WINDOW) revert ErrRecoveryExpired();

        bytes32 newKeyHash = keccak256(req.newPasskeyPubKey);
        ownerPasskeyHash[wallet] = newKeyHash;
        req.executed = true;

        uint256 deposit = recoveryDeposit[wallet];
        if (deposit > 0) {
            recoveryDeposit[wallet] = 0;
            mdaoToken.transfer(req.initiator, deposit);
        }

        emit RecoveryExecutedEv(wallet, newKeyHash);
    }

    function cleanupExpiredRecovery(address wallet) external {
        RecoveryRequest storage req = pendingRecovery[wallet];
        if (req.startedAt == 0) revert ErrNoActiveRecovery();
        if (req.executed) revert ErrRecoveryExecuted();
        if (block.timestamp <= req.startedAt + TIMELOCK + EXECUTION_WINDOW) revert ErrNoExpiredRecovery();

        uint256 deposit = recoveryDeposit[wallet];
        address initiator = req.initiator; // capture before delete
        recoveryDeposit[wallet] = 0;
        delete pendingRecovery[wallet];

        // Return deposit to initiator (F-049, ponytail: anti-spam via 0.01 MDAO cost + 96h wait)
        if (deposit > 0 && initiator != address(0)) {
            mdaoToken.transfer(initiator, deposit);
        }

        emit RecoveryCleanedUp(wallet, deposit);
    }

    function getRecoveryRequest(address wallet) external view returns (
        bytes memory newPasskeyPubKey,
        uint256 startedAt,
        uint256 approvals,
        uint256 vetoes,
        bool vetoed,
        bool executed,
        uint256 deadline,
        uint256 nonce
    ) {
        RecoveryRequest storage req = pendingRecovery[wallet];
        return (
            req.newPasskeyPubKey,
            req.startedAt,
            approvalCount[wallet][req.nonce],
            vetoCount[wallet][req.nonce],
            req.vetoed,
            req.executed,
            req.startedAt + TIMELOCK,
            req.nonce
        );
    }

    // ──────────────────────────────────────────────
    //  Internal
    // ──────────────────────────────────────────────

    function _findGuardian(address wallet, bytes32 identityHash) internal view returns (Guardian memory) {
        uint256 count = guardianCount[wallet];
        for (uint256 i = 0; i < count; i++) {
            if (guardians[wallet][i].identityHash == identityHash) {
                return guardians[wallet][i];
            }
        }
        return Guardian(bytes32(0), bytes32(0), bytes32(0), 0, false);
    }

    /// @notice Convert ASN.1 DER-encoded ECDSA P-256 signature to raw r||s (64 bytes).
    /// @param der The DER-encoded signature (70-72 bytes).
    /// @return r The R component as bytes32.
    /// @return s The S component as bytes32.
    /// @dev Parses: 0x30 [totalLen] 0x02 [rLen] [r] 0x02 [sLen] [s].
    ///      Handles leading 0x00 when high bit is set (rLen/sLen = 33).
    function derToRS(bytes memory der) internal pure returns (bytes32 r, bytes32 s) {
        if (der.length < 8 || der.length > 74) revert ErrDerParsing();

        uint256 offset;
        unchecked {
            // SEQUENCE tag
            if (der[offset] != 0x30) revert ErrDerParsing();
            offset++;

            // Total length
            uint256 totalLen = uint8(der[offset]);
            if (totalLen != der.length - 2) revert ErrDerParsing();
            offset++;

            // R INTEGER tag
            if (der[offset] != 0x02) revert ErrDerParsing();
            offset++;

            // R length
            uint256 rLen = uint8(der[offset]);
            if (rLen < 1 || rLen > 33) revert ErrDerParsing();
            offset++;

            // Strip leading 0x00 if high bit set
            if (rLen == 33) {
                if (der[offset] != 0x00) revert ErrDerParsing();
                offset++;
                rLen = 32;
            }
            if (offset + rLen > der.length) revert ErrDerParsing();

            // Read R (right-aligned in bytes32)
            assembly {
                let ptr := add(add(der, 32), offset)
                r := mload(ptr)
                if lt(rLen, 32) { r := shr(mul(8, sub(32, rLen)), r) }
            }
            offset += rLen;

            // S INTEGER tag
            if (der[offset] != 0x02) revert ErrDerParsing();
            offset++;

            // S length
            uint256 sLen = uint8(der[offset]);
            if (sLen < 1 || sLen > 33) revert ErrDerParsing();
            offset++;

            // Strip leading 0x00 if high bit set
            if (sLen == 33) {
                if (der[offset] != 0x00) revert ErrDerParsing();
                offset++;
                sLen = 32;
            }
            if (offset + sLen > der.length) revert ErrDerParsing();

            // Read S
            assembly {
                let ptr := add(add(der, 32), offset)
                s := mload(ptr)
                if lt(sLen, 32) { s := shr(mul(8, sub(32, sLen)), s) }
            }
        }
    }

    /// @notice Verify a WebAuthn P-256 signature.
    /// @dev Verifies the standard WebAuthn signed message: SHA-256(authenticatorData || SHA-256(clientDataJSON)).
    ///      Uses SHA-256 precompile (0x02) and P-256 verifier (configurable, default RIP-7212 0x100).
    ///      No EIP-191 prefix — matches WebAuthn standard (FIDO2).
    /// @param authenticatorData The authenticator data from the WebAuthn ceremony.
    /// @param clientDataJSON    The client data JSON from the WebAuthn ceremony.
    /// @param signature         The signature: 64-byte raw r||s or 70-72 byte DER-encoded.
    /// @param pubKeyX           The guardian's P-256 public key X coordinate.
    /// @param pubKeyY           The guardian's P-256 public key Y coordinate.
    function _verifyWebAuthn(
        bytes calldata authenticatorData,
        bytes calldata clientDataJSON,
        bytes calldata signature,
        bytes32 pubKeyX,
        bytes32 pubKeyY
    ) internal view returns (bool) {
        if (authenticatorData.length == 0) return false;
        if (clientDataJSON.length == 0) return false;
        if (signature.length != 64 && (signature.length < 70 || signature.length > 74)) return false;

        // Step 1: clientDataHash = SHA-256(clientDataJSON)
        (bool success, bytes memory result) = SHA256_PRECOMPILE.staticcall(clientDataJSON);
        if (!success || result.length < 32) return false;
        bytes32 clientDataHash = bytes32(result);

        // Step 2: signedData = authenticatorData || clientDataHash
        uint256 signedDataLen = authenticatorData.length + 32;
        bytes memory signedData = new bytes(signedDataLen);
        assembly {
            calldatacopy(add(signedData, 32), authenticatorData.offset, authenticatorData.length)
            mstore(add(add(signedData, 32), authenticatorData.length), clientDataHash)
        }

        // Step 3: messageHash = SHA-256(signedData)
        (success, result) = SHA256_PRECOMPILE.staticcall(signedData);
        if (!success || result.length < 32) return false;
        bytes32 messageHash = bytes32(result);

        // Step 4: Extract r, s — support both raw 64-byte and DER-encoded signatures
        bytes32 r;
        bytes32 s;
        if (signature.length == 64) {
            assembly {
                r := calldataload(signature.offset)
                s := calldataload(add(signature.offset, 32))
            }
        } else {
            (r, s) = derToRS(bytes(signature));
        }

        (success, result) = P256_VERIFIER.staticcall(
            abi.encodePacked(messageHash, r, s, pubKeyX, pubKeyY)
        );

        if (!success || result.length < 32) return false;
        return abi.decode(result, (uint256)) == 1;
    }
}
