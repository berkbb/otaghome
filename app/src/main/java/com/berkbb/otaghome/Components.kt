package com.berkbb.otaghome

import android.content.Intent
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Top bar: the clock on the left, the tabs in the centre, and Pitwall-style buttons on the right
 * for voice search, inputs, network and settings.
 */
@Composable
internal fun TopBar(
    tab: LauncherTab,
    onSelectTab: (LauncherTab) -> Unit,
    favoritesTabFocus: FocusRequester,
    allAppsTabFocus: FocusRequester,
    onOpenSettings: () -> Unit,
    upTargets: UpTargets,
    onFocusTabs: () -> Unit,
    /** Shown in the clock card; null when the weather is off or not known yet. */
    weather: WeatherNow?,
    temperatureUnit: TempUnit,
    clockFormat: ClockFormat,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val buttonFocus = remember { List(4) { FocusRequester() } }
    // Each control records where it sits, so a card can send Up to the one above it
    fun Modifier.upTarget(key: String, focus: () -> Unit) = onGloballyPositioned {
        upTargets.place(key, it.boundsInRoot().center.x, focus)
    }
    fun Modifier.upButton(index: Int) = focusRequester(buttonFocus[index])
        .upTarget("button$index") { buttonFocus[index].requestFocus() }
    val voiceLabel = stringResource(R.string.cd_voice_search)
    val networkLabel = stringResource(R.string.settings_network)
    val inputsLabel = stringResource(R.string.cd_inputs)
    val network by rememberNetworkStatus()
    val open = rememberOpener()

    Box(modifier = modifier.fillMaxWidth()) {
        Clock(weather = weather, temperatureUnit = temperatureUnit, clockFormat = clockFormat, modifier = Modifier.align(Alignment.CenterStart))

        TabBar(
            tab = tab,
            onSelectTab = onSelectTab,
            favoritesTabFocus = favoritesTabFocus,
            allAppsTabFocus = allAppsTabFocus,
            modifier = Modifier
                .align(Alignment.Center)
                .upTarget("tabs", onFocusTabs)
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PitwallIconButton(
                iconRes = R.drawable.ic_mic,
                contentDescription = voiceLabel,
                // Left goes back to the selected tab rather than onto the other one, which would switch tabs
                modifier = Modifier
                    .upButton(0)
                    .focusProperties { left = if (tab == LauncherTab.FAVORITES) favoritesTabFocus else allAppsTabFocus },
                onClick = { open(context.voiceSearchIntent(), voiceLabel) }
            )
            PitwallIconButton(
                iconRes = R.drawable.ic_input,
                contentDescription = inputsLabel,
                modifier = Modifier.upButton(1),
                onClick = { open(context.inputsIntent(), inputsLabel) }
            )
            PitwallIconButton(
                iconRes = network.iconRes,
                contentDescription = networkDescription(network),
                modifier = Modifier.upButton(2),
                onClick = { open(context.wifiSettingsIntent(), networkLabel) }
            )
            PitwallIconButton(
                iconRes = R.drawable.ic_settings,
                contentDescription = stringResource(R.string.settings_title),
                modifier = Modifier.upButton(3),
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
internal fun rememberNetworkStatus(): State<NetworkStatus> {
    val context = LocalContext.current
    return produceState<NetworkStatus>(NetworkStatus.read(context)) {
        while (true) {
            value = NetworkStatus.read(context)
            delay(5_000L)
        }
    }
}

@Composable
internal fun networkDescription(status: NetworkStatus): String = when (status) {
    NetworkStatus.Offline -> stringResource(R.string.net_offline)
    NetworkStatus.Ethernet -> stringResource(R.string.net_ethernet)
    is NetworkStatus.Wifi -> stringResource(R.string.net_wifi, status.bars)
}

/** Time in the TV's 12/24-hour setting, the weekday and date underneath, then Pitwall's accent bar. */
@Composable
private fun Clock(weather: WeatherNow?, temperatureUnit: TempUnit, clockFormat: ClockFormat, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L)
        }
    }
    val is24Hour = clockFormat.is24Hour(DateFormat.is24HourFormat(context))
    val timeFormat = remember(locale, is24Hour) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    }
    val dateFormat = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    // On a card like the buttons so it reads on any background; as wide as the date, so the
    // accent bar can run the full width under it
    Column(
        modifier = modifier
            .clip(Corners.medium)
            .background(palette.card)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .width(IntrinsicSize.Max)
            // A long place name is cut short rather than pushing into the tabs
            .widthIn(max = 260.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = timeFormat.format(Date(now)),
                color = palette.textPrimary,
                fontSize = 36.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold
            )
            // The weather beside the time: its icon and the temperature, read like the clock
            if (weather != null) {
                Spacer(modifier = Modifier.weight(1f).widthIn(min = 18.dp))
                Icon(
                    painter = painterResource(weather.kind.icon(weather.isDay)),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = weather.temperatureText(temperatureUnit),
                    color = palette.textPrimary,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            text = dateFormat.format(Date(now)),
            color = palette.textSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        // Pitwall's accent bar, right under the date and as long as it
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(palette.accent, RoundedCornerShape(2.dp))
        )
        if (weather != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = weather.place,
                color = palette.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Pitwall's button as an icon: an 8 dp rounded card that fills with the focus colour and gets a
 * ring of it when focused.
 */
@Composable
private fun PitwallIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = Corners.medium
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(if (isFocused) palette.focus else palette.card)
            .then(if (isFocused) Modifier.border(3.dp, palette.focus, shape) else Modifier)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (isFocused) palette.onFocus else palette.textPrimary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * The tabs on one theme card. The selected tab sits on a tinted pill with an accent bar under it;
 * both slide and stretch to the newly selected tab.
 */
@Composable
private fun TabBar(
    tab: LauncherTab,
    onSelectTab: (LauncherTab) -> Unit,
    favoritesTabFocus: FocusRequester,
    allAppsTabFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    // Where each tab sits inside the bar, measured once laid out
    val bounds = remember { mutableStateMapOf<LauncherTab, Pair<Dp, Dp>>() }
    val (selectedX, selectedWidth) = bounds[tab] ?: (0.dp to 0.dp)
    val indicatorSpec = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val x by animateDpAsState(selectedX, indicatorSpec, label = "tabX")
    val width by animateDpAsState(selectedWidth, indicatorSpec, label = "tabWidth")

    Box(
        modifier = modifier
            .clip(Corners.medium)
            .background(palette.card)
            .padding(6.dp)
    ) {
        if (selectedWidth > 0.dp) {
            // Selection pill behind the selected tab
            Box(
                modifier = Modifier
                    .offset(x = x)
                    .width(width)
                    .height(TAB_HEIGHT)
                    .background(palette.accent.copy(alpha = 0.18f), Corners.small)
            )
            // Accent bar under it
            Box(
                modifier = Modifier
                    .offset(x = x + width * 0.25f, y = TAB_HEIGHT - 4.dp)
                    .width(width * 0.5f)
                    .height(3.dp)
                    .background(palette.accent, RoundedCornerShape(2.dp))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(LauncherTab.FAVORITES, R.string.tab_favorites, favoritesTabFocus),
                Triple(LauncherTab.ALL_APPS, R.string.tab_all_apps, allAppsTabFocus)
            ).forEach { (which, labelRes, focus) ->
                TabChip(
                    text = stringResource(labelRes),
                    selected = tab == which,
                    modifier = Modifier
                        .focusRequester(focus)
                        .onPlaced {
                            bounds[which] = with(density) { it.positionInParent().x.toDp() to it.size.width.toDp() }
                        },
                    onSelect = { onSelectTab(which) }
                )
            }
        }
    }
}

private val TAB_HEIGHT = 44.dp

/** A tab switches as soon as it gets focus, like the stock TV launcher. */
@Composable
private fun TabChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) { if (isFocused) onSelect() }
    val textColor by animateColorAsState(
        when {
            isFocused -> palette.onFocus
            selected -> palette.textPrimary
            else -> palette.textSecondary
        },
        label = "tabText"
    )
    Box(
        modifier = modifier
            .height(TAB_HEIGHT)
            .clip(Corners.small)
            .background(if (isFocused) palette.focus else Color.Transparent)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelect)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 21.sp,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Remote keys of an app card: OK opens; holding OK (its first key repeat) or pressing Menu opens
 * the app's menu, and the release of a held OK does not also open the app. [onUp], when set,
 * takes Up. While the card is being moved ([onMoveKey] set) the arrows move it and OK drops it.
 */
private fun Modifier.tvKeys(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUp: (() -> Unit)?,
    onMoveKey: ((Key) -> Unit)? = null,
    onMoveDone: () -> Unit = {}
): Modifier = composed {
    var longPressHandled by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (onMoveKey != null) {
            return@onPreviewKeyEvent when (event.key) {
                Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) onMoveKey(event.key)
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyUp) onMoveDone()
                    true
                }
                else -> false
            }
        }
        when (event.key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when (event.type) {
                KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        longPressHandled = false
                    } else if (!longPressHandled) {
                        longPressHandled = true
                        onLongClick()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    if (!longPressHandled) onClick()
                    longPressHandled = false
                    true
                }
                else -> false
            }
            Key.Menu -> {
                if (event.type == KeyEventType.KeyUp) onLongClick()
                true
            }
            Key.DirectionUp -> {
                if (onUp == null) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) onUp()
                true
            }
            else -> false
        }
    }
}

