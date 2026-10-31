package dev.animeshvarma.aetherion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.animeshvarma.aetherion.ui.LoginScreen
import dev.animeshvarma.aetherion.ui.LogsScreen
import dev.animeshvarma.aetherion.ui.ProfileScreen
import dev.animeshvarma.aetherion.ui.theme.AetherionTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AetherionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = hiltViewModel()
                    val loginState by viewModel.state.collectAsStateWithLifecycle()

                    if (loginState is LoginUiState.Success) {
                        MainTabbedWorkspace(viewModel = viewModel)
                    } else {
                        LoginScreen(
                            viewModel = viewModel,
                            onLoginSuccess = { /* MainViewModel.state drives the switch */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabbedWorkspace(viewModel: MainViewModel) {
    // 0 = Profile (default), 1 = Logs
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        // WindowInsets(0, 0, 0, 0) prevents this outer Scaffold from consuming
        // the top status bar insets. The child screens (ProfileScreen / LogsScreen)
        // have their own TopAppBars that handle top insets without double padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Logs") },
                    label = { Text("Logs") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> ProfileScreen(viewModel = viewModel)
                1 -> LogsScreen(viewModel = viewModel)
            }
        }
    }
}