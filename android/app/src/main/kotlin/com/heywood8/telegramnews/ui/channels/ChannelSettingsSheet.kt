package com.heywood8.telegramnews.ui.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heywood8.telegramnews.domain.model.Subscription

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelSettingsSheet(
    sub: Subscription,
    onSetMode: (String) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onSetIncludePhotos: (Boolean) -> Unit,
) {
    var newKeyword by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("@${sub.channel}", style = MaterialTheme.typography.titleLarge)

        // Mode selector
        Text("Filter mode", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all", "include", "exclude").forEach { mode ->
                FilterChip(
                    selected = sub.mode == mode,
                    onClick = { onSetMode(mode) },
                    label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        // Photo toggle
        Text("Photos", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Include photos", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = sub.includePhotos,
                onCheckedChange = onSetIncludePhotos,
            )
        }

        // Keywords
        Text("Keywords", style = MaterialTheme.typography.labelMedium)
        if (sub.keywords.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sub.keywords.forEach { kw ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(kw) },
                        trailingIcon = {
                            IconButton(onClick = { onRemoveKeyword(kw) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove keyword")
                            }
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newKeyword,
                onValueChange = { newKeyword = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Add keyword") },
                singleLine = true,
            )
            TextButton(
                onClick = {
                    if (newKeyword.isNotBlank()) {
                        onAddKeyword(newKeyword.trim())
                        newKeyword = ""
                    }
                },
                enabled = newKeyword.isNotBlank(),
            ) {
                Text("Add")
            }
        }
    }
}
