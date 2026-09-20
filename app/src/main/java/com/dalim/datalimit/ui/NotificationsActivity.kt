package com.dalim.datalimit.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.dalim.datalimit.R
import com.dalim.datalimit.core.IMPORTANT_IMPORTANCE
import com.dalim.datalimit.core.LocaleHelper
import com.dalim.datalimit.core.UsagePrefs
import com.dalim.datalimit.core.VaultEntry
import com.dalim.datalimit.data.SqliteNotificationStore
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar

class NotificationsActivity : AppCompatActivity() {

    private lateinit var prefs: UsagePrefs
    private lateinit var store: SqliteNotificationStore

    private lateinit var vaultList: LinearLayout
    private lateinit var vaultEmpty: TextView
    private lateinit var vaultCount: TextView
    private lateinit var vaultGrantText: TextView
    private lateinit var btnVaultGrant: MaterialButton

    private val rows = mutableListOf<VaultEntry>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.resolve(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        prefs = UsagePrefs(this)
        store = SqliteNotificationStore(applicationContext)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        vaultList = findViewById(R.id.vaultList)
        vaultEmpty = findViewById(R.id.vaultEmpty)
        vaultCount = findViewById(R.id.vaultCount)
        vaultGrantText = findViewById(R.id.vaultGrantText)
        btnVaultGrant = findViewById(R.id.btnVaultGrant)

        loadVaultPrefs()
        wire()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshAccessUi()
        refreshList()
    }

    private fun loadVaultPrefs() {
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchVault).isChecked = prefs.vaultEnabled
        findViewById<MaterialButtonToggleGroup>(R.id.retentionGroup).check(
            when (prefs.vaultRetentionDays) {
                3 -> R.id.btnRetention3
                30 -> R.id.btnRetention30
                else -> R.id.btnRetention7
            }
        )
    }

    private fun wire() {
        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchVault)
            .setOnCheckedChangeListener { _, checked ->
                prefs.vaultEnabled = checked
                if (checked) {
                    refreshAccessUi()
                    if (!isListenerEnabled()) openListenerSettings()
                }
            }

        findViewById<MaterialButtonToggleGroup>(R.id.retentionGroup)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    prefs.vaultRetentionDays = when (checkedId) {
                        R.id.btnRetention3 -> 3
                        R.id.btnRetention30 -> 30
                        else -> 7
                    }
                    Thread {
                        cleanup()
                        runOnUiThread { refreshList() }
                    }.start()
                }
            }

        btnVaultGrant.setOnClickListener {
            prefs.vaultEnabled = true
            findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchVault).isChecked = true
            openListenerSettings()
        }

        findViewById<MaterialButton>(R.id.btnVaultClear).setOnClickListener {
            Thread {
                store.clearAll()
                runOnUiThread { refreshList() }
            }.start()
            Snackbar.make(findViewById(R.id.toolbar), getString(R.string.vault_cleared), Snackbar.LENGTH_SHORT).show()
        }

        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchImportant)
            .setOnCheckedChangeListener { _, _ -> refreshList() }

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    refreshList()
                }
            })
    }

    private fun isListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun openListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun refreshAccessUi() {
        val enabled = isListenerEnabled()
        val relevant = findViewById<View>(R.id.btnVaultGrant)
        if (enabled) {
            prefs.vaultEnabled = true
            vaultGrantText.visibility = View.GONE
            relevant.visibility = View.GONE
        } else {
            vaultGrantText.visibility = View.VISIBLE
            relevant.visibility = View.VISIBLE
        }
    }

    private fun refreshList() {
        val search = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchInput)
            .text?.toString()?.trim().orEmpty()
        val importantOnly = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchImportant).isChecked
        Thread {
            cleanup()
            val entries = store.query(search, importantOnly, null, MAX_ROWS, 0L)
            runOnUiThread { renderList(entries) }
        }.start()
    }

    private fun cleanup() {
        val retentionDays = prefs.vaultRetentionDays.toLong()
        val before = System.currentTimeMillis() - retentionDays * 86_400_000L
        store.cleanupBefore(before)
    }

    private fun renderList(entries: List<VaultEntry>) {
        rows.clear()
        rows.addAll(entries)
        vaultList.removeAllViews()
        vaultCount.text = getString(R.string.vault_count_fmt, store.totalCount())

        if (entries.isEmpty()) {
            vaultEmpty.visibility = View.VISIBLE
            return
        }
        vaultEmpty.visibility = View.GONE
        for (entry in entries) {
            vaultList.addView(rowFor(entry), ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun rowFor(entry: VaultEntry): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(10), 0, dp(10))

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val title = TextView(this)
        title.text = entry.title
        title.setTextColor(0xFF202124.toInt())
        title.textSize = 14f
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        title.isSingleLine = true
        body.addView(title)

        val meta = TextView(this)
        meta.text = timeLabel(entry.postedAtMillis) + (if (entry.importance >= IMPORTANT_IMPORTANCE) " · " + getString(R.string.vault_important_badge) else "")
        meta.setTextColor(0xFF9AA0A6.toInt())
        meta.textSize = 11f
        body.addView(meta)

        if (entry.text.isNotEmpty()) {
            val text = TextView(this)
            text.text = entry.text
            text.setTextColor(0xFF5F6368.toInt())
            text.textSize = 12f
            text.maxLines = 2
            body.addView(text)
        }

        val app = TextView(this)
        app.text = entry.appName
        app.setTextColor(0xFF9AA0A6.toInt())
        app.textSize = 11f
        body.addView(app)

        row.addView(body)

        if (!entry.read) {
            val mark = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            mark.text = getString(R.string.vault_mark_read)
            mark.minHeight = 0
            mark.setPadding(dp(6), 0, dp(6), 0)
            mark.setTextColor(0xFF0D47A1.toInt())
            mark.setOnClickListener {
                Thread {
                    store.markRead(entry.id)
                    runOnUiThread { refreshList() }
                }.start()
            }
            row.addView(mark)
        }

        val del = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        del.text = getString(R.string.vault_delete)
        del.minHeight = 0
        del.setPadding(dp(6), 0, dp(6), 0)
        del.setTextColor(0xFFD32F2F.toInt())
        del.setOnClickListener {
            Thread {
                store.delete(entry.id)
                runOnUiThread { refreshList() }
            }.start()
        }
        row.addView(del)

        return row
    }

    private fun timeLabel(millis: Long): String {
        val date = android.text.format.DateFormat.format("dd MMM yyyy", java.util.Date(millis))
        val time = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(millis))
        return "$date · $time"
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_ROWS = 200
    }
}