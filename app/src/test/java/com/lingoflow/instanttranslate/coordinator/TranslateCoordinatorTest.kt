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
    var requestedDirection: Direction? = null
    var callCount = 0
        private set

    override suspend fun translate(text: String, direction: Direction): TranslationResult {
        requestedDirection = direction
        callCount++
        return result
    }
}

class TranslateCoordinatorTest {

    @Test
    fun `disclosure not yet acknowledged blocks the request before the provider is ever called`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("unused", Direction.ENGLISH_TO_HINDI))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(false), FakeConnectivityChecker(true))

        val outcome = coordinator.translate("hello")

        assertEquals(TranslationOutcome.DisclosureRequired, outcome)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `offline blocks the request before the provider is ever called`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("unused", Direction.ENGLISH_TO_HINDI))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(false))

        val outcome = coordinator.translate("hello")

        assertEquals(TranslationOutcome.Offline, outcome)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun `disclosure is checked before connectivity so the user sees the consent gate first`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("unused", Direction.ENGLISH_TO_HINDI))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(false), FakeConnectivityChecker(false))

        val outcome = coordinator.translate("hello")

        assertEquals(TranslationOutcome.DisclosureRequired, outcome)
    }

    @Test
    fun `successful provider result uses the model resolved direction`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("नमस्ते", Direction.ENGLISH_TO_HINDI))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(true))

        val outcome = coordinator.translate("hello") as TranslationOutcome.Translated

        assertEquals("hello", outcome.original)
        assertEquals("नमस्ते", outcome.translated)
        assertEquals(Direction.ENGLISH_TO_HINDI, outcome.direction)
        assertEquals(1, provider.callCount)
        assertEquals(Direction.AUTO, provider.requestedDirection)
    }

    @Test
    fun `Hinglish auto result has the resolved label and one provider call`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("I cannot come tomorrow", Direction.HINGLISH_TO_ENGLISH))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(true))
        val outcome = coordinator.translate("main kal nahi aa sakta") as TranslationOutcome.Translated
        assertEquals(Direction.AUTO, provider.requestedDirection)
        assertEquals(Direction.HINGLISH_TO_ENGLISH, outcome.direction)
        assertEquals(1, provider.callCount)
    }

    @Test
    fun `explicit Romanized output overrides detection after the same gates`() = runTest {
        val provider = FakeProvider(TranslationResult.Success("Aap kaise hain?", Direction.ENGLISH_TO_HINGLISH))
        val coordinator = TranslateCoordinator(provider, FakeDisclosureGate(true), FakeConnectivityChecker(true))
        val outcome = coordinator.translate("How are you?", Direction.ENGLISH_TO_HINGLISH) as TranslationOutcome.Translated
        assertEquals(Direction.ENGLISH_TO_HINGLISH, provider.requestedDirection)
        assertEquals(Direction.ENGLISH_TO_HINGLISH, outcome.direction)
        assertEquals(1, provider.callCount)
        val blocked = TranslateCoordinator(provider, FakeDisclosureGate(false), FakeConnectivityChecker(true))
        assertEquals(TranslationOutcome.DisclosureRequired, blocked.translate("How are you?", Direction.ENGLISH_TO_HINGLISH))
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
