package com.mdaopay.app.feature.contacts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.datastore.Contact
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOFAB
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.core.ui.theme.MarsMono

@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onContactClick: (String) -> Unit,
    onAddClick: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.nickname.contains(searchQuery, ignoreCase = true) ||
            it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(d.bg)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)
        ) {
            MDAOTopBar(title = "\u041A\u043E\u043D\u0442\u0430\u043A\u0442\u044B", onBack = onBack)

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(d.tile)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "\uD83D\uDD0D", fontSize = 16.sp)
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "\u041F\u043E\u0438\u0441\u043A \u043F\u043E \u0438\u043C\u0435\u043D\u0438 \u0438\u043B\u0438 \u043D\u0438\u043A\u0443",
                            color = d.text3,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = d.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(a),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "\u2715",
                            color = d.text2,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { searchQuery = "" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "\u0412\u0441\u0435 \u043A\u043E\u043D\u0442\u0430\u043A\u0442\u044B (${filtered.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = d.text2,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "\uD83D\uDD0D", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "\u041D\u0438\u0447\u0435\u0433\u043E \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u043E",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = d.text
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\u041F\u043E\u043F\u0440\u043E\u0431\u0443\u0439\u0442\u0435 \u0434\u0440\u0443\u0433\u043E\u0439 \u0437\u0430\u043F\u0440\u043E\u0441",
                            fontSize = 13.sp,
                            color = d.text2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            onRemove = { viewModel.removeContact(contact.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(contact.id) },
                            onContactClick = {
                                HapticManager.light()
                                onContactClick(contact.nickname)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        MDAOFAB(
            onClick = {
                HapticManager.light()
                onAddClick()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Text(text = "+", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun ContactCard(
    contact: Contact,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onContactClick: () -> Unit
) {
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    val initials = contact.nickname.take(1).uppercase()
    val avatarColors = listOf(
        listOf(Color(0xFFFF6B00), Color(0xFFFF9A4D)),
        listOf(Color(0xFF2D7FF9), Color(0xFF5DA5FF)),
        listOf(Color(0xFF00B377), Color(0xFF3DD99A)),
        listOf(Color(0xFF7B4DFF), Color(0xFFA884FF)),
        listOf(Color(0xFFF94D9E), Color(0xFFFF85B8)),
        listOf(Color(0xFFFFB300), Color(0xFFFFD166)),
    )
    val colorPair = avatarColors[contact.nickname.length % avatarColors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(d.card)
            .clickable { onContactClick() }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(colorPair[0])
                }
                .shadow(4.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.nickname,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = d.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (contact.address.isNotBlank()) {
                Text(
                    text = "@${contact.nickname}",
                    fontSize = 12.sp,
                    fontFamily = MarsMono,
                    color = a,
                    maxLines = 1
                )
            }
            Text(
                text = contact.address.take(10) + "\u2026" + contact.address.takeLast(4),
                fontSize = 11.sp,
                color = d.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(d.tile)
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (contact.isFavorite) "\u2605" else "\u2606",
                    color = if (contact.isFavorite) a else d.text2,
                    fontSize = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(a.copy(alpha = 0.1f))
                    .clickable { onContactClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2191",
                    color = a,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
