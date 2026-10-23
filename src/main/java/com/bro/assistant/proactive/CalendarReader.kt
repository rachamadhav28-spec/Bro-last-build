package com.bro.assistant.proactive

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract

object CalendarReader {
    fun readUpcomingEvents(context: Context): String {
        try {
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART
            )
            val cursor: Cursor? = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                null,
                null,
                CalendarContract.Events.DTSTART + " ASC LIMIT 3"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                    val title = if (titleIdx != -1) it.getString(titleIdx) else "Event"
                    return "Next Calendar Event: $title"
                }
            }
        } catch (_: Exception) {
            // Permission not granted or calendar unavailable
        }
        return ""
    }
}
