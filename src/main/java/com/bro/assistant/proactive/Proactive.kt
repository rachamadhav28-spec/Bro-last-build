package com.bro.assistant.proactive

object Proactive {
    private val nudges = listOf(
        "Tactical reminder: Push your latest changes with git pull --rebase to keep GitHub Actions green.",
        "Keep your Claude and OpenRouter keys secure in the encrypted SecretBox vault.",
        "Use the 5 skill modes (CODE, FAST, LONG, REASON, GENERAL) to route queries efficiently.",
        "Monitor your build artifacts via GitHub Actions to ensure build #183+ stays green."
    )

    fun getDailyTacticalNudge(): String {
        return nudges.random()
    }
}
