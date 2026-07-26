package ani.sanin.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import ani.sanin.BottomSheetDialogFragment
import ani.sanin.databinding.BottomSheetFirsttimeProvidersBinding
import ani.sanin.parsers.AnimeSources
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName

class FirstTimeProviderDialog : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFirsttimeProvidersBinding? = null
    private val binding get() = _binding!!
    private val allProviders = AnimeSources.allNativeParsers

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFirsttimeProvidersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val enabled = PrefManager.getVal<Set<String>>(PrefName.EnabledProviders)

        val items = allProviders.map { parser ->
            ProviderItem(
                name = parser.name,
                saveName = parser.saveName,
                isEnabled = parser.saveName in enabled
            )
        }.toMutableList()

        binding.firstTimeRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ProviderAdapter(items) {
            val enabledNow = items.filter { it.isEnabled }.map { it.saveName }.toSet()
            PrefManager.setVal(PrefName.EnabledProviders, enabledNow)
        }
        binding.firstTimeRecyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
