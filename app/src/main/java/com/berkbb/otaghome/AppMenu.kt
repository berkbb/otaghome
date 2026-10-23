package com.berkbb.otaghome

import android.content.ComponentName
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One entry of an app's long-press menu. */
data class MenuAction(@param:DrawableRes val iconRes: Int, @param:StringRes val labelRes: Int, val run: () -> Unit)

/**
 * The menu an app card opens on a long press of OK (or Menu), like the stock launcher's: the app's
 * logo and name on top, then its actions. Choosing one closes the menu first; Back just closes it.
 * The held OK that opened the menu is ignored until it is pressed again.
 */
@Composable
internal fun AppMenu(
    entry: AppEntry,
    art: AppArt?,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val firstFocus = remember { FocusRequester() }
    val icon = art?.icon ?: rememberActivityIcon(entry)
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .ignoreHeldOk()
                .width(380.dp)
                // A soft shadow lifts the menu off the dimmed screen
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                icon?.let {
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(44.dp).clip(Corners.medium))
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Text(
                    text = entry.label,
                    color = palette.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            actions.forEachIndexed { index, action ->
                SettingsRow(
                    iconRes = action.iconRes,
                    title = stringResource(action.labelRes),
                    value = null,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = {
                        onDismiss()
                        action.run()
                    }
                )
            }
        }
    }
}

/**
 * The dialog opens while OK is still held (a long press, or the press that opened it); its repeats
 * and its release belong to that press and must not choose the focused row. OK counts only after a
 * fresh press inside the dialog.
 */
@Composable
private fun Modifier.ignoreHeldOk(): Modifier {
    var okArmed by remember { mutableStateOf(false) }
    return onPreviewKeyEvent { event ->
        if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
            return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) okArmed = true
        !okArmed
    }
}

/**
 * Picks one of a few [options] from a list, in the app menu's look: the current one is marked and
 * focused first, OK on a row picks it and closes the list, Back closes it unchanged.
 */
@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val selectedFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .ignoreHeldOk()
                .width(380.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = index == selected.coerceAtLeast(0)
                    SettingsRow(
                        iconRes = if (isSelected) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked,
                        title = option,
                        value = null,
                        modifier = if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier,
                        onClick = {
                            onDismiss()
                            onPick(index)
                        }
                    )
                }
            }
        }
    }
}

/** The logo of an app that has a banner: its card keeps only the banner, so the menu loads the logo itself. */
@Composable
private fun rememberActivityIcon(entry: AppEntry): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, entry.packageName, entry.className) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getActivityIcon(ComponentName(entry.packageName, entry.className))
                    .toBitmap(LauncherViewModel.ICON_PX, LauncherViewModel.ICON_PX).asImageBitmap()
            }.getOrNull()
        }
    }.value
}
