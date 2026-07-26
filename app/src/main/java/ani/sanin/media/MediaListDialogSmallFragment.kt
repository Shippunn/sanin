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
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import android.content.Intent
import androidx.fragment.app.DialogFragment
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
import ani.sanin.databinding.BottomSheetMediaListSmallBinding
import ani.sanin.getThemeColor
import ani.sanin.loadImage
import ani.sanin.navBarHeight
import ani.sanin.others.getSerialized
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.snackString
import ani.sanin.util.FocusEffectUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

class MediaListDialogSmallFragment : DialogFragment() {

    private lateinit var media: Media
    private var _binding: BottomSheetMediaListSmallBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(m: Media): MediaListDialogSmallFragment =
            MediaListDialogSmallFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("media", m as Serializable)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            media = it.getSerialized("media")!!
        }
    }

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
            val controller = WindowInsetsControllerCompat(w, w.decorView)
            controller.isAppearanceLightNavigationBars = ColorUtils.calculateLuminance(surfaceColor) > 0.5
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMediaListSmallBinding.inflate(inflater, container, false)
        FocusEffectUtil.applyFocusListener(binding.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mediaListContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin += navBarHeight }
        val scope = viewLifecycleOwner.lifecycleScope

        binding.mediaListProgressBar.visibility = View.GONE
        binding.mediaListLayout.visibility = View.VISIBLE
        binding.mediaListBannerContainer.visibility = View.VISIBLE
        binding.mediaListBanner.loadImage(media.banner ?: media.cover)

        val statuses: Array<String> = resources.getStringArray(R.array.status)
        val statusStrings = resources.getStringArray(R.array.status_anime)
        val userStatus =
            if (media.userStatus != null) statusStrings[statuses.indexOf(media.userStatus).coerceAtLeast(0)] else statusStrings[0]

        binding.mediaListStatusGroup.removeAllViews()
        statusStrings.forEachIndexed { _, label ->
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

        var total: Int? = null
        binding.mediaListProgress.setText(if (media.userProgress != null) media.userProgress.toString() else "")
        if (media.anime != null) if (media.anime!!.totalEpisodes != null) {
            total = media.anime!!.totalEpisodes!!;binding.mediaListProgress.filters =
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

        binding.mediaListScore.setText(
            if (media.userScore != 0) media.userScore.div(
                10.0
            ).toString() else ""
        )
        binding.mediaListScore.filters =
            arrayOf(InputFilterMinMax(0.0, 10.0), LengthFilter(10.0.toString().length))
        binding.mediaListScoreLayout.suffixTextView.updateLayoutParams {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.mediaListScoreLayout.suffixTextView.gravity = Gravity.CENTER

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
            if (init + 1 == (total ?: 5000)) {
                binding.mediaListStatusGroup.findViewWithTag<com.google.android.material.chip.Chip>(statusStrings[2])?.isChecked = true
            }
        }

        val isRescueMode = PrefManager.getVal<Boolean>(PrefName.RescueMode)
        if (isRescueMode) {
            binding.mediaListPrivate.apply { (parent as? ViewGroup)?.removeView(this) }
        } else {
            binding.mediaListPrivate.visibility = View.VISIBLE
        }
        binding.mediaListPrivate.isChecked = media.isListPrivate
        binding.mediaListPrivate.setOnCheckedChangeListener { _, checked ->
            media.isListPrivate = checked
        }
        val removeList = PrefManager.getCustomVal("removeList", setOf<Int>())
        var remove: Boolean? = null
        binding.mediaListShow.isChecked = media.id in removeList
        binding.mediaListShow.setOnCheckedChangeListener { _, checked ->
            remove = checked
        }
        binding.mediaListSave.setOnClickListener {
            val progressText = binding.mediaListProgress.text.toString()
            val scoreText = binding.mediaListScore.text.toString()
            val newCheckedStatus = _binding?.mediaListStatusGroup?.let { group ->
                val gId = group.checkedChipId
                if (gId != -1) (group.findViewById<com.google.android.material.chip.Chip>(gId)?.text?.toString() ?: statusStrings[0]) else statusStrings[0]
            } ?: statusStrings[0]
            val newStatus = statuses[statusStrings.indexOf(newCheckedStatus).coerceAtLeast(0)]
            scope.launch {
                withContext(Dispatchers.IO) {
                    val progress = _binding?.mediaListProgress?.text.toString().toIntOrNull()
                    val progressVolumes = media.userProgressVolumes
                    val score = (_binding?.mediaListScore?.text.toString().toDoubleOrNull()?.times(10))?.toInt()
                    val status = newStatus
                    val startD = media.userStartedAt
                    val endD = media.userCompletedAt
                    val rescueMode: Boolean = PrefManager.getVal(PrefName.RescueMode)
                    if (rescueMode) {
                        val pending = PendingProgressUpdate(
                            mediaId = media.id,
                            idMAL = media.idMAL,
                            isAnime = media.anime != null,
                            progress = progress ?: 0,
                            status = status,
                            score = score,
                            isPrivate = media.isListPrivate,
                        )
                        val existing: List<PendingProgressUpdate> =
                            PrefManager.getVal(PrefName.PendingProgressUpdates, listOf())
                        val updated = existing.filterNot { it.mediaId == media.id } + pending
                        PrefManager.setVal(PrefName.PendingProgressUpdates, updated)
                    } else {
                        Anilist.mutation.editList(
                            mediaID = media.id,
                            progress = progress,
                            progressVolumes = progressVolumes,
                            score = score,
                            status = status,
                            private = media.isListPrivate,
                            startedAt = startD,
                            completedAt = endD
                        )
                    }
                    MAL.query.editList(
                        media.idMAL,
                        media.anime != null,
                        progress,
                        score,
                        status,
                        start = startD,
                        end = endD
                    )
                }
                if (remove == true) {
                    PrefManager.setCustomVal("removeList", removeList.plus(media.id))
                } else if (remove == false) {
                    PrefManager.setCustomVal("removeList", removeList.minus(media.id))
                }
                Refresh.all()
                if (PrefManager.getVal<Boolean>(PrefName.ListStatusNotification) && media.userStatus != newStatus) {
                    val intent = Intent(requireActivity(), NotificationPopupActivity::class.java).apply {
                        putExtra("title", statusNotificationPhrase(media, newStatus))
                        putExtra("text", getString(R.string.list_updated))
                        putExtra("coverUrl", media.cover)
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
                media.deleteFromList(scope, onSuccess = {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
