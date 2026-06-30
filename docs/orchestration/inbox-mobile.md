# Inbox: Mobile affected files (external audit)

## 1. NetworkModule.kt — CertificatePinner setup with CERT_PIN_API

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/di/NetworkModule.kt`

**Релевантные строки (23–26):**
```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.mdaopay.com", BuildConfig.CERT_PIN_API)
    .add("mdaopay.com", BuildConfig.CERT_PIN_BACKUP)
    .build()
```

**Всего строк:** 35 (конец файла)

---

## 2. app/build.gradle.kts — buildConfigField для CERT_PIN_API, RPC_URL_1_DEV, RPC_URL_2

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/build.gradle.kts`

**Релевантные строки:**

**CERT_PIN_API (строка 79):**
```kotlin
buildConfigField("String", "CERT_PIN_API", "\"${project.findProperty("CERT_PIN_API") ?: "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}\"")
```

**RPC_URL_1_DEV / RPC_URL_2_DEV (строки 33–34):**
```kotlin
buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_DEV") ?: "https://ethereum-sepolia.publicnode.com"}\"")
buildConfigField("String", "RPC_URL_2", "\"${project.findProperty("RPC_URL_2_DEV") ?: "https://rpc.ankr.com/eth_sepolia"}\"")
```

**RPC_URL_1_STAGING / RPC_URL_2_STAGING (строки 49–50):**
```kotlin
buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_STAGING") ?: "https://ethereum-sepolia.publicnode.com"}\"")
buildConfigField("String", "RPC_URL_2", "\"${project.findProperty("RPC_URL_2_STAGING") ?: "https://rpc.ankr.com/eth_sepolia"}\"")
```

**RPC_URL_1_PROD / RPC_URL_2_PROD (строки 61–62):**
```kotlin
buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_PROD") ?: ""}\"")
buildConfigField("String", "RPC_URL_2", "\"${project.findProperty("RPC_URL_2_PROD") ?: ""}\"")
```

**Всего строк:** 220

---

## 3. NetworkConfig.kt — CHAIN_ID, MDAO_CONTRACT, SOCIAL_RECOVERY_MODULE, isConfigured()

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/blockchain/NetworkConfig.kt`

**Релевантные строки:**

**CHAIN_ID (строка 11):**
```kotlin
const val CHAIN_ID = 56L
```

**MDAO_CONTRACT (строка 13):**
```kotlin
const val MDAO_CONTRACT = "0x0000000000000000000000000000000000000000" // Set by deploy script
```

**SOCIAL_RECOVERY_MODULE (строка 19):**
```kotlin
const val SOCIAL_RECOVERY_MODULE = "0x0000000000000000000000000000000000000000" // Set by deploy script
```

**isConfigured() (строки 24–26):**
```kotlin
fun isConfigured(): Boolean =
    MDAO_CONTRACT != "0x0000000000000000000000000000000000000000" &&
    SOCIAL_RECOVERY_MODULE != "0x0000000000000000000000000000000000000000"
```

**Всего строк:** 39

---

## 4. EthereumProviderInjector.kt — handleSendTransaction

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/blockchain/EthereumProviderInjector.kt`

**Релевантные строки (198–210):**
```kotlin
private fun handleSendTransaction(params: JSONArray, origin: String): String {
    if (params.length() < 1) return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"Missing tx params"}}"""
    if (wallet == null) return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"Wallet not available"}}"""
    val tx = params.getJSONObject(0)
    val to = tx.optString("to", "")
    val value = tx.optString("value", "0x0")

    if (!confirmAction(origin, "Send transaction to:\n$to\nValue: $value")) {
        return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"User rejected transaction"}}"""
    }

    return """{"jsonrpc":"2.0","id":"1","result":"0x0000000000000000000000000000000000000000000000000000000000000000"}"""
}
```

**Всего строк:** 306

---

## 5. DeviceIntegrityManager.kt — checkIntegrity с isRooted/isEmulator

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/security/DeviceIntegrityManager.kt`

**Релевантные строки:**

**checkIntegrity (строки 55–69):**
```kotlin
suspend fun checkIntegrity(operation: WalletOperation): IntegrityResult {
    if (isRooted()) {
        return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Rooted device"))
    }
    if (isEmulator()) {
        return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Emulator"))
    }
    if (operation.riskLevel == RiskLevel.LOW) {
        return IntegrityResult(IntegrityLevel.TRUSTED, emptyList())
    }
    return checkPlayIntegrity(operation)
}
```

**isRooted (строки 75–111):**
```kotlin
private fun isRooted(): Boolean { ... }
```

**isEmulator (строки 113–133):**
```kotlin
private fun isEmulator(): Boolean { ... }
```

**Всего строк:** 385

---

## 6. TxQueueEntity.kt — полное содержимое

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/datastore/TxQueueEntity.kt`

**Полный файл (строки 1–47):**
```kotlin
@Entity(tableName = "tx_queue")
data class TxQueueEntity(
    @PrimaryKey
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "recipient_address")
    val recipientAddress: String,
    @ColumnInfo(name = "wei_amount")
    val weiAmount: String,
    val nickname: String,
    @ColumnInfo(name = "display_amount")
    val displayAmount: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)
```

**Всего строк:** 47

---

