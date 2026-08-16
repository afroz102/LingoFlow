package com.lingoflow.instanttranslate.testhost

import android.os.Bundle
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

    private fun webViewFixtureHtml(): String = """
        <html>
          <body style="font-family: sans-serif; font-size: 16px;">
            <p>Selectable WebView text: the quick brown fox jumps over the lazy dog.</p>
            <p>Hinglish sample: kal milte hain. Devanagari sample: कल मिलते हैं।</p>
          </body>
        </html>
    """.trimIndent()
}
