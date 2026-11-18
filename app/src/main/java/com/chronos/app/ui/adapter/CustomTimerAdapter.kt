package com.chronos.app.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chronos.app.R
import com.chronos.app.data.local.HabitCycleState
import com.chronos.app.databinding.ItemCustomTimerSummaryBinding
import com.chronos.app.domain.ChronosTimeEngine
import com.chronos.app.domain.ProjectedTimelineNode
import com.chronos.app.feedback.CyberFeedbackManager
import com.chronos.app.theme.CyberThemeId
import com.chronos.app.theme.ResolvedCyberPalette
import com.chronos.app.ui.custom.CinematicVfxHelper

class CustomTimerAdapter(
    private val onNodeClick: (ProjectedTimelineNode) -> Unit,
    private val onHabitCycleClick: (ProjectedTimelineNode) -> Unit = {}
) : ListAdapter<ProjectedTimelineNode, CustomTimerAdapter.NodeViewHolder>(DiffCallback) {

    private var currentPalette: ResolvedCyberPalette = CyberThemeId.PRINCE_OF_PERSIA_SANDS.resolve(true)

    fun applyPalette(palette: ResolvedCyberPalette) {
        currentPalette = palette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeViewHolder {
        val binding = ItemCustomTimerSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NodeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NodeViewHolder, position: Int) {
        holder.bind(getItem(position), currentPalette, onNodeClick, onHabitCycleClick)
    }

    class NodeViewHolder(
        private val binding: ItemCustomTimerSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            node: ProjectedTimelineNode,
            palette: ResolvedCyberPalette,
            onNodeClick: (ProjectedTimelineNode) -> Unit,
            onHabitCycleClick: (ProjectedTimelineNode) -> Unit
        ) {
            val timer = node.timer
            val dimension = timer.parsedDimension()
            val habitState = timer.parsedHabitState()

            binding.tvCustomName.text = timer.name
            binding.tvCustomName.setTextColor(palette.textPrimary)

            binding.tvCustomDimensionBadge.text = dimension.title.uppercase()
            binding.tvCustomDimensionBadge.setTextColor(palette.primaryNeon)
            binding.ivCustomAnchorIcon.imageTintList = ColorStateList.valueOf(palette.primaryNeon)
            binding.pillDimensionAnchor.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

            binding.tvCustomActivationStatus.text = ChronosTimeEngine.formatNodeActivationStatus(node)
            val statusColor = if (node.isCurrentlyActiveWindow) palette.secondaryNeon else palette.primaryNeon
            binding.tvCustomActivationStatus.setTextColor(statusColor)

            val periodLabel = timer.parsedResetPeriod().label
            val daysSummary = timer.formatDaysSummary()
            binding.tvCustomSchedule.text = "${timer.durationMinutes} min · $periodLabel · $daysSummary"
            binding.tvCustomSchedule.setTextColor(palette.textSecondary)

            binding.tvCustomCountdown.text = ChronosTimeEngine.formatDurationHms(node.liveRemainingSeconds)
            val accent = if (node.isCurrentlyActiveWindow) palette.secondaryNeon else palette.primaryNeon
            binding.tvCustomCountdown.setTextColor(accent)
            binding.tvCustomCountdown.backgroundTintList = ColorStateList.valueOf(palette.capsuleFillColor)

            val (habitIconRes, habitTint) = when (habitState) {
                HabitCycleState.COMPLETED -> R.drawable.ic_habit_check to palette.primaryNeon
                HabitCycleState.INCOMPLETE -> R.drawable.ic_habit_incomplete to palette.tertiaryNeon
                HabitCycleState.NONE -> R.drawable.ic_habit_none to palette.textMuted
            }
            binding.btnHabitCycle.setImageResource(habitIconRes)
            binding.btnHabitCycle.imageTintList = ColorStateList.valueOf(habitTint)
            binding.btnHabitCycle.contentDescription = "Estado hábito ${timer.name}: ${habitState.label}"
            binding.btnHabitCycle.setOnClickListener {
                val feedback = CyberFeedbackManager.getInstance(binding.root.context)
                when (habitState.next()) {
                    HabitCycleState.COMPLETED -> feedback.playSuccessChime(withHaptic = true)
                    HabitCycleState.INCOMPLETE -> feedback.playErrorGlitch()
                    HabitCycleState.NONE -> feedback.playTerminalClick()
                }
                CinematicVfxHelper.playTacticalPressBurst(it)
                onHabitCycleClick(node)
            }

            val stroke = when {
                habitState == HabitCycleState.COMPLETED -> palette.primaryNeon
                habitState == HabitCycleState.INCOMPLETE -> palette.tertiaryNeon
                node.isCurrentlyActiveWindow -> palette.secondaryNeon
                else -> palette.rimHighlightColor
            }
            binding.cardCustomItem.setCardBackgroundColor(palette.glassSurfaceColor)
            CinematicVfxHelper.attachCardCinematicPulse(
                card = binding.cardCustomItem,
                palette = palette,
                baseStrokeColor = stroke,
                elevationDp = 10f
            )
            binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(accent)

            binding.root.setOnClickListener {
                CyberFeedbackManager.getInstance(binding.root.context).playTerminalClick()
                CinematicVfxHelper.playTacticalPressBurst(it)
                onNodeClick(node)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProjectedTimelineNode>() {
        override fun areItemsTheSame(
            oldItem: ProjectedTimelineNode,
            newItem: ProjectedTimelineNode
        ): Boolean = oldItem.timer.id == newItem.timer.id

        override fun areContentsTheSame(
            oldItem: ProjectedTimelineNode,
            newItem: ProjectedTimelineNode
        ): Boolean = oldItem == newItem
    }
}
