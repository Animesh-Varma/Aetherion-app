package dev.animeshvarma.aetherion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.animeshvarma.aetherion.LoginUiState
import dev.animeshvarma.aetherion.MainViewModel

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) onLoginSuccess()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Aetherion", fontSize = 32.sp)
        Text("Research Client", fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        when (val s = state) {
            is LoginUiState.TwoFactor -> {
                Text("Enter the 6-digit verification code", fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.take(6).filter { c -> c.isDigit() } },
                    label = { Text("Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.submitTwoFactor(code) },
                    enabled = code.length == 6,
                    modifier = Modifier.fillMaxWidth(0.8f).height(48.dp)
                ) { Text("Verify") }
            }

            is LoginUiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.reset() }) { Text("Try again") }
            }

            is LoginUiState.Loading -> {
                CircularProgressIndicator()
                Text("Working...", fontSize = 13.sp)
            }

            is LoginUiState.Success -> Text("Logged in!", fontSize = 14.sp)

            LoginUiState.Idle -> {
                OutlinedTextField(username, { username = it }, label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { viewModel.login(username, password) },
                    enabled = username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(0.8f).height(48.dp)
                ) { Text("Log In") }
            }
        }
    }
}