## 7. KeystoreCrypto.kt — encrypt и getOrCreateBiometricKey

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/security/KeystoreCrypto.kt`

**Релевантные строки:**

**getOrCreateBiometricKey (строки 24–29):**
```kotlin
fun getOrCreateBiometricKey(alias: String): SecretKey {
    if (keystore.containsAlias(alias)) {
        return keystore.getKey(alias, null) as SecretKey
    }
    return generateKey(alias, requireAuth = true)
}
```

**encrypt (строки 70–76):**
```kotlin
fun encrypt(keyAlias: String, plaintext: ByteArray): ByteArray {
    val key = getOrCreateBiometricKey(keyAlias)
    val cipher = Cipher.getInstance(AES_GCM)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val ciphertext = cipher.doFinal(plaintext)
    return cipher.iv + ciphertext
}
```

**Всего строк:** 98

---

## 8. WalletManager.kt — saveMnemonic

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/blockchain/WalletManager.kt`

**Релевантные строки (73–80):**
```kotlin
fun saveMnemonic(mnemonic: String): Boolean {
    return try {
        val encrypted = KeystoreCrypto.encrypt(KEY_ALIAS, mnemonic.encodeToByteArray())
        prefs.edit().putString(KEY_MNEMONIC, encrypted.toHexString()).commit()
    } catch (e: Exception) {
        false
    }
}
```

**Всего строк:** 127

---

## 9. RecoveryScreen.kt (1 версия)

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/feature/recovery/presentation/RecoveryScreen.kt`

**Поиск второй версии:** не найдено (только 1 файл).

**Всего строк:** 1139

---

## 10. ContactsScreen.kt — avatar circle с colorPair

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/feature/contacts/presentation/ContactsScreen.kt`

**Релевантные строки (207–215):**
```kotlin
val avatarColors = listOf(
    listOf(Color(0xFFFF6B00), Color(0xFFFF9A4D)),
    listOf(Color(0xFF2D7FF9), Color(0xFF5DA5FF)),
    listOf(Color(0xFF00B377), Color(0xFF3DD99A)),
    listOf(Color(0xFF7B4DFF), Color(0xFFA884FF)),
    listOf(Color(0xFFF94D9E), Color(0xFFFF85B8)),
    listOf(Color(0xFFFFB300), Color(0xFFFFD166)),
)
val colorPair = avatarColors[contact.nickname.length % avatarColors.size]
```

**Использование (строка 231):**
```kotlin
.drawBehind {
    drawCircle(colorPair[0])
}
```

**Всего строк:** 305

---

## 11. HomeScreen.kt — walletToTokens

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/feature/home/presentation/HomeScreen.kt`

**Релевантные строки (588–599):**
```kotlin
private data class TokenInfo(
    val symbol: String,
    val balance: java.math.BigDecimal,
    val accentColor: androidx.compose.ui.graphics.Color
)

private fun walletToTokens(wallet: WalletState): List<TokenInfo> = buildList {
    add(TokenInfo("MDAO", wallet.balanceMdao, MDAOPurple))
    add(TokenInfo("USDT", wallet.balanceUsdt, SuccessGreen))
    if (wallet.balanceEth > java.math.BigDecimal.ZERO)
        add(TokenInfo("Sepolia ETH", wallet.balanceEth, androidx.compose.ui.graphics.Color(0xFF627EEA)))
}
```

**Всего строк:** 1059

---

## 12. SettingsScreen.kt — RoundedCornerShape с java.lang.Float.valueOf

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/feature/settings/presentation/SettingsScreen.kt`

**Релевантная строка (187):**
```kotlin
.clip(java.lang.Float.valueOf(MDARadius.xxl.value).let { androidx.compose.foundation.shape.RoundedCornerShape(it.dp) })
```

**Контекст (строки 184–191):**
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(java.lang.Float.valueOf(MDARadius.xxl.value).let { androidx.compose.foundation.shape.RoundedCornerShape(it.dp) })
        .background(d.card)
        .shadow(elevation = 4.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(MDARadius.xxl), clip = false)
        .clickable { onProfileClick() }
        .padding(14.dp, 14.dp)
)
```

**Всего строк:** 625

---

## 13. SocialAuthManager.kt — WEB_CLIENT_ID

**Полный путь:**  
`/home/ekzent/project/MDAOPay/app/src/main/java/com/mdaopay/app/core/security/SocialAuthManager.kt`

**Релевантная строка (145):**
```kotlin
const val WEB_CLIENT_ID = "925151210559-01ge4gml47c0u6pnpu88ebf6hbfntmu2.apps.googleusercontent.com"
```

**Использование (строка 41):**
```kotlin
.setServerClientId(WEB_CLIENT_ID)
```

**Всего строк:** 153

---

## Сводка

| № | Файл | Строк | Ключевые строки |
|---|------|-------|-----------------|
| 1 | NetworkModule.kt | 35 | 23-26 |
| 2 | app/build.gradle.kts | 220 | 33-34, 49-50, 61-62, 79-80 |
| 3 | NetworkConfig.kt | 39 | 11, 13, 19, 24-26 |
| 4 | EthereumProviderInjector.kt | 306 | 198-210 |
| 5 | DeviceIntegrityManager.kt | 385 | 55-69, 75-111, 113-133 |
| 6 | TxQueueEntity.kt | 47 | весь файл |
| 7 | KeystoreCrypto.kt | 98 | 24-29, 70-76 |
| 8 | WalletManager.kt | 127 | 73-80 |
| 9 | RecoveryScreen.kt | 1139 | весь файл |
| 10 | ContactsScreen.kt | 305 | 207-215, 231 |
| 11 | HomeScreen.kt | 1059 | 588-599 |
| 12 | SettingsScreen.kt | 625 | 187 |
| 13 | SocialAuthManager.kt | 153 | 41, 145 |
