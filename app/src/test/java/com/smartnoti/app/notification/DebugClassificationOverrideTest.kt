package com.smartnoti.app.notification

import android.os.Bundle
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the debug-only sentinel-marker contract introduced by plan
 * `2026-04-22-priority-recipe-debug-inject-hook.md`. This policy object
 * lets the journey-tester recipe pin a classification result ahead of the
 * usual rule/category pipeline so accumulated user rules in the emulator
 * can't shadow the test sender.
 *
 * Release builds strip the caller (via `BuildConfig.DEBUG` dead-code
 * folding) so these contracts only ever apply to debug APKs.
 */
@RunWith(RobolectricTestRunner::class)
class DebugClassificationOverrideTest {

    private val fallback = notificationUiModel(
        status = NotificationStatusUi.SILENT,
        reasonTags = listOf("사용자 규칙", "인증번호"),
    )

    @Test
    fun `marker absent returns the fallback unchanged`() {
        val extras = Bundle()

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertSame(fallback, resolved)
    }

    @Test
    fun `marker PRIORITY overrides status and replaces reasonTags`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "PRIORITY")
        }

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertEquals(NotificationStatusUi.PRIORITY, resolved.status)
        assertEquals(listOf("디버그 주입"), resolved.reasonTags)
    }

    @Test
    fun `marker DIGEST overrides status`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "DIGEST")
        }

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertEquals(NotificationStatusUi.DIGEST, resolved.status)
        assertEquals(listOf("디버그 주입"), resolved.reasonTags)
    }

    @Test
    fun `marker SILENT overrides status`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "SILENT")
        }

        val fallbackAsPriority = fallback.copy(status = NotificationStatusUi.PRIORITY)

        val resolved = DebugClassificationOverride.resolve(extras, fallbackAsPriority)

        assertEquals(NotificationStatusUi.SILENT, resolved.status)
        assertEquals(listOf("디버그 주입"), resolved.reasonTags)
    }

    @Test
    fun `marker IGNORE overrides status`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "IGNORE")
        }

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertEquals(NotificationStatusUi.IGNORE, resolved.status)
        assertEquals(listOf("디버그 주입"), resolved.reasonTags)
    }

    @Test
    fun `marker is case-sensitive and lowercase digest falls through`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "digest")
        }

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertSame(fallback, resolved)
    }

    @Test
    fun `bogus marker value falls through to fallback`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "BOGUS")
        }

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertSame(fallback, resolved)
    }

    @Test
    fun `unrelated extras do not trigger override`() {
        val extras = Bundle().apply {
            putString("com.smartnoti.debug.OTHER", "PRIORITY")
            putString("android.title", "PRIORITY")
        }

        val resolved = DebugClassificationOverride.resolve(extras, fallback)

        assertSame(fallback, resolved)
    }

    @Test
    fun `override preserves non-status fields of the fallback`() {
        val extras = Bundle().apply {
            putString(DebugClassificationOverride.MARKER_KEY, "PRIORITY")
        }
        val rich = fallback.copy(
            id = "abc",
            appName = "TestApp",
            title = "hello",
            body = "world",
            matchedRuleIds = listOf("rule-1"),
        )

        val resolved = DebugClassificationOverride.resolve(extras, rich)

        assertEquals("abc", resolved.id)
        assertEquals("TestApp", resolved.appName)
        assertEquals("hello", resolved.title)
        assertEquals("world", resolved.body)
        assertEquals(listOf("rule-1"), resolved.matchedRuleIds)
    }

    private fun notificationUiModel(
        status: NotificationStatusUi,
        reasonTags: List<String>,
    ): NotificationUiModel = NotificationUiModel(
        id = "id",
        appName = "app",
        packageName = "com.example",
        sender = null,
        title = "title",
        body = "body",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = reasonTags,
    )
}
