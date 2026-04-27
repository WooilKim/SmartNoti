package com.smartnoti.app.ui.screens.ignored

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan `docs/plans/2026-04-27-ignored-archive-bulk-affordance-polish.md` Task 1 —
 * characterization tests for the two affordance regressions in
 * [IgnoredArchiveScreen]:
 *
 * 1. Header SmartSurfaceCard's two OutlinedButtons (`모두 PRIORITY 로 복구` /
 *    `모두 지우기`) split the screen 50/50 and the first label wraps onto a
 *    second line. The fix (Task 2, Risks Q1 Option A) shortens the label so
 *    both buttons render on one line. Pinned here as an exact-text match
 *    against [IgnoredArchiveAffordanceCopy.HEADER_RESTORE_ALL_LABEL_FIXED].
 *
 * 2. Per-row `PRIORITY 로 복구` TextButton sits in a `Column` next to
 *    [com.smartnoti.app.ui.components.NotificationCard] — outside the card
 *    border, so the action's visual anchor to "this card" is weak. The fix
 *    (Task 3, Risks Q2 Option D) wraps the card and the button in a single
 *    shared `Card` border so they read as one unit. Pinned here as a
 *    structural assertion: the row root has a `Card` ancestor that also
 *    contains the restore button.
 *
 * 3. While multi-select is active, the per-row `PRIORITY 로 복구` button
 *    must not exist (existing behavior — guarded so the polish PR cannot
 *    accidentally regress the long-press contract documented in
 *    `docs/journeys/ignored-archive.md` Observable step 7).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IgnoredArchiveScreenAffordanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun header_restore_all_button_uses_post_fix_short_label() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                IgnoredArchiveHeaderBulkActions(
                    onRestoreAllClick = {},
                    onClearAllClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(IgnoredArchiveAffordanceTags.HEADER_RESTORE_ALL_BUTTON)
            .assertIsDisplayed()
            .assertTextEquals(IgnoredArchiveAffordanceCopy.HEADER_RESTORE_ALL_LABEL_FIXED)

        composeTestRule
            .onNodeWithTag(IgnoredArchiveAffordanceTags.HEADER_CLEAR_ALL_BUTTON)
            .assertIsDisplayed()
            .assertTextEquals(IgnoredArchiveAffordanceCopy.HEADER_CLEAR_ALL_LABEL)
    }

    @Test
    fun row_card_and_restore_button_share_a_card_wrapper_when_multi_select_inactive() {
        composeTestRule.setContent {
            IgnoredArchiveRow(
                notification = sampleIgnoredNotification(),
                isSelected = false,
                isMultiSelectActive = false,
                onCardClick = {},
                onCardLongClick = {},
                onRestoreClick = {},
            )
        }

        // Card + restore button must be visually anchored: the row exposes a
        // single shared wrapper test tag that both descendants live under.
        // Today the `Column` parent does not carry this tag — Task 3 adds the
        // wrapping `Card` (or equivalent shared border) and the tag flips
        // green.
        composeTestRule
            .onNodeWithTag(IgnoredArchiveAffordanceTags.ROW_CARD_AND_RESTORE_WRAPPER)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(IgnoredArchiveAffordanceCopy.ROW_RESTORE_LABEL)
            .assertIsDisplayed()
    }

    @Test
    fun row_restore_button_is_absent_when_multi_select_active() {
        composeTestRule.setContent {
            IgnoredArchiveRow(
                notification = sampleIgnoredNotification(),
                isSelected = true,
                isMultiSelectActive = true,
                onCardClick = {},
                onCardLongClick = {},
                onRestoreClick = {},
            )
        }

        composeTestRule
            .onNodeWithTag(IgnoredArchiveAffordanceTags.ROW_RESTORE_BUTTON)
            .assertDoesNotExist()
    }

    private fun sampleIgnoredNotification(): NotificationUiModel = NotificationUiModel(
        id = "noti-ignore-1",
        appName = "Promo App",
        packageName = "com.example.promo",
        sender = null,
        title = "할인 안내",
        body = "오늘만 50% 할인",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.IGNORE,
        reasonTags = listOf("키워드 광고"),
    )
}

