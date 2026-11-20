package com.berkbb.otaghome

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/** Dialog for giving an app a custom display name on the home screen, or restoring the original name. */
@Composable
internal fun RenameAppDialog(
    originalLabel: String,
    currentLabel: String,
    hasCustomLabel: Boolean,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) = NamePromptDialog(
    title = stringResource(R.string.rename_title),
    subtitle = originalLabel,
    hint = stringResource(R.string.rename_hint),
    initial = currentLabel,
    maxLength = 40,
    resetLabel = if (hasCustomLabel) stringResource(R.string.btn_default) else null,
    onReset = if (hasCustomLabel) { { onSave(null) } } else null,
    onSave = onSave,
    onDismiss = onDismiss
)

/** Dialog for creating or renaming a user category tab. */
@Composable
internal fun CategoryNameDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) = NamePromptDialog(
    title = title,
    subtitle = null,
    hint = stringResource(R.string.category_name_hint),
    initial = initial,
    maxLength = 24,
    resetLabel = null,
    onReset = null,
    onSave = onSave,
    onDismiss = onDismiss
)

@Composable
private fun NamePromptDialog(
    title: String,
    subtitle: String?,
    hint: String,
    initial: String,
    maxLength: Int,
    resetLabel: String?,
    onReset: (() -> Unit)?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    var text by remember(initial) { mutableStateOf(initial) }
    val firstFocus = remember { FocusRequester() }
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(text = title, color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Corners.medium)
                    .background(palette.card)
                    .border(2.dp, if (fieldFocused) palette.accent else Color.Transparent, Corners.medium)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (text.isEmpty()) {
                    Text(text = hint, color = palette.textSecondary, fontSize = 17.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(maxLength) },
                    singleLine = true,
                    interactionSource = fieldInteraction,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(palette.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onSave(text)
                        onDismiss()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFocus)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (resetLabel != null && onReset != null) {
                    PillButton(text = resetLabel, primary = false) {
                        onReset()
                        onDismiss()
                    }
                }
                PillButton(text = stringResource(R.string.btn_cancel), primary = false, onClick = onDismiss)
                PillButton(text = stringResource(R.string.btn_apply), primary = true) {
                    onSave(text)
                    onDismiss()
                }
            }
        }
    }
}

/** Multi-select dialog for assigning an app to one or more custom categories. */
@Composable
internal fun AppCategoriesDialog(
    appLabel: String,
    categories: List<AppCategory>,
    packageName: String,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPalette.current
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .width(420.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.categories_for_app, appLabel),
                color = palette.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { index, category ->
                    val member = packageName in category.packages
                    SettingsRow(
                        iconRes = if (member) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked,
                        title = category.name,
                        value = null,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onToggle(category.id) }
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton(
                    text = stringResource(R.string.btn_close),
                    primary = true,
                    modifier = if (categories.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = onDismiss
                )
            }
        }
    }
}

/** Picture picker dialog for assigning a custom 16:9 card cover to an app (or resetting it). */
@Composable
internal fun AppCoverPickerDialog(
    appLabel: String,
    currentCoverUri: String?,
    onPickUri: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val notify = LocalNotice.current
    val firstFocus = remember { FocusRequester() }
    val hasSystemPicker = remember(context) { context.hasDocumentPicker(Intent.ACTION_OPEN_DOCUMENT, "image/*") }
    val systemLabel = stringResource(R.string.bg_choose_system)
    val safPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            onPickUri(uri.toString())
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .fillMaxHeight(0.86f)
                    .shadow(16.dp, Corners.extraLarge)
                    .clip(Corners.extraLarge)
                    .background(palette.background)
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.menu_custom_art),
                    color = palette.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = appLabel,
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                )
                DeviceImagesPage(
                    selectedUri = currentCoverUri,
                    firstFocus = firstFocus,
                    onPickUri = { uri ->
                        onPickUri(uri)
                        onDismiss()
                    },
                    onOpenSystemPicker = if (hasSystemPicker) {
                        {
                            if (runCatching { safPicker.launch(arrayOf("image/*")) }.isFailure) {
                                notify(Notice(context.getString(R.string.msg_launch_failed, systemLabel), R.drawable.ic_error))
                            }
                        }
                    } else null,
                    onReset = if (currentCoverUri != null) {
                        {
                            onReset()
                            onDismiss()
                        }
                    } else null,
                    importContentPicker = false
                )
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
