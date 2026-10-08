package com.guardian.sentinel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class SortMode { RISK, UPLOAD }

/** Urutkan hasil pindaian sesuai mode yang dipilih. */
fun List<AppRisk>.sortedFor(mode: SortMode): List<AppRisk> = when (mode) {
    SortMode.RISK -> sortedWith(
        compareByDescending<AppRisk> { it.score }.thenByDescending { it.txBytes }
    )
    SortMode.UPLOAD -> sortedWith(
        compareByDescending<AppRisk> { it.txBytes }.thenByDescending { it.score }
    )
}

@Composable
fun SortToggle(
    mode: SortMode,
    onModeChange: (SortMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SortOption(
            label = "Risiko Tertinggi",
            selected = mode == SortMode.RISK,
            onClick = { onModeChange(SortMode.RISK) },
            modifier = Modifier.weight(1f)
        )
        SortOption(
            label = "Upload Terbesar",
            selected = mode == SortMode.UPLOAD,
            onClick = { onModeChange(SortMode.UPLOAD) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SortOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}