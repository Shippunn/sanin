package ani.sanin.settings.extensionprefs

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import eu.kanade.tachiyomi.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource

class AnimeSourcePreferencesFragment : PreferenceFragmentCompat() {
    private var source: ConfigurableAnimeSource? = null
    private var setup: ((PreferenceScreen) -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    fun getInstance(source: ConfigurableAnimeSource, callback: () -> Unit): AnimeSourcePreferencesFragment {
        this.source = source
        this.onDismiss = callback
        return this
    }

    fun getInstance(setup: (PreferenceScreen) -> Unit, callback: () -> Unit): AnimeSourcePreferencesFragment {
        this.setup = setup
        this.onDismiss = callback
        return this
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        if (source != null) source?.setupPreferenceScreen(screen) else setup?.invoke(screen)
        preferenceScreen = screen
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRemoving) {
            onDismiss?.invoke()
        }
    }
}
