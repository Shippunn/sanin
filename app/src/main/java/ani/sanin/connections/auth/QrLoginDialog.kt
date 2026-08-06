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
    private var lastPollStatus: String? = null

    fun show() {
        Logger.log("[QR-DEBUG] QrLoginDialog.show()")
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
        Logger.log("[QR-DEBUG] QrLoginDialog.dismiss()")
        cancelPolling()
        countdownTimer?.cancel()
        dialog?.dismiss()
        dialog = null
        dialogBinding = null
        currentSessionId = null
    }

    private fun createSessionAndStartPolling(binding: DialogQrLoginBinding) {
        // Prevent duplicate session creation
        if (isCreatingSession) {
            Logger.log("[QR-DEBUG] createSessionAndStartPolling: already creating, returning")
            return
        }
        isCreatingSession = true
        lastPollStatus = null

        scope.launch {
            try {
                // Show loading
                binding.qrLoadingIndicator.visibility = View.VISIBLE
                binding.qrCodeImageView.visibility = View.GONE
                binding.qrStatusText.text = "Creating session..."
                binding.qrRefreshButton.isEnabled = false

                Logger.log("[QR-DEBUG] Calling QrLoginApi.createSession()...")
                // Create session
                val session = QrLoginApi.createSession()
                currentSessionId = session.sessionId
                Logger.log("[QR-DEBUG] Session created successfully: sessionId=${session.sessionId}")

                // Generate QR code
                Logger.log("[QR-DEBUG] Generating QR code...")
                val qrBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    QRCode.ofSquares()
                        .withSize(10)
                        .withColor(android.graphics.Color.BLACK)
                        .build(session.qrUrl)
                        .render()
                        .nativeImage() as android.graphics.Bitmap
                }
                Logger.log("[QR-DEBUG] QR code generated")

                // Update UI
                binding.qrCodeImageView.setImageBitmap(qrBitmap)
                binding.qrLoadingIndicator.visibility = View.GONE
                binding.qrCodeImageView.visibility = View.VISIBLE
                binding.qrStatusText.text = "Waiting for login..."
                binding.qrInstructionsText.text = "Scan this QR code with your phone to sign in to AniList."

                // Start countdown
                startCountdown(binding, session.expiresIn)

                // Start polling
                Logger.log("[QR-DEBUG] Starting polling for sessionId=${session.sessionId}")
                startPolling(session.sessionId, binding)

            } catch (e: Exception) {
                Logger.log("[QR-DEBUG] EXCEPTION in createSessionAndStartPolling: ${e.javaClass.simpleName}: ${e.message}")
                Logger.log("[QR-DEBUG] Stacktrace: ", e)
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
        Logger.log("[QR-DEBUG] refreshQrSession called")
        // Prevent duplicate refresh
        if (isCreatingSession) {
            Logger.log("[QR-DEBUG] refreshQrSession: already creating, returning")
            return
        }

        cancelPolling()
        countdownTimer?.cancel()
        currentSessionId = null
        createSessionAndStartPolling(binding)
    }

    private fun startPolling(sessionId: String, binding: DialogQrLoginBinding) {
        cancelPolling()

        pollingJob = scope.launch {
            Logger.log("[QR-DEBUG] Polling loop started for sessionId=$sessionId")
            while (isActive) {
                try {
                    delay(2000) // Poll every 2 seconds

                    Logger.log("[QR-DEBUG] Polling sessionId=$sessionId")
                    val response = QrLoginApi.getSessionStatus(sessionId)

                    // Detect status change
                    if (lastPollStatus != null && lastPollStatus != response.status) {
                        Logger.log("[QR-DEBUG] STATUS CHANGE: $lastPollStatus -> ${response.status}")
                    }
                    lastPollStatus = response.status

                    when (response.status) {
                        "authorized" -> {
                            Logger.log("[QR-DEBUG] Status is 'authorized' - stopping polling, calling consumeSession...")
                            // Stop polling
                            cancelPolling()
                            countdownTimer?.cancel()

                            // Consume the authorization code (one-time retrieval)
                            try {
                                Logger.log("[QR-DEBUG] Calling consumeSession for sessionId=$sessionId")
                                val consumeResponse = QrLoginApi.consumeSession(sessionId)
                                val authCode = consumeResponse.authorizationCode
                                Logger.log("[QR-DEBUG] consumeSession returned: hasCode=${authCode.isNotEmpty()}, codeLength=${authCode.length}")

                                if (authCode.isNotEmpty()) {
                                    // Exchange authorization code for access token
                                    binding.qrStatusText.text = "Exchanging authorization code..."
                                    Logger.log("[QR-DEBUG] Starting token exchange with AniList...")
                                    val tokenResponse = QrLoginApi.exchangeAuthorizationCode(
                                        code = authCode,
                                        clientId = "47875",
                                        clientSecret = "rPOWDPFARSGR7CnR08bAz9PX06QQfJKUN9vajdSb",
                                        redirectUri = "https://sanin-auth.shemaus58.workers.dev/callback"
                                    )
                                    val token = tokenResponse.access_token
                                    Logger.log("[QR-DEBUG] Token exchange result: hasToken=${token.isNotEmpty()}, tokenLength=${token.length}")

                                    if (token.isNotEmpty()) {
                                        Logger.log("[QR-DEBUG] Saving token...")
                                        Anilist.token = token
                                        PrefManager.setVal(PrefName.AnilistToken, token)
                                        Logger.log("[QR-DEBUG] Token saved successfully")
                                    } else {
                                        Logger.log("[QR-DEBUG] WARNING: Token is empty after exchange!")
                                    }
                                } else {
                                    Logger.log("[QR-DEBUG] WARNING: Authorization code is empty after consume!")
                                }
                            } catch (e: Exception) {
                                Logger.log("[QR-DEBUG] EXCEPTION in token flow: ${e.javaClass.simpleName}: ${e.message}")
                                Logger.log("[QR-DEBUG] Stacktrace: ", e)
                                binding.qrStatusText.text = "Failed to complete login"
                                binding.qrRefreshButton.isEnabled = true
                                return@launch
                            }

                            // Update UI
                            binding.qrStatusText.text = "Successfully signed in!"
                            binding.qrRefreshButton.isEnabled = false

                            // Handle authentication
                            Logger.log("[QR-DEBUG] Calling onAuthenticated()...")
                            try {
                                onAuthenticated()
                                Logger.log("[QR-DEBUG] onAuthenticated() completed")
                            } catch (e: Exception) {
                                Logger.log("[QR-DEBUG] EXCEPTION in onAuthenticated: ${e.javaClass.simpleName}: ${e.message}")
                                Logger.log("[QR-DEBUG] Stacktrace: ", e)
                            }

                            // Close dialog after a short delay
                            delay(1000)
                            Logger.log("[QR-DEBUG] Dismissing dialog, login flow complete")
                            dialog?.dismiss()
                            dialog = null
                            dialogBinding = null
                            currentSessionId = null
                            return@launch
                        }
                        "expired" -> {
                            Logger.log("[QR-DEBUG] Status is 'expired' - stopping polling")
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
                    Logger.log("[QR-DEBUG] EXCEPTION in polling loop: ${e.javaClass.simpleName}: ${e.message}")
                    Logger.log("[QR-DEBUG] Stacktrace: ", e)
                    // If dialog was dismissed (user pressed back), don't show error
                    if (dialog == null || e is kotlinx.coroutines.CancellationException) {
                        Logger.log("[QR-DEBUG] Dialog dismissed or cancelled, exiting polling loop")
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
        Logger.log("[QR-DEBUG] Showing network error dialog for sessionId=$sessionId")
        context.customAlertDialog().apply {
            setTitle("Connection lost")
            setMessage("Retry?")
            setPosButton(R.string.ok) {
                Logger.log("[QR-DEBUG] User clicked Retry on network error dialog")
                // Retry polling
                startPolling(sessionId, binding)
                startCountdown(binding, 300) // Restart with 5 minutes
            }
            setNegButton(R.string.cancel) {
                Logger.log("[QR-DEBUG] User clicked Cancel on network error dialog")
                dismiss()
            }
        }.show()
    }
}
