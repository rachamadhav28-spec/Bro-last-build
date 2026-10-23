package com.bro.assistant.proactive

import android.content.Context
import java.util.Calendar

data class BriefingData(
    val greeting: String,
    val timeString: String,
    val calendarSummary: String,
    val proactiveTip: String
)

object Briefing {
    fun generateMorningBriefing(context: Context): BriefingData {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning, Operator Madhav."
            hour < 17 -> "Good afternoon, Operator Madhav."
            else -> "Good evening, Operator Madhav."
        }

        val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR), cal.get(Calendar.MINUTE))
        val calendarNotes = CalendarReader.readUpcomingEvents(context)
        val tip = Proactive.getDailyTacticalNudge()

        return BriefingData(
            greeting = greeting,
            timeString = timeStr,
            calendarSummary = if (calendarNotes.isNotBlank()) calendarNotes else "No urgent calendar events scheduled.",
            proactiveTip = tip
        )
    }
}
