package ani.sanin.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.DialogUserAgentBinding
import ani.sanin.databinding.FragmentLoginBinding
import ani.sanin.loadImage
import ani.sanin.openLinkInBrowser
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.settings.saving.internal.PreferenceKeystore
import ani.sanin.settings.saving.internal.PreferencePackager
import ani.sanin.toast
import ani.sanin.util.Logger
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rescueMode = ani.sanin.settings.saving.PrefManager.getVal<Boolean>(ani.sanin.settings.saving.PrefName.RescueMode)
        if (rescueMode) {
            binding.loginButton.text = getString(R.string.login)
            (binding.loginButton as com.google.android.material.button.MaterialButton).setIconResource(R.drawable.ic_myanimelist)
            binding.loginButton.setOnClickListener { ani.sanin.connections.mal.MAL.loginIntent(requireActivity()) }
        } else {
            binding.loginButton.setOnClickListener { Anilist.loginIntent(requireActivity()) }
        }
        binding.loginDiscord.setOnClickListener { openLinkInBrowser(getString(R.string.discord)) }
        binding.loginGithub.setOnClickListener { openLinkInBrowser(getString(R.string.github)) }
        binding.loginTelegram.setOnClickListener { openLinkInBrowser(getString(R.string.telegram)) }

        binding.loginQrButton.visibility = View.GONE
        binding.loginTokenSubmit.setOnClickListener {
            val token = binding.loginTokenEditText.text?.toString()?.trim()
            if (!token.isNullOrBlank()) {
                PrefManager.setVal(PrefName.AnilistToken, token)
                if (Anilist.getSavedToken()) {
                    toast("Login successful")
                    restartApp()
                } else {
                    toast("Invalid token")
                }
            } else {
                toast("Enter a token")
            }
        }

        TvKeyboardUtil.setupTvInput(binding.loginTokenEditText)

        val openDocumentLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    try {
                        val jsonString =
                            requireActivity().contentResolver.openInputStream(uri)?.readBytes()
                                ?: throw Exception("Error reading file")
                        val name =
                            DocumentFile.fromSingleUri(requireActivity(), uri)?.name ?: "settings"
                        //.sani is encrypted, .ani is not
                        if (name.endsWith(".sani")) {
                            passwordAlertDialog { password ->
                                if (password != null) {
                                    val salt = jsonString.copyOfRange(0, 16)
                                    val encrypted = jsonString.copyOfRange(16, jsonString.size)
                                    val decryptedJson = try {
                                        PreferenceKeystore.decryptWithPassword(
                                            password,
                                            encrypted,
                                            salt
                                        )
                                    } catch (e: Exception) {
                                        toast("Incorrect password")
                                        return@passwordAlertDialog
                                    }
                                    if (PreferencePackager.unpack(decryptedJson))
                                        restartApp()
                                } else {
                                    toast("Password cannot be empty")
                                }
                            }
                        } else if (name.endsWith(".ani")) {
                            val decryptedJson = jsonString.toString(Charsets.UTF_8)
                            if (PreferencePackager.unpack(decryptedJson))
                                restartApp()
                        } else {
                            toast("Invalid file type")
                        }
                    } catch (e: Exception) {
                        Logger.log(e)
                        toast("Error importing settings")
                    }
                }
            }

        binding.importSettingsButton.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("*/*"))
        }
    }

    private fun passwordAlertDialog(callback: (CharArray?) -> Unit) {
        val password = CharArray(16).apply { fill('0') }

        // Inflate the dialog layout
        val dialogView = DialogUserAgentBinding.inflate(layoutInflater).apply {
            userAgentTextBox.hint = "Password"
            subtitle.visibility = View.VISIBLE
            subtitle.text = getString(R.string.enter_password_to_decrypt_file)
        }

        requireActivity().customAlertDialog().apply {
            setTitle("Enter Password")
            setCustomView(dialogView.root)
            setPosButton(R.string.ok) {
                val editText = dialogView.userAgentTextBox
                if (editText.text?.isNotBlank() == true) {
                    editText.text?.toString()?.trim()?.toCharArray(password)
                    callback(password)
                } else {
                    toast("Password cannot be empty")
                }
            }
            setNegButton(R.string.cancel) {
                password.fill('0')
                callback(null)
            }
        }.show()


    }

    private fun restartApp() {
        val intent = Intent(requireActivity(), requireActivity().javaClass)
        requireActivity().finish()
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

}