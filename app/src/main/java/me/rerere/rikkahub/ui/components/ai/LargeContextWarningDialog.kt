package me.rerere.rikkahub.ui.components.ai

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics

@Composable
fun LargeContextWarningDialog(
    messageCount: Int,
    enableHaptics: Boolean,
    onConfirm: () -> Unit,
) {
    val haptics = rememberPremiumHaptics(enabled = enableHaptics)
    var secondsRemaining by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining -= 1
        }
    }

    val confirmEnabled = secondsRemaining == 0
    val confirmLabel = if (confirmEnabled) {
        stringResource(R.string.chat_large_context_warning_confirm)
    } else {
        stringResource(R.string.chat_large_context_warning_confirm_countdown, secondsRemaining)
    }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(text = stringResource(R.string.chat_large_context_warning_title))
        },
        text = {
            Text(text = stringResource(R.string.chat_large_context_warning_message, messageCount))
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    onConfirm()
                }
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = null,
    )
}
