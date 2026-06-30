package com.mdaopay.app.core.guardian

import android.util.Base64
import com.mdaopay.app.core.blockchain.EthereumClient
import com.mdaopay.app.core.blockchain.NetworkConfig
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.erc4337.BundlerClient
import com.mdaopay.app.core.blockchain.erc4337.UserOperation
import com.mdaopay.app.core.blockchain.paymaster.PaymasterClient
import com.mdaopay.app.core.security.CborDecoder
import com.mdaopay.app.core.security.CborItem
import com.mdaopay.app.core.security.PasskeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

data class GuardianKeyData(
    val pubKeyXHex: String,
    val pubKeyYHex: String
)

/**
 * Builds and sends ERC-4337 UserOperations for guardian on-chain actions.
 * Drops legacy relay-only approach — these UserOps interact directly with
 * SocialRecoveryModule on-chain.
 *
 * ponytail: reuses RecoveryUserOpBuilder patterns; no new dependencies.
 */
@Singleton
class GuardianUserOpBuilder @Inject constructor(
    private val walletManager: WalletManager,
    private val bundlerClient: BundlerClient,
    private val paymasterClient: PaymasterClient,
    private val ethereumClient: EthereumClient,
    private val passkeyManager: PasskeyManager
) {

    /**
     * 1. Creates a P-256 passkey via PasskeyManager
     * 2. Extracts the public key coordinates from the WebAuthn registration
     * 3. Calls confirmGuardian(wallet) on-chain via UserOp
     *
     * Returns the extracted pubKeyX/pubKeyY for storage in GuardianInfo.
     */
    suspend fun acceptInviteAndRegister(
        invite: GuardianInviteResponse,
        userId: String
    ): Result<GuardianKeyData> = withContext(Dispatchers.IO) {
        try {
            // Step 1: create passkey (P-256 key pair)
            val passkeyResult = passkeyManager.createRecoveryPasskey(userId)
            val passkeyData = passkeyResult.getOrElse {
                return@withContext Result.failure(it)
            }

            // Step 2: extract P-256 public key from registration JSON
            val keyData = extractP256PublicKey(passkeyData.registrationJson)
                ?: return@withContext Result.failure(
                    IllegalStateException("Failed to extract P-256 public key from passkey registration")
                )

            // Step 3: call confirmGuardian on-chain
            val txHash = sendConfirmGuardian(invite.walletAddress)
                ?: return@withContext Result.failure(
                    Exception("confirmGuardian UserOp failed")
                )

            Result.success(keyData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends approveRecovery(wallet, guardianIdentityHash, authenticatorData, clientDataJSON, p256Signature)
     * on-chain via UserOp. Extracts WebAuthn assertion data from the passkey authentication JSON.
     */
    suspend fun approveRecovery(
        wallet: String,
        guardianIdentityHash: String,
        authenticationJson: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val webauthn = extractWebAuthnAssertion(authenticationJson)
                ?: return@withContext Result.failure(
                    IllegalStateException("Failed to extract WebAuthn assertion from authentication response")
                )

            val txHash = sendApproveRecovery(
                wallet = wallet,
                guardianIdentityHash = guardianIdentityHash,
                authenticatorData = webauthn.authenticatorData,
                clientDataJSON = webauthn.clientDataJSON,
                signature = webauthn.signature
            ) ?: return@withContext Result.failure(
                Exception("approveRecovery UserOp failed")
            )

            Result.success(txHash)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends vetoRecovery(wallet, guardianIdentityHash, authenticatorData, clientDataJSON, p256Signature)
     * on-chain via UserOp.
     */
    suspend fun vetoRecovery(
        wallet: String,
        guardianIdentityHash: String,
        authenticationJson: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val webauthn = extractWebAuthnAssertion(authenticationJson)
                ?: return@withContext Result.failure(
                    IllegalStateException("Failed to extract WebAuthn assertion from authentication response")
                )

            val txHash = sendVetoRecovery(
                wallet = wallet,
                guardianIdentityHash = guardianIdentityHash,
                authenticatorData = webauthn.authenticatorData,
                clientDataJSON = webauthn.clientDataJSON,
                signature = webauthn.signature
            ) ?: return@withContext Result.failure(
                Exception("vetoRecovery UserOp failed")
            )

            Result.success(txHash)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    //  UserOp builders
    // ──────────────────────────────────────────────

    private suspend fun sendConfirmGuardian(wallet: String): String? {
        val walletData = walletManager.getWalletData() ?: return null
        val keyPair = walletData.keyPair
        val smartAccountAddress = getOrComputeAddress(keyPair).getOrNull() ?: return null
        val nonce = getNonce(smartAccountAddress).getOrNull() ?: return null
        val deployed = isDeployed(smartAccountAddress)

        // Inner: confirmGuardian(address wallet)
        val innerCalldata = FunctionEncoder.encode(
            Function("confirmGuardian", listOf(Address(wallet)), emptyList())
        )

        // Outer: execute(target, value, data)
        val executeCalldata = FunctionEncoder.encode(
            Function(
                "execute",
                listOf(
                    Address(NetworkConfig.SOCIAL_RECOVERY_MODULE),
                    Uint256(BigInteger.ZERO),
                    DynamicBytes(Numeric.hexStringToByteArray(innerCalldata))
                ),
                emptyList()
            )
        )

        val initCode: ByteArray = if (deployed) ByteArray(0) else buildInitCode(keyPair)

        var userOp = buildUserOp(
            sender = smartAccountAddress,
            nonce = nonce,
            initCode = initCode,
            callData = Numeric.hexStringToByteArray(executeCalldata)
        )
        userOp = applyPaymaster(userOp)

        val userOpHash = userOp.computeUserOpHash(NetworkConfig.ENTRY_POINT, NetworkConfig.CHAIN_ID)
        userOp.signature = signUserOp(userOpHash, keyPair)

        val result = bundlerClient.sendUserOperation(userOp, NetworkConfig.ENTRY_POINT)
        return result.getOrNull()
    }

    private suspend fun sendApproveRecovery(
        wallet: String,
        guardianIdentityHash: String,
        authenticatorData: ByteArray,
        clientDataJSON: ByteArray,
        signature: ByteArray
    ): String? {
        val walletData = walletManager.getWalletData() ?: return null
        val keyPair = walletData.keyPair
        val smartAccountAddress = getOrComputeAddress(keyPair).getOrNull() ?: return null
        val nonce = getNonce(smartAccountAddress).getOrNull() ?: return null
        val deployed = isDeployed(smartAccountAddress)

        // Inner: approveRecovery(address, bytes32, bytes, bytes, bytes)
        val innerCalldata = FunctionEncoder.encode(
            Function(
                "approveRecovery",
                listOf(
                    Address(wallet),
                    Bytes32(Numeric.hexStringToByteArray(guardianIdentityHash)),
                    DynamicBytes(authenticatorData),
                    DynamicBytes(clientDataJSON),
                    DynamicBytes(signature)
                ),
                emptyList()
            )
        )

        val executeCalldata = FunctionEncoder.encode(
            Function(
                "execute",
                listOf(
                    Address(NetworkConfig.SOCIAL_RECOVERY_MODULE),
                    Uint256(BigInteger.ZERO),
                    DynamicBytes(Numeric.hexStringToByteArray(innerCalldata))
                ),
                emptyList()
            )
        )

        val initCode: ByteArray = if (deployed) ByteArray(0) else buildInitCode(keyPair)

        var userOp = buildUserOp(
            sender = smartAccountAddress,
            nonce = nonce,
            initCode = initCode,
            callData = Numeric.hexStringToByteArray(executeCalldata)
        )
        userOp = applyPaymaster(userOp)

        val userOpHash = userOp.computeUserOpHash(NetworkConfig.ENTRY_POINT, NetworkConfig.CHAIN_ID)
        userOp.signature = signUserOp(userOpHash, keyPair)

        val result = bundlerClient.sendUserOperation(userOp, NetworkConfig.ENTRY_POINT)
        return result.getOrNull()
    }

    private suspend fun sendVetoRecovery(
        wallet: String,
        guardianIdentityHash: String,
        authenticatorData: ByteArray,
        clientDataJSON: ByteArray,
        signature: ByteArray
    ): String? {
        val walletData = walletManager.getWalletData() ?: return null
        val keyPair = walletData.keyPair
        val smartAccountAddress = getOrComputeAddress(keyPair).getOrNull() ?: return null
        val nonce = getNonce(smartAccountAddress).getOrNull() ?: return null
        val deployed = isDeployed(smartAccountAddress)

        // Inner: vetoRecovery(address, bytes32, bytes, bytes, bytes)
        val innerCalldata = FunctionEncoder.encode(
            Function(
                "vetoRecovery",
                listOf(
                    Address(wallet),
                    Bytes32(Numeric.hexStringToByteArray(guardianIdentityHash)),
                    DynamicBytes(authenticatorData),
                    DynamicBytes(clientDataJSON),
                    DynamicBytes(signature)
                ),
                emptyList()
            )
        )

        val executeCalldata = FunctionEncoder.encode(
            Function(
                "execute",
                listOf(
                    Address(NetworkConfig.SOCIAL_RECOVERY_MODULE),
                    Uint256(BigInteger.ZERO),
                    DynamicBytes(Numeric.hexStringToByteArray(innerCalldata))
                ),
                emptyList()
            )
        )

        val initCode: ByteArray = if (deployed) ByteArray(0) else buildInitCode(keyPair)

        var userOp = buildUserOp(
            sender = smartAccountAddress,
            nonce = nonce,
            initCode = initCode,
            callData = Numeric.hexStringToByteArray(executeCalldata)
        )
        userOp = applyPaymaster(userOp)

        val userOpHash = userOp.computeUserOpHash(NetworkConfig.ENTRY_POINT, NetworkConfig.CHAIN_ID)
        userOp.signature = signUserOp(userOpHash, keyPair)

        val result = bundlerClient.sendUserOperation(userOp, NetworkConfig.ENTRY_POINT)
        return result.getOrNull()
    }

    // ──────────────────────────────────────────────
    //  P-256 extraction from WebAuthn registration
    // ──────────────────────────────────────────────

    /**
     * Extracts P-256 public key (x, y) from a WebAuthn registration response JSON.
     *
     * The attestationObject CBOR contains authData which holds the COSE_Key
     * with labels -2 (x) and -3 (y) for the P-256 public key coordinates.
     */
    fun extractP256PublicKey(registrationJson: String): GuardianKeyData? {
        return try {
            val json = JSONObject(registrationJson)
            val response = json.getJSONObject("response")
            val attestationB64 = response.getString("attestationObject")
            val attestationBytes = Base64.decode(attestationB64, Base64.URL_SAFE)

            val (attestation, _) = CborDecoder.decode(attestationBytes)
            val attestationMap = attestation as? CborItem.CborMap ?: return null
            val authDataEntry = attestationMap.entries[CborItem.CborText("authData")] as? CborItem.CborBytes ?: return null
            val authData = authDataEntry.value

            val (x, y) = parseCoseKeyFromAuthData(authData) ?: return null

            GuardianKeyData(
                pubKeyXHex = Numeric.toHexStringNoPrefix(x),
                pubKeyYHex = Numeric.toHexStringNoPrefix(y)
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts WebAuthn assertion data (authenticatorData, clientDataJSON, signature)
     * from a passkey authentication response JSON.
     */
    fun extractWebAuthnAssertion(authenticationJson: String): WebAuthnAssertion? {
        return try {
            val json = JSONObject(authenticationJson)
            val response = json.getJSONObject("response")
            val authDataB64 = response.getString("authenticatorData")
            val clientDataB64 = response.getString("clientDataJSON")
            val sigB64 = response.getString("signature")

            WebAuthnAssertion(
                authenticatorData = Base64.decode(authDataB64, Base64.URL_SAFE),
                clientDataJSON = Base64.decode(clientDataB64, Base64.URL_SAFE),
                signature = Base64.decode(sigB64, Base64.URL_SAFE)
            )
        } catch (_: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private fun parseCoseKeyFromAuthData(authData: ByteArray): Pair<ByteArray, ByteArray>? {
        var offset = 0
        if (authData.size < 37) return null
        offset += 32 // RP ID hash
        val flags = authData[32].toInt() and 0xFF
        offset += 5 // flags (1) + sign count (4)

        if (flags and 0x40 == 0) return null // AT flag not set — no credential data

        // AAGUID (16 bytes)
        if (offset + 16 > authData.size) return null
        offset += 16

        // Credential ID length (2 bytes)
        if (offset + 2 > authData.size) return null
        val credIdLen = ((authData[offset].toInt() and 0xFF) shl 8) or (authData[offset + 1].toInt() and 0xFF)
        offset += 2

        // Skip credential ID
        if (offset + credIdLen > authData.size) return null
        offset += credIdLen

        // Parse COSE_Key (CBOR map)
        val (coseKey, _) = CborDecoder.decode(authData, offset)
        val coseMap = coseKey as? CborItem.CborMap ?: return null

        var xBytes: ByteArray? = null
        var yBytes: ByteArray? = null

        for ((key, value) in coseMap.entries) {
            if (key is CborItem.CborUInt) {
                when (key.value) {
                    -2L -> xBytes = (value as? CborItem.CborBytes)?.value
                    -3L -> yBytes = (value as? CborItem.CborBytes)?.value
                }
            }
        }

        if (xBytes != null && yBytes != null) return Pair(xBytes, yBytes)
        return null
    }

    private fun buildUserOp(
        sender: String,
        nonce: BigInteger,
        initCode: ByteArray,
        callData: ByteArray
    ): UserOperation {
        return UserOperation(
            sender = sender,
            nonce = nonce,
            initCode = initCode,
            callData = callData,
            callGasLimit = BigInteger.valueOf(200_000),
            verificationGasLimit = BigInteger.valueOf(150_000),
            preVerificationGas = BigInteger.valueOf(50_000),
            maxFeePerGas = BigInteger.valueOf(1_500_000_000),
            maxPriorityFeePerGas = BigInteger.valueOf(1_000_000_000),
            paymasterAndData = ByteArray(0)
        )
    }

    /**
     * Tries to get paymasterAndData from the Paymaster backend.
     * Falls back to empty (guardian pays gas) on any error.
     * ponytail: single try-catch, graceful degradation.
     */
    private suspend fun applyPaymaster(userOp: UserOperation): UserOperation {
        return try {
            // N-5: Guardian ops are sponsored by the project treasury.
            // Pass generous maxAmount so backend can sign regardless of guardian's balance.
            val sponsoredMax = BigInteger.valueOf(10_000).multiply(BigInteger.TEN.pow(18))
            val response = paymasterClient.signUserOp(
                sender = userOp.sender,
                nonce = userOp.nonce,
                initCode = userOp.initCode,
                callData = userOp.callData,
                verificationGasLimit = userOp.verificationGasLimit,
                callGasLimit = userOp.callGasLimit,
                preVerificationGas = userOp.preVerificationGas,
                maxPriorityFeePerGas = userOp.maxPriorityFeePerGas,
                maxFeePerGas = userOp.maxFeePerGas,
                mdaoMaxAmount = sponsoredMax,
                usdtMaxAmount = null,
            )
            userOp.copy(paymasterAndData = Numeric.hexStringToByteArray(response.paymasterAndData))
        } catch (_: Exception) {
            userOp // graceful degradation: fallback to empty paymaster
        }
    }

    private fun signUserOp(userOpHash: ByteArray, keyPair: ECKeyPair): ByteArray {
        val signatureData = Sign.signMessage(userOpHash, keyPair)
        return ByteArray(65).apply {
            signatureData.r.copyInto(this, 0)
            signatureData.s.copyInto(this, 32)
            this[64] = signatureData.v.last()
        }
    }

    private suspend fun getOrComputeAddress(keyPair: ECKeyPair): Result<String> {
        val owner = Keys.getAddress(keyPair)
        val salt = Hash.sha3(Numeric.hexStringToByteArray(owner))
        val callData = FunctionEncoder.encode(
            Function(
                "createAccount",
                listOf(Address("0x$owner"), Uint256(BigInteger(salt))),
                emptyList()
            )
        )
        return try {
            val web3j = ethereumClient.getWeb3j()
            val result = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    NetworkConfig.ENTRY_POINT,
                    NetworkConfig.SIMPLE_ACCOUNT_FACTORY,
                    callData
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            if (result.hasError()) {
                Result.failure(Exception("eth_call error: ${result.error.message}"))
            } else {
                val address = "0x" + result.value.substring(result.value.length - 40)
                Result.success(address)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getNonce(address: String): Result<BigInteger> {
        val callData = FunctionEncoder.encode(
            Function("getNonce", listOf(Address(address)), emptyList())
        )
        return try {
            val web3j = ethereumClient.getWeb3j()
            val result = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    NetworkConfig.ENTRY_POINT,
                    NetworkConfig.ENTRY_POINT,
                    callData
                ),
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            if (result.hasError()) {
                Result.failure(Exception("getNonce error: ${result.error.message}"))
            } else {
                Result.success(Numeric.toBigInt(result.value))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun isDeployed(address: String): Boolean {
        return try {
            val web3j = ethereumClient.getWeb3j()
            val code = web3j.ethGetCode(
                address,
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send()
            code.code != null && code.code != "0x" && code.code.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun buildInitCode(keyPair: ECKeyPair): ByteArray {
        val owner = Keys.getAddress(keyPair)
        val salt = Hash.sha3(Numeric.hexStringToByteArray(owner))
        val createAccountData = FunctionEncoder.encode(
            Function(
                "createAccount",
                listOf(Address("0x$owner"), Uint256(BigInteger(salt))),
                emptyList()
            )
        )
        val factoryAddress = Numeric.hexStringToByteArray(NetworkConfig.SIMPLE_ACCOUNT_FACTORY)
        val createAccountBytes = Numeric.hexStringToByteArray(createAccountData)
        return factoryAddress + createAccountBytes
    }

    data class WebAuthnAssertion(
        val authenticatorData: ByteArray,
        val clientDataJSON: ByteArray,
        val signature: ByteArray
    )
}
