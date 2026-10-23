package com.bro.assistant.proactive

import android.content.Context
import com.bro.assistant.security.SecretBox

object Proactive {

    private val tacticalNudges = listOf(
        "Use FAST skill (Groq/Cerebras) for sub-second answers and CODE skill (Claude/Qwen) for Kotlin.",
        "Tap WEB: ON in the HUD bar whenever you want live Google Search grounding on Claude or Gemini.",
        "Switch animations anytime in the 104 LAB tab—your choices auto-save in SecretBox.",
        "Long AI job taking too long? Hit the red STOP button in the composer to cancel the HTTP stream immediately."
    )

    fun getDailyTacticalNudge(): String {
        return tacticalNudges.random()
    }

    fun recordFinishedTask(context: Context, taskTitle: String) {
        val current = SecretBox.getSetting(context, "finished_tasks", "")
        val updated = if (current.isBlank()) taskTitle else "$current|$taskTitle"
        SecretBox.saveSetting(context, "finished_tasks", updated)
    }

    fun getFinishedTasksSummary(context: Context): String {
        val raw = SecretBox.getSetting(context, "finished_tasks", "")
        if (raw.isBlank()) return "0 tasks logged today — ready for commands"
        val items = raw.split("|").filter { it.isNotBlank() }
        return "${items.size} finished (${items.takeLast(3).joinToString(", ")})"
    }
}