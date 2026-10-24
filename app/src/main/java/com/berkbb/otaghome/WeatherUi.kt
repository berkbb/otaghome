package com.berkbb.otaghome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asked once before the internet address is used to find the place: allow it, pick a city
 * instead, or turn the weather off. Back counts as not deciding yet; it is asked again next time.
 */
@Composable
internal fun WeatherConsentDialog(onAllow: () -> Unit, onPickCity: () -> Unit, onDeny: () -> Unit, onLater: () -> Unit) {
    val palette = LocalPalette.current
    // Focus starts on "Pick a city", so sharing the internet address takes a deliberate move
    val pickFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onLater) {
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { pickFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(560.dp)
                .shadow(16.dp, Corners.extraLarge)
                .clip(Corners.extraLarge)
                .background(palette.background)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).background(palette.accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(R.drawable.ic_w_partly), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = stringResource(R.string.consent_title), color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = stringResource(R.string.consent_text), color = palette.textSecondary, fontSize = 16.sp, lineHeight = 23.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                PillButton(stringResource(R.string.consent_deny), primary = false, onClick = onDeny)
                PillButton(stringResource(R.string.consent_pick_city), primary = false, modifier = Modifier.focusRequester(pickFocus), onClick = onPickCity)
                PillButton(stringResource(R.string.consent_allow), primary = true, onClick = onAllow)
            }
        }
    }
}

/**
 * The weather settings: shown or not, where (a city searched in the TV's language, or the place
 * found from the internet address when allowed), and how often it is refreshed.
 */
@Composable
internal fun WeatherPage(
    settings: WeatherSettings,
    firstRowFocus: FocusRequester,
    onEnabledChange: (Boolean) -> Unit,
    onPlaceChange: (Place?) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onCycleInterval: () -> Unit,
    onCycleUnit: () -> Unit,
    search: suspend (query: String, language: String) -> List<Place>?
) {
    val palette = LocalPalette.current
    val language = LocalConfiguration.current.locales[0].language
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // null: not searched yet; empty: nothing found
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val runSearch: () -> Unit = {
        if (query.isNotBlank() && !searching) {
            searching = true
            scope.launch {
                val found = search(query, language)
                failed = found == null
                results = found.orEmpty()
                searching = false
            }
        }
    }

    SettingsRow(
        iconRes = R.drawable.ic_w_partly,
        title = stringResource(R.string.weather_show),
        value = stringResource(if (settings.enabled) R.string.state_on else R.string.state_off),
        modifier = Modifier.focusRequester(firstRowFocus),
        onClick = { onEnabledChange(!settings.enabled) }
    )
    SettingsRow(
        iconRes = R.drawable.ic_language,
        title = stringResource(R.string.weather_location),
        value = settings.place?.label ?: stringResource(if (settings.ipConsent == true) R.string.weather_auto else R.string.weather_not_set),
        onClick = { if (settings.place != null) onPlaceChange(null) }
    )
    SettingsRow(
        iconRes = R.drawable.ic_wallpaper,
        title = stringResource(R.string.weather_ip),
        value = stringResource(
            when (settings.ipConsent) {
                true -> R.string.consent_allowed
                false -> R.string.consent_denied
                null -> R.string.consent_unasked
            }
        ),
        onClick = { onConsentChange(settings.ipConsent != true) }
    )
    if (settings.place != null) {
        SettingsRow(iconRes = R.drawable.ic_language, title = stringResource(R.string.weather_use_auto), value = null, onClick = { onPlaceChange(null) })
    }
    SettingsRow(
        iconRes = R.drawable.ic_w_clear_day,
        title = stringResource(R.string.weather_unit),
        value = settings.unit.symbol,
        onClick = onCycleUnit
    )
    SettingsRow(
        iconRes = R.drawable.ic_timer,
        title = stringResource(R.string.weather_interval),
        value = stringResource(R.string.minutes, settings.intervalMinutes),
        onClick = onCycleInterval
    )

    // City search, in the TV's language
    Spacer(modifier = Modifier.height(6.dp))
    SearchField(
        hint = stringResource(R.string.weather_search_hint),
        value = query,
        onValueChange = { query = it },
        onSearch = runSearch
    )
    PillButton(stringResource(R.string.btn_search), primary = true, modifier = Modifier.fillMaxWidth(), onClick = runSearch)
    when {
        searching -> Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.5.dp)
        }
        failed -> Text(text = stringResource(R.string.weather_search_failed), color = Color(0xFFFF8A80), fontSize = 14.sp)
        results?.isEmpty() == true -> Text(text = stringResource(R.string.weather_no_results), color = palette.textSecondary, fontSize = 14.sp)
        else -> results.orEmpty().forEach { place ->
            SettingsRow(
                iconRes = R.drawable.ic_w_partly,
                title = place.name,
                value = place.label,
                onClick = {
                    onPlaceChange(place)
                    results = null
                    query = ""
                }
            )
        }
    }
    Text(text = stringResource(R.string.weather_sources), color = palette.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
}

/** A one-line text box; OK opens the TV's keyboard, Done runs [onSearch]. */
@Composable
private fun SearchField(hint: String, value: String, onValueChange: (String) -> Unit, onSearch: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Corners.medium)
            .background(palette.card)
            .border(2.dp, if (isFocused) palette.accent else Color.Transparent, Corners.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) Text(text = hint, color = palette.textSecondary, fontSize = 17.sp)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(60)) },
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            .padding(horizontal = 20.dp),
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
