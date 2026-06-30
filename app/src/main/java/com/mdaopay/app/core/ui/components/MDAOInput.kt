package com.mdaopay.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun MDAOInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    error: String = "",
    suffix: @Composable (() -> Unit)? = null,
    affix: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true
) {
    val d = MaterialTheme.extended.themeColors
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            MDAOInputLabel(text = label)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(MDARadius.xl), clip = false)
                .clip(RoundedCornerShape(MDARadius.xl))
                .background(if (error.isNotBlank()) d.surface else d.tile)
                .border(
                    width = 1.dp,
                    color = if (error.isNotBlank()) MaterialTheme.extended.danger else d.softBorder,
                    shape = RoundedCornerShape(MDARadius.xl)
                )
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (affix != null) 36.dp else 16.dp, end = if (suffix != null) 36.dp else 16.dp),
                placeholder = {
                    if (placeholder.isNotBlank()) {
                        Text(placeholder, color = d.text3, fontSize = 14.sp)
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.extended.accent,
                    focusedTextColor = d.text,
                    unfocusedTextColor = d.text,
                    disabledTextColor = d.text2,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                visualTransformation = visualTransformation,
                enabled = enabled
            )
            if (suffix != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    suffix()
                }
            }
            if (affix != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    affix()
                }
            }
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                color = MaterialTheme.extended.danger,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun MDAOAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = ""
) {
    val d = MaterialTheme.extended.themeColors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotBlank()) {
            MDAOInputLabel(text = label)
            Spacer(Modifier.height(8.dp))
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("0.00", color = d.text3, fontSize = 44.sp)
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = MaterialTheme.extended.accent,
                focusedTextColor = d.text,
                unfocusedTextColor = d.text,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.5).sp
            ),
            visualTransformation = VisualTransformation.None
        )
    }
}

@Composable
fun MDAOInputLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.extended.themeColors.text2
    )
}
