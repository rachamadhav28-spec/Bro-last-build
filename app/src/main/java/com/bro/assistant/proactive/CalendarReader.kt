package com.bro.assistant.proactive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CalendarReader {

    fun readUpcomingEvents(context: Context, limit: Int = 3): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Calendar permission not granted yet"
        }

        return try {
            val now = System.currentTimeMillis()
            val projection = arrayOf(
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ?"
            val selectionArgs = arrayOf(now.toString())
            val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

            val events = mutableListOf<String>()
            val fmt = SimpleDateFormat("hh:mm a", Locale.US)

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
                val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)

                while (cursor.moveToNext() && events.size < limit) {
                    val title = if (titleIdx != -1) cursor.getString(titleIdx) ?: "Untitled" else "Event"
                    val startMs = if (startIdx != -1) cursor.getLong(startIdx) else now
                    events.add("$title (${fmt.format(Date(startMs))})")
                }
            }

            if (events.isEmpty()) "" else "Upcoming: ${events.joinToString(" | ")}"
        } catch (_: Exception) {
            ""
        }
    }
}