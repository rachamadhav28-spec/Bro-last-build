package com.bro.assistant.proactive

import android.content.Context
import java.util.Calendar

object Briefing {
    fun generateMorningBriefing(context: Context): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = if (hour < 12) "Good morning, Madhav." else "Good evening, Madhav."
        return "$greeting All systems green."
    }
}
