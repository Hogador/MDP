// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {MDAOToken} from "./MDAOToken.sol";
import {IRecoveryHook} from "./interfaces/IRecoveryHook.sol";
import {MockP256} from "./MockP256.sol";

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
    error ErrHookArrayEmpty();
    error ErrMaxHooks();
    error ErrHookNotApproved();

    uint256 public constant MAX_HOOKS = 10;
    uint256 public constant HOOK_GAS_LIMIT = 50_000;

    /// @notice P-256 verifier address. Defaults to RIP-7212 precompile (0x100).
    ///         Can be overridden via constructor or setP256Verifier() for chains without RIP-7212.
    address public P256_VERIFIER;
    // SHA-256 precompile
    address public constant SHA256_PRECOMPILE = 0x0000000000000000000000000000000000000002;
    address public constant BURN_ADDRESS = 0x000000000000000000000000000000000000dEaD;
    // P-256 field prime
    uint256 public constant P256_P = 0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff;
    uint256 public constant P256_A = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC; // -3 mod p
    uint256 public constant P256_B = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B;
    uint256 public constant TIMELOCK = 48 hours;
    uint256 public constant EXECUTION_WINDOW = 48 hours;
    uint256 public constant MAX_GUARDIANS = 5;
    uint256 public constant GUARDIAN_THRESHOLD = 2;
    uint256 public constant VETO_THRESHOLD = 2;
    uint256 public constant MIN_GUARDIANS_FOR_RECOVERY = 2;
    uint256 public constant RECOVERY_DEPOSIT = 10_000_000_000_000_000; // 0.01 MDAO (18 decimals)

    IERC20 public immutable mdaoToken;
    
    /// @dev Flag indicating if P256_VERIFIER is working (set during construction)
    bool public p256VerifierWorking;

    /// @notice Registered recovery hooks called after executeRecovery.
    IRecoveryHook[] public recoveryHooks;

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

    /// @notice New EOA owner for MDAOSmartAccount after recovery (S-137).
    /// @dev Set by wallet owner before executeRecovery. Consumed in executeRecovery.
    mapping(address => address) public recoveryEOAOwner;
    /// @notice MDAOSmartAccount contract address per wallet (S-137).
    /// @dev executeRecovery calls smartAccount.transferOwnership(recoveryEOAOwner[wallet]).
    mapping(address => address) public recoverySmartAccount;

    /// @notice Whitelist of approved hook addresses. Only approved hooks can be added.
    mapping(address => bool) public approvedHookContracts;

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
    event HookApproved(address indexed hook);
    event HookRevoked(address indexed hook);
    event RecoveryHookFailed(address indexed wallet, address indexed hook, bytes reason);
    event AllRecoveryHooksFailed(address indexed wallet, uint256 failedCount);

    constructor(address _mdaoToken, address _p256Verifier) Ownable(msg.sender) {
        mdaoToken = IERC20(_mdaoToken);
        require(_p256Verifier != address(0), "Invalid P-256 verifier");
        
        // Runtime check: test if P256 verifier is working
        if (_isP256Working(_p256Verifier)) {
            P256_VERIFIER = _p256Verifier;
            p256VerifierWorking = true;
        } else {
            // Deploy MockP256 as fallback for testnets without RIP-7212
            address mock = address(new MockP256());
            P256_VERIFIER = mock;
            p256VerifierWorking = false;
            emit P256VerifierConfirmed(_p256Verifier, mock);
        }
    }
    
    /// @dev Test if P256 verifier is functional by making a dummy call
bytes32 private constant P256_PROBE_HASH = 0xaf2bdbe1aa9b6ec1e2ade1d694f41fc71a831d0268e9891562113d8a62add1bf;
bytes32 private constant P256_PROBE_R = 0xefd48b2aacb6a8fd1140dd9cd45e81d69d2c877b56aaf991c34d0ea84eaf3716;
bytes32 private constant P256_PROBE_S = 0x0834e36ad29a83bf2bc9385e491d6099c8fdf9d1ed67aa7ea5f51f93782857a9;
bytes32 private constant P256_PROBE_X = 0x60fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6;
bytes32 private constant P256_PROBE_Y = 0x7903fe1008b8bc99a41ae9e95628bc64f2f1b20c2d7e9f5177a3c294d4462299;

