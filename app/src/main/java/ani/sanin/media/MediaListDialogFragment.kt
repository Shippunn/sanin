package ani.sanin.media

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.content.Intent
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import ani.sanin.DatePickerFragment
import ani.sanin.notifications.subscription.NotificationPopupActivity
import ani.sanin.InputFilterMinMax
import ani.sanin.R
import ani.sanin.Refresh
import ani.sanin.connections.PendingProgressUpdate
import ani.sanin.connections.statusNotificationPhrase
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anilist.api.FuzzyDate
import ani.sanin.connections.mal.MAL
import ani.sanin.databinding.BottomSheetMediaListBinding
import ani.sanin.getThemeColor
import ani.sanin.loadImage
import ani.sanin.navBarHeight
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.tryWith
import ani.sanin.util.FocusEffectUtil
import ani.sanin.util.GlassComponent
import ani.sanin.util.GlassEffectManager
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaListDialogFragment : DialogFragment() {

    private var _binding: BottomSheetMediaListBinding? = null
    private val binding get() = _binding!!

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setBackgroundDrawableResource(android.R.color.transparent)
            val widthPx = (resources.displayMetrics.widthPixels * 0.80f).toInt()
            w.setLayout(widthPx, WindowManager.LayoutParams.WRAP_CONTENT)
            w.setGravity(Gravity.CENTER)
            w.setDimAmount(0.5f)
            w.statusBarColor = Color.TRANSPARENT
            val surfaceColor = requireContext().getThemeColor(com.google.android.material.R.attr.colorSurface)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                w.navigationBarColor = surfaceColor
            }
            WindowInsetsCompat.Type.navigationBars()
            val controller = androidx.core.view.WindowInsetsControllerCompat(w, w.decorView)
            controller.isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(surfaceColor) > 0.5
        }
        GlassEffectManager.applyGlassToSheet(binding.mediaListContainer, GlassComponent.ListEditor, 16f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMediaListBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaListContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        GlassEffectManager.applyGlassToSheet(binding.mediaListContainer, GlassComponent.ListEditor, 16f)
        var media: Media?
        val model: MediaDetailsViewModel by activityViewModels()
        val scope = viewLifecycleOwner.lifecycleScope

        model.getMedia().observe(this) { it ->
            media = it
            if (media != null) {
                binding.mediaListProgressBar.visibility = View.GONE
                binding.mediaListLayout.visibility = View.VISIBLE
                binding.mediaListBannerContainer.visibility = View.VISIBLE
                binding.mediaListBanner.loadImage(media!!.banner ?: media!!.cover)

                val statuses: Array<String> = resources.getStringArray(R.array.status)
                val statusStrings = resources.getStringArray(R.array.status_anime)
                val userStatus =
                    if (media!!.userStatus != null) statusStrings[statuses.indexOf(media!!.userStatus).coerceAtLeast(0)] else statusStrings[0]

                binding.mediaListStatusGroup.removeAllViews()
                statusStrings.forEachIndexed { index, label ->
                    val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                        text = label
                        tag = label
                        isCheckable = true
                        isClickable = true
                        isFocusable = true
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    }
                    binding.mediaListStatusGroup.addView(chip)
                    if (label == userStatus) chip.isChecked = true
                }
                FocusEffectUtil.applyFocusListener(
                    *(0 until binding.mediaListStatusGroup.childCount).map { binding.mediaListStatusGroup.getChildAt(it) }.toTypedArray()
                )

                var total: Int? = null
                binding.mediaListProgress.setText(if (media!!.userProgress != null) media!!.userProgress.toString() else "")
                if (media!!.anime != null) if (media!!.anime!!.totalEpisodes != null) {
                    total = media!!.anime!!.totalEpisodes!!
                    binding.mediaListProgress.filters =
                        arrayOf(
                            InputFilterMinMax(0.0, total.toDouble(), binding.mediaListStatusGroup),
                            LengthFilter(total.toString().length)
                        )
                }
                binding.mediaListProgressLayout.suffixText = " / ${total ?: '?'}"
                binding.mediaListProgressLayout.suffixTextView.updateLayoutParams {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                binding.mediaListProgressLayout.suffixTextView.gravity = Gravity.CENTER

                binding.mediaListVolumeProgressLayout.visibility = View.GONE

                binding.mediaListScore.setText(
                    if (media!!.userScore != 0) media!!.userScore.div(
                        10.0
                    ).toString() else ""
                )
                binding.mediaListScore.filters =
                    arrayOf(InputFilterMinMax(0.0, 10.0), LengthFilter(10.0.toString().length))
                binding.mediaListScoreLayout.suffixTextView.updateLayoutParams {
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
                binding.mediaListScoreLayout.suffixTextView.gravity = Gravity.CENTER

                val start = DatePickerFragment(requireActivity(), media!!.userStartedAt)
                val end = DatePickerFragment(requireActivity(), media!!.userCompletedAt)
                binding.mediaListStart.setText(media!!.userStartedAt.toStringOrEmpty())
                binding.mediaListStart.setOnClickListener {
                    tryWith(false) {
                        if (!start.dialog.isShowing) start.dialog.show()
                    }
                }
                binding.mediaListStart.setOnFocusChangeListener { _, b ->
                    tryWith(false) {
                        if (b && !start.dialog.isShowing) start.dialog.show()
                    }
                }
                binding.mediaListEnd.setText(media!!.userCompletedAt.toStringOrEmpty())
                binding.mediaListEnd.setOnClickListener {
                    tryWith(false) {
                        if (!end.dialog.isShowing) end.dialog.show()
                    }
                }
                binding.mediaListEnd.setOnFocusChangeListener { _, b ->
                    tryWith(false) {
                        if (b && !end.dialog.isShowing) end.dialog.show()
                    }
                }
                start.dialog.setOnDismissListener { _binding?.mediaListStart?.setText(start.date.toStringOrEmpty()) }
                end.dialog.setOnDismissListener { _binding?.mediaListEnd?.setText(end.date.toStringOrEmpty()) }

                fun onComplete() {
                    if (total != null) binding.mediaListProgress.setText(total.toString())
                    if (start.date.year == null) {
                        start.date = FuzzyDate().getToday()
                        binding.mediaListStart.setText(start.date.toString())
                    }
                    end.date = FuzzyDate().getToday()
                    binding.mediaListEnd.setText(end.date.toString())
                }

                var startBackupDate: FuzzyDate? = null
                var endBackupDate: FuzzyDate? = null
                var progressBackup: String? = null
                binding.mediaListStatusGroup.setOnCheckedStateChangeListener { group, _ ->
                    val checkedId = group.checkedChipId
                    if (checkedId != -1) {
                        val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedId)
                        val i = statusStrings.indexOf(chip?.text?.toString())
                        if (i == 2 && total != null) {
                            startBackupDate = start.date
                            endBackupDate = end.date
                            progressBackup = binding.mediaListProgress.text.toString()
                            onComplete()
                        } else {
                            if (progressBackup != null) binding.mediaListProgress.setText(progressBackup)
                            if (startBackupDate != null) {
                                binding.mediaListStart.setText(startBackupDate.toStringOrEmpty())
                                start.date = startBackupDate
                            }
                            if (endBackupDate != null) {
                                binding.mediaListEnd.setText(endBackupDate.toStringOrEmpty())
                                end.date = endBackupDate
                            }
                        }
                    }
                }
                binding.mediaListIncrement.setOnClickListener {
                    val checkedId = binding.mediaListStatusGroup.checkedChipId
                    val currentChip = if (checkedId != -1) binding.mediaListStatusGroup.findViewById<com.google.android.material.chip.Chip>(checkedId) else null
                    if (currentChip?.text?.toString() == statusStrings[0]) {
                        binding.mediaListStatusGroup.findViewWithTag<com.google.android.material.chip.Chip>(statusStrings[1])?.isChecked = true
                    }
                    val init =
                        if (binding.mediaListProgress.text.toString() != "") binding.mediaListProgress.text.toString()
                            .toInt() else 0
                    if (init < (total ?: 5000)) {
                        val progressText = "${init + 1}"
                        binding.mediaListProgress.setText(progressText)
                    }
                    if (total != null && init + 1 == total) {
                        binding.mediaListStatusGroup.findViewWithTag<com.google.android.material.chip.Chip>(statusStrings[2])?.isChecked = true
                        onComplete()
                    }
                }

                val isRescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
                if (isRescueMode) {
                    binding.mediaListPrivate.apply { (parent as? ViewGroup)?.removeView(this) }
                } else {
                    binding.mediaListPrivate.visibility = View.VISIBLE
                }
                binding.mediaListPrivate.isChecked = media?.isListPrivate ?: false
                binding.mediaListPrivate.setOnCheckedChangeListener { _, checked ->
                    media?.isListPrivate = checked
                }
                val removeList = PrefManager.getCustomVal("removeList", setOf<Int>())
                var remove: Boolean? = null
                binding.mediaListShow.isChecked = media?.id in removeList
                binding.mediaListShow.setOnCheckedChangeListener { _, checked ->
                    remove = checked
                }
                media?.userRepeat?.apply {
                    binding.mediaListRewatch.setText(this.toString())
                }

                media?.notes?.apply {
                    binding.mediaListNotes.setText(this)
                }

                if (media?.inCustomListsOf?.isEmpty() != false || isRescueMode)
                    binding.mediaListAddCustomList.apply {
                        (parent as? ViewGroup)?.removeView(this)
                    }

                if (!isRescueMode) {
                    media?.inCustomListsOf?.forEach {
                        MaterialSwitch(requireContext()).apply {
                            isChecked = it.value
                            text = it.key
                            setOnCheckedChangeListener { _, isChecked ->
                                media?.inCustomListsOf?.put(it.key, isChecked)
                            }
                            binding.mediaListCustomListContainer.addView(this)
                        }
                    }
                }

                binding.mediaListSave.setOnClickListener {
                    val progressText = binding.mediaListProgress.text.toString()
                    val scoreText = binding.mediaListScore.text.toString()
                    val checkedId = binding.mediaListStatusGroup.checkedChipId
                    val statusText = if (checkedId != -1)
                        (binding.mediaListStatusGroup.findViewById<com.google.android.material.chip.Chip>(checkedId)?.text?.toString() ?: statusStrings[0])
                    else statusStrings[0]
                    val rewatchText = binding.mediaListRewatch.text?.toString()
                    val notesText = binding.mediaListNotes.text?.toString()
                    val newStatus =
                        statuses[statusStrings.indexOf(statusText).coerceAtLeast(0)]
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (media != null) {
                                val progress =
                                    _binding?.mediaListProgress?.text.toString().toIntOrNull()
                                val score =
                                    (_binding?.mediaListScore?.text.toString().toDoubleOrNull()
                                        ?.times(10))?.toInt()
                                val progressVolumes =
                                    _binding?.mediaListVolumeProgress?.text.toString().toIntOrNull()
                                val status = newStatus
                                val rewatch = rewatchText?.toIntOrNull()
                                val notes = notesText
                                val startD = start.date
                                val endD = end.date
                                val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
                                if (rescueMode) {
                                    val pending = PendingProgressUpdate(
                                        mediaId = media!!.id,
                                        idMAL = media!!.idMAL,
                                        isAnime = media!!.anime != null,
                                        progress = progress ?: 0,
                                        status = status,
                                        score = score,
                                        rewatch = rewatch,
                                        notes = notes,
                                        isPrivate = media?.isListPrivate ?: false,
                                        startDate = startD,
                                        endDate = endD,
                                        customLists = media?.inCustomListsOf
                                            ?.mapNotNull { if (it.value) it.key else null },
                                    )
                                    val existing: List<PendingProgressUpdate> =
                                        PrefManager.getVal(PrefName.PendingProgressUpdates, listOf())
                                    val updated = existing.filterNot { it.mediaId == media!!.id } + pending
                                    PrefManager.setVal(PrefName.PendingProgressUpdates, updated)
                                } else {
                                    Anilist.mutation.editList(
                                        mediaID = media!!.id,
                                        progress = progress,
                                        progressVolumes = progressVolumes,
                                        score = score,
                                        repeat = rewatch,
                                        notes = notes,
                                        status = status,
                                        private = media?.isListPrivate ?: false,
                                        startedAt = startD,
                                        completedAt = endD,
                                        customList = media?.inCustomListsOf?.mapNotNull { if (it.value) it.key else null }
                                    )
                                }
                                MAL.query.editList(
                                    media!!.idMAL,
                                    media!!.anime != null,
                                    progress,
                                    score,
                                    status,
                                    rewatch,
                                    startD,
                                    endD
                                )
                            }
                        }
                        if (remove == true) {
                            PrefManager.setCustomVal("removeList", removeList.plus(media!!.id))
                        } else if (remove == false) {
                            PrefManager.setCustomVal("removeList", removeList.minus(media!!.id))
                        }
                        Refresh.all()
                        if (PrefManager.getVal<Boolean>(PrefName.ListStatusNotification) && media!!.userStatus != newStatus) {
                            val intent = Intent(requireActivity(), NotificationPopupActivity::class.java).apply {
                                putExtra("title", statusNotificationPhrase(media!!, newStatus))
                                putExtra("text", getString(R.string.list_updated))
                                putExtra("coverUrl", media!!.cover)
                            }
                            requireActivity().startActivity(intent)
                        } else {
                            val intent = Intent(requireActivity(), NotificationPopupActivity::class.java).apply {
                                putExtra("title", getString(R.string.list_updated))
                                putExtra("text", "")
                            }
                            requireActivity().startActivity(intent)
                        }
                        dismissAllowingStateLoss()
                    }
                }

                binding.mediaListDelete.setOnClickListener {
                    scope.launch {
                        media?.deleteFromList(scope, onSuccess = {
                            Refresh.all()
                            snackString(getString(R.string.deleted_from_list))
                            dismissAllowingStateLoss()
                        }, onError = { e ->
                            withContext(Dispatchers.Main) {
                                snackString(
                                    getString(
                                        R.string.delete_fail_reason, e.message
                                    )
                                )
                            }
                        }, onNotFound = {
                            snackString(getString(R.string.no_list_id))
                        })
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
