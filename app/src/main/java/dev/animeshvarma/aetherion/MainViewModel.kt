package dev.animeshvarma.aetherion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.animeshvarma.aetherion.auth.LoginObserver
import dev.animeshvarma.aetherion.auth.LoginStep
import dev.animeshvarma.aetherion.auth.NoopLoginObserver
import dev.animeshvarma.aetherion.auth.StepStatus
import dev.animeshvarma.aetherion.auth.StepUi
import dev.animeshvarma.aetherion.auth.TerminalLevel
import dev.animeshvarma.aetherion.auth.TerminalLine
import dev.animeshvarma.aetherion.bloks.BloksLoginOrchestrator
import dev.animeshvarma.aetherion.bloks.LoginResult
import dev.animeshvarma.aetherion.bloks.TwoFactorRequiredException
import dev.animeshvarma.aetherion.network.InstagramApiClient
import dev.animeshvarma.aetherion.network.models.OwnProfileData
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import dev.animeshvarma.aetherion.util.DemoMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data class Loading(val stepMessage: String = "Working…") : LoginUiState
    data class TwoFactor(val twoFactorIdentifier: String) : LoginUiState
    data class Success(val result: LoginResult) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Loaded(val profile: OwnProfileData) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

private fun freshSteps(): List<StepUi> = LoginStep.entries.map { StepUi(it) }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val orchestrator: BloksLoginOrchestrator,
    private val apiClient: InstagramApiClient,
    val deviceFingerprint: DeviceFingerprint
) : ViewModel(), LoginObserver {

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _steps = MutableStateFlow(freshSteps())
    val steps: StateFlow<List<StepUi>> = _steps.asStateFlow()

    private val _terminal = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminal: StateFlow<List<TerminalLine>> = _terminal.asStateFlow()

    private val lineId = AtomicLong(0)
    private val imageCache = ConcurrentHashMap<String, Bitmap>()
    private var activeStep: LoginStep? = null
    private var pendingUsername: String = ""

    // ---------------- LoginObserver ----------------

    override fun step(step: LoginStep, status: StepStatus, detail: String?) {
        if (status == StepStatus.RUNNING) activeStep = step
        _steps.update { list ->
            list.map {
                if (it.step != step) it
                else it.copy(status = status, detail = detail ?: it.detail)
            }
        }
    }

    override fun log(level: TerminalLevel, text: String) {
        _terminal.update { current ->
            (current + TerminalLine(lineId.incrementAndGet(), level, text)).takeLast(200)
        }
    }

    fun clearTerminal() {
        _terminal.value = emptyList()
    }

    // ---------------- init ----------------

    init {
        if (deviceFingerprint.hasActiveSession()) {
            _state.value = LoginUiState.Success(
                LoginResult(
                    success = true,
                    sessionId = deviceFingerprint.sessionId,
                    userId = deviceFingerprint.selfUserId,
                    username = deviceFingerprint.selfUsername,
                    was2faFlow = deviceFingerprint.is2faEnabled
                )
            )
            refreshOwnProfile()
        } else {
            log(TerminalLevel.INFO, "aetherion ${dev.animeshvarma.aetherion.util.Constants.APP_VERSION} ready")
            log(TerminalLevel.INFO, "awaiting credentials")
        }
    }

    // ---------------- login ----------------

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginUiState.Error("Enter both your username and password.")
            return
        }
        pendingUsername = username.trim()
        activeStep = null
        _steps.value = freshSteps()
        _terminal.value = emptyList()
        _profileState.value = ProfileUiState.Loading
        _state.value = LoginUiState.Loading("Authenticating…")

        viewModelScope.launch {
            try {
                val result = orchestrator.login(pendingUsername, password, this@MainViewModel)
                val profile = orchestrator.fetchProfile(this@MainViewModel)
                _profileState.value = ProfileUiState.Loaded(profile)

                log(TerminalLevel.GOOD, "ready — opening workspace")
                delay(750)                      // let the final ticks render
                _state.value = LoginUiState.Success(result)

            } catch (e: TwoFactorRequiredException) {
                failActive("2FA required")
                log(TerminalLevel.ERROR, "two-factor is enabled on this account")
                log(TerminalLevel.WARN, "this build supports accounts without 2FA only")
                _state.value = LoginUiState.Error(
                    "That account has 2FA enabled. Turn it off, or use an account without it."
                )
            } catch (e: Throwable) {
                val message = e.message ?: "Login failed"
                failActive(message.take(40))
                log(TerminalLevel.ERROR, message)
                _state.value = LoginUiState.Error(message)
            }
        }
    }

    private fun failActive(detail: String) {
        activeStep?.let { step(it, StepStatus.FAILED, detail) }
        activeStep = null
    }

    fun reset() {
        activeStep = null
        _steps.value = freshSteps()
        _state.value = LoginUiState.Idle
    }

    fun resetDeviceIdentity() {
        deviceFingerprint.rotateDeviceIdentity()
        activeStep = null
        _steps.value = freshSteps()
        _terminal.value = emptyList()
        imageCache.clear()
        _state.value = LoginUiState.Idle
        _profileState.value = ProfileUiState.Loading
        log(TerminalLevel.WARN, "device identity rotated — new ids generated")
    }

    // ---------------- profile ----------------

    fun refreshOwnProfile() {
        _profileState.value = ProfileUiState.Loading
        viewModelScope.launch {
            runCatching { orchestrator.fetchProfile(NoopLoginObserver) }
                .onSuccess { _profileState.value = ProfileUiState.Loaded(it) }
                .onFailure {
                    _profileState.value = ProfileUiState.Error(
                        it.message ?: "Failed to load your profile"
                    )
                }
        }
    }

    suspend fun loadBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        imageCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = apiClient.fetchImageBytes(url) ?: return@withContext null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) imageCache[url] = bitmap
            bitmap
        }
    }

    fun logout() {
        deviceFingerprint.clearSession()
        imageCache.clear()
        activeStep = null
        _steps.value = freshSteps()
        _terminal.value = emptyList()
        _state.value = LoginUiState.Idle
        _profileState.value = ProfileUiState.Loading
        log(TerminalLevel.INFO, "session cleared")
    }

    fun enterDemoMode() {
        log(TerminalLevel.WARN, "demo mode — no requests will be sent")
        _profileState.value = ProfileUiState.Loaded(DemoMode.profile())
        _state.value = LoginUiState.Success(
            LoginResult(
                success = true,
                sessionId = "demo",
                userId = "0",
                username = "demo_account",
                was2faFlow = false
            )
        )
    }
}