package ani.sanin.connections.anilist

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ani.sanin.logError
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import kotlinx.coroutines.launch
import ani.sanin.startMainActivity
import ani.sanin.themes.ThemeManager

class Login : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        val data: Uri? = intent?.data
        if (data != null) {
            val fragment = data.encodedFragment ?: data.toString()
            val match = Regex("""(?<=access_token=)[^&]+""").find(fragment)
            if (match != null) {
                Anilist.token = match.value
                PrefManager.setVal(PrefName.AnilistToken, match.value)
                // Fetch the profile (id/name/avatar) before opening the
                // app, exactly like the QR login flow. Without this the
                // first user query fails with
                // "User ID/Name and type arguments required".
                lifecycleScope.launch {
                    try {
                        Anilist.query.getUserData()
                    } catch (e: Exception) {
                        logError(e)
                    }
                    startMainActivity(this@Login)
                }
            } else {
                snackString("Login failed: no token in response")
                logError(Exception("Anilist login: no token found in $fragment"))
                startMainActivity(this)
            }
        } else {
            snackString("Login failed: no response URI")
            logError(Exception("Anilist login: intent.data is null"))
            startMainActivity(this)
        }
    }
}
