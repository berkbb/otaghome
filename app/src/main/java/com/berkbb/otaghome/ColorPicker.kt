package com.berkbb.otaghome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

/**
 * Colour picker for every colour the launcher lets the user choose (accent, solid background,
 * gradient ends). It always offers all three ways in: the hex code, the spectrum with saturation
 * and brightness, and the red, green and blue values; changing one updates the others.
 * [onReset], when set, adds a Default button.
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initial: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    onReset: (() -> Unit)? = null
) {
    val palette = LocalPalette.current
    var hsv by remember { mutableStateOf(ColorMath.toHsv(initial)) }
    var hexText by remember { mutableStateOf(HexColor.format(initial)) }
    val color = ColorMath.toRgb(hsv)
    val firstFocus = remember { FocusRequester() }

    fun setHsv(next: Hsv) {
        hsv = next
        hexText = HexColor.format(ColorMath.toRgb(next))
    }
    fun setRgb(next: Int) = setHsv(ColorMath.toHsv(next, keepHue = hsv.hue))

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(840.dp)
                    .shadow(16.dp, Corners.extraLarge)
                    .clip(Corners.extraLarge)
                    .background(palette.background)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title, color = palette.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                // Preview, hex code and the RGB values
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 200.dp, height = 96.dp)
                            .clip(Corners.medium)
                            .background(Color(color))
                            .border(1.5.dp, palette.textSecondary.copy(alpha = 0.4f), Corners.medium)
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HexField(
                            label = stringResource(R.string.picker_hex),
                            value = hexText,
                            onValueChange = { text ->
                                hexText = text
                                HexColor.parse(text)?.let { parsed -> hsv = ColorMath.toHsv(parsed, keepHue = hsv.hue) }
                            }
                        )
                        if (HexColor.parse(hexText) == null) {
                            Text(text = stringResource(R.string.bg_invalid_hex), color = Color(0xFFFF8A80), fontSize = 13.sp)
                        } else {
                            Text(
                                text = stringResource(R.string.color_rgb, ColorMath.red(color), ColorMath.green(color), ColorMath.blue(color)),
                                color = palette.textSecondary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Spectrum on the left, red / green / blue on the right
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorSlider(
                            label = stringResource(R.string.picker_hue),
                            valueText = "${hsv.hue.roundToInt()}°",
                            value = hsv.hue, max = 359f, step = 2f,
                            track = Brush.horizontalGradient(HUE_STOPS),
                            modifier = Modifier.focusRequester(firstFocus)
                        ) { setHsv(hsv.copy(hue = it)) }
                        ColorSlider(
                            label = stringResource(R.string.picker_saturation),
                            valueText = "${(hsv.saturation * 100).roundToInt()}%",
                            value = hsv.saturation, max = 1f, step = 0.01f,
                            track = Brush.horizontalGradient(
                                listOf(Color(ColorMath.toRgb(hsv.copy(saturation = 0f))), Color(ColorMath.toRgb(hsv.copy(saturation = 1f))))
                            )
                        ) { setHsv(hsv.copy(saturation = it)) }
                        ColorSlider(
                            label = stringResource(R.string.picker_brightness),
                            valueText = "${(hsv.value * 100).roundToInt()}%",
                            value = hsv.value, max = 1f, step = 0.01f,
                            track = Brush.horizontalGradient(listOf(Color.Black, Color(ColorMath.toRgb(hsv.copy(value = 1f)))))
                        ) { setHsv(hsv.copy(value = it)) }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val r = ColorMath.red(color)
                        val g = ColorMath.green(color)
                        val b = ColorMath.blue(color)
                        ColorSlider(
                            label = stringResource(R.string.picker_red), valueText = "$r",
                            value = r.toFloat(), max = 255f, step = 1f,
                            track = Brush.horizontalGradient(listOf(Color(ColorMath.rgb(0, g, b)), Color(ColorMath.rgb(255, g, b))))
                        ) { setRgb(ColorMath.rgb(it.roundToInt(), g, b)) }
                        ColorSlider(
                            label = stringResource(R.string.picker_green), valueText = "$g",
                            value = g.toFloat(), max = 255f, step = 1f,
                            track = Brush.horizontalGradient(listOf(Color(ColorMath.rgb(r, 0, b)), Color(ColorMath.rgb(r, 255, b))))
                        ) { setRgb(ColorMath.rgb(r, it.roundToInt(), b)) }
                        ColorSlider(
                            label = stringResource(R.string.picker_blue), valueText = "$b",
                            value = b.toFloat(), max = 255f, step = 1f,
                            track = Brush.horizontalGradient(listOf(Color(ColorMath.rgb(r, g, 0)), Color(ColorMath.rgb(r, g, 255))))
                        ) { setRgb(ColorMath.rgb(r, g, it.roundToInt())) }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    if (onReset != null) {
                        DialogButton(text = stringResource(R.string.btn_default), primary = false) {
                            onReset()
                            onDismiss()
                        }
                    }
                    DialogButton(text = stringResource(R.string.btn_cancel), primary = false, onClick = onDismiss)
                    DialogButton(text = stringResource(R.string.btn_apply), primary = true) {
                        // An unfinished hex code does not count; the sliders' colour does
                        onPick(color)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** The spectrum: red through yellow, green, cyan, blue and magenta back to red. */
