package com.gowthaman.callbkp

import org.json.JSONArray
import org.json.JSONObject

/** One call-log row, reduced to the fields worth restoring. */
data class CallRecord(
    val number: String,
    val name: String?,
    val date: Long,      // epoch millis
    val duration: Long,  // seconds
    val type: Int,       // CallLog.Calls.*_TYPE
)

/** Backup file format: {"version":1,"calls":[...]} */
object CallBackup {
    private const val VERSION = 1

    fun toJson(calls: List<CallRecord>): String {
        val arr = JSONArray()
        for (c in calls) {
            arr.put(
                JSONObject()
                    .put("number", c.number)
                    .put("name", c.name ?: JSONObject.NULL)
                    .put("date", c.date)
                    .put("duration", c.duration)
                    .put("type", c.type)
            )
        }
        return JSONObject().put("version", VERSION).put("calls", arr).toString(2)
    }

    fun fromJson(json: String): List<CallRecord> {
        val arr = JSONObject(json).getJSONArray("calls")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CallRecord(
                number = o.getString("number"),
                name = if (o.isNull("name")) null else o.getString("name"),
                date = o.getLong("date"),
                duration = o.optLong("duration", 0),
                type = o.getInt("type"),
            )
        }
    }
}
