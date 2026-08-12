package dev.animeshvarma.aetherion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.animeshvarma.aetherion.bloks.BloksLoginOrchestrator
import dev.animeshvarma.aetherion.bloks.LoginResult
import dev.animeshvarma.aetherion.bloks.TwoFactorRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class TwoFactor(val twoFactorIdentifier: String) : LoginUiState
    data class Success(val result: LoginResult) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val orchestrator: BloksLoginOrchestrator
) : ViewModel() {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var pendingUsername: String = ""
    private var pendingTwoFactorId: String = ""

    fun login(username: String, password: String) {
        _state.value = LoginUiState.Loading
        pendingUsername = username
        viewModelScope.launch {
            try {
                val result = orchestrator.login(username, password)
                _state.value = LoginUiState.Success(result)
            } catch (e: TwoFactorRequiredException) {
                pendingTwoFactorId = e.twoFactorIdentifier
                _state.value = LoginUiState.TwoFactor(e.twoFactorIdentifier)
            } catch (e: Exception) {
                _state.value = LoginUiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun submitTwoFactor(code: String) {
        _state.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val result = orchestrator.submitTwoFactor(
                    pendingUsername, pendingTwoFactorId, code
                )
                _state.value = LoginUiState.Success(result)
            } catch (e: TwoFactorRequiredException) {
                pendingTwoFactorId = e.twoFactorIdentifier
                _state.value = LoginUiState.TwoFactor(e.twoFactorIdentifier)
            } catch (e: Exception) {
                _state.value = LoginUiState.Error(e.message ?: "2FA failed")
            }
        }
    }

    fun reset() { _state.value = LoginUiState.Idle }
}