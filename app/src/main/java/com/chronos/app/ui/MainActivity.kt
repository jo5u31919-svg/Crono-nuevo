package com.chronos.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.chronos.app.R
import com.chronos.app.data.local.CustomTimerEntity
import com.chronos.app.data.local.DashboardSection
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.data.local.PerformanceCategory
import com.chronos.app.databinding.ActivityMainBinding
import com.chronos.app.databinding.ItemPerformanceTimerBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.service.FloatingOverlayService
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.adapter.CustomTimerAdapter
import com.chronos.app.ui.custom.CinematicVfxHelper
import com.chronos.app.ui.dialog.CustomTimerDialogFragment
import com.chronos.app.ui.dialog.NodePopupDialogFragment
import com.chronos.app.ui.dialog.SettingsDialogFragment
import com.chronos.app.ui.fragment.StatsFragment
import com.chronos.app.ui.fragment.TacticalCalendarFragment
import com.chronos.app.ui.viewmodel.ChronosUiState
import com.chronos.app.ui.viewmodel.ChronosViewModel
import com.chronos.app.ui.viewmodel.PerformanceCardUiModel
import com.chronos.app.widget.ChronosWidgetProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChronosViewModel by viewModels()
    private lateinit var customTimerAdapter: CustomTimerAdapter

    private var lastPaletteApplied: ResolvedCyberPalette? = null
    private var lastDashboardOrderApplied: List<DashboardSection>? = null
    private var lastQuoteTextApplied: String? = null
    private var activeNavItemId: Int = R.id.nav_home
    private val feedback by lazy { CyberFeedbackManager.getInstance(this) }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            viewModel.updateOverlaySettings { it.copy(overlayEnabled = true) }
            startOverlayWithNotificationCheck()
        } else {
            Toast.makeText(this, "Permiso de superposición requerido para la burbuja flotante", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        FloatingOverlayService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        setupRecyclerView()
        setupBottomNavigation(savedInstanceState)
        setupListeners()
        observeUiState()

        if (savedInstanceState == null) {
            binding.mainContentCoordinator.alpha = 0f
            binding.hackerSplashView.onSplashFinished = {
                binding.mainContentCoordinator.animate()
                    .alpha(1f)
                    .setDuration(420L)
                    .start()
            }
            binding.hackerSplashView.startBootSequence()
        } else {
            binding.hackerSplashView.visibility = View.GONE
            binding.mainContentCoordinator.alpha = 1f
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_ACTIVE_NAV_ID, activeNavItemId)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.refreshAppearance(this)
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainScrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                (92 * resources.displayMetrics.density).toInt()
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupBottomNavigation(savedInstanceState: Bundle?) {
        activeNavItemId = savedInstanceState?.getInt(KEY_ACTIVE_NAV_ID, R.id.nav_home) ?: R.id.nav_home

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeNavItemId != R.id.nav_home) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    if (activeNavItemId != R.id.nav_home) feedback.playTerminalClick()
                    switchToHomeTab()
                    true
                }
                R.id.nav_stats -> {
                    if (activeNavItemId != R.id.nav_stats) feedback.playTerminalClick()
                    switchToStatsTab()
                    true
                }
                R.id.nav_calendar -> {
                    if (activeNavItemId != R.id.nav_calendar) feedback.playTerminalClick()
                    switchToCalendarTab()
                    true
                }
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = activeNavItemId
        when (activeNavItemId) {
            R.id.nav_stats -> switchToStatsTab()
            R.id.nav_calendar -> switchToCalendarTab()
            else -> switchToHomeTab()
        }
    }

    private fun switchToHomeTab() {
        activeNavItemId = R.id.nav_home
        binding.fragmentContainer.visibility = View.GONE
        binding.mainScrollView.visibility = View.VISIBLE
        binding.fabAddTimer.show()
    }

    private fun switchToStatsTab() {
        activeNavItemId = R.id.nav_stats
        binding.mainScrollView.visibility = View.GONE
        binding.fabAddTimer.hide()
        binding.fragmentContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, StatsFragment(), TAG_STATS_FRAGMENT)
            .commit()
    }

    private fun switchToCalendarTab() {
        activeNavItemId = R.id.nav_calendar
        binding.mainScrollView.visibility = View.GONE
        binding.fabAddTimer.hide()
        binding.fragmentContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, TacticalCalendarFragment(), TAG_CALENDAR_FRAGMENT)
            .commit()
    }

    private fun setupRecyclerView() {
        customTimerAdapter = CustomTimerAdapter(
            onNodeClick = { node ->
                openNodePopup(node.timer.id)
            },
            onHabitCycleClick = { node ->
                viewModel.cycleCustomTimerHabitState(node.timer.id)
            }
        )
        binding.rvCustomTimers.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = customTimerAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupListeners() {
        binding.cardMotivationalQuote.setOnClickListener {
            feedback.playTerminalClick()
            CinematicVfxHelper.playTacticalPressBurst(it, binding.sandsParticleVfx)
            viewModel.rotateQuote(1)
        }

        binding.laserTimelineView.onNodeSelected = { node ->
            openNodePopup(node.timer.id)
        }

        binding.fabAddTimer.setOnClickListener {
            feedback.playTerminalClick()
            CinematicVfxHelper.playTacticalPressBurst(it, binding.sandsParticleVfx)
            openCreateOrEditCustomTimer(null)
        }

        binding.btnSettings.setOnClickListener {
            feedback.playTerminalClick()
            CinematicVfxHelper.playTacticalPressBurst(it, binding.sandsParticleVfx)
            openSettingsDialog()
        }

        binding.cardOverclockLauncher.setOnClickListener {
            feedback.playBeepProgress()
            CinematicVfxHelper.playTacticalPressBurst(it, binding.sandsParticleVfx)
            launchOverclockMode()
        }

        binding.btnFloatingToggle.setOnClickListener {
            val currentlyEnabled = viewModel.uiState.value.overlaySettings.overlayEnabled
            if (currentlyEnabled) {
                feedback.playTerminalClick()
                viewModel.updateOverlaySettings { it.copy(overlayEnabled = false) }
                FloatingOverlayService.stop(this)
                Toast.makeText(this, "Burbuja flotante desactivada", Toast.LENGTH_SHORT).show()
            } else {
                feedback.playBeepProgress()
                if (Settings.canDrawOverlays(this)) {
                    viewModel.updateOverlaySettings { it.copy(overlayEnabled = true) }
                    startOverlayWithNotificationCheck()
                } else {
                    requestOverlayPermission()
                }
            }
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: ChronosUiState) {
        val palette = state.palette
        if (lastPaletteApplied != palette) {
            lastPaletteApplied = palette
            applyThemePalette(palette)
            ChronosWidgetProvider.requestRefresh(this)
        }

        if (lastDashboardOrderApplied != state.dashboardOrder) {
            lastDashboardOrderApplied = state.dashboardOrder
            applyDashboardOrder(state.dashboardOrder)
        }

        val quoteText = state.currentQuote.text
        val authorText = "— ${state.currentQuote.author} · ${state.currentQuote.role}"
        if (lastQuoteTextApplied == null) {
            lastQuoteTextApplied = quoteText
            binding.tvMotivationalQuote.text = quoteText
            binding.tvMotivationalAuthor.text = authorText
        } else if (lastQuoteTextApplied != quoteText) {
            lastQuoteTextApplied = quoteText
            CinematicVfxHelper.animateSmoothTextTransition(
                binding.tvMotivationalQuote,
                binding.tvMotivationalAuthor
            ) {
                binding.tvMotivationalQuote.text = quoteText
                binding.tvMotivationalAuthor.text = authorText
            }
        }
        binding.progressQuoteRotation.progress = (state.quoteRotationProgress * 100f).toInt().coerceIn(5, 100)
        binding.tvDayPhaseBadge.text = "FASE: ${state.dayPhase.label}"
        binding.tvDayResetCountdown.text = state.dayResetFormatted

        binding.laserTimelineView.updateTimeline(
            progress = state.dayProgressFraction,
            phase = state.dayPhase,
            nodes = state.projectedNodes,
            palette = palette
        )

        val overlayTint = if (state.overlaySettings.overlayEnabled) {
            palette.secondaryNeon
        } else {
            palette.primaryNeon
        }
        binding.btnFloatingToggle.imageTintList = ColorStateList.valueOf(overlayTint)

        val cardBindings = mapOf(
            PerformanceCategory.PHYSICAL to binding.cardPhysical,
            PerformanceCategory.MENTAL to binding.cardMental,
            PerformanceCategory.EXPRESSIVE to binding.cardExpressive,
            PerformanceCategory.OCCUPATIONAL to binding.cardOccupational
        )
        state.performanceCards.forEach { model ->
            val cardBinding = cardBindings[model.category] ?: return@forEach
            bindPerformanceCard(cardBinding, model, palette)
        }

        customTimerAdapter.submitList(state.projectedNodes)
        val activeNodesCount = state.projectedNodes.count { it.isCurrentlyActiveWindow }
        binding.tvCustomNodeCount.text = "${state.projectedNodes.size} NODOS · $activeNodesCount ACTIVO"
        binding.tvEmptyCustomTimers.visibility = if (state.projectedNodes.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCustomTimers.visibility = if (state.projectedNodes.isEmpty()) View.GONE else View.VISIBLE

        binding.tvOverclockPointsBadge.text = "${state.overclockSummary.totalPoints} PTS"
    }

    private fun applyDashboardOrder(order: List<DashboardSection>) {
        val sectionViews = mapOf(
            DashboardSection.MOTIVATIONAL_QUOTES to binding.sectionQuotes,
            DashboardSection.DAY_RESET_LASER to binding.sectionDayLaser,
            DashboardSection.OVERCLOCK_LAUNCHER to binding.sectionOverclock,
            DashboardSection.PERFORMANCE_DIMENSIONS to binding.sectionDimensions,
            DashboardSection.CUSTOM_NODES to binding.sectionCustomNodes
        )

        val container = binding.dashboardContainer
        android.transition.TransitionManager.beginDelayedTransition(container)
        // Keep headerBar at index 0, detach and reattach ordered sections after it
        sectionViews.values.forEach { container.removeView(it) }
        order.forEach { section ->
            sectionViews[section]?.let { container.addView(it) }
        }
    }

    private fun bindPerformanceCard(
        card: ItemPerformanceTimerBinding,
        model: PerformanceCardUiModel,
        palette: ResolvedCyberPalette
    ) {
        val cat = model.category
        val isRunning = model.entity.isRunning

        card.ivPerfIcon.setImageResource(cat.iconRes)
        card.tvPerfName.text = cat.title.uppercase()
        card.tvPerfName.setTextColor(palette.textPrimary)

        val accentColor = when (cat) {
            PerformanceCategory.PHYSICAL -> palette.secondaryNeon
            PerformanceCategory.MENTAL -> palette.primaryNeon
            PerformanceCategory.EXPRESSIVE -> palette.tertiaryNeon
            PerformanceCategory.OCCUPATIONAL -> palette.primaryNeon
        }

        card.ivPerfIcon.imageTintList = ColorStateList.valueOf(accentColor)
        card.badgePerfIcon.backgroundTintList = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 40 else 28, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
        )
        card.tvPerfCountdown.text = ChronosTimeEngine.formatDurationMs(model.liveRemainingSeconds)
        card.tvPerfCountdown.setTextColor(if (isRunning) accentColor else palette.textPrimary)

        card.tvPerfStatus.text = if (isRunning) "ACTIVO" else cat.subtitle.uppercase()
        card.tvPerfStatus.setTextColor(if (isRunning) accentColor else palette.textSecondary)

        card.progressPerf.progress = (model.progressFraction * 1000).toInt()
        card.progressPerf.progressTintList = ColorStateList.valueOf(accentColor)
        card.progressPerf.progressBackgroundTintList = ColorStateList.valueOf(palette.surfaceElevatedColor)

        card.cardPerfRoot.setCardBackgroundColor(palette.glassSurfaceColor)
        CinematicVfxHelper.attachCardCinematicPulse(
            card = card.cardPerfRoot,
            palette = palette,
            baseStrokeColor = if (isRunning) accentColor else palette.rimHighlightColor,
            elevationDp = 12f
        )

        card.btnPerfPlayPause.setImageResource(if (isRunning) R.drawable.ic_pause else R.drawable.ic_play)
        card.btnPerfPlayPause.imageTintList = ColorStateList.valueOf(accentColor)
        card.btnPerfPlayPause.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        card.btnPerfReset.imageTintList = ColorStateList.valueOf(palette.textSecondary)
        card.btnPerfConfig.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)

        card.btnPerfPlayPause.setOnClickListener {
            feedback.playBeepProgress()
            CinematicVfxHelper.playTacticalPressBurst(card.cardPerfRoot, binding.sandsParticleVfx)
            viewModel.togglePerformanceTimer(cat)
            ChronosWidgetProvider.requestRefresh(this)
        }
        card.btnPerfReset.setOnClickListener {
            feedback.playTerminalClick()
            viewModel.resetPerformanceTimer(cat)
            ChronosWidgetProvider.requestRefresh(this)
        }
        card.btnPerfConfig.setOnClickListener {
            feedback.playTerminalClick()
            showPerformanceDurationDialog(cat, (model.entity.totalSeconds / 60L).toInt())
        }

        // Anchored Custom Nodes Container inside Dimension Card
        card.anchoredNodesDivider.setBackgroundColor(palette.subtleBorderColor)
        card.tvAnchoredHeader.setTextColor(palette.textMuted)
        card.tvAnchoredCount.text = model.anchoredNodes.size.toString()
        card.tvAnchoredCount.setTextColor(accentColor)
        card.btnAnchorNode.imageTintList = ColorStateList.valueOf(accentColor)
        card.btnAnchorNode.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        card.btnAnchorNode.setOnClickListener {
            feedback.playTerminalClick()
            CinematicVfxHelper.playTacticalPressBurst(it, binding.sandsParticleVfx)
            openCreateOrEditCustomTimer(null, defaultDimension = cat)
        }

        val container = card.anchoredNodesContainer
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val monoFont = ResourcesCompat.getFont(this, R.font.jetbrains_mono)
        val rajdhaniFont = ResourcesCompat.getFont(this, R.font.rajdhani)

        if (model.anchoredNodes.isEmpty()) {
            val tvAddAnchored = TextView(this).apply {
                text = "Anclar nodo ${cat.title.lowercase()}"
                typeface = monoFont
                textSize = 9.5f
                setTextColor(accentColor)
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_anchor_node, 0, 0, 0)
                compoundDrawablePadding = (6f * density).toInt()
                compoundDrawableTintList = ColorStateList.valueOf(accentColor)
                setPadding(
                    (10f * density).toInt(),
                    (6f * density).toInt(),
                    (10f * density).toInt(),
                    (6f * density).toInt()
                )
                minimumHeight = (38f * density).toInt()
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_harmony_anchor_pill)
                backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
                setOnClickListener {
                    feedback.playTerminalClick()
                    CinematicVfxHelper.playTacticalPressBurst(it, binding.sandsParticleVfx)
                    openCreateOrEditCustomTimer(null, defaultDimension = cat)
                }
            }
            container.addView(tvAddAnchored)
        } else {
            model.anchoredNodes.forEach { node ->
                val hState = node.timer.parsedHabitState()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (4f * density).toInt()
                    }
                    setPadding(
                        (4f * density).toInt(),
                        (3f * density).toInt(),
                        (6f * density).toInt(),
                        (3f * density).toInt()
                    )
                    background = GradientDrawable().apply {
                        cornerRadius = 10f * density
                        setColor(palette.backgroundColor)
                        setStroke((1f * density).toInt(), palette.subtleBorderColor)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        feedback.playTerminalClick()
                        openNodePopup(node.timer.id)
                    }
                }

                val (iconRes, iconTint) = when (hState) {
                    HabitCycleState.COMPLETED -> R.drawable.ic_habit_check to palette.primaryNeon
                    HabitCycleState.INCOMPLETE -> R.drawable.ic_habit_incomplete to palette.tertiaryNeon
                    HabitCycleState.NONE -> R.drawable.ic_habit_none to palette.textMuted
                }
                val btnHabit = ImageButton(this).apply {
                    val btnSz = (36f * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(btnSz, btnSz)
                    setImageResource(iconRes)
                    imageTintList = ColorStateList.valueOf(iconTint)
                    background = null
                    contentDescription = "Ciclar hábito ${node.timer.name}"
                    setOnClickListener {
                        when (hState.next()) {
                            HabitCycleState.COMPLETED -> feedback.playSuccessChime(withHaptic = true)
                            HabitCycleState.INCOMPLETE -> feedback.playErrorGlitch()
                            HabitCycleState.NONE -> feedback.playTerminalClick()
                        }
                        viewModel.cycleCustomTimerHabitState(node.timer.id)
                    }
                }

                val textCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = (4f * density).toInt()
                    }
                }

                val tvName = TextView(this).apply {
                    text = node.timer.name
                    typeface = rajdhaniFont
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(palette.textPrimary)
                }

                val countdownCompact = ChronosTimeEngine.formatDurationHms(node.liveRemainingSeconds)
                val statusShort = if (node.isCurrentlyActiveWindow) {
                    "> CONTEO ACTIVADO · $countdownCompact"
                } else {
                    "> SE ACTIVA EN: ${ChronosTimeEngine.formatMinuteOfDay(node.activationMinuteOfDay)} · $countdownCompact"
                }
                val tvStatus = TextView(this).apply {
                    text = statusShort
                    typeface = monoFont
                    textSize = 8.5f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(if (node.isCurrentlyActiveWindow) palette.secondaryNeon else accentColor)
                }

                textCol.addView(tvName)
                textCol.addView(tvStatus)

                row.addView(btnHabit)
                row.addView(textCol)
                container.addView(row)
            }
        }
    }

    private fun showPerformanceDurationDialog(category: PerformanceCategory, currentMinutes: Int) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentMinutes.toString())
            setSelection(text.length)
            setPadding(48, 32, 48, 24)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Duración: ${category.title}")
            .setMessage("Define los minutos para ${category.subtitle}:")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aplicar") { _, _ ->
                val minutes = input.text.toString().toIntOrNull()?.coerceIn(1, 360)
                if (minutes != null) {
                    feedback.playBeepProgress(withHaptic = false)
                    feedback.hapticDoubleConfirm()
                    viewModel.updatePerformanceDuration(category, minutes)
                } else {
                    feedback.playErrorGlitch()
                }
            }
            .show()
    }

    private fun openCreateOrEditCustomTimer(
        existing: CustomTimerEntity?,
        defaultDimension: PerformanceCategory? = null
    ) {
        val dialog = CustomTimerDialogFragment.newInstance(existing, defaultDimension).apply {
            onSaveTimer = { id, name, startMin, duration, daysMask, resetPeriod, notes, dimension ->
                feedback.playSuccessChime(withHaptic = true)
                viewModel.saveCustomTimer(
                    id = id,
                    name = name,
                    startMinuteOfDay = startMin,
                    durationMinutes = duration,
                    activeDaysMask = daysMask,
                    resetPeriod = resetPeriod,
                    privateNotes = notes,
                    dimension = dimension
                )
                ChronosWidgetProvider.requestRefresh(this@MainActivity)
            }
        }
        dialog.show(supportFragmentManager, "custom_timer_dialog")
    }

    private fun openNodePopup(timerId: Long) {
        val popup = NodePopupDialogFragment.newInstance(timerId).apply {
            onEditRequested = { entity ->
                openCreateOrEditCustomTimer(entity)
            }
        }
        popup.show(supportFragmentManager, "node_popup_dialog")
    }

    private fun openSettingsDialog() {
        val dialog = SettingsDialogFragment().apply {
            onRequestOverlayPermission = {
                requestOverlayPermission()
            }
            onOverlayToggleChanged = { enabled ->
                if (enabled) {
                    startOverlayWithNotificationCheck()
                } else {
                    FloatingOverlayService.stop(this@MainActivity)
                }
            }
        }
        dialog.show(supportFragmentManager, "settings_dialog")
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun launchOverclockMode() {
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            R.anim.cyber_hack_enter,
            R.anim.cyber_hack_exit
        )
        startActivity(OverclockActivity.createIntent(this), options.toBundle())
    }

    private fun startOverlayWithNotificationCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            FloatingOverlayService.start(this)
        }
    }

    private fun applyThemePalette(palette: ResolvedCyberPalette) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !palette.isDark
            isAppearanceLightNavigationBars = !palette.isDark
        }

        binding.rootContainer.setBackgroundColor(palette.backgroundColor)
        binding.ambientAuraBackdrop.background = createHarmonyAmbientBackground(palette)
        CinematicVfxHelper.applyAmbientBlurEffectIfSupported(binding.ambientAuraBackdrop, enabled = true)
        binding.sandsParticleVfx.applyPalette(palette)
        binding.hackerSplashView.applyTheme(palette)

        val isPrinceTheme = palette.themeId == CyberThemeId.PRINCE_OF_PERSIA_SANDS
        binding.tvSystemTag.text = if (isPrinceTheme) {
            "CHRONOS // DAGAS DEL TIEMPO"
        } else {
            getString(R.string.cyber_header_tag)
        }
        binding.ivOverclockBolt.setImageResource(
            if (isPrinceTheme) R.drawable.ic_dagger_time else R.drawable.ic_bolt
        )

        binding.tvSystemTag.setTextColor(palette.primaryNeon)
        binding.tvSystemTag.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvAppTitle.setTextColor(palette.textPrimary)
        binding.tvDayResetTitle.setTextColor(palette.textSecondary)
        binding.tvDayResetCountdown.setTextColor(palette.primaryNeon)
        binding.tvTimelineHint.setTextColor(palette.textMuted)
        binding.tvQuoteBadge.setTextColor(palette.secondaryNeon)
        binding.tvDayPhaseBadge.setTextColor(palette.primaryNeon)
        binding.tvDayPhaseBadge.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvMotivationalQuote.setTextColor(palette.textPrimary)
        binding.tvMotivationalAuthor.setTextColor(palette.primaryNeon)
        binding.progressQuoteRotation.progressTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.progressQuoteRotation.progressBackgroundTintList = ColorStateList.valueOf(palette.surfaceElevatedColor)
        binding.tvPerformanceSectionTitle.setTextColor(palette.textPrimary)
        binding.tvCustomSectionTitle.setTextColor(palette.textPrimary)
        binding.tvCustomNodeCount.setTextColor(palette.primaryNeon)
        binding.tvCustomNodeCount.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvEmptyCustomTimers.setTextColor(palette.textSecondary)

        binding.cardMotivationalQuote.setCardBackgroundColor(palette.glassSurfaceColor)
        CinematicVfxHelper.attachCardCinematicPulse(binding.cardMotivationalQuote, palette, palette.rimHighlightColor)

        binding.cardDayLaser.setCardBackgroundColor(palette.glassSurfaceColor)
        CinematicVfxHelper.attachCardCinematicPulse(binding.cardDayLaser, palette, palette.rimHighlightColor)

        binding.cardOverclockLauncher.setCardBackgroundColor(palette.glassSurfaceColor)
        CinematicVfxHelper.attachCardCinematicPulse(binding.cardOverclockLauncher, palette, palette.secondaryNeon)
        binding.ivOverclockBolt.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)
        binding.badgeOverclockIcon.backgroundTintList = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 42 else 28, Color.red(palette.secondaryNeon), Color.green(palette.secondaryNeon), Color.blue(palette.secondaryNeon))
        )
        binding.tvOverclockCardTitle.setTextColor(palette.textPrimary)
        binding.tvOverclockCardSubtitle.setTextColor(palette.textSecondary)
        binding.tvOverclockActionPill.setTextColor(palette.secondaryNeon)
        binding.tvOverclockActionPill.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.tvOverclockPointsBadge.setTextColor(palette.primaryNeon)

        binding.btnFloatingToggle.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.btnSettings.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)
        binding.btnSettings.imageTintList = ColorStateList.valueOf(palette.secondaryNeon)

        binding.fabAddTimer.backgroundTintList = ColorStateList.valueOf(palette.primaryNeon)
        binding.fabAddTimer.setTextColor(palette.onPrimaryColor)
        binding.fabAddTimer.iconTint = ColorStateList.valueOf(palette.onPrimaryColor)

        binding.bottomNavigation.setBackgroundColor(palette.surfaceColor)
        binding.bottomNavDivider.setBackgroundColor(palette.rimHighlightColor)
        val navStates = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val navColors = intArrayOf(palette.primaryNeon, palette.textMuted)
        val navColorStateList = ColorStateList(navStates, navColors)
        binding.bottomNavigation.itemIconTintList = navColorStateList
        binding.bottomNavigation.itemTextColor = navColorStateList
        binding.bottomNavigation.itemRippleColor = ColorStateList.valueOf(
            Color.argb(if (palette.isDark) 44 else 28, Color.red(palette.primaryNeon), Color.green(palette.primaryNeon), Color.blue(palette.primaryNeon))
        )

        customTimerAdapter.applyPalette(palette)
    }

    private fun createHarmonyAmbientBackground(palette: ResolvedCyberPalette): LayerDrawable {
        val density = resources.displayMetrics.density
        val base = ColorDrawable(palette.backgroundColor)
        val primaryAlpha = if (palette.isDark) 42 else 30
        val secondaryAlpha = if (palette.isDark) 34 else 24

        val primaryAura = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.2f, 0.12f)
            gradientRadius = 420f * density
            colors = intArrayOf(
                Color.argb(primaryAlpha, Color.red(palette.primaryNeon), Color.green(palette.primaryNeon), Color.blue(palette.primaryNeon)),
                Color.TRANSPARENT
            )
        }

        val secondaryAura = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.85f, 0.72f)
            gradientRadius = 380f * density
            colors = intArrayOf(
                Color.argb(secondaryAlpha, Color.red(palette.secondaryNeon), Color.green(palette.secondaryNeon), Color.blue(palette.secondaryNeon)),
                Color.TRANSPARENT
            )
        }

        return LayerDrawable(arrayOf(base, primaryAura, secondaryAura))
    }

    companion object {
        private const val KEY_ACTIVE_NAV_ID = "active_nav_item_id"
        private const val TAG_STATS_FRAGMENT = "chronos_stats_fragment"
        private const val TAG_CALENDAR_FRAGMENT = "chronos_calendar_fragment"
    }
}
