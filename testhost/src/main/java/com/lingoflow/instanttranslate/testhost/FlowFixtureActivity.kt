package com.lingoflow.instanttranslate.testhost

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/** Dev-only cross-UID fixture with the same Process Text result contract as an editable host. */
class FlowFixtureActivity : AppCompatActivity() {
    private lateinit var draft: EditText
    private lateinit var status: TextView
    private var readonly = false
    private val returned = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { outcome ->
        val replacement = outcome.data?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (outcome.resultCode == RESULT_OK && replacement != null && !readonly) {
            val start = draft.text.indexOf('|') + 2
            val end = draft.text.lastIndexOf('|') - 1
            draft.text.replace(start, end, replacement)
            status.text = "Writing returned replacement"
        } else status.text = if (replacement == null) "No replacement returned" else "Unexpected replacement"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        draft = EditText(this).apply { setText("Before | main kal nahi aa sakta | After"); contentDescription = "Writing draft" }
        content.addView(draft)
        status = TextView(this).apply { text = "Ready" }; content.addView(status)
        fun button(label: String, action: () -> Unit) { content.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }) }
        fun process(text: String, readOnly: Boolean) {
            readonly = readOnly
            returned.launch(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .setClassName("com.lingoflow.instanttranslate", "com.lingoflow.instanttranslate.textaction.ProcessTextActivity")
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly))
        }
        button("Translate draft selection") { process("main kal nahi aa sakta", false) }
        button("Read selected message") { process("main kal nahi aa sakta", true) }
        button("Read unsupported script") { process("कल आना", true) }
        button("Copy received message") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Received message", "main kal nahi aa sakta"))
        }
        button("Copy another message") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Received message", "kal raid ke baad milte hain"))
        }
        button("Copy sensitive message") {
            val clip = ClipData.newPlainText("Sensitive", "secret test sample")
            if (android.os.Build.VERSION.SDK_INT >= 24) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
        button("Focus password field") {
            draft.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            draft.requestFocus()
            getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(draft, 0)
        }
        setContentView(content)
        draft.clearFocus()
    }
}
