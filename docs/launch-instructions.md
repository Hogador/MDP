# Запуск MDAOPay на BSC Testnet — инструкция

## ⏳ Шаг 0. Развернуть контракты

Контракты деплоятся скриптом `Deploy.s.sol`:

```bash
cd contracts
forge script script/Deploy.s.sol \
  --rpc-url https://data-seed-prebsc-1-s1.binance.org:8545 \
  --broadcast --verify \
  --verifier-url https://testnet.bscscan.com/api \
  --etherscan-api-key $BSCSCAN_API_KEY
```

После деплоя запишите адрес MDAO токена.

Проверка транзакции:

```bash
curl -s "https://data-seed-prebsc-1-s1.binance.org:8545" \
  -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_getTransactionReceipt","params":["<TX_HASH>"],"id":1}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); r=d.get('result'); print('Статус:', 'MINED' if r else 'PENDING')"
```

**Если MINED** — MDAO развёрнут (адрес появится в логе forge script).

**Контракты BSC Testnet:**
| Контракт | Адрес |
|---|---|
| USDT (тестовый) | `0x337610d27c682E347C9cD60BD4b3b107C9d34dDD` |
| WBNB | `0xae13d989daC2f0dEbFf460aC112a837C89BAa7cd` |
| EntryPoint | `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` |
| MDAO | определяется после деплоя |

---

## 🔥 Шаг 1. Создать Firebase проект (для push-уведомлений)

### Если вы в РФ — используйте VPN

1. Откройте https://console.firebase.google.com/
2. Нажмите **Create a project** (или **Добавить проект**)
3. Название: `MDAOPay` (любое)
4. Google Analytics — **отключите** (не нужен)
5. Дождитесь создания проекта

### Добавить Android приложение

1. На главной проекта нажмите **Android** иконку
2. **Package name:** `com.mdaopay.app`
3. **App nickname:** `MDAOPay Debug`
4. **Debug signing certificate SHA-1** — оставьте пустым
5. Нажмите **Register app**

### Скачать google-services.json

1. Нажмите **Download google-services.json**
2. Положите файл в:
   ```
   MDAOPay/app/google-services.json
   ```
3. Нажмите **Next**, **Next**, **Finish** (галочки в консоли можно пропустить)

---

## 📱 Шаг 2. Включить FCM в коде

### 2.1. Откройте `app/build.gradle.kts`

В корне файла, после `id("kotlin.compose")`, добавьте:

```kotlin
id("com.google.gms.google-services") version "4.4.2"
```

Должно выглядеть так:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") version "4.4.2"   // <-- добавить
    alias(libs.plugins.kotlin.serialization)
    // ...
}
```

### 2.2. Откройте `build.gradle.kts` (корень проекта)

Добавьте ту же строку, но с `apply false`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false  // <-- добавить
    // ...
}
```

### 2.3. Раскомментируйте FCM service

Откройте `app/src/main/AndroidManifest.xml`

Найдите блок:

```xml
<!--
<service
    android:name=".core.notification.MDAOFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
-->
```

Удалите `<!--` и `-->` вокруг него.

### 2.4. Соберите проект

```bash
./gradlew :app:compileDebugKotlin
```

Если ошибок нет — FCM работает.

---

## 🐳 Шаг 3. Запустить backend

```bash
cd backend
cp .env.bsc .env
docker compose up -d
```

Проверка:
```bash
curl http://localhost:8080/health
```

**Ожидаемый ответ:** `{"status":"ok"}`

---

## 📲 Шаг 4. Собрать и установить app на телефон

### 4.1. Узнать IP вашего компьютера в локальной сети

```bash
# Linux
hostname -I

# macOS
ipconfig getifaddr en0
```

IP будет что-то вроде `192.168.x.x`

### 4.2. Собрать debug .apk

```bash
cd MDAOPay
./gradlew :app:assembleDebug
```

### 4.3. Установить на телефон

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Либо** через Android Studio: Run → выберите ваше устройство

> **Важно:** телефон и компьютер должны быть в одной сети Wi-Fi.
> Backend URL уже настроен на `10.0.2.2:8080` для эмулятора.
> Для реального телефона нужно указать ваш IP:

```bash
# В терминале перед сборкой:
echo "BACKEND_URL=http://192.168.x.x:8080" >> ~/.gradle/gradle.properties
# или установите в файл gradle.properties в корне проекта
```

---

## ✅ Шаг 5. Проверка работы

| Что проверить | Как |
|---|---|
| Push приходит | Отправить USDT → уведомление "Транзакция отправлена" |
| Badge на иконке | После уведомления — цифра на иконке |
| DND режим | Включите "Не беспокоить" → уведомления платежей всё равно приходят |
| WorkManager fallback | Отключите интернет → через 15 мин после восстановления проверит статус |
| Транзакция в BSCscan | Откройте `https://testnet.bscscan.com/tx/<TX>` |

---

## ⚠️ Если что-то пошло не так

### FCM не отправляет уведомления
- Проверьте что `google-services.json` в `app/`
- Проверьте что FCM service раскомментирован в `AndroidManifest.xml`
- Пересоберите проект

### Backend не отвечает
- `docker ps` — контейнер запущен?
- `docker logs mdaopay-paymaster -f` — что в логах?
- `.env` скопирован с правильными значениями?

### Транзакция не отправляется
- `Pimlico` ключ в `~/.gradle/gradle.properties`?
- `BACKEND_URL` указывает на ваш IP?
- Телефон и компьютер в одной сети?
- Есть ли tBNB на кошельке? (запросите через https://testnet.bnbchain.org/faucet-smart)