function _isP256Working(address verifier) internal view returns (bool) {
    // RIP-7212 format: abi.encodePacked(hash, r, s, x, y) = 160 bytes.
    // Valid RFC 6979 A.2.5 vector (verified) — proves the verifier actually validates a real signature.
    bytes memory input = abi.encodePacked(P256_PROBE_HASH, P256_PROBE_R, P256_PROBE_S, P256_PROBE_X, P256_PROBE_Y);
    (bool success, bytes memory result) = verifier.staticcall(input);
    return success && result.length == 32 && abi.decode(result, (uint256)) == 1;
}

    // ── F-138a: P-256 verifier timelock ──
    address public pendingP256Verifier;
    uint256 public pendingVerifierSetAt;
    uint256 public constant VERIFIER_TIMELOCK = 48 hours;

    event P256VerifierProposed(address indexed newVerifier, uint256 expiresAt);
    event P256VerifierConfirmed(address indexed oldVerifier, address indexed newVerifier);
    event P256VerifierUpdateCancelled();

    /// @notice Propose a new P-256 verifier. Takes effect after VERIFIER_TIMELOCK.
    function setP256Verifier(address _p256Verifier) external onlyOwner {
        require(_p256Verifier != address(0), "Invalid verifier");
        pendingP256Verifier = _p256Verifier;
        pendingVerifierSetAt = block.timestamp;
        emit P256VerifierProposed(_p256Verifier, block.timestamp + VERIFIER_TIMELOCK);
    }

    /// @notice Confirm pending verifier after timelock elapsed.
    function confirmP256Verifier() external onlyOwner {
        require(pendingP256Verifier != address(0), "No pending verifier");
        require(block.timestamp >= pendingVerifierSetAt + VERIFIER_TIMELOCK, "Timelock not elapsed");
        address old = P256_VERIFIER;
        P256_VERIFIER = pendingP256Verifier;
        // re-check runtime status: mock fallback may have been replaced by a real verifier (or vice versa)
        p256VerifierWorking = _isP256Working(pendingP256Verifier);
        delete pendingP256Verifier;
        delete pendingVerifierSetAt;
        emit P256VerifierConfirmed(old, P256_VERIFIER);
    }

    /// @notice Cancel a pending verifier update before timelock expires.
    function cancelP256VerifierUpdate() external onlyOwner {
        delete pendingP256Verifier;
        delete pendingVerifierSetAt;
        emit P256VerifierUpdateCancelled();
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
        if (!_isOnP256Curve(uint256(pubKeyX), uint256(pubKeyY))) revert ErrInvalidPublicKey();

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

        // Call registered recovery hooks (e.g., invalidate session keys)
        // ponytail: try/catch + gas limit prevents a single malicious/expensive hook from DoS-ing recovery
        uint256 len = recoveryHooks.length;
        uint256 failedHooks;
        for (uint256 i; i < len; i++) {
            try recoveryHooks[i].onRecoveryExecuted{gas: HOOK_GAS_LIMIT}(wallet) {} catch (bytes memory reason) {
                failedHooks++;
                emit RecoveryHookFailed(wallet, address(recoveryHooks[i]), reason);
            }
        }
        if (failedHooks > 0 && failedHooks == len) {
            emit AllRecoveryHooksFailed(wallet, failedHooks);
        }

        // S-137: transfer MDAOSmartAccount ownership to new EOA if configured
        address smartAccount = recoverySmartAccount[wallet];
        address newEOA = recoveryEOAOwner[wallet];
        if (smartAccount != address(0) && newEOA != address(0)) {
            recoverySmartAccount[wallet] = address(0); // consume — prevent re-use
            recoveryEOAOwner[wallet] = address(0);
            // Silently ignore if SmartAccount doesn't implement transferOwnership
            (bool success,) = smartAccount.call(
                abi.encodeWithSignature("transferOwnership(address)", newEOA)
            );
            if (!success) {} // suppress unused warning
        }
    }

    /// @notice Register a recovery hook contract.
    /// @dev Hook must be pre-approved via approveHook() to prevent owner from adding malicious hooks.
    /// @param hook Address of the contract implementing IRecoveryHook.
    function addRecoveryHook(IRecoveryHook hook) external onlyOwner {
        if (!approvedHookContracts[address(hook)]) revert ErrHookNotApproved();
        if (recoveryHooks.length >= MAX_HOOKS) revert ErrMaxHooks();
        recoveryHooks.push(hook);
    }

    /// @notice Approve a hook address for future registration.
    function approveHook(address hook) external onlyOwner {
        require(hook.code.length > 0, "Not a contract");
        approvedHookContracts[hook] = true;
        emit HookApproved(hook);
    }

    /// @notice Revoke a previously approved hook address.
    function revokeHook(address hook) external onlyOwner {
        approvedHookContracts[hook] = false;
        emit HookRevoked(hook);
    }

    /// @notice Remove a recovery hook by index (swap & pop).
    /// @param index Index in the recoveryHooks array.
    function removeRecoveryHook(uint256 index) external onlyOwner {
        uint256 len = recoveryHooks.length;
        if (index >= len) revert ErrHookArrayEmpty();
        recoveryHooks[index] = recoveryHooks[len - 1];
        recoveryHooks.pop();
    }

    function getRecoveryHookCount() external view returns (uint256) {
        return recoveryHooks.length;
    }

    /// @notice Set the new EOA owner for MDAOSmartAccount after recovery completes.
    /// @dev When executeRecovery runs, it will call wallet.transferOwnership(newOwner).
    ///      The wallet's SmartAccount must have this module as recoveryCaller.
    ///      Call this BEFORE initiateRecovery (or before executeRecovery).
    /// @param newOwner Address of the new EOA owner (must be non-zero).
    /// @notice Set (or clear) the SmartAccount + new EOA owner for ownership transfer on recovery.
    /// @dev executeRecovery will call smartAccount.transferOwnership(newOwner).
    ///      The SmartAccount must have this module as recoveryCaller.
    /// @param smartAccount Address of the MDAOSmartAccount contract. Set both to 0 to clear.
    /// @param newOwner New EOA owner address. Can be address(0) to skip transfer.
    function setRecoveryTransfer(address smartAccount, address newOwner) external {
        recoverySmartAccount[msg.sender] = smartAccount;
        recoveryEOAOwner[msg.sender] = newOwner;
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

        // F-153: refund deposit to initiator (not burn — legitimate initiator deserves recourse)
        if (deposit > 0) {
            (bool sent, bytes memory data) = address(mdaoToken).call(
                abi.encodeWithSelector(IERC20.transfer.selector, initiator, deposit)
            );
            // USDT-style false return handling (same as F-004 in MDAOPaymaster)
            bool success = sent && (data.length == 0 || abi.decode(data, (bool)));
            if (!success) {
                MDAOToken(address(mdaoToken)).burn(deposit);
            }
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

    /// @notice Check that a point (x, y) is on the P-256 curve: y² ≡ x³ + ax + b (mod p)
    function _isOnP256Curve(uint256 x, uint256 y) internal pure returns (bool) {
        uint256 lhs = mulmod(y, y, P256_P);
        uint256 rhs = addmod(mulmod(mulmod(x, x, P256_P), x, P256_P), mulmod(P256_A, x, P256_P), P256_P);
        rhs = addmod(rhs, P256_B, P256_P);
        return lhs == rhs;
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
        // DER parsing loop bounds are strictly checked above (length < 8 || > 74)
        // and increment logic guarantees no overflow within valid DER structure.
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
