package ani.sanin.connections.auth

import android.app.AlertDialog
import android.content.Context
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.DialogQrLoginBinding
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.Logger
import ani.sanin.util.customAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import qrcode.QRCode

class QrLoginDialog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAuthenticated: suspend () -> Unit
) {

    private var dialog: AlertDialog? = null
    private var dialogBinding: DialogQrLoginBinding? = null
    private var pollingJob: Job? = null
    private var countdownTimer: CountDownTimer? = null
    private var currentSessionId: String? = null
    private var isCreatingSession = false

    fun show() {
        val binding = DialogQrLoginBinding.inflate(LayoutInflater.from(context))
        dialogBinding = binding

        val builder = context.customAlertDialog().apply {
            setTitle("Sign in with AniList")
            setCustomView(binding.root)
            setCancelable(true)
            setOnCancelListener { dismiss() }
            attach { alertDialog ->
                alertDialog.window?.apply {
                    setDimAmount(0.8f)
                }
                dialog = alertDialog
            }
        }

        // Set up button click listeners
        binding.qrBrowserButton.setOnClickListener {
            dismiss()
            Anilist.loginIntent(context)
        }

        binding.qrRefreshButton.setOnClickListener {
            refreshQrSession(binding)
        }

        binding.qrCancelButton.setOnClickListener { dismiss() }

        builder.setNegButton(R.string.cancel) { dismiss() }
        builder.show()

        // Set up D-pad focus chain
        binding.qrBrowserButton.requestFocus()
        FocusEffectUtil.applyFocusListener(binding.qrBrowserButton)
        FocusEffectUtil.applyFocusListener(binding.qrCodeCard)
        FocusEffectUtil.applyFocusListener(binding.qrRefreshButton)
        FocusEffectUtil.applyFocusListener(binding.qrCancelButton)

        binding.qrBrowserButton.nextFocusDownId = R.id.qrCodeCard
        binding.qrCodeCard.nextFocusUpId = R.id.qrBrowserButton
        binding.qrCodeCard.nextFocusDownId = R.id.qrRefreshButton
        binding.qrRefreshButton.nextFocusUpId = R.id.qrCodeCard
        binding.qrRefreshButton.nextFocusDownId = R.id.qrCancelButton
        binding.qrCancelButton.nextFocusUpId = R.id.qrRefreshButton

        createSessionAndStartPolling(binding)
    }

    fun dismiss() {
        cancelPolling()
        countdownTimer?.cancel()
        dialog?.dismiss()
        dialog = null
        dialogBinding = null
        currentSessionId = null
    }

    private fun createSessionAndStartPolling(binding: DialogQrLoginBinding) {
        // Prevent duplicate session creation
        if (isCreatingSession) return
        isCreatingSession = true

        scope.launch {
            try {
                // Show loading
                binding.qrLoadingIndicator.visibility = View.VISIBLE
                binding.qrCodeImageView.visibility = View.GONE
                binding.qrStatusText.text = "Creating session..."
                binding.qrRefreshButton.isEnabled = false

                // Create session
                val session = QrLoginApi.createSession()
                currentSessionId = session.sessionId

                // Generate QR code
                val qrBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    QRCode.ofSquares()
                        .withSize(10)
                        .withColor(android.graphics.Color.BLACK)
                        .build(session.qrUrl)
                        .render()
                        .nativeImage() as android.graphics.Bitmap
                }

                // Update UI
                binding.qrCodeImageView.setImageBitmap(qrBitmap)
                binding.qrLoadingIndicator.visibility = View.GONE
                binding.qrCodeImageView.visibility = View.VISIBLE
                binding.qrStatusText.text = "Waiting for login..."
                binding.qrInstructionsText.text = "Scan this QR code with your phone to sign in to AniList."

                // Start countdown
                startCountdown(binding, session.expiresIn)

                // Start polling
                startPolling(session.sessionId, binding)

            } catch (e: Exception) {
                Logger.log(e)
                binding.qrLoadingIndicator.visibility = View.GONE
                binding.qrCodeImageView.visibility = View.VISIBLE
                binding.qrStatusText.text = "Failed to create session"
                binding.qrRefreshButton.isEnabled = true
                toast("Failed to connect to server")
            } finally {
                isCreatingSession = false
            }
        }
    }

    private fun refreshQrSession(binding: DialogQrLoginBinding) {
        // Prevent duplicate refresh
        if (isCreatingSession) return

        cancelPolling()
        countdownTimer?.cancel()
        currentSessionId = null
        createSessionAndStartPolling(binding)
    }

    private fun startPolling(sessionId: String, binding: DialogQrLoginBinding) {
        cancelPolling()

        pollingJob = scope.launch {
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
                            binding.qrStatusText.text = "Successfully signed in!"
                            binding.qrRefreshButton.isEnabled = false

                            // Handle authentication
                            onAuthenticated()

                            // Close dialog after a short delay
                            delay(1000)
                            dialog?.dismiss()
                            dialog = null
                            dialogBinding = null
                            currentSessionId = null
                            return@launch
                        }
                        "expired" -> {
                            // Stop polling
                            cancelPolling()
                            countdownTimer?.cancel()

                            // Update UI
                            binding.qrStatusText.text = "QR Code Expired"
                            binding.qrRefreshButton.isEnabled = true
                            binding.qrInstructionsText.text = "Please refresh to generate a new QR code."
                            return@launch
                        }
                        "pending" -> {
                            // Continue polling
                            binding.qrStatusText.text = "Waiting for login..."
                        }
                    }
                } catch (e: Exception) {
                    // If dialog was dismissed (user pressed back), don't show error
                    if (dialog == null || e is kotlinx.coroutines.CancellationException) {
                        return@launch
                    }
                    // Network error
                    cancelPolling()
                    countdownTimer?.cancel()

                    // Show retry dialog
                    showNetworkErrorDialog(sessionId, binding)
                    return@launch
                }
            }
        }
    }

    private fun cancelPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startCountdown(binding: DialogQrLoginBinding, expiresIn: Int) {
        countdownTimer?.cancel()

        val totalMillis = expiresIn * 1000L
        countdownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                val minutes = secondsRemaining / 60
                val seconds = secondsRemaining % 60
                binding.qrExpiryText.text = "Expires in: %02d:%02d".format(minutes, seconds)
            }

            override fun onFinish() {
                binding.qrExpiryText.text = "Expires in: 00:00"
            }
        }.start()
    }

    private fun showNetworkErrorDialog(sessionId: String, binding: DialogQrLoginBinding) {
        context.customAlertDialog().apply {
            setTitle("Connection lost")
            setMessage("Retry?")
            setPosButton(R.string.ok) {
                // Retry polling
                startPolling(sessionId, binding)
                startCountdown(binding, 300) // Restart with 5 minutes
            }
            setNegButton(R.string.cancel) {
                dismiss()
            }
        }.show()
    }
}
