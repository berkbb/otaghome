package com.berkbb.otaghome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Constraints
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
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import java.util.Date

private data class TopBarButtonSpec(
    @param:DrawableRes val iconRes: Int,
    val contentDescription: String,
    val onClick: () -> Unit
)

/**
 * Top bar: the clickable clock/weather card on the left, the tabs in the centre, and Pitwall-style
 * buttons on the right (configurable in Settings → Display → Top bar).
 */
@Composable
internal fun TopBar(
    tab: HomeTab,
    categories: List<AppCategory>,
    onSelectTab: (HomeTab) -> Unit,
    tabFocus: (HomeTab) -> FocusRequester,
    onOpenClock: () -> Unit,
    onOpenSettings: () -> Unit,
    upTargets: UpTargets,
    onFocusTabs: () -> Unit,
    /** Shown in the clock card; null when the weather is off or not known yet. */
    weather: WeatherNow?,
    temperatureUnit: TempUnit,
    display: DisplaySettings,
    network: NetworkStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Each control records where it sits, so a card can send Up to the one above it
    fun Modifier.upTarget(key: String, focus: () -> Unit) = onGloballyPositioned {
        upTargets.place(key, it.boundsInRoot().center.x, focus)
    }
    val clockFocus = remember { FocusRequester() }
    val searchLabel = stringResource(R.string.cd_search)
    val voiceLabel = stringResource(R.string.cd_voice_search)
    val networkLabel = stringResource(R.string.settings_network)
    val inputsLabel = stringResource(R.string.cd_inputs)
    val settingsLabel = stringResource(R.string.settings_title)
    val networkDesc = networkDescription(network)
    val open = rememberOpener()
    val actions = buildList {
        if (display.showSearch) add(TopBarButtonSpec(R.drawable.ic_search, searchLabel) { open(context.keyboardSearchIntent(), searchLabel) })
        if (display.showVoiceSearch) add(TopBarButtonSpec(R.drawable.ic_mic, voiceLabel) { open(context.voiceSearchIntent(), voiceLabel) })
        if (display.showInputs) add(TopBarButtonSpec(R.drawable.ic_input, inputsLabel) { open(context.inputsIntent(), inputsLabel) })
        if (display.showNetwork) add(TopBarButtonSpec(network.iconRes, networkDesc) { open(context.wifiSettingsIntent(), networkLabel) })
        add(TopBarButtonSpec(R.drawable.ic_settings, settingsLabel, onOpenSettings))
    }
    val buttonFocus = remember(actions.size) { List(actions.size) { FocusRequester() } }

    // Measured rather than fixed, so the tabs never run into the clock or the buttons on any screen
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Clock(
                weather = weather,
                temperatureUnit = temperatureUnit,
                clockFormat = display.clockFormat,
                cardOpacity = display.cardOpacity,
                onClick = onOpenClock,
                modifier = Modifier
                    .focusRequester(clockFocus)
                    .upTarget("clock") { clockFocus.requestFocus() }
            )

            TabBar(
                tab = tab,
                categories = categories,
                onSelectTab = onSelectTab,
                tabFocus = tabFocus,
                cardOpacity = display.cardOpacity,
                modifier = Modifier.upTarget("tabs", onFocusTabs)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                actions.forEachIndexed { index, action ->
                    PitwallIconButton(
                        iconRes = action.iconRes,
                        contentDescription = action.contentDescription,
                        cardOpacity = display.cardOpacity,
                        modifier = Modifier
                            .focusRequester(buttonFocus[index])
                            .upTarget("button$index") { buttonFocus[index].requestFocus() }
                            .let { mod ->
                                // Left from the first button goes back to the selected tab rather than switching tabs
                                if (index == 0) mod.focusProperties { left = tabFocus(tab) } else mod
                            },
                        onClick = action.onClick
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val tabs = measurables[1].measure(loose.copy(maxWidth = Constraints.Infinity))
        val buttons = measurables[2].measure(loose)
        val width = constraints.maxWidth
        val placement = placeTabs(width, clock.width, buttons.width, tabs.width, TOP_BAR_GAP.roundToPx())
        val height = maxOf(clock.height, buttons.height, (tabs.height * placement.scale).roundToInt())
        layout(width, height) {
            clock.place(0, (height - clock.height) / 2)
            buttons.place(width - buttons.width, (height - buttons.height) / 2)
            tabs.placeWithLayer(placement.x.roundToInt(), ((height - tabs.height * placement.scale) / 2f).roundToInt()) {
                scaleX = placement.scale
                scaleY = placement.scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** Space the tabs keep from the clock and from the buttons. */
private val TOP_BAR_GAP = 24.dp

@Composable
internal fun networkDescription(status: NetworkStatus): String = when (status) {
    NetworkStatus.Offline -> stringResource(R.string.net_offline)
    NetworkStatus.Ethernet -> stringResource(R.string.net_ethernet)
    is NetworkStatus.Wifi -> stringResource(R.string.net_wifi, status.bars)
}

/** Time in the TV's 12/24-hour setting, the weekday and date underneath, then Pitwall's accent bar. */
@Composable
private fun Clock(
    weather: WeatherNow?,
    temperatureUnit: TempUnit,
    clockFormat: ClockFormat,
    cardOpacity: CardOpacity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val lifecycleOwner = LocalLifecycleOwner.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val clockLabel = stringResource(R.string.cd_clock_weather)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tvIs24Hour by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val updateTime = {
            now = System.currentTimeMillis()
            tvIs24Hour = DateFormat.is24HourFormat(context)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                updateTime()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        var registered = false
        val register = {
            if (!registered) {
                runCatching {
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    registered = true
                }
            }
        }
        val unregister = {
            if (registered) {
                runCatching { context.unregisterReceiver(receiver) }
                registered = false
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            updateTime()
            register()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    updateTime()
                    register()
                }
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    val is24Hour = clockFormat.is24Hour(tvIs24Hour)
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
            .background(if (isFocused) palette.cardFocused else palette.card.copy(alpha = cardOpacity.alpha))
            .then(if (isFocused) Modifier.border(2.dp, palette.accent, Corners.medium) else Modifier)
            .semantics { contentDescription = clockLabel }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
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
    cardOpacity: CardOpacity = CardOpacity.SOLID,
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
            .background(if (isFocused) palette.focus else palette.card.copy(alpha = cardOpacity.alpha))
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
    tab: HomeTab,
    categories: List<AppCategory>,
    onSelectTab: (HomeTab) -> Unit,
    tabFocus: (HomeTab) -> FocusRequester,
    cardOpacity: CardOpacity,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val density = LocalDensity.current
    // Where each tab sits inside the bar, measured once laid out
    val bounds = remember(categories) { mutableStateMapOf<HomeTab, Pair<Dp, Dp>>() }
    val (selectedX, selectedWidth) = bounds[tab] ?: (0.dp to 0.dp)
    val indicatorSpec = spring<Dp>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val x by animateDpAsState(selectedX, indicatorSpec, label = "tabX")
    val width by animateDpAsState(selectedWidth, indicatorSpec, label = "tabWidth")
    val tabs = listOf(
        HomeTab.Favorites to stringResource(R.string.tab_favorites),
        HomeTab.AllApps to stringResource(R.string.tab_all_apps)
    ) + categories.map { HomeTab.Category(it.id) to it.name }

    Box(
        modifier = modifier
            .clip(Corners.medium)
            .background(palette.card.copy(alpha = cardOpacity.alpha))
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
            tabs.forEach { (which, label) ->
                TabChip(
                    text = label,
                    selected = tab == which,
                    modifier = Modifier
                        .focusRequester(tabFocus(which))
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
    isLocked: Boolean = false,
    cardOpacity: CardOpacity = CardOpacity.SOLID,
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
    val lockedLabel = stringResource(R.string.cd_app_locked)

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
            .background(if (isFocused || isMoving) palette.cardFocused else palette.card.copy(alpha = cardOpacity.alpha))
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
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xB3111318))
                        .semantics { contentDescription = lockedLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
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
