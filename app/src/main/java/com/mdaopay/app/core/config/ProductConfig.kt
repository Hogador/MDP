package com.mdaopay.app.core.config

data class ProductItem(
    val name: String,
    val description: String,
    val url: String,
    val icon: String,
    val showOnHome: Boolean = true,
)

val allProducts = listOf(
    ProductItem("MarsDAO Arena", "Турниры, ставки и соревнования", "https://arena.daomars.com", "\u2694\uFE0F", showOnHome = true),
    ProductItem("MarsDAO DEX", "Децентрализованный обмен токенов", "https://dex.daomars.com", "\uD83D\uDD04", showOnHome = true),
    ProductItem("Flopi Dating", "Децентрализованный дейтинг", "https://t.me/flopi_dating_bot", "\uD83D\uDC9C", showOnHome = true),
    ProductItem("Prosto VPN", "Безопасный VPN от MarsDAO", "https://t.me/v_utushkin/66560", "\uD83D\uDD12", showOnHome = true),
    ProductItem("Mommy's Trader", "Покупка/продажа USDT за рубли", "https://t.me/mommys_trader_bot", "\uD83D\uDCB3", showOnHome = false),
    ProductItem("MarsDAO Burn", "Сжигание токенов MDAO", "https://burn.daomars.com", "\uD83D\uDD25", showOnHome = false),
)

val homeServices: List<ProductItem> get() = allProducts.filter { it.showOnHome }
