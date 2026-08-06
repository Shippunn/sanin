package ani.sanin.connections.auth

import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only diagnostics for login tracking.
 * These values are never uploaded or exposed externally.
 */
object LoginDiagnostics {
    private const val KEY_LAST_LOGIN_TIMESTAMP = "login_last_timestamp"
    private const val KEY_LOGIN_METHOD = "login_method"
    private const val KEY_APP_VERSION = "login_app_version"

    enum class LoginMethod {
        QR_CODE,
        TOKEN_PASTE,
        OAUTH_BROWSER
    }

    /**
     * Record a successful login.
     */
    fun recordLogin(method: LoginMethod) {
        PrefManager.setVal(PrefName.LastLoginTimestamp, System.currentTimeMillis())
        PrefManager.setVal(PrefName.LoginMethod, method.name)
        PrefManager.setVal(PrefName.LoginAppVersion, getAppVersion())
    }

    /**
     * Get the last successful login timestamp.
     */
    fun getLastLoginTimestamp(): Long {
        return PrefManager.getVal(PrefName.LastLoginTimestamp, 0L)
    }

    /**
     * Get the login method used.
     */
    fun getLoginMethod(): String {
        return PrefManager.getVal(PrefName.LoginMethod, "UNKNOWN")
    }

    /**
     * Get the app version at login time.
     */
    fun getAppVersion(): String {
        return try {
            val packageInfo = ani.sanin.currContext()?.packageManager
                ?.getPackageInfo(ani.sanin.currContext()?.packageName ?: "", 0)
            packageInfo?.versionName ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    /**
     * Get a formatted string of the last login time.
     */
    fun getLastLoginFormatted(): String {
        val timestamp = getLastLoginTimestamp()
        if (timestamp == 0L) return "Never"
        
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Get diagnostics summary for local display.
     */
    fun getDiagnosticsSummary(): Map<String, String> {
        return mapOf(
            "Last Login" to getLastLoginFormatted(),
            "Login Method" to getLoginMethod(),
            "App Version" to getAppVersion()
        )
    }
}
