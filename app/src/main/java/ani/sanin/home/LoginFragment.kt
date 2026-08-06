package ani.sanin.home

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.auth.QrLoginApi
import ani.sanin.databinding.DialogQrLoginBinding
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import qrcode.QRCode

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private var pollingJob: Job? = null
    private var countdownTimer: CountDownTimer? = null
    private var currentDialog: AlertDialog? = null
    private var currentDialogBinding: DialogQrLoginBinding? = null
    private var currentSessionId: String? = null
    private var isCreatingSession = false

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
        val dialogBinding = DialogQrLoginBinding.inflate(layoutInflater)
        currentDialogBinding = dialogBinding

        val dialog = requireContext().customAlertDialog().apply {
            setTitle("Sign in with AniList")
            setCustomView(dialogBinding.root)
            setCancelable(true)
            setOnCancelListener {
                cancelPolling()
                countdownTimer?.cancel()
                currentDialog = null
                currentDialogBinding = null
                currentSessionId = null
            }
            attach { alertDialog ->
                alertDialog.window?.apply {
                    setDimAmount(0.8f)
                }
                currentDialog = alertDialog
            }
        }

        // Set up button click listeners
        dialogBinding.qrRefreshButton.setOnClickListener {
            refreshQrSession(dialogBinding)
        }

        dialogBinding.qrCancelButton.setOnClickListener {
            cancelPolling()
            countdownTimer?.cancel()
            currentDialog?.dismiss()
            currentDialog = null
            currentDialogBinding = null
            currentSessionId = null
        }

        dialog.setNegButton(R.string.cancel) {
            cancelPolling()
            countdownTimer?.cancel()
            currentDialog = null
            currentDialogBinding = null
            currentSessionId = null
        }
        dialog.show()

        // Set up D-pad focus chain
        dialogBinding.qrCodeCard.requestFocus()
        FocusEffectUtil.applyFocusListener(dialogBinding.qrCodeCard)
        FocusEffectUtil.applyFocusListener(dialogBinding.qrRefreshButton)
        FocusEffectUtil.applyFocusListener(dialogBinding.qrCancelButton)

        // Focus chain for QR dialog
        dialogBinding.qrCodeCard.nextFocusDownId = R.id.qrRefreshButton
        dialogBinding.qrRefreshButton.nextFocusUpId = R.id.qrCodeCard
        dialogBinding.qrRefreshButton.nextFocusDownId = R.id.qrCancelButton
        dialogBinding.qrCancelButton.nextFocusUpId = R.id.qrRefreshButton

        // Create session and start polling
        createSessionAndStartPolling(dialogBinding)
    }

    private fun createSessionAndStartPolling(dialogBinding: DialogQrLoginBinding) {
        // Prevent duplicate session creation
        if (isCreatingSession) return
        isCreatingSession = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show loading
                dialogBinding.qrLoadingIndicator.visibility = View.VISIBLE
                dialogBinding.qrCodeImageView.visibility = View.GONE
                dialogBinding.qrStatusText.text = "Creating session..."
                dialogBinding.qrRefreshButton.isEnabled = false

                // Create session
                val session = QrLoginApi.createSession()
                currentSessionId = session.sessionId

                // Generate QR code
                val qrBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    qrcode.QRCode.ofSquares()
                        .withSize(10)
                        .withColor(android.graphics.Color.BLACK)
                        .build(session.qrUrl)
                        .render()
                        .nativeImage() as android.graphics.Bitmap
                }

                // Update UI
                dialogBinding.qrCodeImageView.setImageBitmap(qrBitmap)
                dialogBinding.qrLoadingIndicator.visibility = View.GONE
                dialogBinding.qrCodeImageView.visibility = View.VISIBLE
                dialogBinding.qrStatusText.text = "Waiting for login..."
                dialogBinding.qrInstructionsText.text = "Scan this QR code with your phone to sign in to AniList."

                // Start countdown
                startCountdown(dialogBinding, session.expiresIn)

                // Start polling
                startPolling(session.sessionId, dialogBinding)

            } catch (e: Exception) {
                Logger.log(e)
                dialogBinding.qrLoadingIndicator.visibility = View.GONE
                dialogBinding.qrCodeImageView.visibility = View.VISIBLE
                dialogBinding.qrStatusText.text = "Failed to create session"
                dialogBinding.qrRefreshButton.isEnabled = true
                toast("Failed to connect to server")
            } finally {
                isCreatingSession = false
            }
        }
    }

    private fun refreshQrSession(dialogBinding: DialogQrLoginBinding) {
        // Prevent duplicate refresh
        if (isCreatingSession) return
        
        cancelPolling()
        countdownTimer?.cancel()
        currentSessionId = null
        createSessionAndStartPolling(dialogBinding)
    }

    private fun startPolling(sessionId: String, dialogBinding: DialogQrLoginBinding) {
        cancelPolling()

        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                try {
                    delay(2000) // Poll every 2 seconds

                    val response = QrLoginApi.getSessionStatus(sessionId)

                    when (response.status) {
                        "authenticated" -> {
                            // Stop polling
                            cancelPolling()
                            countdownTimer?.cancel()

                            // Save the token returned by the relay
                            val token = response.token
                            if (!token.isNullOrEmpty()) {
                                Anilist.token = token
                                PrefManager.setVal(PrefName.AnilistToken, token)
                            }

                            // Update UI
                            dialogBinding.qrStatusText.text = "Successfully signed in!"
                            dialogBinding.qrRefreshButton.isEnabled = false

                            // Handle authentication
                            handleAuthenticated()

                            // Close dialog after a short delay
                            delay(1000)
                            currentDialog?.dismiss()
                            currentDialog = null
                            currentDialogBinding = null
                            currentSessionId = null
                            return@launch
                        }
                        "expired" -> {
                            // Stop polling
                            cancelPolling()
                            countdownTimer?.cancel()

                            // Update UI
                            dialogBinding.qrStatusText.text = "QR Code Expired"
                            dialogBinding.qrRefreshButton.isEnabled = true
                            dialogBinding.qrInstructionsText.text = "Please refresh to generate a new QR code."
                            return@launch
                        }
                        "pending" -> {
                            // Continue polling
                            dialogBinding.qrStatusText.text = "Waiting for login..."
                        }
                    }
                } catch (e: Exception) {
                    // Network error
                    cancelPolling()
                    countdownTimer?.cancel()

                    // Show retry dialog
                    showNetworkErrorDialog(sessionId, dialogBinding)
                    return@launch
                }
            }
        }
    }

    private fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startCountdown(dialogBinding: DialogQrLoginBinding, expiresIn: Int) {
        countdownTimer?.cancel()

        val totalMillis = expiresIn * 1000L
        countdownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                dialogBinding.qrExpiryText.text = "Expires in: %02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                dialogBinding.qrExpiryText.text = "Expires in: 00:00"
            }
        }.start()
    }

    private fun showNetworkErrorDialog(sessionId: String, dialogBinding: DialogQrLoginBinding) {
        requireActivity().customAlertDialog().apply {
            setTitle("Connection lost")
            setMessage("Retry?")
            setPosButton(R.string.ok) {
                // Retry polling
                startPolling(sessionId, dialogBinding)
                startCountdown(dialogBinding, 300) // Restart with 5 minutes
            }
            setNegButton(R.string.cancel) {
                // Close dialog
                currentDialog?.dismiss()
                currentDialog = null
                currentDialogBinding = null
                currentSessionId = null
            }
        }.show()
    }

    private suspend fun handleAuthenticated() {
        try {
            // Get user data from AniList
            if (Anilist.getSavedToken()) {
                Anilist.query.getUserData()

                // Record login diagnostics
                ani.sanin.connections.auth.LoginDiagnostics.recordLogin(
                    ani.sanin.connections.auth.LoginDiagnostics.LoginMethod.QR_CODE
                )

                // Update UI
                updateUIBasedOnLoginState()

                toast("Successfully signed in")
            } else {
                toast("Login failed: no token received from relay")
            }
        } catch (e: Exception) {
            Logger.log(e)
            toast("Failed to retrieve user data")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelPolling()
        countdownTimer?.cancel()
        currentDialog?.dismiss()
        currentDialog = null
        currentDialogBinding = null
        currentSessionId = null
        _binding = null
    }
}
