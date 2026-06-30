package com.mdaopay.app.feature.assets.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.common.toDisplayAmount
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOFAB
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.feature.home.domain.model.WalletState
import java.math.BigDecimal

data class AssetInfo(
    val symbol: String,
    val balance: BigDecimal,
    val usdValue: String = "",
    val trendPercent: String = "",
    val trendUp: Boolean = true,
    val accentColor: Color
)

private data class AssetGroup(
    val label: String,
    val assets: List<AssetInfo>
)

private val filterOptions = listOf("All tokens", "NFTs", "Recent")

@Composable
fun AssetsScreen(
    wallet: WalletState,
    onBack: () -> Unit,
    onAssetClick: (String) -> Unit
) {
    val assets = remember(wallet) { buildAssetList(wallet) }
    var selectedFilter by remember { mutableStateOf(0) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tc.tile),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "\u2190", fontSize = 20.sp, color = tc.text)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Активы",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = tc.text,
                letterSpacing = (-0.5).sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filterOptions.forEachIndexed { i, label ->
                val isSelected = i == selectedFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) extended.accent else tc.tile)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = MarsFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (isSelected) Color.White else tc.text
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            items(assets) { group ->
                Text(
                    text = group.label,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = tc.text2,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                group.assets.forEach { asset ->
                    AssetRow(
                        asset = asset,
                        onClick = {
                            HapticManager.light()
                            onAssetClick(asset.symbol)
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MDAOFAB(
            onClick = { onAssetClick("ADD") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
            icon = { Text(text = "+", fontSize = 24.sp, color = Color.White) }
        )
    }
}

@Composable
private fun AssetRow(
    asset: AssetInfo,
    onClick: () -> Unit
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tc.card)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(asset.accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = asset.symbol.take(1),
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = asset.accentColor
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.symbol,
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = tc.text
            )
            if (asset.usdValue.isNotBlank()) {
                Text(
                    text = asset.usdValue,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = tc.text2
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = asset.balance.toDisplayAmount(
                    if (asset.symbol == "Sepolia ETH") 6 else 2
                ),
                fontFamily = MarsMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = tc.text
            )
            if (asset.trendPercent.isNotBlank()) {
                Text(
                    text = "${if (asset.trendUp) "+" else "-"}${asset.trendPercent}",
                    fontFamily = MarsMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (asset.trendUp) extended.success else extended.danger
                )
            }
        }
    }
}

private fun buildAssetList(wallet: WalletState): List<AssetGroup> {
    val main = mutableListOf<AssetInfo>()
    if (wallet.balanceMdao > BigDecimal.ZERO)
        main.add(AssetInfo("MDAO", wallet.balanceMdao, accentColor = Color(0xFF7B4DFF)))
    if (wallet.balanceUsdt > BigDecimal.ZERO)
        main.add(AssetInfo("USDT", wallet.balanceUsdt, accentColor = Color(0xFF00D68F)))
    if (wallet.balanceEth > BigDecimal.ZERO)
        main.add(AssetInfo("Sepolia ETH", wallet.balanceEth, accentColor = Color(0xFF627EEA)))

    return listOf(AssetGroup("Основные", main))
}
