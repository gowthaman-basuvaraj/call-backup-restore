package com.gowthaman.callbkp

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.view.Gravity
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Two buttons: Backup writes the whole call log to a JSON file the user picks;
 * Restore reads such a file back, skipping calls that are already in the log.
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var buttons: List<Button>
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, pad, 0, 0)
            text = "Back up your call history to a file, or restore it from one."
        }
        val backup = Button(this).apply {
            text = "Backup call log"
            setOnClickListener { withPermissions(::pickBackupFile) }
        }
        val restore = Button(this).apply {
            text = "Restore call log"
            setOnClickListener { withPermissions(::pickRestoreFile) }
        }
        buttons = listOf(backup, restore)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(backup, LinearLayout.LayoutParams(-1, -2))
            addView(restore, LinearLayout.LayoutParams(-1, -2))
            addView(status)
            // targetSdk 35+ is edge-to-edge: keep content clear of the system bars.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
                insets
            }
        }
        setContentView(root)
    }

    // --- permissions ---------------------------------------------------------

    private val perms = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG)

    private fun withPermissions(action: () -> Unit) {
        if (perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            action()
        } else {
            pendingAction = action
            requestPermissions(perms, REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, results: IntArray) {
        if (code != REQ_PERMS) return
        val action = pendingAction
        pendingAction = null
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            action?.invoke()
        } else {
            status.text = "Call log permission is required."
        }
    }

    // --- file pickers --------------------------------------------------------

    private fun pickBackupFile() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "calls-$stamp.json"),
            REQ_BACKUP
        )
    }

    private fun pickRestoreFile() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"),
            REQ_RESTORE
        )
    }

    @Deprecated("Framework Activity API; fine without androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_BACKUP -> runInBackground("Backing up…") { backup(uri) }
            REQ_RESTORE -> runInBackground("Restoring…") { restore(uri) }
        }
    }

    private fun runInBackground(busy: String, work: () -> String) {
        status.text = busy
        buttons.forEach { it.isEnabled = false }
        Thread {
            val msg = try { work() } catch (e: Exception) { "Failed: ${e.message}" }
            runOnUiThread {
                status.text = msg
                buttons.forEach { it.isEnabled = true }
            }
        }.start()
    }

    // --- the actual work -----------------------------------------------------

    private fun readCallLog(): List<CallRecord> {
        val cols = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls.TYPE,
        )
        val out = mutableListOf<CallRecord>()
        contentResolver.query(CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    out += CallRecord(
                        number = c.getString(0) ?: "",
                        name = c.getString(1),
                        date = c.getLong(2),
                        duration = c.getLong(3),
                        type = c.getInt(4),
                    )
                }
            }
        return out
    }

    private fun backup(uri: Uri): String {
        val calls = readCallLog()
        contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
            it.write(CallBackup.toJson(calls))
        }
        return "Backed up ${calls.size} calls."
    }

    private fun restore(uri: Uri): String {
        val json = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val incoming = CallBackup.fromJson(json)
        // A call is "already there" if the same number has an entry at the same instant.
        val existing = readCallLog().mapTo(HashSet()) { it.date to it.number }
        val toInsert = incoming.filter { (it.date to it.number) !in existing }
        val values = toInsert.map { c ->
            ContentValues().apply {
                put(CallLog.Calls.NUMBER, c.number)
                put(CallLog.Calls.CACHED_NAME, c.name)
                put(CallLog.Calls.DATE, c.date)
                put(CallLog.Calls.DURATION, c.duration)
                put(CallLog.Calls.TYPE, c.type)
                put(CallLog.Calls.NEW, 0)
            }
        }.toTypedArray()
        // Insert in chunks: one bulkInsert is a single Binder transaction (~1 MB cap), and
        // ContentResolver swallows the TransactionTooLargeException and just returns 0.
        var inserted = 0
        for (chunk in values.asList().chunked(INSERT_CHUNK)) {
            val n = contentResolver.bulkInsert(CallLog.Calls.CONTENT_URI, chunk.toTypedArray())
            if (n == 0) error("call log rejected the insert after $inserted of ${values.size} calls")
            inserted += n
            val done = inserted
            runOnUiThread { status.text = "Restoring… $done / ${values.size}" }
        }
        val skipped = incoming.size - toInsert.size
        return "Restored $inserted calls" + if (skipped > 0) " ($skipped already present)." else "."
    }

    companion object {
        private const val REQ_PERMS = 1
        private const val REQ_BACKUP = 2
        private const val REQ_RESTORE = 3
        private const val INSERT_CHUNK = 200 // ~40 KB per Binder call, well under the cap
    }
}
