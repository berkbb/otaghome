package com.berkbb.otaghome

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Full-screen About window, as in Pitwall and AynaCast: the logo and title with a close button,
 * cards for the developer, website, licence, version, device and the name's meaning, then the
 * disclaimer beside View licence and Close. Back closes it. The window covers the launcher's
 * footer, so it has its own: the result of a check the user asked for slides up there.
 */
@Composable
internal fun AboutWindow(viewModel: LauncherViewModel, noticeSeconds: Int, onClose: () -> Unit) {
    val palette = LocalPalette.current
    val update by viewModel.update.collectAsState()
    val install by viewModel.updateInstall.collectAsState()
    val closeFocus = remember { FocusRequester() }
    var showLicense by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Pair<Notice, Long>?>(null) }
    var asked by remember { mutableStateOf(false) }
    val checkResult = updateNotice(update)
    LaunchedEffect(update) {
        if (asked && update != UpdateStatus.Checking && checkResult != null) {
            asked = false
            notice = checkResult to System.nanoTime()
        }
    }
    val installFailure = (install as? UpdateInstall.Failed)?.let { Notice(stringResource(it.messageRes), R.drawable.ic_error) }
    LaunchedEffect(install) { if (installFailure != null) notice = installFailure to System.nanoTime() }
    val device = stringResource(
        R.string.about_device_value,
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Build.MODEL, Build.PRODUCT, Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    )

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
        Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.about_title),
                    color = palette.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                CloseButton(onClose)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(stringResource(R.string.about_developer), stringResource(R.string.about_developer_name), Modifier.weight(1f))
                    InfoCard(stringResource(R.string.about_website), stringResource(R.string.about_website_url), Modifier.weight(1f), palette.accent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(stringResource(R.string.about_license), stringResource(R.string.about_license_name), Modifier.weight(1f))
                    InfoCard(stringResource(R.string.about_version), "v${BuildConfig.VERSION_NAME}", Modifier.weight(1f), palette.accent)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoCard(stringResource(R.string.about_device), device, Modifier.weight(1f))
                    InfoCard(stringResource(R.string.about_name), stringResource(R.string.about_name_story), Modifier.weight(1f))
                }
                InfoCard(stringResource(R.string.about_update), updateText(update, install), Modifier.fillMaxWidth(), updateColor(update, install))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.about_disclaimer),
                    color = palette.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                if ((update as? UpdateStatus.Available)?.apk != null && install !is UpdateInstall.Downloading) {
                    AboutButton(stringResource(R.string.update_now), primary = false, onClick = viewModel::installUpdate)
                } else {
                    AboutButton(
                        stringResource(if (update == UpdateStatus.Checking) R.string.update_checking else R.string.update_check),
                        primary = false,
                        onClick = { asked = true; viewModel.checkForUpdates() }
                    )
                }
                AboutButton(stringResource(R.string.btn_view_license), primary = false) { showLicense = true }
                AboutButton(stringResource(R.string.btn_close), primary = true, modifier = Modifier.focusRequester(closeFocus), onClick = onClose)
            }
        }
        FooterBar(
            notice = notice?.first,
            noticeKey = notice?.second,
            durationMillis = noticeSeconds * 1000L,
            onClose = { notice = null },
            onTimeout = { notice = null }
        )
        }
        if (showLicense) LicenseWindow(onClose = { showLicense = false })
    }
}

/**
 * The GNU GPL v3 text from the app's assets, read with the remote: Up and Down scroll it and only
 * move to the button once the text has reached its end.
 */
@Composable
private fun LicenseWindow(onClose: () -> Unit) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val closeFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val text = remember(context) {
        runCatching { context.assets.open("LICENSE").bufferedReader().use { it.readText() } }
            .getOrDefault("https://www.gnu.org/licenses/gpl-3.0.txt")
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(48.dp)
                .onPreviewKeyEvent { event ->
                    val step = when (event.key) {
                        Key.DirectionDown -> if (scroll.canScrollForward) 300f else null
                        Key.DirectionUp -> if (scroll.canScrollBackward) -300f else null
                        else -> null
                    } ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(step) }
                    true
                }
        ) {
            Text(text = stringResource(R.string.about_license_name), color = palette.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(Corners.large)
                    .background(palette.card)
            ) {
                Text(
                    text = text,
                    color = palette.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(scroll).padding(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AboutButton(stringResource(R.string.btn_close), primary = true, modifier = Modifier.focusRequester(closeFocus), onClick = onClose)
            }
        }
    }
}

/** The update card's line: the check's result, then the download or installer's state over it. */
@Composable
private fun updateText(update: UpdateStatus, install: UpdateInstall): String = when (install) {
    is UpdateInstall.Downloading -> stringResource(R.string.update_downloading, install.progress)
    UpdateInstall.WaitingForConfirmation -> stringResource(R.string.update_confirm)
    is UpdateInstall.Failed -> stringResource(install.messageRes)
    UpdateInstall.Idle -> when (update) {
        UpdateStatus.Idle -> stringResource(R.string.update_idle, BuildConfig.VERSION_NAME)
        UpdateStatus.Checking -> stringResource(R.string.update_checking)
        is UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date, update.current)
        is UpdateStatus.Available ->
            if (update.apk != null) stringResource(R.string.update_available, update.latest)
            else stringResource(R.string.update_no_apk, update.latest, SELF_RELEASES_PAGE)
        UpdateStatus.Unavailable -> stringResource(R.string.update_unavailable)
        UpdateStatus.Failed -> stringResource(R.string.update_failed)
    }
}

/** The footer message for a finished check, or null while nothing has been checked yet. */
@Composable
private fun updateNotice(update: UpdateStatus): Notice? = when (update) {
    UpdateStatus.Idle, UpdateStatus.Checking -> null
    is UpdateStatus.UpToDate -> Notice(stringResource(R.string.update_up_to_date, update.current), R.drawable.ic_info)
    is UpdateStatus.Available ->
        if (update.apk != null) Notice(stringResource(R.string.update_available, update.latest), R.drawable.ic_update)
        else Notice(stringResource(R.string.update_no_apk, update.latest, SELF_RELEASES_PAGE), R.drawable.ic_update)
    UpdateStatus.Unavailable -> Notice(stringResource(R.string.update_unavailable), R.drawable.ic_info)
    UpdateStatus.Failed -> Notice(stringResource(R.string.update_failed), R.drawable.ic_error)
}

@Composable
private fun updateColor(update: UpdateStatus, install: UpdateInstall): Color {
    val palette = LocalPalette.current
    return when {
        install is UpdateInstall.Failed || update == UpdateStatus.Failed -> Color(0xFFFF8A80)
        update is UpdateStatus.Available || install !is UpdateInstall.Idle -> palette.accent
        else -> palette.textPrimary
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier, valueColor: Color = LocalPalette.current.textPrimary) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(Corners.large)
            .background(palette.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = title.uppercase(), color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        Text(text = value, color = valueColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AboutButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(Corners.medium)
            .background(
                when {
                    isFocused -> palette.focus
                    primary -> palette.accent
                    else -> palette.card
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                isFocused -> palette.onFocus
                primary -> Color.White
                else -> palette.textPrimary
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val label = stringResource(R.string.btn_close)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isFocused) palette.focus else palette.card)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "✕", color = if (isFocused) palette.onFocus else palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
