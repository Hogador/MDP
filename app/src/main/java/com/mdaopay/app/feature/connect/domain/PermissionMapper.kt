package com.mdaopay.app.feature.connect.domain

import java.math.BigInteger

object PermissionMapper {

    enum class Capability {
        PROFILE_READ,
        ADDRESS_READ,
        BALANCE_READ,
        PAYMENTS_SEND,
        NFT_TRANSFER,
        APPROVE_TOKENS,
        ADMIN_ACTIONS,
        UNKNOWN
    }

    data class Permission(
        val capability: Capability,
        val title: String,
        val description: String,
        val isDangerous: Boolean = false,
        val limit: String? = null
    )

    fun mapPermissions(
        selectors: List<String>,
        maxAmount: BigInteger? = null,
        dailyLimit: BigInteger? = null,
        allowedTokens: List<String>? = null
    ): List<Permission> {
        return selectors.map { selector ->
            val cap = mapSelector(selector)
            buildPermission(cap, maxAmount, dailyLimit, allowedTokens)
        }
    }

    private fun mapSelector(selector: String): Capability {
        return when (selector.lowercase().removePrefix("0x")) {
            "a9059cbb" -> Capability.PAYMENTS_SEND
            "095ea7b3" -> Capability.APPROVE_TOKENS
            "42842e0e", "b88d4fde" -> Capability.NFT_TRANSFER
            "70a08231" -> Capability.BALANCE_READ
            "06fdde03" -> Capability.PROFILE_READ
            else -> Capability.UNKNOWN
        }
    }

    private fun buildPermission(
        cap: Capability,
        maxAmount: BigInteger?,
        dailyLimit: BigInteger?,
        allowedTokens: List<String>?
    ): Permission {
        return when (cap) {
            Capability.PROFILE_READ -> Permission(
                cap, "Доступ к профилю", "Чтение вашего @username и аватара"
            )
            Capability.ADDRESS_READ -> Permission(
                cap, "Доступ к адресу", "Чтение адреса вашего кошелька"
            )
            Capability.BALANCE_READ -> Permission(
                cap, "Просмотр баланса", "Сервис может видеть ваши токены"
            )
            Capability.PAYMENTS_SEND -> Permission(
                cap, "Отправка платежей",
                "Лимит: ${maxAmount?.toReadable()} за операцию" +
                if (dailyLimit != null) ", ${dailyLimit.toReadable()} в день" else "",
                limit = maxAmount?.toString()
            )
            Capability.NFT_TRANSFER -> Permission(
                cap, "Перемещение NFT", "Сервис может переводить ваши NFT", isDangerous = true
            )
            Capability.APPROVE_TOKENS -> Permission(
                cap, "Approve токенов", "Сервис может разрешить вывод ваших токенов", isDangerous = true
            )
            Capability.ADMIN_ACTIONS -> Permission(
                cap, "Административные действия", "Смена настроек, recovery", isDangerous = true
            )
            Capability.UNKNOWN -> Permission(
                cap, "Неизвестная операция", "Сервис запрашивает неизвестное действие. Рекомендуется отклонить.", isDangerous = true
            )
        }
    }

    fun hasDangerousPermissions(permissions: List<Permission>): Boolean = permissions.any { it.isDangerous }

    fun hasUnknownPermissions(permissions: List<Permission>): Boolean = permissions.any { it.capability == Capability.UNKNOWN }

    private fun BigInteger.toReadable(): String {
        val eth = this.toDouble() / 1e18
        return if (eth >= 1.0) String.format("%.2f MDAO", eth) else "${this} wei"
    }
}
