package com.jing.sakura.compose.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.type

private val DirectionalDpadKeys = setOf(
    NativeKeyEvent.KEYCODE_DPAD_LEFT,
    NativeKeyEvent.KEYCODE_DPAD_RIGHT,
    NativeKeyEvent.KEYCODE_DPAD_UP,
    NativeKeyEvent.KEYCODE_DPAD_DOWN
)

private val VerticalDpadKeys = setOf(
    NativeKeyEvent.KEYCODE_DPAD_UP,
    NativeKeyEvent.KEYCODE_DPAD_DOWN
)

@Composable
fun rememberDpadRepeatGate(
    minIntervalMs: Long = 82L,
    gatedKeys: Set<Int> = VerticalDpadKeys
): (KeyEvent) -> Boolean {
    var lastKeyCode by remember { mutableStateOf(-1) }
    var lastHandledAt by remember { mutableStateOf(0L) }
    return remember(minIntervalMs, gatedKeys) {
        { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                val keyCode = event.nativeKeyEvent.keyCode
                if (keyCode !in DirectionalDpadKeys) {
                    lastKeyCode = keyCode
                    lastHandledAt = SystemClock.uptimeMillis()
                    false
                } else {
                    val now = SystemClock.uptimeMillis()
                    val shouldConsume = keyCode in gatedKeys &&
                        event.nativeKeyEvent.repeatCount > 0 &&
                        lastKeyCode == keyCode &&
                        now - lastHandledAt < minIntervalMs
                    lastKeyCode = keyCode
                    lastHandledAt = now
                    shouldConsume
                }
            }
        }
    }
}
