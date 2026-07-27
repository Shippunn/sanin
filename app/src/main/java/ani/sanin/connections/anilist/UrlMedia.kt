package ani.sanin.connections.anilist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.os.bundleOf
import ani.sanin.loadMedia
import ani.sanin.startMainActivity
import ani.sanin.themes.ThemeManager

class UrlMedia : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        val data = intent?.data ?: return

        // If URL is an OAuth/api URL, forward to browser instead
        if (data.pathSegments?.getOrNull(0) == "api") {
            val browserIntent = Intent(Intent.ACTION_VIEW, data)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(browserIntent)
            finish()
            return
        }

        val type = data.pathSegments?.getOrNull(0)
        if (type != "user") {
            var id: Int? = intent?.extras?.getInt("media", 0) ?: 0
            var isMAL = false
            var continueMedia = true
            if (id == 0) {
                continueMedia = false
                isMAL = data.host != "anilist.co"
                id = data.pathSegments?.getOrNull(1)?.toIntOrNull()
            } else loadMedia = id
            val mediaType = type?.uppercase()
            startMainActivity(
                this,
                bundleOf("mediaId" to id, "mal" to isMAL, "continue" to continueMedia, "mediaType" to mediaType)
            )
        } else {
            val username = data.pathSegments?.getOrNull(1)
            startMainActivity(this, bundleOf("username" to username))
        }
    }
}