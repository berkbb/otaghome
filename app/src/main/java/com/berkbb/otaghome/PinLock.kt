package com.berkbb.otaghome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.security.SecureRandom

/** Optional local PIN protection for Settings and individual locked apps. */
data class LockSettings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val lockSettings: Boolean = false,
    val lockedApps: Set<String> = emptySet()
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank() && !pinSalt.isNullOrBlank()
    fun isSettingsLocked(): Boolean = hasPin && lockSettings
    fun isAppLocked(packageName: String): Boolean = hasPin && packageName in lockedApps
}

/** Pure, unit-testable salted SHA-256 PIN hashing and constant-time verification. */
object PinLock {
    const val PIN_LENGTH = 4
    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$salt:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        if (!isValidPin(pin) || salt.isBlank() || expectedHash.isBlank()) return false
        val actual = hash(pin, salt).toByteArray(Charsets.UTF_8)
        val expected = expectedHash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }
}

enum class PinDialogMode { VERIFY, SET }

/**
 * 4-digit PIN entry dialog usable both with the D-pad keypad and physical remote number keys.
 * In [PinDialogMode.SET], asks twice to confirm the new PIN before calling [onSuccess].
 */
@Composable
internal fun PinDialog(
    title: String,
    mode: PinDialogMode,
    verify: ((String) -> Boolean)? = null,
    onSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val firstFocus = remember { FocusRequester() }
    var digits by remember { mutableStateOf("") }
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    fun enterDigit(digit: Char) {
        errorRes = null
        if (digits.length >= PinLock.PIN_LENGTH) return
        val next = digits + digit
        if (next.length < PinLock.PIN_LENGTH) {
            digits = next
            return
        }
        if (mode == PinDialogMode.VERIFY) {
            if (verify?.invoke(next) == true) {
                onDismiss()
                onSuccess(next)
            } else {
                digits = ""
                errorRes = R.string.pin_wrong
            }
        } else {
            val expected = firstEntry
            if (expected == null) {
                firstEntry = next
                digits = ""
            } else if (expected == next) {
                onDismiss()
                onSuccess(next)
            } else {
                digits = ""
                errorRes = R.string.pin_mismatch
            }
        }
    }

    fun backspace() {
        errorRes = null
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }

    fun clearDigits() {
        errorRes = null
        digits = ""
    }

    val promptText = when {
        mode == PinDialogMode.VERIFY -> stringResource(R.string.pin_enter)
        firstEntry == null -> stringResource(R.string.pin_enter_new)
        else -> stringResource(R.string.pin_confirm_new)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { firstFocus.requestFocus() }
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .shadow(16.dp, Corners.extraLarge)
                    .clip(Corners.extraLarge)
                    .background(palette.background)
                    .verticalScroll(rememberScrollState())
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val digit = event.key.toDigitChar()
                        when {
                            digit != null && event.nativeKeyEvent.repeatCount == 0 -> {
                                enterDigit(digit)
                                true
                            }
                            event.key == Key.Backspace || event.key == Key.Delete -> {
                                backspace()
                                true
                            }
                            else -> false
                        }
                    }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(40.dp).background(palette.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(R.drawable.ic_lock), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = title, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = promptText, color = palette.textSecondary, fontSize = 14.sp)
                    }
                }

                // 4-dot PIN indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    repeat(PinLock.PIN_LENGTH) { index ->
                        val filled = index < digits.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (filled) palette.accent else Color.Transparent)
                                .border(2.dp, if (filled) palette.accent else palette.textSecondary, CircleShape)
                        )
                    }
                }

                // Fixed-height feedback line so validation messages never shift focused keypad keys
                Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                    errorRes?.let { res ->
                        Text(text = stringResource(res), color = palette.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }

                // 3x4 keypad
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("clear", "0", "back")
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { key ->
                                val label = when (key) {
                                    "clear" -> stringResource(R.string.pin_clear)
                                    "back" -> stringResource(R.string.pin_backspace)
                                    else -> key
                                }
                                PinKeyButton(
                                    label = label,
                                    isAction = key == "clear" || key == "back",
                                    modifier = Modifier
                                        .weight(1f)
                                        .let { if (key == "1") it.focusRequester(firstFocus) else it },
                                    onClick = {
                                        when (key) {
                                            "clear" -> clearDigits()
                                            "back" -> backspace()
                                            else -> enterDigit(key[0])
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                PillButton(
                    text = stringResource(R.string.btn_cancel),
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun PinKeyButton(
    label: String,
    isAction: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(Corners.medium)
            .background(if (isFocused) palette.focus else palette.card)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) palette.onFocus else if (isAction) palette.textSecondary else palette.textPrimary,
            fontSize = if (isAction) 14.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Maps hardware/remote number keys (top row or numpad) to their digit char, or null otherwise. */
internal fun Key.toDigitChar(): Char? = when (this) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}

/** Confirmation dialog with initial D-pad focus on Cancel per UI safety rules. */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { cancelFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = title, color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(text = message, color = palette.textSecondary, fontSize = 16.sp, lineHeight = 23.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                PillButton(
                    text = stringResource(R.string.btn_cancel),
                    primary = false,
                    modifier = Modifier.focusRequester(cancelFocus),
                    onClick = onDismiss
                )
                PillButton(
                    text = confirmLabel,
                    primary = true,
                    onClick = {
                        onDismiss()
                        onConfirm()
                    }
                )
            }
        }
    }
}
