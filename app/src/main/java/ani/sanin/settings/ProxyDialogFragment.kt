package ani.sanin.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import ani.sanin.BottomSheetDialogFragment
import ani.sanin.databinding.BottomSheetProxyBinding
import ani.sanin.restartApp
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.FocusEffectUtil

class ProxyDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetProxyBinding? = null
    private val binding get() = _binding!!

    private var proxyHostValue by mutableStateOf(PrefManager.getVal<String>(PrefName.Socks5ProxyHost).orEmpty())
    private var proxyPortValue by mutableStateOf(PrefManager.getVal<String>(PrefName.Socks5ProxyPort).orEmpty())
    private var proxyUsernameValue by mutableStateOf(PrefManager.getVal<String>(PrefName.Socks5ProxyUsername).orEmpty())
    private var proxyPasswordValue by mutableStateOf(PrefManager.getVal<String>(PrefName.Socks5ProxyPassword).orEmpty())
    private var authEnabled by mutableStateOf(PrefManager.getVal<Boolean>(PrefName.ProxyAuthEnabled))
    private val proxyEnabled: Boolean = PrefManager.getVal<Boolean>(PrefName.EnableSocks5Proxy)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProxyBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.proxyAuthentication.isChecked = authEnabled

        binding.proxyHost.setContent {
            OutlinedTextField(
                value = proxyHostValue,
                onValueChange = { proxyHostValue = it },
                singleLine = true,
                placeholder = { androidx.compose.material3.Text(getString(R.string.host)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White
                )
            )
        }
        binding.proxyPort.setContent {
            OutlinedTextField(
                value = proxyPortValue,
                onValueChange = { proxyPortValue = it },
                singleLine = true,
                placeholder = { androidx.compose.material3.Text(getString(R.string.port)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White
                )
            )
        }
        binding.proxyUsername.setContent {
            OutlinedTextField(
                value = proxyUsernameValue,
                onValueChange = { proxyUsernameValue = it },
                singleLine = true,
                enabled = authEnabled,
                placeholder = { androidx.compose.material3.Text(getString(R.string.username)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White
                )
            )
        }
        binding.proxyPassword.setContent {
            OutlinedTextField(
                value = proxyPasswordValue,
                onValueChange = { proxyPasswordValue = it },
                singleLine = true,
                enabled = authEnabled,
                placeholder = { androidx.compose.material3.Text(getString(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White
                )
            )
        }

        toggleAuthentication(authEnabled)

        binding.proxySave.setOnClickListener {
            PrefManager.setVal(PrefName.Socks5ProxyHost, proxyHostValue)
            PrefManager.setVal(PrefName.Socks5ProxyPort, proxyPortValue)
            PrefManager.setVal(PrefName.Socks5ProxyUsername, proxyUsernameValue)
            PrefManager.setVal(PrefName.Socks5ProxyPassword, proxyPasswordValue)

            dismiss()
            if (proxyEnabled) activity?.restartApp()
        }

        binding.proxyAuthentication.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ProxyAuthEnabled, isChecked)
            toggleAuthentication(isChecked)
        }
    }

    private fun toggleAuthentication(isChecked: Boolean) {
        authEnabled = isChecked
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
