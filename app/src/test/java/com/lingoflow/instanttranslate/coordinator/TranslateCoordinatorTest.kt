package com.lingoflow.instanttranslate.coordinator

import com.lingoflow.instanttranslate.cloud.ConnectivityChecker
import com.lingoflow.instanttranslate.cloud.DisclosureGate
import com.lingoflow.instanttranslate.direction.Direction
import com.lingoflow.instanttranslate.provider.FailureReason
import com.lingoflow.instanttranslate.provider.TranslationProvider
import com.lingoflow.instanttranslate.provider.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeDisclosureGate(private var acknowledged: Boolean) : DisclosureGate {
    override fun isAcknowledged() = acknowledged
    override fun acknowledge() {
        acknowledged = true
    }
}

private class FakeConnectivityChecker(private val connected: Boolean) : ConnectivityChecker {
    override fun isConnected() = connected
}

private class FakeProvider(private val result: TranslationResult) : TranslationProvider {
    var callCount = 0
        private set

    override suspend fun translate(text: String, direction: Direction): TranslationResult {
        callCount++
        return result
    }
}

class TranslateCoordinatorTest {

    @Test
    fun `disclosure not yet acknowledged blocks the request before the provider is ever called`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("unused"))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(false), FakeConnectivityChecker(true))

        val outcome = coordinator.translate("hello")

        assertEquals(TranslationOutcome.DisclosureRequired, outcome)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `offline blocks the request before the provider is ever called`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("unused"))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(false))

        val outcome = coordinator.translate("hello")

        assertEquals(TranslationOutcome.Offline, outcome)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `disclosure is checked before connectivity so the user sees the consent gate first`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("unused"))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(false), FakeConnectivityChecker(false))

        val outcome = coordinator.translate("hello")

        assertEquals(TranslationOutcome.DisclosureRequired, outcome)
    }

    @Test
    fun `successful provider result is wrapped with the detected direction`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("नमस्ते"))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(true))

        val outcome = coordinator.translate("hello") as TranslationOutcome.Translated

        assertEquals("hello", outcome.original)
        assertEquals("नमस्ते", outcome.translated)
        assertEquals(Direction.ENGLISH_TO_HINDI, outcome.direction)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `provider failure reason is passed through unchanged`() = runTest {
        val provider = FakeProvider(TranslationResult.Failure(FailureReason.RATE_LIMITED))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(true))

        val outcome = coordinator.translate("hello") as TranslationOutcome.Failed

        assertEquals(FailureReason.RATE_LIMITED, outcome.reason)
    }

    @Test
    fun `each failure reason round-trips through the coordinator unchanged`() = runTest {
        for (reason in FailureReason.entries) {
            val provider = FakeProvider(TranslationResult.Failure(reason))
            val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(true))

            val outcome = coordinator.translate("hello") as TranslationOutcome.Failed

            assertEquals(reason, outcome.reason)
        }
    }
}
