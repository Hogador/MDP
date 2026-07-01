package com.mdaopay.app.feature.send.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdaopay.app.core.blockchain.BlockchainRepository
import com.mdaopay.app.core.blockchain.NicknameResolver
import com.mdaopay.app.core.blockchain.WalletManager
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.isRetryable
import com.mdaopay.app.core.common.toDisplayAmount
import com.mdaopay.app.core.common.toUserMessage
import com.mdaopay.app.core.datastore.Contact
import com.mdaopay.app.core.datastore.ContactsStore
import com.mdaopay.app.core.datastore.TransactionHistory
import com.mdaopay.app.core.security.DeviceIntegrityManager
import com.mdaopay.app.domain.usecase.GaslessSendResult
import com.mdaopay.app.feature.send.domain.OfflineQueuedException
import com.mdaopay.app.feature.send.domain.SendTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

sealed class SendState {
    data object Idle : SendState()
    data class RecipientInput(val nickname: String = "", val error: String? = null) : SendState()
    data class AmountInput(
        val nickname: String,
        val amount: BigDecimal = BigDecimal.ZERO,
        val error: String? = null
    ) : SendState()
    data class Confirmation(
        val nickname: String,
        val amount: BigDecimal,
        val address: String,
        val feeMdao: BigDecimal,
        val feeUsd: BigDecimal,
        val estimatedSeconds: Int
    ) : SendState()
    data object Processing : SendState()
    data class Success(val txHash: String) : SendState()
    data class Queued(
        val nickname: String,
        val amount: BigDecimal
    ) : SendState()
    data class Error(
        val appError: AppError,
        val failedState: SendState
    ) : SendState() {
        val message: String get() = appError.toUserMessage()
        val retryable: Boolean get() = appError.isRetryable()
    }
}

