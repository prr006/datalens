package com.datalens.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.datalens.app.domain.model.SummaryEntry
import com.datalens.app.util.ByteFormatter

/** "Mobile Data" summary: today / yesterday / 7 days / this cycle + comparisons. */
@Composable
fun SummaryCard(
    today: SummaryEntry,
    yesterday: SummaryEntry,
    lastSevenDays: SummaryEntry,
    currentCycle: SummaryEntry,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Mobile data", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            SummaryRow(today)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            SummaryRow(yesterday)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            SummaryRow(lastSevenDays)
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            SummaryRow(currentCycle)

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Comparisons are only shown when the earlier period recorded usage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryRow(entry: SummaryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.bodyMedium)
            if (entry.comparison != null) {
                Text(
                    text = entry.comparison,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = ByteFormatter.format(entry.bytes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
