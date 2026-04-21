package com.smartnoti.app.notification

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Detail "처리 완료로 표시" action flow for the
 * `silent-archive-drift-fix` Task 3 plan.
 *
 * Expected contract:
 * - The DB flip (`markSilentProcessed`) runs first.
 * - Only on a `true` flip do we look up the source tray entry key.
 * - The listener service's `cancelSourceEntryIfConnected` helper is invoked
 *   exactly once when a key exists.
 * - When the DB flip is a no-op (row already processed, not silent, missing),
 *   we never query for the source entry key and never attempt to cancel.
 * - When the flipped row is a legacy null-key row, no cancel is attempted
 *   even though the DB flip succeeded.
 * - If the listener is disconnected (`cancelSourceEntryIfConnected` returns
 *   false) the DB transition still stands — the chain tolerates best-effort
 *   tray cancellation without reverting the persistence change.
 */
class MarkSilentProcessedTrayCancelChainTest {

    @Test
    fun chain_cancels_tray_entry_when_db_flip_succeeds_and_key_is_present() = runBlocking {
        val recorder = Recorder()
        val chain = MarkSilentProcessedTrayCancelChain(
            markSilentProcessed = { id ->
                recorder.markCalls += id
                true
            },
            sourceEntryKeyForId = { id ->
                recorder.keyLookups += id
                "0|com.foo|42|null|10"
            },
            cancelSourceEntryIfConnected = { key ->
                recorder.cancelCalls += key
                true
            },
        )

        val result = chain.run("notif-1")

        assertEquals(ChainOutcome.CANCELLED, result)
        assertEquals(listOf("notif-1"), recorder.markCalls)
        assertEquals(listOf("notif-1"), recorder.keyLookups)
        assertEquals(listOf("0|com.foo|42|null|10"), recorder.cancelCalls)
    }

    @Test
    fun chain_skips_cancel_when_db_flip_is_noop() = runBlocking {
        val recorder = Recorder()
        val chain = MarkSilentProcessedTrayCancelChain(
            markSilentProcessed = { _ -> false },
            sourceEntryKeyForId = { id ->
                recorder.keyLookups += id
                "should-not-be-called"
            },
            cancelSourceEntryIfConnected = { key ->
                recorder.cancelCalls += key
                true
            },
        )

        val result = chain.run("already-processed")

        assertEquals(ChainOutcome.DB_NOOP, result)
        assertTrue(recorder.keyLookups.isEmpty())
        assertTrue(recorder.cancelCalls.isEmpty())
    }

    @Test
    fun chain_skips_cancel_for_legacy_null_key_rows() = runBlocking {
        val recorder = Recorder()
        val chain = MarkSilentProcessedTrayCancelChain(
            markSilentProcessed = { _ -> true },
            sourceEntryKeyForId = { _ -> null },
            cancelSourceEntryIfConnected = { key ->
                recorder.cancelCalls += key
                true
            },
        )

        val result = chain.run("legacy-null-key")

        assertEquals(ChainOutcome.FLIPPED_WITHOUT_KEY, result)
        assertTrue(recorder.cancelCalls.isEmpty())
    }

    @Test
    fun chain_records_disconnect_when_listener_is_not_live() = runBlocking {
        val chain = MarkSilentProcessedTrayCancelChain(
            markSilentProcessed = { _ -> true },
            sourceEntryKeyForId = { _ -> "0|com.bar|1|tag|10" },
            cancelSourceEntryIfConnected = { _ -> false },
        )

        val result = chain.run("listener-offline")

        assertEquals(ChainOutcome.LISTENER_DISCONNECTED, result)
    }

    @Test
    fun chain_is_idempotent_on_re_invocation() = runBlocking {
        var firstFlip = true
        val recorder = Recorder()
        val chain = MarkSilentProcessedTrayCancelChain(
            markSilentProcessed = { _ ->
                val current = firstFlip
                firstFlip = false
                current
            },
            sourceEntryKeyForId = { _ -> "0|com.baz|7|null|10" },
            cancelSourceEntryIfConnected = { key ->
                recorder.cancelCalls += key
                true
            },
        )

        val first = chain.run("notif-x")
        val second = chain.run("notif-x")

        assertEquals(ChainOutcome.CANCELLED, first)
        assertEquals(ChainOutcome.DB_NOOP, second)
        assertEquals(1, recorder.cancelCalls.size)
    }

    @Test
    fun result_exposes_convenience_flags_for_ui_consumption() {
        assertTrue(ChainOutcome.CANCELLED.flipped)
        assertFalse(ChainOutcome.DB_NOOP.flipped)
        assertTrue(ChainOutcome.FLIPPED_WITHOUT_KEY.flipped)
        assertTrue(ChainOutcome.LISTENER_DISCONNECTED.flipped)
    }

    private class Recorder {
        val markCalls = mutableListOf<String>()
        val keyLookups = mutableListOf<String>()
        val cancelCalls = mutableListOf<String>()
    }
}
