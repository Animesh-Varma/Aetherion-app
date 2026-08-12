package dev.animeshvarma.aetherion.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.aetherion.MainViewModel
import dev.animeshvarma.aetherion.network.interceptors.LogEntry
import dev.animeshvarma.aetherion.network.interceptors.LogType
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var autoScroll by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<LogType?>(null) }

    // Tools for copying to clipboard and showing a Toast
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    LaunchedEffect(Unit) {
        while (true) {
            logs = LoggingInterceptor.getLogs().filter {
                filter == null || it.type == filter
            }
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Logs") },
                actions = {
                    // --- NEW COPY BUTTON ---
                    TextButton(onClick = {
                        val logText = logs.reversed().joinToString(separator = "\n\n") { entry ->
                            val time = timeFormat.format(Date(entry.timestamp))
                            "[${entry.tag}] $time | ID: ${entry.requestId}\n${entry.message}"
                        }
                        clipboardManager.setText(AnnotatedString(logText))
                        Toast.makeText(context, "Copied ${logs.size} logs", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy", color = MaterialTheme.colorScheme.onSurface)
                    }

                    IconButton(onClick = { LoggingInterceptor.clearLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                            contentDescription = if (autoScroll) "Pause" else "Resume"
                        )
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Text("Filter")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        LogType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    filter = if (filter == type) null else type
                                    menuExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("All") },
                            onClick = {
                                filter = null
                                menuExpanded = false
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs.reversed()) { entry ->
                LogEntryItem(entry = entry, timeFormat = timeFormat)
            }
        }
    }
}

@Composable
fun LogEntryItem(entry: LogEntry, timeFormat: SimpleDateFormat) {
    val timeString = timeFormat.format(Date(entry.timestamp))

    val (bgColor, textColor) = when (entry.type) {
        LogType.REQUEST -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        LogType.RESPONSE -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        LogType.ERROR -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        LogType.NETWORK -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        else -> Color(0xFFFAFAFA) to Color(0xFF424242)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[${entry.tag}] $timeString",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = textColor.copy(alpha = 0.7f)
                )
                if (entry.requestId.isNotEmpty()) {
                    Text(
                        text = "ID: ${entry.requestId}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.message,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                maxLines = 20,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}