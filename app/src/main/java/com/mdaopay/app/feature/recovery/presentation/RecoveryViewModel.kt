package com.mdaopay.app.feature.recovery.presentation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ProcessLifecycleOwner
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.blockchain.WalletResult
import com.mdaopay.app.core.blockchain.erc4337.RecoveryUserOpBuilder
import com.mdaopay.app.core.datastore.UserPreferences
import com.mdaopay.app.core.guardian.GuardianInfo
import com.mdaopay.app.core.guardian.GuardianInvite
import com.mdaopay.app.core.guardian.GuardianManager
import com.mdaopay.app.core.guardian.GuardianStorage
import com.mdaopay.app.core.security.DeviceIntegrityManager
import com.mdaopay.app.core.security.PasskeyManager
import com.mdaopay.app.core.security.RecoveryShareManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.web3j.crypto.MnemonicUtils
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val walletManager: WalletManager,
    private val recoveryShareManager: RecoveryShareManager,
    private val passkeyManager: PasskeyManager,
    private val guardianManager: GuardianManager,
    private val guardianStorage: GuardianStorage,
    private val recoveryUserOpBuilder: RecoveryUserOpBuilder,
    private val userPreferences: UserPreferences,
    private val integrityManager: DeviceIntegrityManager,
) : ViewModel() {

    private val _mnemonic = MutableStateFlow<String?>(null)
    val mnemonic: StateFlow<String?> = _mnemonic.asStateFlow()

    private val _revealed = MutableStateFlow(false)
    val revealed: StateFlow<Boolean> = _revealed.asStateFlow()

    enum class Tab { SHOW, IMPORT, BACKUP, GUARDIANS }

    private val _tab = MutableStateFlow(Tab.SHOW)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _importSuccess = MutableStateFlow(false)
    val importSuccess: StateFlow<Boolean> = _importSuccess.asStateFlow()

    private val _hasShare1 = MutableStateFlow(false)
    val hasShare1: StateFlow<Boolean> = _hasShare1.asStateFlow()

    private val _hasShare2 = MutableStateFlow(false)
    val hasShare2: StateFlow<Boolean> = _hasShare2.asStateFlow()

    private val _hasShare3 = MutableStateFlow(false)
    val hasShare3: StateFlow<Boolean> = _hasShare3.asStateFlow()

    private val _hasShare4 = MutableStateFlow(false)
    val hasShare4: StateFlow<Boolean> = _hasShare4.asStateFlow()

    private val _hasPasskey = MutableStateFlow(false)
    val hasPasskey: StateFlow<Boolean> = _hasPasskey.asStateFlow()

    private val _share4ExportHex = MutableStateFlow<String?>(null)
    val share4ExportHex: StateFlow<String?> = _share4ExportHex.asStateFlow()

    private val _share4ImportError = MutableStateFlow<String?>(null)
    val share4ImportError: StateFlow<String?> = _share4ImportError.asStateFlow()

    private val _share4ImportSuccess = MutableStateFlow(false)
    val share4ImportSuccess: StateFlow<Boolean> = _share4ImportSuccess.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _myGuardians = MutableStateFlow<List<GuardianInfo>>(emptyList())
    val myGuardians: StateFlow<List<GuardianInfo>> = _myGuardians.asStateFlow()

    private val _myInvites = MutableStateFlow<List<GuardianInvite>>(emptyList())
    val myInvites: StateFlow<List<GuardianInvite>> = _myInvites.asStateFlow()

    private val _guardianError = MutableStateFlow<String?>(null)
    val guardianError: StateFlow<String?> = _guardianError.asStateFlow()

    private val _guardianLoading = MutableStateFlow(false)
    val guardianLoading: StateFlow<Boolean> = _guardianLoading.asStateFlow()

    private val _isHermitMode = MutableStateFlow(false)
    val isHermitMode: StateFlow<Boolean> = _isHermitMode.asStateFlow()

    private val _share3ExportHex = MutableStateFlow<String?>(null)
    val share3ExportHex: StateFlow<String?> = _share3ExportHex.asStateFlow()

    private val _share3ImportError = MutableStateFlow<String?>(null)
    val share3ImportError: StateFlow<String?> = _share3ImportError.asStateFlow()

    private val _share3ImportSuccess = MutableStateFlow(false)
    val share3ImportSuccess: StateFlow<Boolean> = _share3ImportSuccess.asStateFlow()

    private val _coldDevicePinSet = MutableStateFlow(false)
    val coldDevicePinSet: StateFlow<Boolean> = _coldDevicePinSet.asStateFlow()

    init {
        viewModelScope.launch {
            val scenario = userPreferences.getRecoveryScenario()
            _isHermitMode.value = scenario == "hermit"
            _coldDevicePinSet.value = userPreferences.getColdDevicePinHash() != null
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                _revealed.value = false
            }
        })
    }

    sealed class BackupState {
        object Idle : BackupState()
        object Creating : BackupState()
        object CreatingPasskey : BackupState()
        object Created : BackupState()
        object NeedBiometric : BackupState()
        object AuthenticatingPasskey : BackupState()
        object Recovering : BackupState()
        data class Success(val mnemonic: String) : BackupState()
        data class Error(val message: String) : BackupState()
    }

    fun setTab(t: Tab) {
        _tab.value = t
        _importError.value = null
        _importSuccess.value = false
        _share4ImportError.value = null
        _share4ImportSuccess.value = false
        if (t == Tab.BACKUP) checkShares()
        if (t == Tab.GUARDIANS && !_isHermitMode.value) loadGuardians()
    }

    fun loadMnemonic() {
        _mnemonic.value = walletManager.loadWallet().mapNullable { it.mnemonic }
    }

    fun reveal() {
        loadMnemonic()
        _revealed.value = true
    }

    fun lock() {
        _revealed.value = false
    }

    fun importMnemonic(phrase: String) {
        viewModelScope.launch {
            val integrity = integrityManager.checkIntegrity(
                DeviceIntegrityManager.WalletOperation.RECOVERY_INITIATE
            )
            if (integrity.level == DeviceIntegrityManager.IntegrityLevel.BLOCKED) {
                _importError.value = "Операция заблокирована: устройство не соответствует требованиям безопасности"
                return@launch
            }

            val cleaned = phrase.trim().lowercase()
            val words = cleaned.split("\\s+".toRegex())
            if (words.size < 12 || words.size > 24) {
                _importError.value = "Фраза должна содержать 12–24 слова"
                return@launch
            }
            try {
                MnemonicUtils.generateSeed(cleaned, "")
            } catch (e: Exception) {
                _importError.value = "Неверная seed-фраза: ошибка контрольной суммы"
                return@launch
            }
            val saved = walletManager.saveMnemonic(cleaned)
            if (saved) {
                _importSuccess.value = true
                _importError.value = null
            } else {
                _importError.value = "Ошибка при сохранении"
            }
        }
    }

    fun checkShares() {
        _hasShare1.value = recoveryShareManager.hasShare1()
        _hasShare2.value = recoveryShareManager.hasShare2()
        _hasShare3.value = recoveryShareManager.hasShare3()
        _hasShare4.value = recoveryShareManager.hasShare4()
        _hasPasskey.value = recoveryShareManager.getEvalInput() != null
    }

    fun createBackup() {
        viewModelScope.launch {
            val integrity = integrityManager.checkIntegrity(
                DeviceIntegrityManager.WalletOperation.RECOVERY_INITIATE
            )
            if (integrity.level == DeviceIntegrityManager.IntegrityLevel.BLOCKED) {
                _backupState.value = BackupState.Error(
                    "Операция заблокирована: устройство не соответствует требованиям безопасности"
                )
                return@launch
            }

            _backupState.value = BackupState.Creating
            val mnemonic = _mnemonic.value ?: run {
                when (val result = walletManager.loadWallet()) {
                    is WalletResult.Success -> _mnemonic.value = result.wallet.mnemonic
                    is WalletResult.Error -> {
                        _backupState.value = BackupState.Error("Не удалось загрузить кошелёк")
                        return@launch
                    }
                }
                _mnemonic.value ?: run {
                    _backupState.value = BackupState.Error("Кошелёк не найден")
                    return@launch
                }
            }
            if (mnemonic.isBlank()) {
                _backupState.value = BackupState.Error("Seed-фраза пуста")
                return@launch
            }

            try {
                val secret = mnemonic.encodeToByteArray()
                if (_isHermitMode.value) {
                    val shares = RecoveryShareManager.split(secret, RecoveryShareManager.HERMIT_REQUIRED, RecoveryShareManager.HERMIT_TOTAL)
                    val saved1 = recoveryShareManager.saveShare1(shares[0])
                    val saved3 = recoveryShareManager.saveShare3(shares[2])
                    if (!saved1 || !saved3) {
                        _backupState.value = BackupState.Error("Не удалось сохранить части")
                        return@launch
                    }
                    exportShare3()
                } else {
                    val shares = RecoveryShareManager.split(secret)
                    val saved1 = recoveryShareManager.saveShare1(shares[0])
                    val saved3 = recoveryShareManager.saveShare3(shares[2])
                    val saved4 = recoveryShareManager.saveShare4(shares[3])
                    if (!saved1 || !saved3 || !saved4) {
                        _backupState.value = BackupState.Error("Не удалось сохранить части")
                        return@launch
                    }
                    exportShare4()
                }

                checkShares()
                _backupState.value = BackupState.Created
            } catch (e: Throwable) {
                _backupState.value = BackupState.Error("Ошибка создания: ${e.localizedMessage ?: "неизвестная"}")
            }
        }
    }

    fun setupPasskeyBackup() {
        viewModelScope.launch {
            _backupState.value = BackupState.CreatingPasskey
            val mnemonic = _mnemonic.value ?: run {
                when (val result = walletManager.loadWallet()) {
                    is WalletResult.Success -> _mnemonic.value = result.wallet.mnemonic
                    is WalletResult.Error -> {
                        _backupState.value = BackupState.Error("Не удалось загрузить кошелёк")
                        return@launch
                    }
                }
                _mnemonic.value ?: run {
                    _backupState.value = BackupState.Error("Кошелёк не найден")
                    return@launch
                }
            }

            val userId = walletManager.loadWallet().mapNullable { it.address } ?: "mdaopay-user"

            val result = passkeyManager.createRecoveryPasskey(userId)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                _backupState.value = BackupState.Error(
                    when {
                        error is androidx.credentials.exceptions.CreateCredentialCancellationException ->
                            "Создание passkey отменено"
                        error?.message?.contains("PRF", ignoreCase = true) == true ->
                            "Ваше устройство не поддерживает PRF. Попробуйте другое устройство."
                        else -> "Ошибка: ${error?.localizedMessage ?: "неизвестная"}"
                    }
                )
                return@launch
            }

            val passkeyData = result.getOrThrow()
            val evalInput = recoveryShareManager.getEvalInput()
                ?: passkeyManager.generateEvalInput()
            recoveryShareManager.saveEvalInput(evalInput)

            val key = passkeyManager.deriveKeyFromPrf(passkeyData.prfOutput, "mdaopay-s2")

            val secret = mnemonic.encodeToByteArray()
            val shares = if (_isHermitMode.value) {
                RecoveryShareManager.split(secret, RecoveryShareManager.HERMIT_REQUIRED, RecoveryShareManager.HERMIT_TOTAL)
            } else {
                RecoveryShareManager.split(secret)
            }
            recoveryShareManager.saveShare2Encrypted(shares[1], key)

            checkShares()
            _backupState.value = BackupState.Created
        }
    }

    fun exportShare4() {
        _share4ExportHex.value = recoveryShareManager.exportShare4Hex()
    }

    fun hideShare4Export() {
        _share4ExportHex.value = null
    }

    fun importShare4FromHex(hex: String) {
        val success = recoveryShareManager.importShare4Hex(hex)
        if (success) {
            checkShares()
            _share4ImportSuccess.value = true
            _share4ImportError.value = null
        } else {
            _share4ImportError.value = "Неверный формат ключа восстановления"
            _share4ImportSuccess.value = false
        }
    }

    fun exportShare3() {
        _share3ExportHex.value = recoveryShareManager.exportShare3Hex()
    }

    fun hideShare3Export() {
        _share3ExportHex.value = null
    }

    fun importShare3FromHex(hex: String) {
        val success = recoveryShareManager.importShare3Hex(hex)
        if (success) {
            checkShares()
            _share3ImportSuccess.value = true
            _share3ImportError.value = null
        } else {
            _share3ImportError.value = "Неверный формат ключа холодного устройства"
            _share3ImportSuccess.value = false
        }
    }

    fun setColdDevicePin(pin: String) {
        viewModelScope.launch {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = pin.pbkdf2Hash(salt)
            userPreferences.setColdDevicePinSalt(salt.joinToString("") { "%02x".format(it) })
            userPreferences.setColdDevicePinHash(hash)
            _coldDevicePinSet.value = true
        }
    }

    fun verifyColdDevicePin(pin: String): Boolean {
        return runBlocking {
            val saltHex = userPreferences.getColdDevicePinSalt() ?: return@runBlocking false
            val storedHash = userPreferences.getColdDevicePinHash() ?: return@runBlocking false
            val hash = pin.pbkdf2Hash(saltHex.hexToByteArray())
            MessageDigest.isEqual(hash.encodeToByteArray(), storedHash.encodeToByteArray())
        }
    }

    fun startRecovery() {
        viewModelScope.launch {
            val integrity = integrityManager.checkIntegrity(
                DeviceIntegrityManager.WalletOperation.RECOVERY_INITIATE
            )
            if (integrity.level == DeviceIntegrityManager.IntegrityLevel.BLOCKED) {
                _backupState.value = BackupState.Error(
                    "Операция заблокирована: устройство не соответствует требованиям безопасности"
                )
                return@launch
            }
            _backupState.value = BackupState.NeedBiometric
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            try {
                _backupState.value = BackupState.AuthenticatingPasskey
                val evalInput = recoveryShareManager.getEvalInput()
                if (evalInput == null) {
                    _backupState.value = BackupState.Error("Passkey не настроен. Нет eval input.")
                    return@launch
                }

                val authResult = passkeyManager.authenticateWithPasskey(evalInput)
                if (authResult.isFailure) {
                    val error = authResult.exceptionOrNull()
                    _backupState.value = BackupState.Error(
                        when {
                            error is androidx.credentials.exceptions.GetCredentialCancellationException ->
                                "Аутентификация отменена"
                            else -> "Ошибка passkey: ${error?.localizedMessage ?: "неизвестная"}"
                        }
                    )
                    return@launch
                }

                _backupState.value = BackupState.Recovering

                val authData = authResult.getOrThrow()
                val key = passkeyManager.deriveKeyFromPrf(authData.prfOutput, "mdaopay-s2")
                val share2 = recoveryShareManager.getShare2Encrypted(key)
                val shares = mutableListOf<com.mdaopay.app.core.security.Share>()

                recoveryShareManager.getShare1()?.let { shares.add(it) }
                if (share2 != null) shares.add(share2)
                recoveryShareManager.getShare3()?.let { shares.add(it) }
                if (!_isHermitMode.value) {
                    recoveryShareManager.getShare4()?.let { shares.add(it) }
                }

                val required = if (_isHermitMode.value) RecoveryShareManager.HERMIT_REQUIRED else 3
                if (shares.size < required) {
                    _backupState.value = BackupState.Error(
                        if (shares.isEmpty()) "Нет резервных копий"
                        else "Нужно минимум $required из ${if (_isHermitMode.value) 3 else 4} частей (доступно ${shares.size})"
                    )
                    return@launch
                }

                val indices = shares.indices.toList()
                val combos = combinations(indices, required)
                var seed: ByteArray? = null

                for (combo in combos) {
                    val comboList = combo.map { shares[it] }
                    val result = RecoveryShareManager.join(comboList, required)
                    if (result != null) {
                        seed = result
                        break
                    }
                }

                if (seed != null) {
                    val mnemonic = seed.decodeToString()
                    val words = mnemonic.trim().split("\\s+".toRegex())
                    if (words.size !in 12..24) {
                        _backupState.value = BackupState.Error("Ошибка восстановления: неверная seed-фраза")
                        return@launch
                    }
                    if (walletManager.saveMnemonic(mnemonic)) {
                        _backupState.value = BackupState.Success(mnemonic)
                    } else {
                        _backupState.value = BackupState.Error("Не удалось сохранить кошелёк")
                    }
                } else {
                    _backupState.value = BackupState.Error("Ошибка восстановления seed-фразы")
                }
            } catch (e: Exception) {
                _backupState.value = BackupState.Error(
                    "Ошибка восстановления: ${e.localizedMessage ?: "неизвестная"}"
                )
            }
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupState.Idle
    }

    fun loadGuardians() {
        viewModelScope.launch {
            _guardianLoading.value = true
            try {
                _myGuardians.value = guardianManager.getMyGuardians()
                _myInvites.value = guardianManager.getMyInvites()
                _guardianError.value = null
            } catch (e: Exception) {
                _guardianError.value = e.localizedMessage ?: "Ошибка загрузки guardian'ов"
            } finally {
                _guardianLoading.value = false
            }
        }
    }

    fun inviteGuardian(guardianLabel: String, shareIndex: Int) {
        viewModelScope.launch {
            _guardianLoading.value = true
            _guardianError.value = null
            try {
                val wallet = walletManager.getWalletData()
                if (wallet == null) {
                    _guardianError.value = "Кошелёк не загружен"
                    return@launch
                }
                val result = guardianManager.inviteGuardian(
                    walletAddress = wallet.address,
                    guardianLabel = guardianLabel,
                    shareIndex = shareIndex,
                    fcmToken = ""
                )
                result.onFailure { e ->
                    _guardianError.value = e.localizedMessage ?: "Ошибка приглашения"
                }
                loadGuardians()
            } catch (e: Exception) {
                _guardianError.value = e.localizedMessage ?: "Ошибка"
            } finally {
                _guardianLoading.value = false
            }
        }
    }

    fun removeGuardian(identityHash: String) {
        viewModelScope.launch {
            try {
                guardianStorage.removeGuardian(identityHash)
                loadGuardians()
            } catch (e: Exception) {
                _guardianError.value = e.localizedMessage ?: "Ошибка удаления"
            }
        }
    }

    fun acceptInvite(inviteId: String) {
        viewModelScope.launch {
            _guardianLoading.value = true
            try {
                val userId = walletManager.getWalletData()?.address ?: "guardian"
                val result = guardianManager.acceptInvite(inviteId, userId)
                result.onFailure { e ->
                    _guardianError.value = e.localizedMessage ?: "Ошибка принятия инвайта"
                }
                loadGuardians()
            } catch (e: Exception) {
                _guardianError.value = e.localizedMessage ?: "Ошибка"
            } finally {
                _guardianLoading.value = false
            }
        }
    }

    fun submitRecoveryExecution() {
        viewModelScope.launch {
            _backupState.value = BackupState.Recovering
            try {
                val addressResult = recoveryUserOpBuilder.getSmartAccountAddress()
                val address = addressResult.getOrElse {
                    _backupState.value = BackupState.Error("Не удалось получить адрес аккаунта")
                    return@launch
                }
                val txResult = recoveryUserOpBuilder.buildRecoveryExecutionUserOp(address)
                txResult.onSuccess { txHash ->
                    _backupState.value = BackupState.Success("Recovery submitted: $txHash")
                }.onFailure { e ->
                    _backupState.value = BackupState.Error("Ошибка отправки recovery: ${e.localizedMessage}")
                }
            } catch (e: Exception) {
                _backupState.value = BackupState.Error("Ошибка: ${e.localizedMessage}")
            }
        }
    }

    private fun combinations(n: List<Int>, k: Int): List<List<Int>> {
        if (k == 0) return listOf(emptyList())
        if (n.size < k) return emptyList()
        val first = n.first()
        val rest = n.drop(1)
        return combinations(rest, k - 1).map { listOf(first) + it } + combinations(rest, k)
    }
}

// F-121: PBKDF2-HMAC-SHA256 with 600k iterations (OWASP 2023 minimum)
// ponytail: Argon2id migration planned (Phase 1) — see security/findings/F-121.md
private const val PBKDF2_ITERATIONS = 600_000

private fun String.pbkdf2Hash(salt: ByteArray): String {
    val spec = PBEKeySpec(this.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
}

private fun String.hexToByteArray(): ByteArray {
    val len = length / 2
    return ByteArray(len) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
