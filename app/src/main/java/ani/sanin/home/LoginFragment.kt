package ani.sanin.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.auth.QrLoginDialog
import ani.sanin.databinding.DialogUserAgentBinding
import ani.sanin.databinding.FragmentLoginBinding
import ani.sanin.loadImage
import ani.sanin.openLinkInBrowser
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.settings.saving.internal.PreferenceKeystore
import ani.sanin.settings.saving.internal.PreferencePackager
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.Logger
import ani.sanin.util.TvKeyboardUtil
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var qrLoginDialog: QrLoginDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Check if user is already logged in
        updateUIBasedOnLoginState()

        val rescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
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

        binding.loginQrButton.setOnClickListener {
            showQrLoginDialog()
        }

        binding.loginTokenSubmit.setOnClickListener {
            val token = binding.loginTokenEditText.text?.toString()?.trim()
            if (!token.isNullOrBlank()) {
                PrefManager.setVal(PrefName.AnilistToken, token)
                if (Anilist.getSavedToken()) {
                    // Update UI instead of restarting
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            Anilist.query.getUserData()
                            // Record login diagnostics
                            ani.sanin.connections.auth.LoginDiagnostics.recordLogin(
                                ani.sanin.connections.auth.LoginDiagnostics.LoginMethod.TOKEN_PASTE
                            )
                            updateUIBasedOnLoginState()
                            toast("Login successful")
                        } catch (e: Exception) {
                            Logger.log(e)
                            toast("Failed to fetch user data")
                        }
                    }
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

        // Set up logged-in state buttons
        binding.switchAccountButton.setOnClickListener {
            showSwitchAccountDialog()
        }

        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // Set up social links for logged-in state
        binding.loggedInDiscord.setOnClickListener { openLinkInBrowser(getString(R.string.discord)) }
        binding.loggedInGithub.setOnClickListener { openLinkInBrowser(getString(R.string.github)) }
        binding.loggedInTelegram.setOnClickListener { openLinkInBrowser(getString(R.string.telegram)) }
    }

    override fun onResume() {
        super.onResume()
        // Check login state when fragment resumes
        updateUIBasedOnLoginState()
    }

    private fun updateUIBasedOnLoginState() {
        val isLoggedIn = Anilist.getSavedToken()

        if (isLoggedIn) {
            // Show logged-in state with loading
            binding.loggedOutContainer.visibility = View.GONE
            binding.loggedInContainer.visibility = View.VISIBLE
            binding.loggedInUsername.text = "Restoring session..."

            // Validate token in background
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val isValid = Anilist.validateToken()
                    if (isValid == true) {
                        // Token valid - fetch user data
                        Anilist.query.getUserData()
                        binding.loggedInUsername.text = Anilist.username ?: "Unknown"
                        
                        // Load avatar
                        Anilist.avatar?.let { avatarUrl ->
                            if (avatarUrl.isNotEmpty()) {
                                binding.loggedInAvatar.loadImage(avatarUrl)
                            }
                        }
                    } else if (isValid == false) {
                        // Token invalid - return to logged out
                        Anilist.removeSavedToken()
                        updateUIBasedOnLoginState()
                        toast("Session expired, please login again")
                    }
                    // If null (network error), keep existing session
                } catch (e: Exception) {
                    Logger.log(e)
                    // Network error - keep existing session
                }
            }
        } else {
            // Show logged-out state
            binding.loggedOutContainer.visibility = View.VISIBLE
            binding.loggedInContainer.visibility = View.GONE
        }
    }

    private fun showLogoutConfirmationDialog() {
        requireActivity().customAlertDialog().apply {
            setTitle("Log Out")
            setMessage("Are you sure you want to log out?")
            setPosButton("Yes") {
                performLogout()
            }
            setNegButton("No", null)
        }.show()
    }

    private fun performLogout() {
        // Clear AniList token and cached data
        Anilist.removeSavedToken()

        // Update UI
        updateUIBasedOnLoginState()

        toast("Logged out successfully")
    }

    private fun showSwitchAccountDialog() {
        requireActivity().customAlertDialog().apply {
            setTitle("Switch Account")
            setMessage("Start a new QR login session to switch to a different account?")
            setPosButton("Yes") {
                showQrLoginDialog()
            }
            setNegButton("No", null)
        }.show()
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

    private fun showQrLoginDialog() {
        if (qrLoginDialog != null) return
        qrLoginDialog = QrLoginDialog(
            requireContext(),
            viewLifecycleOwner.lifecycleScope
        ) {
            handleAuthenticated()
        }.also { it.show() }
    }

    private suspend fun handleAuthenticated() {
        try {
            Logger.log("[QR-DEBUG] LoginFragment: handleAuthenticated called")
            // Get user data from AniList
            if (Anilist.getSavedToken()) {
                Logger.log("[QR-DEBUG] LoginFragment: calling getUserData()")
                Anilist.query.getUserData()

                // Record login diagnostics
                ani.sanin.connections.auth.LoginDiagnostics.recordLogin(
                    ani.sanin.connections.auth.LoginDiagnostics.LoginMethod.QR_CODE
                )

                // Update UI
                updateUIBasedOnLoginState()

                toast("Successfully signed in")
            } else {
                Logger.log("[QR-DEBUG] LoginFragment: getSavedToken returned false")
                toast("Login failed: no token received from relay")
            }
        } catch (e: Exception) {
            Logger.log("[QR-DEBUG] EXCEPTION in LoginFragment.handleAuthenticated: ${e.javaClass.simpleName}: ${e.message}")
            Logger.log(e)
            toast("Failed to retrieve user data")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        qrLoginDialog?.dismiss()
        qrLoginDialog = null
        _binding = null
    }
}
