package com.gowthaman.callbkp

import org.junit.Assert.assertEquals
import org.junit.Test

class CallBackupTest {
    @Test
    fun roundTrip() {
        val calls = listOf(
            CallRecord("+15551234567", "Alice", 1_700_000_000_000, 42, 1),
            CallRecord("12345", null, 1_700_000_100_000, 0, 3),
        )
        assertEquals(calls, CallBackup.fromJson(CallBackup.toJson(calls)))
    }

    @Test
    fun emptyBackup() {
        assertEquals(emptyList<CallRecord>(), CallBackup.fromJson(CallBackup.toJson(emptyList())))
    }
}
