# Errors Index
> Read this first. For full details: see ERRORS-MEMORY.md sections
> Last updated: 2026-06-30

## Patterns by Category

### CRYPTOGRAPHY
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

### BLOCKCHAIN
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

### SMART CONTRACT
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

### SECRETS
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

### LOGGING
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

### PROCESS
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

### ARCHITECTURE
| EM-001: | `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)` |
| EM-002: | `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2` |
| EM-003: | `"\x19Ethereum Signed Message:\n32"` for quotes/approvals |
| EM-004: | P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32` |
| EM-005: | `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers |
| EM-010: | PaymasterService signs paymasterAndData, contract never verifies |
| EM-011: | Recovery approve/veto hash without `block.chainid` |
| EM-012: | `modifier onlyWalletOwner` on initiateRecovery |
| EM-013: | executeRecovery public, no upper time bound |
| EM-014: | Failed payment has no cooldown/blocklist |
| EM-020: | Admin function without max value check |
| EM-021: | Claiming multisig protection in TDD, no code enforcement |
| EM-022: | `withdrawToken` function in RefundVault |
| EM-023: | MockP256.sol with `verify() returns 1` deployable to mainnet |
| EM-024: | Claiming "uses OZ safeTransfer, no reentrancy" |
| EM-030: | `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml |
| EM-031: | `PAYMASTER_PRIVATE_KEY=0x...` in .env.example |
| EM-032: | Claiming "no secrets in git" based on `git ls-files` only |
| EM-040: | Removing all `${e.message}` from logs without differentiation |
| EM-041: | Deleting txHash from swap logs for "privacy" |
| EM-042: | Generic "Recovery initiated" without wallet |
| EM-043: | `Result.failure(IllegalArgumentException("Invalid signature"))` without details |
| EM-050: | Commit message says "fixed" but no test added |
| EM-051: | F-06 HIGH → MEDIUM without reason in lifecycle |
| EM-052: | Subagent fails → coordinator does the work |
| EM-053: | 51% of findings are duplicates across waves |
| EM-054: | Commit says "use OZ ECDSA.recover" but code has raw ecrecover |
| EM-060: | Hardcoded publicnode/ankr URLs in RpcProviderManager |
| EM-061: | No CertificatePinner in Android app |
| EM-062: | One RPC URL in AppConfig |
| EM-063: | `synchronized(this)` + ConcurrentHashMap for nickname uniqueness |
| EM-064: | Redis.incr → null → return false при падении Redis |
| EM-065: | Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote) |
| EM-066: | `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key |
| EM-067: | `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт) |
| EM-068: | API key передаётся как query-параметр в widget URL и возвращается клиенту |
| EM-069: | WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false |
| EM-070: | `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile |
| EM-071: | CI pipeline runs only tests, no trivy/codeql/secret-scanning |
| EM-072: | `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format |
| EM-073: | @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist |
| EM-074: | Client-side Base64 decode of JWT payload without signature verification |
| EM-075: | PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares |

