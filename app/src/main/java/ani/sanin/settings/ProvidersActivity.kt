package ani.sanin.settings

import android.os.Bundle
import android.widget.Toast
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

        val enabled = PrefManager.getNullableVal<Set<String>>(PrefName.EnabledProviders, null)
            ?: allProviders.map { it.saveName }.toSet()

        val items = allProviders.map { parser ->
            ProviderItem(
                name = parser.name,
                saveName = parser.saveName,
                isEnabled = parser.saveName in enabled
            )
        }

        binding.providersRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.providersRecyclerView.adapter = ProviderAdapter(items) { item, checked ->
            val current = PrefManager.getNullableVal<Set<String>>(PrefName.EnabledProviders, null)
                ?: allProviders.map { it.saveName }.toSet()
            val updated = if (checked) current + item.saveName else current - item.saveName
            PrefManager.setVal(PrefName.EnabledProviders, updated)
            Toast.makeText(this, "${item.name} ${if (checked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }
    }
}

data class ProviderItem(val name: String, val saveName: String, val isEnabled: Boolean)
