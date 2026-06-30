// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {ECDSA} from "openzeppelin-contracts/contracts/utils/cryptography/ECDSA.sol";

/// @title MDAOPay NicknameRegistry — Identity Layer (On-Chain)
/// @notice Deterministic identity registry: nicknameHash = keccak256(signer).
///         This contract is the **on-chain identity layer** — it proves that an Ethereum address
///         was the first to register a given identity hash. It does NOT store human-readable names.
///
/// # Human-Readable Names (Off-Chain)
/// Human-readable nicknames (e.g. `crazy-cherry`) are handled by the **MDAOPay backend**:
///   - REST API: `GET /v1/nickname/{name}` → `0x...`
///   - Storage: PostgreSQL (canonical) + Redis cache (hot path) + CDN edge cache
///   - Constraints: 3-20 chars, `[a-zA-Z0-9_-]`, globally unique
///
/// # Security Model
///
/// ## What this contract IS
/// - A mapping: bytes32(pubkey) → address
/// - Registration requires proof of key ownership via EIP-712 typed signature
/// - The nickname is deterministically derived from the signer's address
///
/// ## What this contract IS NOT
/// - ❌ NOT a human-readable naming system (use backend API for name resolution)
/// - ❌ NOT transferable (nickname = identity = key, keys cannot be transferred)
/// - ❌ NOT expirable (identity does not expire)
/// - ❌ NOT ownable (no admin, no owner, no censorship)
///
/// ## Attack Mitigations
/// - Cross-chain replay: prevented by EIP-712 domain separator (includes chainId)
/// - Frontrunning: prevented by signer == msg.sender check
/// - Squatting: prevented by deterministic derivation from signer's address
/// - Impersonation: prevented by ecrecover proof of key ownership
/// - Signature malleability: prevented by OZ ECDSA.recover
/// - Replay: prevented by nonce per wallet
contract NicknameRegistry {
    error NicknameAlreadyTaken(bytes32 nicknameHash);
    error NicknameNotRegistered(bytes32 nicknameHash);
    error InvalidNonce();
    error InvalidSigner();

    /// @notice EIP-712 struct: Registration(address wallet, uint256 nonce)
    struct Registration {
        address wallet;
        uint256 nonce;
    }

    bytes32 public constant REGISTRATION_TYPEHASH = keccak256("Registration(address wallet,uint256 nonce)");

    /// @dev Cached EIP-712 domain separator (F-031)
    bytes32 private _cachedDomainSeparator;

    mapping(bytes32 => address) public nicknameToAddress;
    mapping(address => bytes32) public addressToNickname;
    mapping(address => uint256) public nonces;

    constructor() {
        _cachedDomainSeparator = _buildDomainSeparator();
    }

    event NicknameRegistered(bytes32 indexed nicknameHash, address indexed wallet);

    function register(bytes calldata signature) external {
        uint256 nonce = nonces[msg.sender];
        bytes32 digest = _hashTypedData(
            keccak256(abi.encode(REGISTRATION_TYPEHASH, Registration({wallet: msg.sender, nonce: nonce})))
        );
        address signer = ECDSA.recover(digest, signature);
        if (signer == address(0)) revert InvalidSigner();
        if (signer != msg.sender) revert InvalidSigner();

        bytes32 nicknameHash = keccak256(abi.encodePacked(signer));
        if (nicknameToAddress[nicknameHash] != address(0)) {
            revert NicknameAlreadyTaken(nicknameHash);
        }

        nonces[msg.sender] = nonce + 1;
        nicknameToAddress[nicknameHash] = signer;
        addressToNickname[signer] = nicknameHash;
        emit NicknameRegistered(nicknameHash, signer);
    }

    function resolve(bytes32 nicknameHash) external view returns (address) {
        address addr = nicknameToAddress[nicknameHash];
        if (addr == address(0)) revert NicknameNotRegistered(nicknameHash);
        return addr;
    }

    /// @dev EIP-712 domain separator cached in constructor (F-031)
    function domainSeparator() public view returns (bytes32) {
        return _cachedDomainSeparator;
    }

    /// @dev Build domain separator once — chainId and address(this) are immutable after deploy
    function _buildDomainSeparator() private view returns (bytes32) {
        return keccak256(abi.encode(
            keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"),
            keccak256("MDAOPay"),
            keccak256("1"),
            block.chainid,
            address(this)
        ));
    }

    function _hashTypedData(bytes32 structHash) internal view returns (bytes32) {
        return keccak256(abi.encodePacked("\x19\x01", _cachedDomainSeparator, structHash));
    }
}
