package ani.sanin.connections.auth

import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.DialogQrLoginBinding
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.restartApp
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private var pollCount = 0
    private var createSessionId: String? = null

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

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

        binding.qrVerifyButton.setOnClickListener {
            onVerifyClicked(binding)
        }

        // The native dialog Cancel button (setNegButton) handles dismissal
        builder.setNegButton(R.string.cancel) { dismiss() }
        builder.show()

        // Show debug panel
        binding.qrDebugPanel.visibility = View.VISIBLE

        // Set up D-pad focus chain
        binding.qrBrowserButton.requestFocus()
        FocusEffectUtil.applyFocusListener(binding.qrBrowserButton)
        FocusEffectUtil.applyFocusListener(binding.qrCodeCard)
        FocusEffectUtil.applyFocusListener(binding.qrRefreshButton)
        FocusEffectUtil.applyFocusListener(binding.qrVerifyButton)

        binding.qrBrowserButton.nextFocusDownId = R.id.qrCodeCard
        binding.qrCodeCard.nextFocusUpId = R.id.qrBrowserButton
        binding.qrCodeCard.nextFocusDownId = R.id.qrRefreshButton
        binding.qrRefreshButton.nextFocusUpId = R.id.qrCodeCard
        binding.qrRefreshButton.nextFocusDownId = R.id.qrVerifyButton
        binding.qrVerifyButton.nextFocusUpId = R.id.qrRefreshButton
        // Native dialog Cancel button (bottom of dialog) closes the chain
        binding.qrVerifyButton.nextFocusDownId = android.R.id.button2
        dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)?.nextFocusUpId = R.id.qrVerifyButton

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

    private fun setDebugStep(binding: DialogQrLoginBinding, step: String) {
        binding.qrDebugStepText.text = "Step: $step"
    }

    private fun setDebugSessionId(binding: DialogQrLoginBinding, sid: String?) {
        binding.qrDebugSessionIdText.text = "Session: ${sid?.take(20) ?: "—"}"
    }

    private fun setDebugStatus(binding: DialogQrLoginBinding, status: String) {
        binding.qrDebugStatusText.text = "Status: $status"
    }

    private fun setDebugPollCount(binding: DialogQrLoginBinding) {
        binding.qrDebugPollCountText.text = "Polls: $pollCount"
    }

    private fun setDebugLastPollTime(binding: DialogQrLoginBinding) {
        binding.qrDebugLastPollTimeText.text = "Last poll: ${timeFmt.format(Date())}"
    }

    private fun setDebugHttpCode(binding: DialogQrLoginBinding, code: String) {
        binding.qrDebugHttpCodeText.text = "HTTP: $code"
    }

    private fun setDebugPollDetail(binding: DialogQrLoginBinding, detail: String, show: Boolean = true) {
        binding.qrDebugPollDetailText.text = detail
        binding.qrDebugPollDetailText.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun onVerifyClicked(binding: DialogQrLoginBinding) {
        val sid = currentSessionId ?: return
        binding.qrVerifyButton.isEnabled = false
        setDebugStep(binding, "Manual verify...")

        scope.launch {
            try {
                setDebugStep(binding, "Checking status...")
                val statusResponse = QrLoginApi.getSessionStatus(sid)
                setDebugStatus(binding, statusResponse.status)
                setDebugHttpCode(binding, "200")

                // Verify session ID consistency
                verifySessionId(binding, sid, "verify-check")

                when (statusResponse.status) {
                    "authorized" -> {
                        performLoginFlow(binding, sid)
                    }
                    "pending" -> {
                        binding.qrStatusText.text = "Authorization has not completed yet."
                        setDebugStep(binding, "Manual verify: pending")
                    }
                    "expired" -> {
                        binding.qrStatusText.text = "QR session expired."
                        setDebugStep(binding, "Manual verify: expired")
                    }
                    else -> {
                        binding.qrStatusText.text = "Unknown status: ${statusResponse.status}"
                        setDebugStep(binding, "FAILED: Unknown status ${statusResponse.status}")
                    }
                }
            } catch (e: Exception) {
                Logger.log("[QR-DEBUG] Verify failed: ${e.message}")
                binding.qrStatusText.text = "Verify failed: ${e.message}"
                setDebugStep(binding, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
                setDebugHttpCode(binding, "ERR")
            } finally {
                binding.qrVerifyButton.isEnabled = true
            }
        }
    }

    private fun verifySessionId(binding: DialogQrLoginBinding, polledSid: String, source: String) {
        val createdSid = createSessionId
        if (createdSid != null && createdSid != polledSid) {
            setDebugPollDetail(binding, "❌ Session ID mismatch: created=${createdSid?.take(8)} vs $source=${polledSid.take(8)}")
        }
    }

    private fun performLoginFlow(binding: DialogQrLoginBinding, sessionId: String) {
        scope.launch {
            try {
                cancelPolling()
                countdownTimer?.cancel()

                // Verify session ID before consume
                verifySessionId(binding, sessionId, "consume")

                // Consume
                setDebugStep(binding, "Consuming authorization code")
                val consumeResponse = QrLoginApi.consumeSession(sessionId)
                val authCode = consumeResponse.authorizationCode
                setDebugStep(binding, if (authCode.isNotEmpty()) "Authorization code received (len=${authCode.length})" else "FAILED: Empty authorization code")

                if (authCode.isEmpty()) {
                    binding.qrStatusText.text = "Failed: empty authorization code"
                    binding.qrRefreshButton.isEnabled = true
                    return@launch
                }

                // Exchange
                setDebugStep(binding, "Exchanging code for token")
                val tokenResponse = QrLoginApi.exchangeAuthorizationCode(
                    code = authCode,
                    clientId = "47875",
                    clientSecret = "rPOWDPFARSGR7CnR08bAz9PX06QQfJKUN9vajdSb",
                    redirectUri = "https://sanin-auth.shemaus58.workers.dev/callback"
                )
                val token = tokenResponse.access_token
                setDebugStep(binding, if (token.isNotEmpty()) "Token received (len=${token.length})" else "FAILED: Empty token")

                if (token.isEmpty()) {
                    binding.qrStatusText.text = "Failed: empty token"
                    binding.qrRefreshButton.isEnabled = true
                    return@launch
                }

                // Save
                setDebugStep(binding, "Saving token")
                Anilist.token = token
                PrefManager.setVal(PrefName.AnilistToken, token)
                setDebugStep(binding, "Fetching AniList profile")

                // Profile
                binding.qrStatusText.text = "Fetching profile..."
                Anilist.query.getUserData()

                // Done
                setDebugStep(binding, "Login complete")
                binding.qrStatusText.text = "Successfully signed in!"
                binding.qrRefreshButton.isEnabled = false

                Logger.log("[QR-DEBUG] Calling onAuthenticated()...")
                try {
                    onAuthenticated()
                    Logger.log("[QR-DEBUG] onAuthenticated() completed")
                } catch (e: Exception) {
                    Logger.log("[QR-DEBUG] EXCEPTION in onAuthenticated: ${e.javaClass.simpleName}: ${e.message}")
                    Logger.log(e)
                }

                delay(1000)
                dialog?.dismiss()
                dialog = null
                dialogBinding = null
                currentSessionId = null

                // Restart the app so it boots with the fresh AniList session
                Logger.log("[QR-DEBUG] Restarting app after QR login")
                val activity = context as? Activity
                if (activity != null && !activity.isFinishing) {
                    activity.restartApp()
                } else {
                    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(it)
                    }
                }
            } catch (e: Exception) {
                Logger.log("[QR-DEBUG] Login flow failed: ${e.message}")
                setDebugStep(binding, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
                binding.qrStatusText.text = "Login failed: ${e.message}"
                binding.qrRefreshButton.isEnabled = true
            }
        }
    }

    private fun createSessionAndStartPolling(binding: DialogQrLoginBinding) {
        // Prevent duplicate session creation
        if (isCreatingSession) {
            Logger.log("[QR-DEBUG] createSessionAndStartPolling: already creating, returning")
            return
        }
        isCreatingSession = true
        lastPollStatus = null
        pollCount = 0
        createSessionId = null

        scope.launch {
            try {
                // Show loading
                binding.qrLoadingIndicator.visibility = View.VISIBLE
                binding.qrCodeImageView.visibility = View.GONE
                binding.qrStatusText.text = "Creating session..."
                binding.qrRefreshButton.isEnabled = false

                setDebugStep(binding, "Creating session...")
                setDebugStatus(binding, "—")
                setDebugHttpCode(binding, "—")
                setDebugPollCount(binding)
                setDebugLastPollTime(binding)
                setDebugPollDetail(binding, "Poll detail: —", show = false)

                Logger.log("[QR-DEBUG] Calling QrLoginApi.createSession()...")
                // Create session
                val session = QrLoginApi.createSession()
                currentSessionId = session.sessionId
                createSessionId = session.sessionId
                Logger.log("[QR-DEBUG] Session created successfully: sessionId=${session.sessionId}")

                setDebugSessionId(binding, session.sessionId)
                setDebugStep(binding, "Session created")
                setDebugHttpCode(binding, "200")

                // Generate QR code
                Logger.log("[QR-DEBUG] Generating QR code...")
                setDebugStep(binding, "Generating QR code...")
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

                setDebugStep(binding, "Waiting for authorization")

                // Start countdown
                startCountdown(binding, session.expiresIn)

                // Start polling
                Logger.log("[QR-DEBUG] Starting polling for sessionId=${session.sessionId}")
                startPolling(session.sessionId, binding)

            } catch (e: Exception) {
                Logger.log("[QR-DEBUG] EXCEPTION in createSessionAndStartPolling: ${e.javaClass.simpleName}: ${e.message}")
                Logger.log(e)
                setDebugStep(binding, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
                setDebugHttpCode(binding, "ERR")
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
        pollCount = 0
        createSessionId = null
        createSessionAndStartPolling(binding)
    }

    private fun startPolling(sessionId: String, binding: DialogQrLoginBinding) {
        cancelPolling()

        pollingJob = scope.launch {
            Logger.log("[QR-DEBUG] Polling loop started for sessionId=$sessionId")
            while (isActive) {
                try {
                    delay(1000) // Poll every 1 second

                    pollCount++
                    setDebugPollCount(binding)
                    setDebugLastPollTime(binding)
                    setDebugStep(binding, "Polling...")

                    Logger.log("[QR-DEBUG] Polling sessionId=$sessionId")
                    val response = QrLoginApi.getSessionStatus(sessionId)
                    setDebugHttpCode(binding, "200")
                    setDebugStatus(binding, response.status)

                    // Detect status change
                    if (lastPollStatus != null && lastPollStatus != response.status) {
                        Logger.log("[QR-DEBUG] STATUS CHANGE: $lastPollStatus -> ${response.status}")
                    }
                    lastPollStatus = response.status

                    setDebugPollDetail(binding, "Poll #$pollCount | HTTP 200 | Status: ${response.status}")

                    when (response.status) {
                        "authorized" -> {
                            Logger.log("[QR-DEBUG] Status is 'authorized' - stopping polling, calling consumeSession...")
                            setDebugStep(binding, "Authorization detected")
                            setDebugPollDetail(binding, "Poll #$pollCount | HTTP 200 | Status: authorized")
                            performLoginFlow(binding, sessionId)
                        }
                        "expired" -> {
                            Logger.log("[QR-DEBUG] Status is 'expired' - stopping polling")
                            // Stop polling
                            cancelPolling()
                            countdownTimer?.cancel()

                            setDebugStep(binding, "QR session expired")

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
                    Logger.log(e)
                    setDebugStep(binding, "FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    setDebugHttpCode(binding, "ERR")
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
