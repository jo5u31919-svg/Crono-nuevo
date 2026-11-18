package com.chronos.app.ui.dialog

import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.chronos.app.R
import com.chronos.app.data.local.DashboardSection
import com.chronos.app.databinding.DialogSettingsBinding
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.theme.ThemeAppearanceMode
import com.chronos.app.ui.viewmodel.ChronosViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class SettingsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChronosViewModel

    var onRequestOverlayPermission: (() -> Unit)? = null
    var onOverlayToggleChanged: ((Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ChronosViewModel::class.java]

        val state = viewModel.uiState.value

        val modeChipId = when (state.appearanceMode) {
            ThemeAppearanceMode.NIGHT -> R.id.chipModeNight
            ThemeAppearanceMode.DAY -> R.id.chipModeDay
            ThemeAppearanceMode.SYSTEM -> R.id.chipModeSystem
        }
        binding.chipGroupAppearanceMode.check(modeChipId)

        binding.chipGroupAppearanceMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val mode = when (selectedId) {
                R.id.chipModeDay -> ThemeAppearanceMode.DAY
                R.id.chipModeSystem -> ThemeAppearanceMode.SYSTEM
                else -> ThemeAppearanceMode.NIGHT
            }
            if (mode != viewModel.uiState.value.appearanceMode) {
                CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                viewModel.selectAppearanceMode(mode)
            }
        }

        val themeChipId = when (state.themeId) {
            CyberThemeId.CYBERPUNK_CLASSIC -> R.id.chipThemeCyberpunk
            CyberThemeId.HARMONY_AURORA -> R.id.chipThemeHarmony
            CyberThemeId.MATRIX_PROTOCOL -> R.id.chipThemeMatrix
            CyberThemeId.SYNTHWAVE_OUTRUN -> R.id.chipThemeSynthwave
            CyberThemeId.GHOST_CRIMSON -> R.id.chipThemeCrimson
            CyberThemeId.ARCTIC_NEURAL -> R.id.chipThemeArctic
            CyberThemeId.PRINCE_OF_PERSIA_SANDS -> R.id.chipThemePrince
        }
        binding.chipGroupThemes.check(themeChipId)

        binding.chipGroupThemes.setOnCheckedStateChangeListener { _, checkedIds ->
            val selectedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val theme = when (selectedId) {
                R.id.chipThemeHarmony -> CyberThemeId.HARMONY_AURORA
                R.id.chipThemeMatrix -> CyberThemeId.MATRIX_PROTOCOL
                R.id.chipThemeSynthwave -> CyberThemeId.SYNTHWAVE_OUTRUN
                R.id.chipThemeCrimson -> CyberThemeId.GHOST_CRIMSON
                R.id.chipThemeArctic -> CyberThemeId.ARCTIC_NEURAL
                R.id.chipThemePrince -> CyberThemeId.PRINCE_OF_PERSIA_SANDS
                else -> CyberThemeId.CYBERPUNK_CLASSIC
            }
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            viewModel.selectTheme(theme)
        }

        binding.btnResetWindowOrder.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playBeepProgress()
            viewModel.resetDashboardOrder()
        }

        val overlay = state.overlaySettings
        binding.switchOverlayEnabled.isChecked = overlay.overlayEnabled
        binding.cbShowDayReset.isChecked = overlay.showDayReset
        binding.cbShowPerformance.isChecked = overlay.showPerformanceTimers
        binding.cbShowCustom.isChecked = overlay.showCustomTimers
        binding.sliderMaxTimers.value = overlay.maxSimultaneousTimers.toFloat()
        binding.tvMaxTimersValue.text = overlay.maxSimultaneousTimers.toString()

        val feedback = state.feedbackSettings
        binding.switchSoundEnabled.isChecked = feedback.soundEffectsEnabled
        binding.switchHapticsEnabled.isChecked = feedback.hapticsEnabled

        refreshPermissionButton()

        binding.btnGrantOverlayPermission.setOnClickListener {
            onRequestOverlayPermission?.invoke()
        }

        binding.switchOverlayEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(requireContext())) {
                binding.switchOverlayEnabled.isChecked = false
                onRequestOverlayPermission?.invoke()
            } else {
                viewModel.updateOverlaySettings { it.copy(overlayEnabled = isChecked) }
                onOverlayToggleChanged?.invoke(isChecked)
            }
        }

        binding.cbShowDayReset.setOnCheckedChangeListener { _, checked ->
            viewModel.updateOverlaySettings { it.copy(showDayReset = checked) }
        }
        binding.cbShowPerformance.setOnCheckedChangeListener { _, checked ->
            viewModel.updateOverlaySettings { it.copy(showPerformanceTimers = checked) }
        }
        binding.cbShowCustom.setOnCheckedChangeListener { _, checked ->
            viewModel.updateOverlaySettings { it.copy(showCustomTimers = checked) }
        }
        binding.sliderMaxTimers.addOnChangeListener { _, value, _ ->
            val maxCount = value.toInt().coerceIn(1, 6)
            binding.tvMaxTimersValue.text = maxCount.toString()
            viewModel.updateOverlaySettings { it.copy(maxSimultaneousTimers = maxCount) }
        }

        binding.switchSoundEnabled.setOnCheckedChangeListener { _, checked ->
            viewModel.updateFeedbackSettings { it.copy(soundEffectsEnabled = checked) }
            if (checked) {
                CyberFeedbackManager.getInstance(requireContext()).playBeepProgress()
            }
        }

        binding.switchHapticsEnabled.setOnCheckedChangeListener { _, checked ->
            viewModel.updateFeedbackSettings { it.copy(hapticsEnabled = checked) }
            if (checked) {
                CyberFeedbackManager.getInstance(requireContext()).hapticDoubleConfirm()
            }
        }

        binding.btnCloseSettings.setOnClickListener {
            CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { ui ->
                    applyPalette(ui.palette)
                    renderWindowOrderList(ui.dashboardOrder, ui.palette)
                }
            }
        }
    }

    private fun renderWindowOrderList(order: List<DashboardSection>, palette: ResolvedCyberPalette) {
        val container = binding.windowOrderContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val monoFont = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono)
        val rajdhaniFont = ResourcesCompat.getFont(requireContext(), R.font.rajdhani)

        order.forEachIndexed { index, section ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 14f * density
                cardElevation = 0f
                strokeWidth = (1f * density).toInt()
                strokeColor = palette.subtleBorderColor
                setCardBackgroundColor(palette.backgroundColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6f * density).toInt()
                }
            }

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (12f * density).toInt(),
                    (6f * density).toInt(),
                    (6f * density).toInt(),
                    (6f * density).toInt()
                )
            }

            val rankBadge = TextView(requireContext()).apply {
                text = "#${index + 1}"
                typeface = monoFont
                textSize = 11f
                setTextColor(palette.primaryNeon)
                setPadding(0, 0, (10f * density).toInt(), 0)
            }

            val textCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTitle = TextView(requireContext()).apply {
                text = section.title
                typeface = rajdhaniFont
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(palette.textPrimary)
            }

            val tvSub = TextView(requireContext()).apply {
                text = section.subtitle
                typeface = rajdhaniFont
                textSize = 11.5f
                setTextColor(palette.textSecondary)
            }

            textCol.addView(tvTitle)
            textCol.addView(tvSub)

            val btnSize = (48f * density).toInt()
            val btnUp = ImageButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                setImageResource(R.drawable.ic_arrow_up)
                background = null
                contentDescription = "Subir prioridad de ${section.title}"
                isEnabled = index > 0
                alpha = if (index > 0) 1f else 0.3f
                imageTintList = ColorStateList.valueOf(palette.primaryNeon)
                setOnClickListener {
                    CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                    viewModel.moveDashboardSection(section, -1)
                }
            }

            val btnDown = ImageButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
                setImageResource(R.drawable.ic_arrow_down)
                background = null
                contentDescription = "Bajar prioridad de ${section.title}"
                isEnabled = index < order.lastIndex
                alpha = if (index < order.lastIndex) 1f else 0.3f
                imageTintList = ColorStateList.valueOf(palette.secondaryNeon)
                setOnClickListener {
                    CyberFeedbackManager.getInstance(requireContext()).playTerminalClick()
                    viewModel.moveDashboardSection(section, 1)
                }
            }

            row.addView(rankBadge)
            row.addView(textCol)
            row.addView(btnUp)
            row.addView(btnDown)
            card.addView(row)
            container.addView(card)
        }
    }

    private fun applyPalette(palette: ResolvedCyberPalette) {
        binding.settingsRootContainer.setBackgroundColor(palette.surfaceColor)
        binding.tvSettingsHeader.setTextColor(palette.primaryNeon)
        binding.btnResetWindowOrder.setTextColor(palette.secondaryNeon)
        binding.tvMaxTimersValue.setTextColor(palette.primaryNeon)
        binding.tvMaxTimersValue.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.btnCloseSettings.backgroundTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.btnCloseSettings.setTextColor(palette.onPrimaryColor)
        binding.btnGrantOverlayPermission.setTextColor(palette.secondaryNeon)
        binding.btnGrantOverlayPermission.strokeColor = ColorStateList.valueOf(palette.secondaryNeon)
        binding.sliderMaxTimers.thumbTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.sliderMaxTimers.trackActiveTintList = ColorStateList.valueOf(palette.primaryNeon)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionButton()
    }

    private fun refreshPermissionButton() {
        val granted = Settings.canDrawOverlays(requireContext())
        binding.btnGrantOverlayPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
