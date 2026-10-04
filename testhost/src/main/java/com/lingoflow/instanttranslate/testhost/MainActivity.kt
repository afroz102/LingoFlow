package com.lingoflow.instanttranslate.testhost

import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.lingoflow.instanttranslate.testhost.databinding.ActivityMainBinding

/**
 * Dev-only controlled host (docs/VALIDATION_PLAN.md §2.2). Every field on screen is a known,
 * controlled fixture for exercising Process Text discovery — this app is never published and
 * has no purpose beyond compatibility testing.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fixtureTextOverride()?.let { binding.textReadonly.text = it }

        binding.customSelectionView.text = getString(R.string.fixture_custom_selection_sample)

        binding.composeContainer.setContent { ComposeTextFixture() }

        binding.webview.loadDataWithBaseURL(
            null,
            webViewFixtureHtml(),
            "text/html",
            "UTF-8",
            null,
        )
    }

    /**
     * Lets the Gate 1 latency harness (benchmark/TIMING_HARNESS.md) drive a specific workload
     * through the read-only fixture instead of the fixed sample string, so latency can be sliced
     * by text length and direction as docs/VALIDATION_PLAN.md §3.3 requires.
     *
     * Base64 rather than a plain string extra because the harness starts this activity through
     * `adb shell am start`, and the workloads contain Devanagari, emoji, quotes, ampersands and
     * newlines — all of which either need escaping or are silently mangled by the shell. Encoding
     * removes the whole class of quoting bugs rather than trying to escape each one.
     *
     * This hook exists only in the dev-only test host, which is never published
     * (docs/VALIDATION_PLAN.md §2.2). Nothing in the shipping app reads it.
     */
    private fun fixtureTextOverride(): String? {
        val encoded = intent?.getStringExtra(EXTRA_FIXTURE_TEXT_B64) ?: return null
        return try {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (malformed: IllegalArgumentException) {
            // Fail loudly: silently falling back to the default fixture would make the harness
            // report timings for a workload it never actually ran.
            throw IllegalArgumentException(
                "$EXTRA_FIXTURE_TEXT_B64 was not valid base64; the benchmark workload could not " +
                    "be applied and any timing from this run would be attributed to the wrong text",
                malformed,
            )
        }
    }

    @Composable
    private fun ComposeTextFixture() {
        MaterialTheme {
            Surface {
                SelectionContainer {
                    Text(
                        text = getString(R.string.fixture_compose_sample),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }

    private companion object {
        const val EXTRA_FIXTURE_TEXT_B64 = "fixture_text_b64"
    }

    private fun webViewFixtureHtml(): String = """
        <html>
          <body style="font-family: sans-serif; font-size: 16px;">
            <p>Selectable WebView text: the quick brown fox jumps over the lazy dog.</p>
            <p>Hinglish sample: kal milte hain. Devanagari sample: कल मिलते हैं।</p>
          </body>
        </html>
    """.trimIndent()
}
