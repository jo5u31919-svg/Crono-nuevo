package com.chronos.app.data.local

import androidx.annotation.DrawableRes
import com.chronos.app.R

enum class PerformanceCategory(
    val title: String,
    val subtitle: String,
    val defaultDurationMinutes: Int,
    @DrawableRes val iconRes: Int
) {
    PHYSICAL(
        title = "Físico",
        subtitle = "Activación Física",
        defaultDurationMinutes = 30,
        iconRes = R.drawable.ic_fitness
    ),
    MENTAL(
        title = "Mental",
        subtitle = "Enfoque Neural",
        defaultDurationMinutes = 45,
        iconRes = R.drawable.ic_brain
    ),
    EXPRESSIVE(
        title = "Expresivo",
        subtitle = "Flujo Creativo",
        defaultDurationMinutes = 25,
        iconRes = R.drawable.ic_palette
    ),
    OCCUPATIONAL(
        title = "Ocupacional",
        subtitle = "Ejecución Táctica",
        defaultDurationMinutes = 60,
        iconRes = R.drawable.ic_work
    );

    companion object {
        fun fromName(name: String?): PerformanceCategory {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MENTAL
        }
    }
}
