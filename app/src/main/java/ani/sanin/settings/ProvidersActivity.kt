package ani.sanin.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.databinding.ActivityProvidersBinding
import ani.sanin.parsers.AnimeSources
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil

class ProvidersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProvidersBinding
    private val allProviders = AnimeSources.allNativeParsers

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProvidersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FocusEffectUtil.applyFocusListener(binding.providersBack)
        binding.providersBack.setOnClickListener { finish() }

        binding.providersDisclaimer.visibility = View.VISIBLE

        val enabled = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders)

        val maintenanceProviders = setOf("Senshi")
        val items = allProviders.map { parser ->
            ProviderItem(
                name = if (parser.saveName in maintenanceProviders) "${parser.name} (under maintenance)" else parser.name,
                saveName = parser.saveName,
                isEnabled = parser.saveName in enabled
            )
        }.toMutableList()

        binding.providersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.providersRecyclerView.adapter = ProviderAdapter(items) {
            val enabledNow = items.filter { it.isEnabled }.map { it.saveName }.toSet()
            PrefManager.setVal(PrefName.EnabledProviders, enabledNow)
        }
    }
}

data class ProviderItem(val name: String, val saveName: String, var isEnabled: Boolean)