@HiltViewModel
class SendViewModel @Inject constructor(
    private val sendTransactionUseCase: SendTransactionUseCase,
    private val blockchainRepository: BlockchainRepository,
    private val walletManager: WalletManager,
    private val nicknameResolver: NicknameResolver,
    private val transactionHistory: TransactionHistory,
    private val contactsStore: ContactsStore,
    private val integrityManager: DeviceIntegrityManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<SendState>(SendState.Idle)
    val state: StateFlow<SendState> = _state.asStateFlow()

    private var recipientNickname = ""
    private var recipientAddress = ""
    private var sendAmount = BigDecimal.ZERO

    init {
        val to = savedStateHandle.get<String>("to") ?: ""
        val amount = savedStateHandle.get<String>("amount") ?: ""
        if (to.isNotBlank()) {
            if (to.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                recipientNickname = to.take(12)
                recipientAddress = to
                val parsedAmount = amount.toBigDecimalOrNull()
                if (parsedAmount != null && parsedAmount > BigDecimal.ZERO) {
                    sendAmount = parsedAmount
                    _state.value = SendState.AmountInput(nickname = to, amount = parsedAmount)
                } else {
                    _state.value = SendState.AmountInput(nickname = to)
                }
            } else {
                onNicknameChanged(to)
            }
        }
    }

    fun onNicknameChanged(value: String) {
        val stripped = if (value.startsWith("@")) value.removePrefix("@") else value
        if (stripped.length > 42) return
        val current = _state.value as? SendState.RecipientInput ?: SendState.RecipientInput()
        _state.value = current.copy(nickname = stripped, error = null)
    }

    fun onRecipientConfirmed(input: String) {
        val nickname = if (input.startsWith("@")) input.removePrefix("@") else input

        if (nickname.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
            recipientNickname = nickname.take(12)
            recipientAddress = nickname
            _state.value = SendState.AmountInput(nickname = nickname)
            return
        }

        if (nickname.length < 3 || nickname.length > 20 || !nickname.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            _state.value = SendState.RecipientInput(
                nickname = nickname,
                error = "Введите @username, адрес Ethereum (0x...) или выберите контакт"
            )
            return
        }

        viewModelScope.launch {
            val resolved = resolveRecipient(nickname)
            if (resolved != null) {
                recipientNickname = nickname.take(12)
                recipientAddress = resolved
                _state.value = SendState.AmountInput(nickname = "@$nickname")
            } else {
                _state.value = SendState.RecipientInput(
                    nickname = nickname,
                    error = "Пользователь «$nickname» не найден"
                )
            }
        }
    }

    private suspend fun resolveRecipient(nickname: String): String? {
        val fromResolver = nicknameResolver.resolve(nickname)
        if (fromResolver != null) return fromResolver

        val fromContacts = contactsStore.getContacts()
            .find { it.nickname.equals(nickname, ignoreCase = true) }
        if (fromContacts != null) return fromContacts.address

        return null
    }

    fun onAmountChanged(value: String) {
        val current = _state.value as? SendState.AmountInput ?: return
        val sanitized = value.replace(",", ".")
        if (sanitized.count { it == '.' } > 1) return
        if (sanitized.length > 15) return
        val amount = sanitized.toBigDecimalOrNull() ?: BigDecimal.ZERO
        _state.value = current.copy(amount = amount, error = null)
    }

    fun onAmountConfirmed(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            val state = _state.value as? SendState.AmountInput ?: return
            _state.value = state.copy(error = "Сумма должна быть больше 0")
            return
        }
        sendAmount = amount
        checkBalanceAndProceed(amount)
    }

    fun onBiometricFailed() {
        _state.value = SendState.Error(
            appError = AppError.BiometricFailed,
            failedState = buildConfirmationState()
        )
    }

    fun onSendConfirmed() {
        viewModelScope.launch {
            val operation = if (sendAmount >= BigDecimal("1000")) {
                DeviceIntegrityManager.WalletOperation.LARGE_TRANSFER
            } else {
                DeviceIntegrityManager.WalletOperation.SMALL_TRANSFER
            }
            val integrity = integrityManager.checkIntegrity(operation)
            if (integrity.level == DeviceIntegrityManager.IntegrityLevel.BLOCKED) {
                _state.value = SendState.Error(
                    appError = AppError.BiometricFailed,
                    failedState = buildConfirmationState()
                )
                return@launch
            }

            val hasPending = transactionHistory.getTransactions()
                .any { it.status == "PENDING" }
            if (hasPending) {
                _state.value = SendState.Error(
                    appError = AppError.PendingTransactionExists,
                    failedState = buildConfirmationState()
                )
                return@launch
            }
            _state.value = SendState.Processing
            executeSend()
        }
    }

    fun onRetry() {
        _state.value = buildConfirmationState()
    }

    fun onBackToAmount() {
        _state.value = SendState.AmountInput(
            nickname = recipientNickname.ifBlank { recipientAddress.take(12) },
            amount = sendAmount
        )
    }

    fun onReset() {
        recipientNickname = ""
        recipientAddress = ""
        sendAmount = BigDecimal.ZERO
        _state.value = SendState.Idle
    }

    fun onDismissError() {
        val current = _state.value
        if (current is SendState.Error) {
            _state.value = current.failedState
        }
    }

    private fun buildConfirmationState() = SendState.Confirmation(
        nickname = recipientNickname,
        amount = sendAmount,
        address = recipientAddress,
        feeMdao = BigDecimal("0.2"),
        feeUsd = BigDecimal("0.04"),
        estimatedSeconds = 3
    )

    private fun checkBalanceAndProceed(amount: BigDecimal) {
        viewModelScope.launch {
            if (!recipientAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
                val current = _state.value as? SendState.AmountInput ?: return@launch
                _state.value = current.copy(error = "Для отправки нужен адрес кошелька (0x...)")
                return@launch
            }

            val walletData = walletManager.getWalletData()
            if (walletData == null) {
                val current = _state.value as? SendState.AmountInput ?: return@launch
                _state.value = current.copy(error = "Кошелёк не найден")
                return@launch
            }

            val balanceResult = blockchainRepository.getUsdtBalance(walletData.address)
            if (balanceResult.isSuccess) {
                val balance = balanceResult.getOrNull() ?: BigDecimal.ZERO
                if (amount > balance) {
                    val current = _state.value as? SendState.AmountInput ?: return@launch
                    _state.value = current.copy(
                        error = "Недостаточно USDT: баланс ${balance.toDisplayAmount()}"
                    )
                    return@launch
                }
            }

            _state.value = buildConfirmationState()
        }
    }

    private fun executeSend() {
        viewModelScope.launch {
            val weiAmount = sendAmount.movePointRight(18).toBigInteger()

            val result = sendTransactionUseCase.send(
                recipientAddress = recipientAddress,
                weiAmount = weiAmount,
                nickname = recipientNickname,
                displayAmount = sendAmount
            )
            when (result) {
                is com.mdaopay.app.core.common.Result.Success -> {
                    val sendResult = result.data
                    val txHash = sendResult.txHash
                    // ponytail: usedFallback=true → gasless недоступен, оплата из BNB
                    val exists = contactsStore.hasContact(recipientNickname)
                    if (!exists && recipientAddress.startsWith("0x")) {
                        contactsStore.addContact(
                            Contact(
                                id = recipientAddress,
                                nickname = recipientNickname,
                                address = recipientAddress,
                                addedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    _state.value = SendState.Success(txHash = txHash)
                }
                is com.mdaopay.app.core.common.Result.Error -> {
                    val error = result.error
                    if (error is AppError.Unknown && error.throwable is OfflineQueuedException) {
                        val queuedError = error.throwable as OfflineQueuedException
                        _state.value = SendState.Queued(
                            nickname = queuedError.nickname,
                            amount = queuedError.displayAmount
                        )
                    } else {
                        _state.value = SendState.Error(
                            appError = error,
                            failedState = buildConfirmationState()
                        )
                    }
                }
                is com.mdaopay.app.core.common.Result.Loading -> {}
            }
        }
    }
}
