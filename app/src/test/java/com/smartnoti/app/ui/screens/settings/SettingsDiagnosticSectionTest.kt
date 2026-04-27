package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md` Task 1.
 *
 * Failing-test gate (P1 release-prep) for [SettingsDiagnosticSection] — the
 * new "진단" section in Settings:
 *
 *  - "로그 기록" Switch tap flips the bound boolean and invokes the callback.
 *  - "본문 raw 기록" Switch is disabled when "로그 기록" is OFF (defensive
 *    UX so the user cannot toggle a sub-option that has no effect).
 *  - "로그 기록" ON ⇒ raw Switch becomes enabled and tapping it invokes the
 *    callback.
 *  - "로그 export" button tap invokes the export callback exactly once.
 *
 * The Composable is intentionally state-driven (no Singleton lookups inside
 * the section) so this test can drive it under a host-JVM Robolectric +
 * `createComposeRule` harness, mirroring `IgnoredArchiveScreenAffordanceTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsDiagnosticSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun toggling_logging_invokes_callback() {
        var loggingEnabled = false
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsDiagnosticSection(
                    state = SettingsDiagnosticSectionState(
                        loggingEnabled = loggingEnabled,
                        rawTitleBodyEnabled = false,
                    ),
                    onLoggingEnabledChange = { loggingEnabled = it },
                    onRawTitleBodyEnabledChange = { },
                    onExportClick = { },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SettingsDiagnosticSectionTags.LOGGING_SWITCH)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()

        assertEquals(true, loggingEnabled)
    }

    @Test
    fun raw_switch_is_disabled_when_logging_off() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsDiagnosticSection(
                    state = SettingsDiagnosticSectionState(
                        loggingEnabled = false,
                        rawTitleBodyEnabled = false,
                    ),
                    onLoggingEnabledChange = { },
                    onRawTitleBodyEnabledChange = { },
                    onExportClick = { },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SettingsDiagnosticSectionTags.RAW_SWITCH)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun raw_switch_is_enabled_and_toggleable_when_logging_on() {
        var rawEnabled = false
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsDiagnosticSection(
                    state = SettingsDiagnosticSectionState(
                        loggingEnabled = true,
                        rawTitleBodyEnabled = rawEnabled,
                    ),
                    onLoggingEnabledChange = { },
                    onRawTitleBodyEnabledChange = { rawEnabled = it },
                    onExportClick = { },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SettingsDiagnosticSectionTags.RAW_SWITCH)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()

        assertEquals(true, rawEnabled)
    }

    @Test
    fun logging_switch_reflects_on_state_when_state_is_on() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsDiagnosticSection(
                    state = SettingsDiagnosticSectionState(
                        loggingEnabled = true,
                        rawTitleBodyEnabled = false,
                    ),
                    onLoggingEnabledChange = { },
                    onRawTitleBodyEnabledChange = { },
                    onExportClick = { },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SettingsDiagnosticSectionTags.LOGGING_SWITCH)
            .assertIsDisplayed()
            .assertIsOn()
    }

    @Test
    fun export_button_tap_invokes_callback_once() {
        var exportCalls = 0
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                SettingsDiagnosticSection(
                    state = SettingsDiagnosticSectionState(
                        loggingEnabled = true,
                        rawTitleBodyEnabled = false,
                    ),
                    onLoggingEnabledChange = { },
                    onRawTitleBodyEnabledChange = { },
                    onExportClick = { exportCalls += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SettingsDiagnosticSectionTags.EXPORT_BUTTON)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, exportCalls)
    }
}
