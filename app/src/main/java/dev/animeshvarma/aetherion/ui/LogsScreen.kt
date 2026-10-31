package dev.animeshvarma.aetherion.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.aetherion.MainViewModel
import dev.animeshvarma.aetherion.network.interceptors.LogEntry
import dev.animeshvarma.aetherion.network.interceptors.LogType
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var autoScroll by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<LogType?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(filter, autoScroll) {
        while (true) {
            if (autoScroll) {
                logs = LoggingInterceptor.getLogs().filter {
                    filter == null || it.type == filter
                }
            }
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text("Network Logs (${logs.size})") },
                actions = {
                    TextButton(onClick = {
                        val logText = logs.reversed().joinToString(separator = "\n\n") { entry ->
                            val time = timeFormat.format(Date(entry.timestamp))
                            "[${entry.tag}] $time | ID: ${entry.requestId}\n${entry.message}"
                        }
                        clipboardManager.setText(AnnotatedString(logText))
                        Toast.makeText(context, "Copied ${logs.size} logs", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy")
                    }

                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = if (autoScroll) "Pause" else "Resume",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        LoggingInterceptor.clearLogs()
                        logs = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }

                    Box {
                        TextButton(onClick = { menuExpanded = true }) {
                            Text(filter?.name ?: "All")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = {
                                    filter = null
                                    menuExpanded = false
                                }
                            )
                            LogType.entries.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        filter = if (filter == type) null else type
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No logs available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Key includes the index to ensure 100% uniqueness even with millisecond timestamps
                itemsIndexed(
                    items = logs.reversed(),
                    key = { index, entry -> "${entry.timestamp}_${index}_${entry.requestId}" }
                ) { _, entry ->
                    LogEntryItem(entry = entry, timeFormat = timeFormat)
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: LogEntry, timeFormat: SimpleDateFormat) {
    val timeString = timeFormat.format(Date(entry.timestamp))

    val (bgColor, textColor) = when (entry.type) {
        LogType.REQUEST -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LogType.RESPONSE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LogType.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogType.NETWORK -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        LogType.INFO -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${entry.tag}] $timeString",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.85f)
                )
                if (entry.requestId.isNotEmpty()) {
                    Text(
                        text = "ID: ${entry.requestId}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textColor.copy(alpha = 0.65f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.message,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                maxLines = 24,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}