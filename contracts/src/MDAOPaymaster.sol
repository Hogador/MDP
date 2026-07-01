// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";
import {Pausable} from "@openzeppelin/contracts/utils/Pausable.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
import {MessageHashUtils} from "@openzeppelin/contracts/utils/cryptography/MessageHashUtils.sol";

/// @title Minimal registry interface for TrustProviderRegistry
interface IRegistry {
    function verify(bytes32 providerId, bytes32 intentHash, bytes calldata proof) external view returns (bool);
}

struct UserOperation {
    address sender;
    uint256 nonce;
    bytes initCode;
    bytes callData;
    uint256 callGasLimit;
    uint256 verificationGasLimit;
    uint256 preVerificationGas;
    uint256 maxFeePerGas;
    uint256 maxPriorityFeePerGas;
    bytes paymasterAndData;
    bytes signature;
}

interface IPaymasterV06 {
    enum PostOpMode {
        opSucceeded,
        opReverted,
        postOpReverted
    }

    function validatePaymasterUserOp(
        UserOperation calldata userOp,
        bytes32 userOpHash,
        uint256 maxCost,
        bytes calldata paymasterData
    ) external returns (bytes memory context, uint256 validationData);

    function postOp(
        PostOpMode mode,
        bytes calldata context,
        uint256 actualGasCost,
        uint256 actualUserOpFeePerGas
    ) external;
}

interface IEntryPointView {
    function balanceOf(address account) external view returns (uint256);
}

