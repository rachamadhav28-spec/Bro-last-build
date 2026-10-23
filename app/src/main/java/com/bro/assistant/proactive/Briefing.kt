package com.bro.assistant.proactive

import android.app.AlarmManager
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BriefingData(
    val greeting: String,
    val timeString: String,
    val alarmStatus: String,
    val weatherStatus: String,
    val calendarSummary: String,
    val finishedTasksSummary: String,
    val proactiveTip: String
)

object Briefing {

    fun generateMorningBriefing(context: Context): BriefingData {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning, Madhav"
            hour < 17 -> "Good afternoon, Madhav"
            else -> "Good evening, Madhav"
        }

        val timeFmt = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.US)
        val timeStr = timeFmt.format(Date())

        val nextAlarm = readNextAlarm(context)
        val calendarNotes = CalendarReader.readUpcomingEvents(context)
        val tasksDone = Proactive.getFinishedTasksSummary(context)
        val nudge = Proactive.getDailyTacticalNudge()

        return BriefingData(
            greeting = greeting,
            timeString = timeStr,
            alarmStatus = nextAlarm,
            weatherStatus = "Local telemetry ready (enable Web Grounding for live forecast)",
            calendarSummary = calendarNotes.ifBlank { "No upcoming calendar events in the next 24h" },
            finishedTasksSummary = tasksDone,
            proactiveTip = nudge
        )
    }

    fun generateDailySummaryText(context: Context): String {
        val b = generateMorningBriefing(context)
        return buildString {
            appendLine("⚡ ${b.greeting} [${b.timeString}]")
            appendLine("• ALARM: ${b.alarmStatus}")
            appendLine("• WEATHER: ${b.weatherStatus}")
            appendLine("• CALENDAR: ${b.calendarSummary}")
            appendLine("• TASKS: ${b.finishedTasksSummary}")
            append("• NUDGE: ${b.proactiveTip}")
        }
    }

    private fun readNextAlarm(context: Context): String {
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val info = am?.nextAlarmClock
            if (info != null) {
                val fmt = SimpleDateFormat("EEE hh:mm a", Locale.US)
                "Next alarm set for ${fmt.format(Date(info.triggerTime))}"
            } else {
                "No active system alarm set"
            }
        } catch (_: Exception) {
            "Alarm status unavailable"
        }
    }
}