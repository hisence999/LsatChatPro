package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * A text field that debounces updates to external state while maintaining
 * responsive local editing. Prevents race conditions when typing fast.
 *
 * Key features:
 * - Local state for immediate UI responsiveness
 * - Debounced sync to external state (saves after typing stops)
 * - Focus-aware incoming sync (doesn't overwrite while user is editing)
 * - Immediate commit on blur
 */
@OptIn(FlowPreview::class)
@Composable
fun DebouncedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    stateKey: Any? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = LocalTextStyle.current,
    debounceMs: Long = 150L
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Local text field state, keyed to reset when switching contexts
    val textFieldState = key(stateKey) {
        rememberTextFieldState(initialText = value)
    }
    
    // Sync from external state ONLY when NOT focused
    // This prevents overwriting user input during typing
    LaunchedEffect(value, isFocused) {
        if (!isFocused && textFieldState.text.toString() != value) {
            textFieldState.edit { 
                replace(0, length, value) 
            }
        }
    }
    
    // Debounced sync to external state
    LaunchedEffect(stateKey) {
        snapshotFlow { textFieldState.text }
            .drop(1) // Skip initial value
            .debounce(debounceMs)
            .collect { text ->
                val currentText = text.toString()
                if (currentText != value) {
                    onValueChange(currentText)
                }
            }
    }
    
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            
            // Commit immediately when focus is lost
            if (wasFocused && !focusState.isFocused) {
                val currentText = textFieldState.text.toString()
                if (currentText != value) {
                    onValueChange(currentText)
                }
            }
        },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        lineLimits = if (singleLine) {
            TextFieldLineLimits.SingleLine
        } else {
            TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = maxLines)
        }
    )
}