/// @title MDAOPaymaster
/// @notice ERC-4337 paymaster that accepts ERC-20 token payments for gas.
/// @dev Supports EIP-2612 permit for gasless approval, owner-settable price oracle,
///      and automatic sender blocking after repeated payment failures.
///
/// **Price == 0 behavior:**
/// When `tokenPrice[token]` is zero the on-chain oracle check is skipped. In this mode
/// `maxTokenAmount` in the paymaster data MUST also be zero — any non-zero value reverts
/// with `AmountTooHigh`. This allows the paymaster to operate without price feeds by
/// relying solely on off-chain quote verification.
contract MDAOPaymaster is IPaymasterV06, Ownable, Pausable, ReentrancyGuard, EIP712("MDAOPay", "1") {
    using SafeERC20 for IERC20;
    using ECDSA for bytes32;

    error Unauthorized();
    error InvalidToken();
    error InsufficientBalance();
    error InsufficientDeposit();
    error InsufficientAllowance();
    error PermitFailed();
    error TransferFailed();
    error QuoteExpired();
    error DeadlineTooSoon();
    error AmountTooHigh();
    error GasPriceTooHigh();
    error PaymentBlocked();
    error NotEmergencyAdmin();
    error AlreadyDeprecated();
    error NotDeprecated();
    error ExitWindowActive();
    error InvalidSigner();
    error NoTransferToZeroAddress();
    error PriceCooldownActive();
    error DailyCapExceeded();
    error PriceBufferTooLow();
    error CooldownOutOfBounds();

    address public immutable entryPoint;

    uint256 public constant EXIT_WINDOW_DURATION = 7 days;

    // F-04: capped failure threshold
    uint256 public constant MAX_BLOCK_FAILURE_THRESHOLD = 10;
    // F-004: anti-griefing — cooldown before counter resets on success
    uint256 public constant BLOCK_COOLDOWN = 1 hours;

    // F-004: differentiated failure reasons
    enum FailureReason { None, InsufficientAllowance, InsufficientBalance, TokenRevert }

    uint256 public deprecationTimestamp;

    uint256 public minimumDeadlineBuffer = 300;
    mapping(address => uint256) public maxTokenAmountLimit;
    uint256 public maxGasPrice = 200 gwei;

    // C-4: failure threshold increased from 3 to 5 per PRD §3.3
    uint256 public blockFailureThreshold = 5;
    // C-4: cooldown reduced from 1h to 30min per PRD §3.3
    uint256 public cooldownPeriod = 30 minutes;

    // F-004: failure tracking with diagnosis
    mapping(address => uint256) public failedPaymentCount;
    mapping(address => uint256) public blockedUntil;
    // F-004: tracks last failure timestamp for cooldown-based counter reset
    mapping(address => uint256) public lastFailureAt;

    // F-018: daily withdrawal cap (5% of balance)
    uint256 public dailyWithdrawalCapBps = 500;
    uint256 public dailyWithdrawnToday;
    uint256 public dailyWithdrawalResetAt;

    address public emergencyAdmin;

    // C-4: raw error selectors for failure type differentiation (OZ 5.x ERC20 custom errors)
    bytes4 private constant _ERR_INSUFFICIENT_BALANCE = 0xe450d38c;
    bytes4 private constant _ERR_INSUFFICIENT_ALLOWANCE = 0xfb8f41b2;

    // F-01+C-1: trusted signer for paymaster signature verification (immutable post-deploy)
    address public immutable trustedSigner;

    // Step 1b: TrustProviderRegistry (optional, settable by owner)
    address public registry;
    // C-1: EIP-712 type hash: Quote(sender,token,maxTokenAmount,maxGasPrice,quoteDeadline,nonce)
    bytes32 public constant QUOTE_TYPEHASH =
        keccak256("Quote(address sender,address token,uint256 maxTokenAmount,uint256 maxGasPrice,uint256 quoteDeadline,uint256 nonce)");

    // C-1: nonce consumption for replay protection
    mapping(address => uint256) public nextQuoteNonce;

    // C-5: price update cooldown
    uint256 public constant PRICE_COOLDOWN = 15 minutes;
    mapping(address => uint256) public priceLastUpdated;

    // F-08: 2-step ownership transfer
    address public pendingOwner;

    modifier onlyEmergency() {
        if (msg.sender != emergencyAdmin) revert NotEmergencyAdmin();
        _;
    }

    event EmergencyAdminUpdated(address indexed oldAdmin, address indexed newAdmin);
    event MinimumDeadlineBufferUpdated(uint256 oldValue, uint256 newValue);
    event MaxTokenAmountLimitUpdated(address indexed token, uint256 oldValue, uint256 newValue);
    event MaxGasPriceUpdated(uint256 oldValue, uint256 newValue);
    event SenderBlocked(address indexed sender, uint256 blockedUntil);
    event SenderUnblocked(address indexed sender);
    event BlockFailureThresholdUpdated(uint256 oldValue, uint256 newValue);
    event CooldownPeriodUpdated(uint256 oldValue, uint256 newValue);
    event DeprecationInitiated(uint256 deprecationTimestamp, uint256 exitWindowEnd);
    event DeprecationFinalized(uint256 timestamp);
    // F-08
    event OwnershipTransferStarted(address indexed previousOwner, address indexed newOwner);
    // C-5 (withdrawTo)
    event WithdrawalExecuted(bytes32 indexed id, address token, address to, uint256 amount);
    // Step 1b
    event RegistryUpdated(address indexed registry);

    struct TokenConfig {
        IERC20 token;
        bool supportsPermit;
    }

    TokenConfig public mdaoConfig;
    TokenConfig public usdtConfig;

    function mdaoToken() external view returns (address) { return address(mdaoConfig.token); }
    function usdtToken() external view returns (address) { return address(usdtConfig.token); }

    event GasPaid(address indexed user, IERC20 indexed token, uint256 amount, uint256 gasCost);

    // ──────────────────────────────────────────────
    // Price oracle — owner-settable token/ETH prices
    // ──────────────────────────────────────────────

    mapping(address => uint256) public tokenPrice; // token amount per 1 ETH (18 decimals)
    uint256 public priceBufferBps = 500; // 5% max over oracle price
    uint256 public constant MAX_PRICE_CHANGE_BPS = 200; // 2% max price change per update
    uint256 public constant maxDeviationBps = 1000; // 10% max price deviation per update (F-005, REGRESSED)
    uint256 public constant MIN_PRICE_BUFFER_BPS = 100; // F-009a: 1% minimum buffer

    // F-12: event now includes oldPrice
    event PriceUpdated(address indexed token, uint256 oldPrice, uint256 newPrice);
    event PriceBufferUpdated(uint256 oldValue, uint256 newValue);
    event PaymentFailed(address indexed user, IERC20 indexed token, uint256 amount, FailureReason reason);
    event DailyWithdrawalCapUpdated(uint256 oldValue, uint256 newValue);
    event SenderPaymentBlocked(address indexed user, FailureReason reason, uint256 consecutiveFailures);

    constructor(
        address _entryPoint,
        address _mdao,
        address _usdt,
        address _trustedSigner
    ) Ownable(msg.sender) {
        if (_entryPoint == address(0)) revert Unauthorized();
        uint256 size;
        assembly { size := extcodesize(_entryPoint) }
        if (size == 0) revert Unauthorized();
        if (_mdao == address(0) || _usdt == address(0)) revert InvalidToken();
        entryPoint = _entryPoint;
        mdaoConfig = TokenConfig(IERC20(_mdao), true);
        usdtConfig = TokenConfig(IERC20(_usdt), false);
        trustedSigner = _trustedSigner;
        // F-010: per-token max amount limits (10k whole tokens)
        maxTokenAmountLimit[_mdao] = 10_000 * (10 ** _tokenDecimals(_mdao));
        maxTokenAmountLimit[_usdt] = 10_000 * (10 ** _tokenDecimals(_usdt));
    }

    modifier onlyEntryPoint() {
        if (msg.sender != entryPoint) revert Unauthorized();
        _;
    }

    // ── EIP-712 domain separator (backward compat for off-chain) ────
    function DOMAIN_SEPARATOR() public view returns (bytes32) {
        return _domainSeparatorV4();
    }

    // ── C-1: trusted signer is immutable, no setter ─────────────────

    // Step 1b: TrustProviderRegistry setter
    function setRegistry(address _registry) external onlyOwner {
        registry = _registry;
        emit RegistryUpdated(_registry);
    }

    // ── F-08: 2-step ownership transfer ─────────────────────────────

    /// @notice Initiate a 2-step ownership transfer. The new owner must call
    ///         `acceptOwnership()` to complete the transfer.
    /// @param newOwner Address of the proposed new owner.
    function transferOwnership(address newOwner) public override onlyOwner {
        if (newOwner == address(0)) revert NoTransferToZeroAddress();
        pendingOwner = newOwner;
        emit OwnershipTransferStarted(owner(), newOwner);
    }

    /// @notice Accept ownership. Must be called by the pending owner.
    function acceptOwnership() external {
        if (msg.sender != pendingOwner) revert Unauthorized();
        address previousOwner = owner();
        delete pendingOwner;
        _transferOwnership(msg.sender);
        emit OwnershipTransferred(previousOwner, msg.sender);
    }

    function setPermitSupport(address token, bool enabled) external onlyOwner {
        if (token == address(mdaoConfig.token)) {
            mdaoConfig.supportsPermit = enabled;
        } else if (token == address(usdtConfig.token)) {
            usdtConfig.supportsPermit = enabled;
        } else {
            revert InvalidToken();
        }
    }

    // F-06: minimum 60s prevents owner from disabling quote expiry protection
    function setMinimumDeadlineBuffer(uint256 newBuffer) external onlyOwner {
        if (newBuffer < 60) revert AmountTooHigh();
        emit MinimumDeadlineBufferUpdated(minimumDeadlineBuffer, newBuffer);
        minimumDeadlineBuffer = newBuffer;
    }

    function setMaxTokenAmountLimit(address token, uint256 newLimit) external onlyOwner {
        uint8 dec = _tokenDecimals(token);
        uint256 maxLimit = 10_000 * (10 ** dec);
        if (newLimit > maxLimit) revert AmountTooHigh();
        emit MaxTokenAmountLimitUpdated(token, maxTokenAmountLimit[token], newLimit);
        maxTokenAmountLimit[token] = newLimit;
    }

    // SEC-25-03: cap at 1000 gwei prevents owner from disabling gas price protection
    // F-011: BSC cap 100 gwei, ETH/L2 cap 5000 gwei
    function setMaxGasPrice(uint256 newMaxGasPrice) external onlyOwner {
        if (newMaxGasPrice < 1 gwei) revert GasPriceTooHigh();
        uint256 cap = block.chainid == 56 ? 100 gwei : 5000 gwei;
        if (newMaxGasPrice > cap) revert GasPriceTooHigh();
        emit MaxGasPriceUpdated(maxGasPrice, newMaxGasPrice);
        maxGasPrice = newMaxGasPrice;
    }

    error PriceChangeTooHigh();

    /// @notice Set the token-to-ETH price for a token. Subsequent updates are
    ///         capped at ±2% per call. The price must be greater than zero.
    function setTokenPrice(address token, uint256 price) external onlyOwner {
        if (price == 0) revert PriceChangeTooHigh();
        uint256 oldPrice = tokenPrice[token];
        // C-5: enforce cooldown between price updates (skip on first set)
        if (oldPrice > 0 && priceLastUpdated[token] + PRICE_COOLDOWN > block.timestamp) revert PriceCooldownActive();
        if (oldPrice > 0) {
            uint256 change = price > oldPrice ? price - oldPrice : oldPrice - price;
            if (change * 10000 / oldPrice > MAX_PRICE_CHANGE_BPS) revert PriceChangeTooHigh();
            // F-005: hard 10% deviation bound (REGRESSED safeguard)
            if (change * 10000 / oldPrice > maxDeviationBps) revert PriceChangeTooHigh();
        }
        tokenPrice[token] = price;
        priceLastUpdated[token] = block.timestamp;
        emit PriceUpdated(token, oldPrice, price);
    }

    // SEC-25-01: max 20% buffer prevents owner from disabling price protection
    // F-009a: MIN_PRICE_BUFFER_BPS (1%) prevents overly tight buffer
    function setPriceBufferBps(uint256 newBuffer) external onlyOwner {
        if (newBuffer < MIN_PRICE_BUFFER_BPS) revert PriceBufferTooLow();
        if (newBuffer > 2000) revert PriceChangeTooHigh();
        emit PriceBufferUpdated(priceBufferBps, newBuffer);
        priceBufferBps = newBuffer;
    }

    struct PermitData {
        uint256 deadline;
        uint8 v;
        bytes32 r;
        bytes32 s;
    }

    struct PaymasterExtra {
        IERC20 token;
        uint256 maxTokenAmount;
        uint256 quoteDeadline;
        PermitData permit;
        bool hasPermit;
    }

    // C-1+C-8: EIP-712 quote signature is appended to paymasterData as suffix
    // Suffix format: sig(65) + lenHex(2) + magic(8) = 75 bytes
    uint256 private constant _SUFFIX_LEN = 75;
    bytes8 private constant _SUFFIX_MAGIC = bytes8(hex"22e325a297439656");

    function _hasSuffix(bytes calldata data) internal pure returns (bool) {
        return data.length >= _SUFFIX_LEN + 1 &&
            bytes8(data[data.length - 8:]) == _SUFFIX_MAGIC;
    }

    function _decodePaymasterData(bytes calldata data) internal pure returns (PaymasterExtra memory) {
        uint256 end = _hasSuffix(data) ? data.length - _SUFFIX_LEN : data.length;
        return _parseQuoteFields(data, end);
    }

    function _parseQuoteFields(bytes calldata data, uint256 end) internal pure returns (PaymasterExtra memory) {
        uint256 offset;
        IERC20 token = IERC20(address(bytes20(data[offset:offset + 20])));
        offset += 20;

        uint256 maxTokenAmount = uint256(bytes32(data[offset:offset + 32]));
        offset += 32;

        uint256 quoteDeadline = uint256(bytes32(data[offset:offset + 32]));
        offset += 32;

        (bool hasPermit, PermitData memory permit) = _parsePermit(data, end, offset);

        return PaymasterExtra(token, maxTokenAmount, quoteDeadline, permit, hasPermit);
    }

    function _parsePermit(bytes calldata data, uint256 end, uint256 offset) internal pure returns (bool hasPermit, PermitData memory permit) {
        if (end < offset + 97) return (false, PermitData(0, 0, 0, 0));
        uint256 deadline = uint256(bytes32(data[offset:offset + 32]));
        offset += 32;
        uint8 v = uint8(data[offset]);
        offset += 1;
        bytes32 r = bytes32(data[offset:offset + 32]);
        offset += 32;
        bytes32 s = bytes32(data[offset:offset + 32]);
        return (true, PermitData(deadline, v, r, s));
    }

    function _getTokenConfig(address token) internal view returns (TokenConfig storage) {
        if (token == address(mdaoConfig.token)) return mdaoConfig;
        if (token == address(usdtConfig.token)) return usdtConfig;
        revert InvalidToken();
    }

    function _tokenDecimals(address token) internal view returns (uint8) {
        (bool success, bytes memory data) = token.staticcall(abi.encodeWithSelector(0x313ce567));
        if (success && data.length == 32) return abi.decode(data, (uint8));
        return 18;
    }

    function validatePaymasterUserOp(
        UserOperation calldata userOp,
        bytes32 userOpHash,
        uint256 maxCost,
        bytes calldata paymasterData
    ) external onlyEntryPoint whenNotPaused returns (bytes memory context, uint256 validationData) {
        if (deprecationTimestamp == type(uint256).max) revert Unauthorized();
        if (blockedUntil[userOp.sender] > block.timestamp) revert PaymentBlocked();

        PaymasterExtra memory extra = _decodePaymasterData(paymasterData);
        _verifySignerIfConfigured(userOp.sender, extra, userOp.maxFeePerGas, paymasterData);

        context = _validateFields(extra, maxCost, userOp.sender, userOp.maxFeePerGas);
        return (context, 0);
    }

    function _verifySignerIfConfigured(
        address sender,
        PaymasterExtra memory extra,
        uint256 maxGasPrice_,
        bytes calldata paymasterData
    ) internal {
        address registry_ = registry;
        if (registry_ != address(0)) {
            // Use TrustProviderRegistry for verification
            if (paymasterData.length < _SUFFIX_LEN) revert InvalidSigner();
            bytes calldata quoteSig = paymasterData[paymasterData.length - _SUFFIX_LEN : paymasterData.length - 10];

            uint256 nonce = nextQuoteNonce[sender];
            bytes32 structHash = keccak256(abi.encode(
                QUOTE_TYPEHASH,
                sender,
                address(extra.token),
                extra.maxTokenAmount,
                maxGasPrice_,
                extra.quoteDeadline,
                nonce
            ));
            bytes32 digest = _hashTypedDataV4(structHash);

            bytes32 providerId = bytes32(uint256(uint160(trustedSigner)));

            if (!IRegistry(registry_).verify(providerId, digest, quoteSig)) revert InvalidSigner();
            nextQuoteNonce[sender] = nonce + 1;
        } else if (trustedSigner != address(0)) {
            // Fallback: direct trusted signer verification (backward compat)
            if (paymasterData.length < _SUFFIX_LEN) revert InvalidSigner();
            bytes calldata quoteSig = paymasterData[paymasterData.length - _SUFFIX_LEN : paymasterData.length - 10];
            _verifyQuoteSignature(sender, extra, maxGasPrice_, quoteSig);
        }
    }

    function _validateFields(
        PaymasterExtra memory extra,
        uint256 maxCost,
        address sender,
        uint256 maxFeePerGas_
    ) internal returns (bytes memory) {
        TokenConfig storage cfg = _getTokenConfig(address(extra.token));
        if (block.timestamp > extra.quoteDeadline) revert QuoteExpired();
        if (extra.quoteDeadline < block.timestamp + minimumDeadlineBuffer) revert DeadlineTooSoon();
        if (extra.maxTokenAmount > maxTokenAmountLimit[address(extra.token)]) revert AmountTooHigh();
        if (maxFeePerGas_ > maxGasPrice) revert GasPriceTooHigh();
        if (IEntryPointView(entryPoint).balanceOf(address(this)) < maxCost) revert InsufficientDeposit();

        uint256 price = tokenPrice[address(extra.token)];
        if (price > 0) {
            uint256 maxAllowed = maxCost * price * (10000 + priceBufferBps) / 10000 / 1e18;
            if (extra.maxTokenAmount > maxAllowed) revert AmountTooHigh();
        } else if (extra.maxTokenAmount > 0) {
            revert AmountTooHigh();
        }

        _checkAllowanceAndPermit(extra, sender, cfg);

        return abi.encode(sender, extra.token, extra.maxTokenAmount);
    }

    function _checkAllowanceAndPermit(
        PaymasterExtra memory extra,
        address sender,
        TokenConfig storage cfg
    ) internal {
        if (extra.token.balanceOf(sender) < extra.maxTokenAmount) revert InsufficientBalance();

        if (extra.hasPermit) {
            if (!cfg.supportsPermit) revert PermitFailed();
            _executePermit(extra.token, sender, address(this), extra.maxTokenAmount, extra.permit);
        }

        if (extra.token.allowance(sender, address(this)) < extra.maxTokenAmount) revert InsufficientAllowance();
    }

    function computeAmountToCharge(
        uint256 maxTokenAmount,
        uint256 actualGasCost,
        address tokenAddr
    ) public view returns (uint256 amountToCharge, uint256 refund) {
        uint256 price = tokenPrice[tokenAddr];
        uint256 actualTokenAmount = actualGasCost * price / 1e18;
        if (actualTokenAmount >= maxTokenAmount) {
            return (maxTokenAmount, 0);
        }
        return (actualTokenAmount, maxTokenAmount - actualTokenAmount);
    }

    // F-004: diagnose failure by checking on-chain state (works for USDT false-return)
    function _diagnoseFailure(address sender, address token, uint256 amount)
        internal view returns (FailureReason)
    {
        if (IERC20(token).allowance(sender, address(this)) < amount) {
            return FailureReason.InsufficientAllowance;
        }
        if (IERC20(token).balanceOf(sender) < amount) {
            return FailureReason.InsufficientBalance;
        }
        return FailureReason.TokenRevert;
    }

    // F-10: ReentrancyGuard on postOp
    function postOp(
        PostOpMode mode,
        bytes calldata context,
        uint256 actualGasCost,
        uint256
    ) external onlyEntryPoint whenNotPaused nonReentrant {
        (address sender, IERC20 token, uint256 maxTokenAmount) =
            abi.decode(context, (address, IERC20, uint256));

        if (mode == PostOpMode.opReverted) return;

        (uint256 amountToCharge, uint256 refund) = computeAmountToCharge(maxTokenAmount, actualGasCost, address(token));

        // Use low-level call to avoid reverting the entire postOp if transfer fails
        (bool success, bytes memory returndata) = address(token).call(
            abi.encodeWithSelector(IERC20.transferFrom.selector, sender, address(this), amountToCharge)
        );

        // F-004: handle USDT-style false return (not revert)
        // USDT returns abi.encode(false) with success=true — we must check return data
        bool chargeSuccess = success && (returndata.length == 0 || abi.decode(returndata, (bool)));

        if (!chargeSuccess) {
            // F-004: diagnose failure by checking on-chain state
            // This handles USDT (returns false instead of revert) correctly
            FailureReason reason = _diagnoseFailure(sender, address(token), amountToCharge);

            lastFailureAt[sender] = block.timestamp;

            if (reason == FailureReason.TokenRevert) {
                // Malicious/griefing — count toward blocking threshold
                failedPaymentCount[sender]++;
                if (failedPaymentCount[sender] >= blockFailureThreshold) {
                    blockedUntil[sender] = block.timestamp + cooldownPeriod;
                    emit SenderPaymentBlocked(sender, reason, failedPaymentCount[sender]);
                }
            } else {
                // User-fixable (allowance/balance) — reset counter, don't block
                failedPaymentCount[sender] = 0;
            }

            emit PaymentFailed(sender, token, amountToCharge, reason);
            return;
        }

        // F-004: reset failure counter on success only after BLOCK_COOLDOWN
        if (block.timestamp > lastFailureAt[sender] + BLOCK_COOLDOWN) {
            failedPaymentCount[sender] = 0;
        }

        // F-07: low-level call for refund prevents postOp revert blocking EntryPoint
        if (refund > 0) {
            (bool refundSuccess,) = address(token).call(
                abi.encodeWithSelector(IERC20.transfer.selector, sender, refund)
            );
            if (!refundSuccess) emit PaymentFailed(sender, token, refund, FailureReason.None);
        }

        emit GasPaid(sender, token, amountToCharge, actualGasCost);
    }

    // C-1: verify EIP-712 backend quote signature (FP-AUTH-001)
    function _verifyQuoteSignature(
        address sender,
        PaymasterExtra memory extra,
        uint256 maxGasPrice_,
        bytes calldata sig
    ) internal {
        uint256 nonce = nextQuoteNonce[sender];
        bytes32 structHash = keccak256(abi.encode(
            QUOTE_TYPEHASH,
            sender,
            address(extra.token),
            extra.maxTokenAmount,
            maxGasPrice_,
            extra.quoteDeadline,
            nonce
        ));
        bytes32 digest = _hashTypedDataV4(structHash);
        // OZ ECDSA.recover rejects malleable s > secp256k1n/2 (F-006)
        address recovered = digest.recover(sig);
        if (recovered != trustedSigner) revert InvalidSigner();
        nextQuoteNonce[sender] = nonce + 1;
    }

    function _executePermit(
        IERC20 token,
        address owner,
        address spender,
        uint256 value,
        PermitData memory permit
    ) internal {
        if (token.allowance(owner, spender) >= value) return;

        (bool success,) = address(token).call(
            abi.encodeWithSignature(
                "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)",
                owner, spender, value, permit.deadline, permit.v, permit.r, permit.s
            )
        );
        if (!success) revert PermitFailed();
    }

    function deposit() external payable {
        (bool success,) = entryPoint.call{value: msg.value}("");
        if (!success) revert TransferFailed();
    }

    // F-04: cap blockFailureThreshold
    function setBlockFailureThreshold(uint256 newThreshold) external onlyOwner {
        if (newThreshold > MAX_BLOCK_FAILURE_THRESHOLD) revert AmountTooHigh();
        emit BlockFailureThresholdUpdated(blockFailureThreshold, newThreshold);
        blockFailureThreshold = newThreshold;
    }

    // F-051: bound cooldown to [1 minute, 7 days] to prevent extreme values
    function setCooldownPeriod(uint256 newPeriod) external onlyOwner {
        if (newPeriod < 1 minutes) revert CooldownOutOfBounds();  // ponytail: min 1 minute
        if (newPeriod > 7 days) revert CooldownOutOfBounds();     // ponytail: max 7 days
        emit CooldownPeriodUpdated(cooldownPeriod, newPeriod);
        cooldownPeriod = newPeriod;
    }

    function setDailyWithdrawalCapBps(uint256 newCapBps) external onlyOwner {
        // max 100% (10000 bps) — owner cannot disable cap entirely beyond 100%
        if (newCapBps > 10000) revert AmountTooHigh();
        emit DailyWithdrawalCapUpdated(dailyWithdrawalCapBps, newCapBps);
        dailyWithdrawalCapBps = newCapBps;
    }

    function unblockSender(address sender) external onlyOwner {
        failedPaymentCount[sender] = 0;
        blockedUntil[sender] = 0;
        lastFailureAt[sender] = 0;
        emit SenderUnblocked(sender);
    }

    function setEmergencyAdmin(address newAdmin) external onlyOwner {
        emit EmergencyAdminUpdated(emergencyAdmin, newAdmin);
        emergencyAdmin = newAdmin;
    }

    function pause() external onlyEmergency {
        _pause();
    }

    function unpause() external onlyEmergency {
        _unpause();
    }

    /// @notice Direct withdrawal with daily cap — protected by TimelockController (onlyOwner)
    function withdrawTo(address token, address to, uint256 amount) external onlyOwner {
        if (to == address(0)) revert NoTransferToZeroAddress();

        if (block.timestamp > dailyWithdrawalResetAt) {
            dailyWithdrawnToday = 0;
            dailyWithdrawalResetAt = block.timestamp + 1 days;
        }
        uint256 balance = token == address(0)
            ? IEntryPointView(entryPoint).balanceOf(address(this))
            : IERC20(token).balanceOf(address(this));
        if (dailyWithdrawnToday + amount > balance * dailyWithdrawalCapBps / 10000) revert DailyCapExceeded();
        dailyWithdrawnToday += amount;

        if (token == address(0)) {
            (bool success,) = entryPoint.call(
                abi.encodeWithSignature("withdrawTo(address,uint256)", to, amount)
            );
            if (!success) revert TransferFailed();
        } else {
            IERC20(token).safeTransfer(to, amount);
        }
        emit WithdrawalExecuted(keccak256(abi.encode(token, to, amount)), token, to, amount);
    }

    receive() external payable {}

    // ──────────────────────────────────────────────
    // Deprecation & Exit Window
    // ──────────────────────────────────────────────

    function initiateDeprecation() external onlyOwner {
        if (deprecationTimestamp != 0) revert AlreadyDeprecated();
        deprecationTimestamp = block.timestamp;
        emit DeprecationInitiated(deprecationTimestamp, deprecationTimestamp + EXIT_WINDOW_DURATION);
    }

    function isDeprecated() public view returns (bool) {
        return deprecationTimestamp != 0;
    }

    function exitWindowEnd() public view returns (uint256) {
        if (deprecationTimestamp == 0) revert NotDeprecated();
        return deprecationTimestamp + EXIT_WINDOW_DURATION;
    }

    function finalizeDeprecation() external onlyOwner {
        if (deprecationTimestamp == 0) revert NotDeprecated();
        if (block.timestamp < exitWindowEnd()) revert ExitWindowActive();
        deprecationTimestamp = type(uint256).max;
        emit DeprecationFinalized(block.timestamp);
    }
}
