package com.arjun.core_alert.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.core_alert.coreui.R
import com.arjun.core_alert.ui.theme.coreAlertColors
import java.util.Locale

/**
 * Base dialog: title, scrollable content, cancel + optional confirm.
 * The confirm lambda returns true when the dialog may close.
 */
@Composable
fun SosDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.btn_save),
    cancelLabel: String = stringResource(R.string.btn_cancel),
    onConfirm: (() -> Boolean)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = content
            )
        },
        confirmButton = {
            if (onConfirm != null) {
                TextButton(onClick = { if (onConfirm()) onDismiss() }) { Text(confirmLabel) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (onCancel != null) onCancel() else onDismiss() }) { Text(cancelLabel) }
        }
    )
}

/** Single tap list (legacy `setItems` behaviour): tapping a row picks and dismisses. */
@Composable
fun ListDialog(
    title: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    Text(
                        text = option,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(index) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {}
    )
}

/** Single choice list confirmed by the positive button. */
@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var choice by remember { mutableStateOf(selected) }
    SosDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = {
            onPick(choice)
            true
        },
        cancelLabel = stringResource(R.string.btn_cancel),
        confirmLabel = stringResource(android.R.string.ok)
    ) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { choice = index }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = choice == index, onClick = { choice = index })
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Multi choice list confirmed by the positive button. */
@Composable
fun MultiChoiceDialog(
    title: String,
    options: List<String>,
    checked: List<Boolean>,
    onCheckedChange: (List<Boolean>) -> Unit,
    onConfirm: () -> Boolean,
    onDismiss: () -> Unit
) {
    SosDialog(
        title = title,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = stringResource(R.string.btn_add),
        cancelLabel = stringResource(R.string.btn_cancel)
    ) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = checked.toMutableList()
                        next[index] = !next[index]
                        onCheckedChange(next)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked[index],
                    onCheckedChange = { value ->
                        val next = checked.toMutableList()
                        next[index] = value
                        onCheckedChange(next)
                    }
                )
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Outlined text field styled for the app dialogs. */
@Composable
fun SosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(hint) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = coreAlertColors().outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

/** Compact HH:MM stepper used by the quiet-hours rule dialog. */
@Composable
fun TimeStepper(
    label: String,
    hour: Int,
    minute: Int,
    onChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("-") {
                val total = (hour * 60 + minute + 1440 - 15) % 1440
                onChange(total / 60, total % 60)
            }
            Text(
                text = String.format(Locale.US, "%02d:%02d", hour, minute),
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = coreAlertColors().accent,
                fontWeight = FontWeight.Bold
            )
            StepButton("+") {
                val total = (hour * 60 + minute + 15) % 1440
                onChange(total / 60, total % 60)
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 18.sp)
    }
}

/** Simple outlined choice button (used for window pickers / time pickers). */
@Composable
fun OutlinedChoiceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, coreAlertColors().outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Tinted full width accent card color helper. */
@Composable
fun warningContainer(): Color = coreAlertColors().warningBg
