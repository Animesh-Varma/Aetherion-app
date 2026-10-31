package dev.animeshvarma.aetherion.auth

/** The six stages the login pipeline walks through, in order. */
enum class LoginStep(val title: String, val caption: String) {
    DEVICE("Register device", "launcher/sync · password encryption key"),
    ATTEST("Sign attestation", "create_android_keystore · ECDSA nonce"),
    SUBMIT("Submit credentials", "accounts/login · #PWD_INSTAGRAM:4"),
    SESSION("Verify session", "accounts/current_user"),
    PROFILE("Fetch profile", "multiple_accounts · users/{id}/info"),
    MEDIA("Fetch media", "feed/user · highlights_tray")
}

enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

data class StepUi(
    val step: LoginStep,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String? = null
)

enum class TerminalLevel { INFO, GOOD, WARN, ERROR }

data class TerminalLine(
    val id: Long,
    val level: TerminalLevel,
    val text: String
)

/** Callback surface the orchestrator uses to narrate itself. */
interface LoginObserver {
    fun step(step: LoginStep, status: StepStatus, detail: String? = null)
    fun log(level: TerminalLevel, text: String)
}

object NoopLoginObserver : LoginObserver {
    override fun step(step: LoginStep, status: StepStatus, detail: String?) = Unit
    override fun log(level: TerminalLevel, text: String) = Unit
}