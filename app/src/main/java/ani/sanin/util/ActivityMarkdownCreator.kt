package ani.sanin.util

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import ani.sanin.R
import ani.sanin.buildMarkwon
import ani.sanin.connections.anilist.Anilist
import ani.sanin.databinding.ActivityMarkdownCreatorBinding
import ani.sanin.initActivity
import ani.sanin.navBarHeight
import ani.sanin.openLinkInBrowser
import ani.sanin.others.AndroidBug5497Workaround
import ani.sanin.statusBarHeight
import ani.sanin.themes.ThemeManager
import ani.sanin.toast
import ani.sanin.util.FocusEffectUtil
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActivityMarkdownCreator : AppCompatActivity() {
    private lateinit var binding: ActivityMarkdownCreatorBinding
    private lateinit var type: String
    private var text: String = ""
    private var markdownValue by mutableStateOf(TextFieldValue(""))
    private var ping: String? = null
    private var parentId: Int = 0
    private var isPreviewMode: Boolean = false

    enum class MarkdownFormat(
        val syntax: String,
        val selectionOffset: Int,
        val imageViewId: Int
    ) {
        BOLD("****", 2, R.id.formatBold),
        ITALIC("**", 1, R.id.formatItalic),
        STRIKETHROUGH("~~~~", 2, R.id.formatStrikethrough),
        SPOILER("~!!~", 2, R.id.formatSpoiler),
        LINK("[Placeholder](%s)", 0, R.id.formatLink),
        IMAGE("img(%s)", 0, R.id.formatImage),
        YOUTUBE("youtube(%s)", 0, R.id.formatYoutube),
        VIDEO("webm(%s)", 0, R.id.formatVideo),
        ORDERED_LIST("1. ", 3, R.id.formatListOrdered),
        UNORDERED_LIST("- ", 2, R.id.formatListUnordered),
        HEADING("# ", 2, R.id.formatTitle),
        CENTERED("~~~~~~", 3, R.id.formatCenter),
        QUOTE("> ", 2, R.id.formatQuote),
        CODE("``", 1, R.id.formatCode)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityMarkdownCreatorBinding.inflate(layoutInflater)
        binding.markdownCreatorToolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = statusBarHeight
        }
        binding.markdownOptionsContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin += navBarHeight
        }
        setContentView(binding.root)
        FocusEffectUtil.applyFocusListener(binding.root)
        AndroidBug5497Workaround.assistActivity(this) {}

        val params = binding.createButton.layoutParams as ViewGroup.MarginLayoutParams
        params.marginEnd = 16 * resources.displayMetrics.density.toInt()
        binding.createButton.layoutParams = params

        if (intent.hasExtra("type")) {
            type = intent.getStringExtra("type")!!
        } else {
            toast("Error: No type")
            finish()
            return
        }
        val editId = intent.getIntExtra("edit", -1)
        val userId = intent.getIntExtra("userId", -1)
        parentId = intent.getIntExtra("parentId", -1)
        when (type) {
            "replyActivity" -> if (parentId == -1) {
                toast("Error: No parent ID")
                finish()
                return
            }

            "message" -> {
                if (editId == -1) {
                    binding.privateCheckbox.visibility = ViewGroup.VISIBLE
                }
            }
        }
        var private = false
        binding.privateCheckbox.setOnCheckedChangeListener { _, isChecked ->
            private = isChecked
        }

        ping = intent.getStringExtra("other")
        text = ping ?: ""
        markdownValue = TextFieldValue(text)
        previewMarkdown(false)

        binding.editText.setContent {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = markdownValue,
                onValueChange = { newVal ->
                    markdownValue = newVal
                    if (!isPreviewMode) {
                        text = newVal.text
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown) {
                            when (ev.key) {
                                Key.DirectionDown -> {
                                    focusManager.moveFocus(FocusDirection.Down)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    },
                placeholder = { androidx.compose.material3.Text(getString(R.string.reply_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ComposeColor.White,
                    unfocusedTextColor = ComposeColor.White,
                    cursorColor = ComposeColor.White
                )
            )
        }

        binding.markdownCreatorBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.createButton.setOnClickListener {
            if (text.isBlank()) {
                toast(getString(R.string.cannot_be_empty))
                return@setOnClickListener
            }
            customAlertDialog().apply {
                setTitle(R.string.warning)
                setMessage(R.string.post_to_anilist_warning)
                setPosButton(R.string.ok) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val isEdit = editId != -1
                        val success = when (type) {
                            "activity" -> if (isEdit) {
                                Anilist.mutation.postActivity(text, editId)
                            } else {
                                Anilist.mutation.postActivity(text)
                            }
                            //"review" -> Anilist.mutation.postReview(text)
                            "replyActivity" -> if (isEdit) {
                                Anilist.mutation.postReply(parentId, text, editId)
                            } else {
                                Anilist.mutation.postReply(parentId, text)
                            }

                            "message" -> if (isEdit) {
                                Anilist.mutation.postMessage(userId, text, editId)
                            } else {
                                Anilist.mutation.postMessage(userId, text, isPrivate = private)
                            }

                            else -> "Error: Unknown type"
                        }
                        toast(success)
                        finish()
                    }
                }
                setNeutralButton(R.string.open_rules) {
                    openLinkInBrowser("https://anilist.co/forum/thread/14")
                }
                setNegButton(R.string.cancel)
                show()
            }
        }

        binding.previewCheckbox.setOnClickListener {
            isPreviewMode = !isPreviewMode
            previewMarkdown(isPreviewMode)
            if (isPreviewMode) {
                toast("Preview enabled")
            } else {
                toast("Preview disabled")
            }
        }
        binding.editText.requestFocus()
        setupMarkdownButtons()
    }

    private fun setupMarkdownButtons() {
        MarkdownFormat.entries.forEach { format ->
            findViewById<ImageView>(format.imageViewId)?.setOnClickListener {
                applyMarkdownFormat(format)
            }
        }
    }

    private fun applyMarkdownFormat(format: MarkdownFormat) {
        val start = markdownValue.selection.start
        val end = markdownValue.selection.end
        val fullText = markdownValue.text

        if (start != end) {
            val selectedText = fullText.substring(start, end)
            val lines = selectedText.split("\n")

            val newText = when (format) {
                MarkdownFormat.UNORDERED_LIST -> {
                    lines.joinToString("\n") { "- $it" }
                }

                MarkdownFormat.ORDERED_LIST -> {
                    lines.mapIndexed { index, line -> "${index + 1}. $line" }.joinToString("\n")
                }

                else -> {
                    if (format.syntax.contains("%s")) {
                        String.format(format.syntax, selectedText)
                    } else {
                        format.syntax.substring(0, format.selectionOffset) +
                                selectedText +
                                format.syntax.substring(format.selectionOffset)
                    }
                }
            }

            markdownValue = TextFieldValue(
                fullText.replaceRange(start, end, newText),
                TextRange(start + newText.length)
            )
        } else {
            if (format.syntax.contains("%s")) {
                showInputDialog(format, start)
            } else {
                val newText = format.syntax
                markdownValue = TextFieldValue(
                    fullText.substring(0, start) + newText + fullText.substring(start),
                    TextRange(start + format.selectionOffset)
                )
            }
        }
    }


    private fun showInputDialog(format: MarkdownFormat, position: Int) {
        val inputLayout = TextInputLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            isHintEnabled = true
        }

        val inputEditText = com.google.android.material.textfield.TextInputEditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputLayout.addView(inputEditText)

        val container = FrameLayout(this).apply {
            addView(inputLayout)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(0, 0, 0, 0)
        }
        customAlertDialog().apply {
            setTitle("Paste your link here")
            setCustomView(container)
            setPosButton(getString(R.string.ok)) {
                val input = inputEditText.text.toString()
                val formattedText = String.format(format.syntax, input)
                val fullText = markdownValue.text
                markdownValue = TextFieldValue(
                    fullText.substring(0, position) + formattedText + fullText.substring(position),
                    TextRange(position + formattedText.length)
                )
            }
            setNegButton(getString(R.string.cancel))
        }.show()

        inputEditText.requestFocus()
    }

    private fun previewMarkdown(preview: Boolean) {
        val markwon = buildMarkwon(this, false, anilist = true)
        if (preview) {
            markdownValue = TextFieldValue("")
            binding.markdownPreview.isVisible = true
            markwon.setMarkdown(binding.markdownPreview, AniMarkdown.getBasicAniHTML(text))
        } else {
            binding.markdownPreview.isVisible = false
            markdownValue = TextFieldValue(text)
        }
    }
}