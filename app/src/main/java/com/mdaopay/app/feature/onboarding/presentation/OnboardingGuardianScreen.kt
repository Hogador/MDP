package com.mdaopay.app.feature.onboarding.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.theme.DarkBorder
import com.mdaopay.app.core.ui.theme.DarkOnSurfaceMuted
import com.mdaopay.app.core.ui.theme.DarkSurface
import com.mdaopay.app.core.ui.theme.MDAOPurple

data class PickedContact(
    val displayName: String,
    val phoneNumber: String = ""
)

@Composable
fun OnboardingGuardianScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingGuardianViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isHermit by viewModel.isHermit.collectAsState()
    var guardians by remember { mutableStateOf(listOf<PickedContact>()) }
    var permissionDenied by remember { mutableStateOf(false) }

    // Show hermit info card
    if (isHermit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MDAOPurple.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, MDAOPurple.copy(alpha = 0.4f))
        ) {
            Text(
                text = "Hermit mode: recovery via cold device. Configure in Settings.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    val pickContact = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let {
            val name = queryContactName(context, it)
            if (name != null && guardians.none { g -> g.displayName == name }) {
                guardians = guardians + PickedContact(displayName = name)
            }
        }
    }

    val requestPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            pickContact.launch(null)
        } else {
            permissionDenied = true
        }
    }

    fun onAddGuardian() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            permissionDenied = false
            pickContact.launch(null)
        } else {
            requestPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = "Доверенные лица",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Выбери 2 человек, которые помогут восстановить кошелёк, если потеряешь доступ",
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurfaceMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            guardians.forEachIndexed { index, guardian ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MDAOPurple.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, MDAOPurple.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MDAOPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = guardian.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { guardians = guardians.toMutableList().apply { removeAt(index) } }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Убрать",
                                tint = DarkOnSurfaceMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (guardians.size < 2) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedCard(
                    onClick = { onAddGuardian() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = DarkSurface
                    ),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MDAOPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Добавить guardian",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MDAOPurple,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (permissionDenied) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Доступ к контактам нужен, чтобы выбрать доверенное лицо",
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkOnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            MDAOButton(
                text = "Продолжить",
                onClick = { onContinue() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onContinue) {
                Text(
                    text = "Пропустить, добавлю позже",
                    color = DarkOnSurfaceMuted,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun queryContactName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            if (nameIdx >= 0) return cursor.getString(nameIdx)
        }
    }
    return null
}
