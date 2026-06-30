package com.mdaopay.app.feature.receive.presentation

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mdaopay.app.core.common.copyToClipboard
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOSegmentedControl
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.components.SecureScreen
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.core.ui.theme.MarsMono
import androidx.compose.foundation.Image as ComposeImage

@Composable
fun ReceiveScreen(
    onBack: () -> Unit,
    viewModel: ReceiveViewModel = hiltViewModel()
) {
    val address by viewModel.address.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    var selectedNetwork by remember { mutableStateOf(0) }
    var requestAmount by remember { mutableStateOf("") }
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    SecureScreen()

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)
        ) {
            MDAOTopBar(title = "\u041F\u043E\u043B\u0443\u0447\u0438\u0442\u044C", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                MDAOSegmentedControl(
                    options = listOf("BNB Chain", "Ethereum", "Polygon"),
                    selectedIndex = selectedNetwork,
                    onSelectionChange = { selectedNetwork = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(d.card)
                        .shadow(8.dp, RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "\u0412\u0410\u0428 QR-\u041A\u041E\u0414",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = d.text2
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .shadow(8.dp, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (address != null) {
                                val qrBitmap = remember(address) {
                                    generateQrBitmap(address!!, 512)
                                }
                                if (qrBitmap != null) {
                                    ComposeImage(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR-\u043A\u043E\u0434 \u0430\u0434\u0440\u0435\u0441\u0430",
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            } else {
                                Text(
                                    text = "\u041A\u043E\u0448\u0435\u043B\u0451\u043A \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (nickname != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "@${nickname}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = MarsMono,
                                color = a
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(d.tile)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF0B90B))
                            )
                            Text(
                                text = "BNB Smart Chain",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = d.text2
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(d.tile)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "\u0410\u0414\u0420\u0415\u0421 \u041A\u041E\u0428\u0415\u041B\u042C\u041A\u0410",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = d.text2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = address ?: "\u0417\u0430\u0433\u0440\u0443\u0437\u043A\u0430\u2026",
                                fontSize = 12.sp,
                                fontFamily = MarsMono,
                                color = d.text,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(d.surface)
                                    .shadow(4.dp, RoundedCornerShape(8.dp))
                                    .clickable {
                                        address?.let { addr ->
                                            context.copyToClipboard(addr, "Wallet address")
                                            copied = true
                                            HapticManager.doubleTap()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Rounded.CheckCircle else Icons.Rounded.ContentCopy,
                                    contentDescription = "\u041A\u043E\u043F\u0438\u0440\u043E\u0432\u0430\u0442\u044C",
                                    tint = if (copied) MaterialTheme.extended.success else d.text,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(d.card)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "\u0417\u0410\u041F\u0420\u041E\u0421\u0418\u0422\u042C \u0421\u0423\u041C\u041C\u0423 (\u043E\u043F\u0446\u0438\u043E\u043D\u0430\u043B\u044C\u043D\u043E)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = d.text2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(d.tile)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                if (requestAmount.isEmpty()) {
                                    Text(
                                        text = "0.00",
                                        color = d.text3,
                                        fontSize = 14.sp,
                                        fontFamily = MarsMono,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                BasicTextField(
                                    value = requestAmount,
                                    onValueChange = {
                                        requestAmount = it.replace(",", ".").filter { c -> c.isDigit() || c == '.' }
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = d.text,
                                        fontSize = 14.sp,
                                        fontFamily = MarsMono,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    cursorBrush = SolidColor(a),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(d.tile)
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "USDT",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = d.text2
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\u041E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u0435\u043B\u044C \u0443\u0432\u0438\u0434\u0438\u0442 \u0441\u0443\u043C\u043C\u0443 \u0432 QR \u0438 \u0441\u043C\u043E\u0436\u0435\u0442 \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C \u043E\u0434\u043D\u0438\u043C \u0442\u0430\u043F\u043E\u043C.",
                            fontSize = 11.sp,
                            color = d.text2,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "\u041F\u041E\u0414\u0415\u041B\u0418\u0422\u042C\u0421\u042F",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = d.text2,
                    modifier = Modifier.padding(start = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "\uD83D\uDCAC" to "Telegram",
                        "\uD83D\uDCF1" to "WhatsApp",
                        "\uD83D\uDD17" to "\u0421\u0441\u044B\u043B\u043A\u0430"
                    ).forEach { (icon, label) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(d.card)
                                .clickable {
                                    address?.let { addr ->
                                        val shareText = "${nickname ?: "MDAO Pay"}\n$addr"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(intent, label))
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = icon, fontSize = 20.sp)
                                Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = d.text2)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "\u041F\u043E\u0434\u0434\u0435\u0440\u0436\u0438\u0432\u0430\u0435\u0442\u0441\u044F \u0442\u043E\u043B\u044C\u043A\u043E \u0441\u0435\u0442\u044C Sepolia (ERC-20 USDT)",
                    fontSize = 11.sp,
                    color = d.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
