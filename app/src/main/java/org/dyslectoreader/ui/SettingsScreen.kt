package org.dyslectoreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dyslectoreader.data.RulerSettings
import org.dyslectoreader.data.SettingsRepository
import kotlin.math.roundToInt

private val lineColorChoices = listOf(
    0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFB8C00,
    0xFF8E24AA, 0xFF00ACC1, 0xFF212121, 0xFFFFFFFF,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings by repo.settings.collectAsState(initial = RulerSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle("Blur")
            SliderSetting(
                label = "Blur area height",
                value = settings.blurHeightDp,
                range = 40f..320f,
                format = { "${it.roundToInt()} dp" },
            ) { scope.launch { repo.setBlurHeight(it) } }
            SliderSetting(
                label = "Blur intensity & opacity",
                value = settings.intensity,
                range = 0.1f..1f,
                format = { "${(it * 100).roundToInt()} %" },
            ) { scope.launch { repo.setIntensity(it) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Ruler")
            SwitchSetting(
                label = "Top half of ruler",
                description = "Blur above the reading gap as well as below it",
                checked = settings.topEnabled,
            ) { scope.launch { repo.setTopEnabled(it) } }
            SliderSetting(
                label = "Reading gap height",
                value = settings.gapHeightDp,
                range = RulerSettings.MIN_GAP_DP..RulerSettings.MAX_GAP_DP,
                format = { "${it.roundToInt()} dp" },
            ) { scope.launch { repo.setGapHeight(it) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Lines")
            SwitchSetting(
                label = "Visible lines",
                description = "Draw the two ruler lines instead of keeping them invisible",
                checked = settings.linesVisible,
            ) { scope.launch { repo.setLinesVisible(it) } }
            if (settings.linesVisible) {
                SliderSetting(
                    label = "Line thickness",
                    value = settings.lineThicknessDp,
                    range = 1f..12f,
                    format = { "${it.roundToInt()} dp" },
                ) { scope.launch { repo.setLineThickness(it) } }
                Text("Line color", style = MaterialTheme.typography.bodyLarge)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lineColorChoices.forEach { c ->
                        val selected = settings.lineColor == c
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .size(36.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable { scope.launch { repo.setLineColor(c) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(format(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
