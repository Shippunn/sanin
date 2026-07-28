package ani.sanin.others

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import androidx.core.view.updateLayoutParams
import ani.sanin.R
import ani.sanin.databinding.ActivityCrashBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.util.FocusEffectUtil
import eu.kanade.tachiyomi.util.system.copyToClipboard
import java.io.File

class CrashActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCrashBinding

    private lateinit var stackTrace: String
    private lateinit var logcat: String
    private var crashReportText by mutableStateOf("")

    /** Which content is currently shown — false = stack trace, true = logcat */
    private var showingLogcat = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityCrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FocusEffectUtil.applyFocusListener(binding.root)
        binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
            bottomMargin = navBarHeight
        }

        stackTrace = intent.getStringExtra("stackTrace") ?: "No stack trace available"
        logcat = intent.getStringExtra("logcat") ?: "No logcat available"

        // Show stack trace by default
        showReport(stackTrace)

        binding.crashReportView.setContent {
            val scrollState = rememberScrollState()
            OutlinedTextField(
                value = crashReportText,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
            )
        }

        binding.copyButton.setOnClickListener {
            val label = if (showingLogcat) "Logcat" else "Crash log"
            copyToClipboard(label, currentContent())
        }

        binding.shareAsTextFileButton.setOnClickListener {
            shareAsTextFile(currentContent(), if (showingLogcat) "logcat.txt" else "crash_log.txt")
        }

        binding.toggleLogcatButton.setOnClickListener {
            showingLogcat = !showingLogcat
            if (showingLogcat) {
                showReport(logcat)
                binding.toggleLogcatButton.text = getString(R.string.show_crash_report)
            } else {
                showReport(stackTrace)
                binding.toggleLogcatButton.text = getString(R.string.show_logcat)
            }
        }
    }

    private fun currentContent() = if (showingLogcat) logcat else stackTrace

    private fun showReport(content: String) {
        crashReportText = content
    }

    private fun shareAsTextFile(content: String, fileName: String) {
        val file = File(cacheDir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }
}