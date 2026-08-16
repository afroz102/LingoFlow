package com.lingoflow.instanttranslate.textaction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.lingoflow.instanttranslate.ui.ResultActivity

/**
 * Text-action adapter (docs/TECHNICAL_PLAN.md §3): the sole exported entry point for
 * `ACTION_PROCESS_TEXT`. It draws no UI of its own (see `Theme.InstantTranslate.NoDisplay`) —
 * it validates the incoming intent, delegates presentation to [ResultActivity], and relays
 * whatever result that activity produces straight back to the host app that invoked Process
 * Text. Cancellation or invalid input leaves the host unmodified (RESULT_CANCELED, no extras).
 */
class ProcessTextActivity : ComponentActivity() {

    private val resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            setResult(result.resultCode, result.data)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only launch once. On recreation (rotation/process death) ResultActivity is already
        // the visible top of the stack; registerForActivityResult above re-attaches the
        // pending callback on its own, and a second launch here would duplicate it.
        if (savedInstanceState != null) return

        val validated = ProcessTextInput.validate(intent)
        if (validated == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        resultLauncher.launch(ResultActivity.createIntent(this, validated.text, validated.isReadOnly))
    }
}