private val HUE_STOPS = listOf(0f, 60f, 120f, 180f, 240f, 300f, 359f).map { Color(ColorMath.toRgb(Hsv(it, 1f, 1f))) }

/**
 * A remote-friendly slider: Left and Right step the value (faster while held), Up and Down move
 * to the next control. The track shows the colours the value would give.
 */
@Composable
private fun ColorSlider(
    label: String,
    valueText: String,
    value: Float,
    max: Float,
    step: Float,
    track: Brush,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = Corners.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isFocused) palette.cardFocused else palette.card)
            .border(2.dp, if (isFocused) palette.accent else Color.Transparent, shape)
            .onPreviewKeyEvent { event ->
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) {
                    val boost = if (event.nativeKeyEvent.repeatCount > 0) 4 else 1
                    onChange((value + direction * step * boost).coerceIn(0f, max))
                }
                true
            }
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = valueText, color = palette.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(track))
            val thumb = 16.dp
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - thumb) * (value / max).coerceIn(0f, 1f))
                    .size(thumb)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color(0xFF111318), CircleShape)
            )
        }
    }
}

/** A hex code field with a swatch of the colour typed so far. */
@Composable
internal fun HexField(label: String, value: String, onValueChange: (String) -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val parsed = HexColor.parse(value)
    val shape = Corners.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(2.dp, if (isFocused) palette.accent else Color.Transparent, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(parsed?.let { Color(it) } ?: Color.Transparent)
                .border(1.5.dp, palette.textSecondary.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = label, color = palette.textSecondary, fontSize = 12.sp)
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.take(9)) },
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = TextStyle(color = palette.textPrimary, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Pitwall-style dialog button; the primary one is filled with the accent. */
@Composable
private fun DialogButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = Corners.medium
    Box(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 120.dp)
            .clip(shape)
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

/** A colour setting's row: swatch, name, hex code and RGB values; OK opens the picker. */
@Composable
internal fun ColorRow(title: String, color: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val primary = if (isFocused) palette.onFocus else palette.textPrimary
    val secondary = if (isFocused) palette.onFocus.copy(alpha = 0.75f) else palette.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Corners.medium)
            .background(if (isFocused) palette.focus else palette.card)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.5.dp, secondary.copy(alpha = 0.5f), CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${HexColor.format(color)} · " +
                    stringResource(R.string.color_rgb, ColorMath.red(color), ColorMath.green(color), ColorMath.blue(color)),
                color = secondary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
