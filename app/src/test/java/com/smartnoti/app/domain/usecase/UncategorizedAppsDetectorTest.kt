package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 10 — the "새 앱 분류 유도 카드" surfaces when the user has received
 * notifications from distinct packages over the last 7 days that are **not**
 * covered by any existing Category's `appPackageName` pin. Once the user taps
 * "나중에" the detection enters a 24-hour snooze.
 *
 * Plan `docs/plans/2026-04-27-uncategorized-prompt-app-rule-coverage.md`
 * extends coverage to APP-type Rules referenced via `Category.ruleIds`.
 *
 * This detector is a pure function so that Home can remember the result
 * cheaply — the actual flow wiring (NotificationRepository + CategoriesRepository
 * + SettingsRepository snooze flag) happens in HomeScreen.
 */
class UncategorizedAppsDetectorTest {

    private val detector = UncategorizedAppsDetector()
    private val nowMillis = 1_700_000_000_000L
    private val sevenDaysMillis = 7L * 24L * 60L * 60L * 1000L

    @Test
    fun returns_none_when_fewer_than_three_uncovered_apps() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", postedAt = nowMillis),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertEquals(UncategorizedAppsDetection.None, result)
    }

    @Test
    fun returns_prompt_when_at_least_three_uncovered_apps_exist() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", appName = "앱A", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", appName = "앱B", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.app.c", appName = "앱C", postedAt = nowMillis),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertTrue("Expected Prompt, got $result", result is UncategorizedAppsDetection.Prompt)
        val prompt = result as UncategorizedAppsDetection.Prompt
        assertEquals(3, prompt.uncoveredCount)
        assertEquals(listOf("앱A", "앱B", "앱C"), prompt.sampleAppLabels)
        assertEquals(listOf("com.app.a", "com.app.b", "com.app.c"), prompt.samplePackageNames)
    }

    @Test
    fun excludes_apps_already_covered_by_a_category_app_pin() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", appName = "앱A", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", appName = "앱B", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.app.c", appName = "앱C", postedAt = nowMillis),
                notification(id = "n4", packageName = "com.app.d", appName = "앱D", postedAt = nowMillis),
                notification(id = "n5", packageName = "com.app.e", appName = "앱E", postedAt = nowMillis),
            ),
            categories = listOf(
                category(id = "cat-a", appPackageName = "com.app.a"),
                category(id = "cat-b", appPackageName = "com.app.b"),
            ),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertTrue("Expected Prompt, got $result", result is UncategorizedAppsDetection.Prompt)
        val prompt = result as UncategorizedAppsDetection.Prompt
        assertEquals(3, prompt.uncoveredCount)
        assertFalse(prompt.samplePackageNames.contains("com.app.a"))
        assertFalse(prompt.samplePackageNames.contains("com.app.b"))
    }

    @Test
    fun exclusion_below_threshold_returns_none() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.app.c", postedAt = nowMillis),
                notification(id = "n4", packageName = "com.app.d", postedAt = nowMillis),
            ),
            categories = listOf(
                category(id = "cat-a", appPackageName = "com.app.a"),
                category(id = "cat-b", appPackageName = "com.app.b"),
            ),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertEquals(UncategorizedAppsDetection.None, result)
    }

    @Test
    fun ignores_notifications_older_than_seven_days() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.app.c", postedAt = nowMillis - sevenDaysMillis - 1L),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertEquals(UncategorizedAppsDetection.None, result)
    }

    @Test
    fun returns_none_while_snooze_is_active() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.app.c", postedAt = nowMillis),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = nowMillis + 1L,
        )

        assertEquals(UncategorizedAppsDetection.None, result)
    }

    @Test
    fun returns_prompt_when_snooze_has_expired() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.b", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.app.c", postedAt = nowMillis),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = nowMillis - 1L,
        )

        assertTrue(result is UncategorizedAppsDetection.Prompt)
    }

    @Test
    fun deduplicates_packages_across_multiple_notifications() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", appName = "앱A", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.app.a", appName = "앱A", postedAt = nowMillis - 1L),
                notification(id = "n3", packageName = "com.app.b", appName = "앱B", postedAt = nowMillis),
                notification(id = "n4", packageName = "com.app.c", appName = "앱C", postedAt = nowMillis),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertTrue(result is UncategorizedAppsDetection.Prompt)
        val prompt = result as UncategorizedAppsDetection.Prompt
        assertEquals(3, prompt.uncoveredCount)
    }

    @Test
    fun sample_labels_top_three_by_most_recent_notification() {
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.app.a", appName = "앱A", postedAt = nowMillis - 100L),
                notification(id = "n2", packageName = "com.app.b", appName = "앱B", postedAt = nowMillis - 50L),
                notification(id = "n3", packageName = "com.app.c", appName = "앱C", postedAt = nowMillis - 10L),
                notification(id = "n4", packageName = "com.app.d", appName = "앱D", postedAt = nowMillis - 200L),
            ),
            categories = emptyList(),
            rules = emptyList(),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertTrue(result is UncategorizedAppsDetection.Prompt)
        val prompt = result as UncategorizedAppsDetection.Prompt
        assertEquals(4, prompt.uncoveredCount)
        // Top 3 most recent: C (most recent) > B > A
        assertEquals(listOf("앱C", "앱B", "앱A"), prompt.sampleAppLabels)
    }

    // --- Plan 2026-04-27: APP-Rule coverage extension --------------------

    @Test
    fun category_with_app_rule_covers_that_package_returns_none() {
        // Category A has no appPackageName pin, but its ruleIds reference an
        // APP-type Rule matching com.example.foo. Combined with two other
        // uncovered apps the count is 2 → below THRESHOLD → None.
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.example.foo", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.example.bar", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.example.baz", postedAt = nowMillis),
            ),
            categories = listOf(
                category(
                    id = "cat-a",
                    appPackageName = null,
                    ruleIds = listOf("app:com.example.foo"),
                ),
            ),
            rules = listOf(
                appRule(id = "app:com.example.foo", matchValue = "com.example.foo"),
            ),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertEquals(UncategorizedAppsDetection.None, result)
    }

    @Test
    fun category_with_keyword_rule_does_not_cover_that_package_returns_prompt() {
        // KEYWORD-only Category does not contribute coverage — the three
        // distinct packages remain uncovered.
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.example.bar", appName = "Bar", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.example.baz", appName = "Baz", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.example.qux", appName = "Qux", postedAt = nowMillis),
            ),
            categories = listOf(
                category(
                    id = "cat-b",
                    appPackageName = null,
                    ruleIds = listOf("kw:promo"),
                ),
            ),
            rules = listOf(
                RuleUiModel(
                    id = "kw:promo",
                    title = "광고",
                    subtitle = "키워드",
                    type = RuleTypeUi.KEYWORD,
                    enabled = true,
                    matchValue = "광고",
                ),
            ),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertTrue("Expected Prompt, got $result", result is UncategorizedAppsDetection.Prompt)
        val prompt = result as UncategorizedAppsDetection.Prompt
        assertEquals(3, prompt.uncoveredCount)
    }

    @Test
    fun orphan_app_rule_not_referenced_by_any_category_does_not_cover() {
        // The APP-Rule exists in RulesRepository but no Category's ruleIds
        // references it → the rule is orphaned and must NOT contribute to
        // coverage. All three packages remain uncovered.
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.example.qux", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.example.foo", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.example.bar", postedAt = nowMillis),
            ),
            categories = emptyList(),
            rules = listOf(
                appRule(id = "app:com.example.qux", matchValue = "com.example.qux"),
            ),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        assertTrue("Expected Prompt, got $result", result is UncategorizedAppsDetection.Prompt)
        val prompt = result as UncategorizedAppsDetection.Prompt
        assertEquals(3, prompt.uncoveredCount)
        assertTrue(prompt.samplePackageNames.contains("com.example.qux"))
    }

    @Test
    fun app_rule_match_value_case_insensitive() {
        // matchValue stored as upper-case must still cover the lower-case
        // packageName from the notification feed.
        val result = detector.detect(
            notifications = listOf(
                notification(id = "n1", packageName = "com.example.foo", postedAt = nowMillis),
                notification(id = "n2", packageName = "com.example.bar", postedAt = nowMillis),
                notification(id = "n3", packageName = "com.example.baz", postedAt = nowMillis),
            ),
            categories = listOf(
                category(
                    id = "cat-foo",
                    appPackageName = null,
                    ruleIds = listOf("app:foo"),
                ),
            ),
            rules = listOf(
                appRule(id = "app:foo", matchValue = "COM.EXAMPLE.FOO"),
            ),
            nowMillis = nowMillis,
            snoozeUntilMillis = 0L,
        )

        // foo is covered case-insensitively → only bar + baz remain → < THRESHOLD → None.
        assertEquals(UncategorizedAppsDetection.None, result)
    }

    private fun notification(
        id: String,
        packageName: String,
        appName: String = packageName,
        postedAt: Long,
    ) = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = packageName,
        sender = null,
        title = "제목",
        body = "본문",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.PRIORITY,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
        postedAtMillis = postedAt,
    )

    private fun category(
        id: String,
        appPackageName: String?,
        ruleIds: List<String> = emptyList(),
    ) = Category(
        id = id,
        name = "분류-$id",
        appPackageName = appPackageName,
        ruleIds = ruleIds,
        action = CategoryAction.PRIORITY,
        order = 0,
    )

    private fun appRule(
        id: String,
        matchValue: String,
    ) = RuleUiModel(
        id = id,
        title = matchValue,
        subtitle = "앱",
        type = RuleTypeUi.APP,
        enabled = true,
        matchValue = matchValue,
    )
}