/**
 * Slides a card to its new place when the cards around it change order (a move, an added or
 * removed favorite) instead of jumping. For layouts that are not lazy, such as the Favorites
 * FlowRow; the lazy All apps grid uses animateItem().
 */
internal fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var animatable by remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val anim = animatable ?: Animatable(target, IntOffset.VectorConverter).also { animatable = it }
            if (anim.targetValue != target) {
                scope.launch { anim.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
            anim.value - target
        }
}

/** The card's picture: the app's banner, or its logo on a soft wash of the logo's own colour. */
@Composable
private fun CardArt(entry: AppEntry, art: AppArt?, logoSize: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val logoColor = art?.color?.let { Color(it) } ?: palette.accent
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(logoColor.copy(alpha = 0.55f), logoColor.copy(alpha = 0.18f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        when {
            art?.banner != null -> Image(
                bitmap = art.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            art?.icon != null -> Image(
                bitmap = art.icon,
                contentDescription = null,
                modifier = Modifier.size(logoSize)
            )
            else -> Text(
                text = entry.label.take(1).uppercase(),
                color = palette.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Material 3 app card: the picture, and the name centred underneath. Used by the All apps grid and,
 * with [focusedScale] and [unfocusedAlpha], by the Favorites row.
 */
@Composable
internal fun AppCard(
    entry: AppEntry,
    art: AppArt?,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    focusedScale: Float = 1.08f,
    unfocusedAlpha: Float = 1f,
    onFocused: () -> Unit = {},
    /** Off: only the picture shows, as on Apple TV. */
    showName: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** Set on the first row: Up goes to the top bar control above the card's centre (given across, in root coordinates). */
    onUp: ((Float) -> Unit)? = null,
    /** Set while this card is being moved: it lifts higher and the arrows go to [onMoveKey]. */
    onMoveKey: ((Key) -> Unit)? = null,
    onMoveDone: () -> Unit = {}
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val isMoving = onMoveKey != null
    var centerX by remember { mutableFloatStateOf(0f) }
    // A card being moved keeps the focus as it changes place, so nothing would scroll it into view;
    // ask for that each time it lands somewhere new (the screen's roomy spec adds the margin)
    val bringIntoView = remember { BringIntoViewRequester() }
    var placedAt by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(isMoving, placedAt) { if (isMoving) bringIntoView.bringIntoView() }
    // The frame lies on the background, so it takes the backdrop's accent
    val backdrop = LocalBackdrop.current
    val scale by animateFloatAsState(
        when {
            isMoving -> focusedScale + 0.08f
            isFocused -> focusedScale
            else -> 1f
        },
        label = "cardScale"
    )
    val alpha by animateFloatAsState(if (isFocused) 1f else unfocusedAlpha, label = "cardAlpha")
    val shape = Corners.large
    val favoriteLabel = stringResource(R.string.favorite_badge)

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .bringIntoViewRequester(bringIntoView)
            .onPlaced { placedAt = it.positionInParent() }
            .onGloballyPositioned { centerX = it.boundsInRoot().center.x }
            .tvKeys(onClick, onLongClick, onUp?.let { up -> { up(centerX) } }, onMoveKey, onMoveDone)
            .shadow(if (isMoving) 24.dp else if (isFocused) 12.dp else 0.dp, shape)
            .clip(shape)
            .background(if (isFocused || isMoving) palette.cardFocused else palette.card)
            .then(
                when {
                    isMoving -> Modifier.border(4.dp, backdrop.accent, shape)
                    isFocused -> Modifier.border(2.dp, backdrop.accent, shape)
                    else -> Modifier
                }
            )
            .semantics { contentDescription = entry.label }
            // Touch and mouse; the remote is handled by tvKeys
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
    ) {
        Box {
            CardArt(entry, art, logoSize = 52.dp, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(10.dp)
                        .background(palette.accent, CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .semantics { contentDescription = favoriteLabel }
                )
            }
        }
        if (showName) {
            Text(
                text = entry.label,
                color = if (isFocused) palette.textPrimary else palette.textSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
internal fun CenteredStatus(text: String, showSpinner: Boolean, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 96.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (showSpinner) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = backdrop.accent, strokeWidth = 2.5.dp)
            }
            Text(text = text, color = backdrop.contentSecondary, fontSize = 18.sp)
        }
    }
}
