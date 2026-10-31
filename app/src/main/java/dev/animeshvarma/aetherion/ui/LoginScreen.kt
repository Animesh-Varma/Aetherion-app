package dev.animeshvarma.aetherion.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.animeshvarma.aetherion.LoginUiState
import dev.animeshvarma.aetherion.MainViewModel
import dev.animeshvarma.aetherion.auth.StepStatus
import dev.animeshvarma.aetherion.auth.StepUi
import dev.animeshvarma.aetherion.network.interceptors.LogEntry
import dev.animeshvarma.aetherion.network.interceptors.LogType
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import dev.animeshvarma.aetherion.util.Constants
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val terminal by viewModel.terminal.collectAsStateWithLifecycle()

    val busy = state is LoginUiState.Loading
    val failed = state is LoginUiState.TwoFactor || state is LoginUiState.Error

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoginSuccess()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Header()

            Spacer(Modifier.height(8.dp))

            CredentialsCard(
                username = username,
                password = password,
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                busy = busy,
                failed = failed,
                errorMessage = when (val s = state) {
                    is LoginUiState.Error -> s.message
                    is LoginUiState.TwoFactor -> "Two-factor authentication is not supported."
                    else -> null
                },
                onLogin = { viewModel.login(username, password) },
                onReset = { viewModel.reset() }
            )

            Spacer(Modifier.height(12.dp))

            StepGuideCard(steps = steps, busy = busy)

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { viewModel.resetDeviceIdentity() },
                    enabled = !busy
                ) {
                    Text(
                        "Reset identity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { viewModel.enterDemoMode() },
                    enabled = !busy
                ) {
                    Text(
                        "Preview demo UI",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Integrated real-time network logs right below the login sequence
            IntegratedNetworkLogsCard(terminalCount = terminal.size)

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Constants.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = Constants.APP_VERSION,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "Instagram ${Constants.IG_VERSION} · ${Constants.IG_VERSION_CODE}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CredentialsCard(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    busy: Boolean,
    failed: Boolean,
    errorMessage: String?,
    onLogin: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                placeholder = { Text("username, email or phone") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (busy) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Authenticating…")
                }
            } else {
                Button(
                    onClick = onLogin,
                    enabled = username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (failed) "Retry login" else "Log in")
                }
            }

            if (failed) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear and edit credentials", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StepGuideCard(steps: List<StepUi>, busy: Boolean) {
    val done = steps.count { it.status == StepStatus.DONE }
    val progress = if (steps.isEmpty()) 0f else done.toFloat() / steps.size

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Login sequence",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$done / ${steps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(Modifier.height(16.dp))

            steps.forEachIndexed { index, step ->
                StepRow(step = step, isLast = index == steps.lastIndex)
            }

            if (!busy && steps.all { it.status == StepStatus.PENDING }) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Six stages: device registration, attestation, credential submission, session probe, profile and media.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepRow(step: StepUi, isLast: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val error = MaterialTheme.colorScheme.error

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepDot(step.status)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(26.dp)
                        .background(
                            when (step.status) {
                                StepStatus.DONE -> primary.copy(alpha = 0.55f)
                                StepStatus.FAILED -> error.copy(alpha = 0.45f)
                                else -> outline.copy(alpha = 0.6f)
                            }
                        )
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
        ) {
            Text(
                text = step.step.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (step.status == StepStatus.PENDING) FontWeight.Normal else FontWeight.SemiBold,
                color = when (step.status) {
                    StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                    StepStatus.FAILED -> error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = step.detail ?: step.step.caption,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepDot(status: StepStatus) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val error = MaterialTheme.colorScheme.error
    val onError = MaterialTheme.colorScheme.onError
    val outline = MaterialTheme.colorScheme.outlineVariant

    when (status) {
        StepStatus.PENDING -> Box(
            modifier = Modifier
                .size(20.dp)
                .border(1.5.dp, outline, CircleShape)
        )

        StepStatus.RUNNING -> Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = primary
            )
        }

        StepStatus.DONE -> Box(
            modifier = Modifier
                .size(20.dp)
                .background(primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "done",
                tint = onPrimary,
                modifier = Modifier.size(12.dp)
            )
        }

        StepStatus.FAILED -> Box(
            modifier = Modifier
                .size(20.dp)
                .background(error, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "failed",
                tint = onError,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------ *
 *  Integrated Network & Protocol Logs Card
 * ------------------------------------------------------------------ */

@Composable
private fun IntegratedNetworkLogsCard(terminalCount: Int) {
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var autoScroll by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf<LogType?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val listState = rememberLazyListState()

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

    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with title and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Network Logs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "${logs.size}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { autoScroll = !autoScroll },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (autoScroll) "Pause" else "Resume",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = filter?.name ?: "All",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
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

                    TextButton(
                        onClick = {
                            val logText = logs.joinToString(separator = "\n\n") { entry ->
                                val time = timeFormat.format(Date(entry.timestamp))
                                "[${entry.tag}] $time | ID: ${entry.requestId}\n${entry.message}"
                            }
                            clipboardManager.setText(AnnotatedString(logText))
                            Toast.makeText(context, "Copied ${logs.size} logs", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        enabled = logs.isNotEmpty()
                    ) {
                        Text("Copy", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = {
                            LoggingInterceptor.clearLogs()
                            logs = emptyList()
                        },
                        modifier = Modifier.size(32.dp),
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Scrollable stream
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Awaiting network activity during login sequence…",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        items = logs,
                        key = { index, entry -> "${entry.timestamp}_${index}_${entry.requestId}" }
                    ) { _, entry ->
                        InlineLogItem(entry = entry, timeFormat = timeFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineLogItem(entry: LogEntry, timeFormat: SimpleDateFormat) {
    val timeString = timeFormat.format(Date(entry.timestamp))
    val (bgColor, textColor) = when (entry.type) {
        LogType.REQUEST -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LogType.RESPONSE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LogType.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogType.NETWORK -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        LogType.INFO -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${entry.tag}] $timeString",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.9f)
                )
                if (entry.requestId.isNotEmpty()) {
                    Text(
                        text = "ID: ${entry.requestId}",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.message,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = textColor,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}