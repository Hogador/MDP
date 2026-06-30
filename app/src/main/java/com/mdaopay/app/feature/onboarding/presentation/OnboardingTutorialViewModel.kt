package com.mdaopay.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel

class OnboardingTutorialViewModel : ViewModel() {

    enum class TutorialPage(
        val icon: String,
        val title: String,
        val subtitle: String,
        val body: String
    ) {
        WELCOME(
            icon = "\uD83D\uDE80",
            title = "MDAO Pay",
            subtitle = "Цифровые деньги, которые ощущаются естественно",
            body = "MDAO Pay — это не просто кошелёк. Это новый способ ощущать цифровые деньги: тактильно, понятно, естественно."
        ),
        SELF_CUSTODY(
            icon = "\uD83D\uDD10",
            title = "Только ты контролируешь",
            subtitle = "Self-Custody кошелёк",
            body = "Твои ключи — твои деньги. Seed-фраза хранится только на твоём устройстве, в зашифрованном хранилище. Никто, кроме тебя, не имеет доступа к средствам."
        ),
        ACCOUNT_ABSTRACTION(
            icon = "\u26A1",
            title = "Умные платежи",
            subtitle = "Технология ERC-4337",
            body = "Плати как в обычном приложении — без сложных газовых токенов. Аккаунт создаётся автоматически при первом переводе. А биометрия заменяет подпись транзакции."
        ),
        START(
            icon = "\uD83C\uDF1F",
            title = "Готов?",
            subtitle = "Всё настроено для работы",
            body = "Кошелёк будет создан за секунду. Ты получишь уникальный никнейм, а твои средства будут под защитой биометрии и шифрования."
        )
    }
}
