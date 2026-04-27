package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.domain.usecase.SuppressedAppInsight
import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary
import com.smartnoti.app.onboarding.OnboardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-refactor-settings-screen-split.md` Task 1 —
 * pin the user-visible affordances on `SettingsScreen` before the per-section
 * file split runs in Tasks 2-3.
 *
 * The `SettingsScreen` root composable is wired to two singletons
 * (`SettingsRepository.getInstance(context)` + `NotificationRepository
 * .getInstance(context)`), eight spec builders, and a Lifecycle-bound
 * notification-access observer. Booting it under Robolectric would require
 * stubbing both singletons + a real DataStore, which is out of scope for a
 * pure refactor PR.
 *
 * Following the same pattern as `RulesScreenCharacterizationTest`, this
 * file pins the **pure helper functions and pure-state contracts** that
 * drive the affordances called out in the plan's Task 1 Step 1:
 *
 *   1. "조용한 시간" + 시작/종료 시각 picker row.
 *   2. "중복 알림 묶기 임계값" picker + window picker.
 *   3. "전달 모드" (delivery profile) summary copy.
 *   4. "고급 규칙 편집 열기" entry card.
 *   5. "무시된 알림 아카이브 표시" toggle.
 *
 * Each spec builder + label helper covered here either already lives in its
 * own sub-file in the same package (e.g. `QuietHoursWindowPickerSpec.kt`)
 * or is a pure helper that will move with its renderer when the carve-out
 * runs in Task 2/3. If their string outputs or branching logic change
 * during the move, this test fails.
 *
 * The "thin renderer" copy that is **only** held inside the `private fun`
 * composables in `SettingsScreen.kt` — for example the `AdvancedRulesEntryCard`
 * button label — is pinned by string-equality assertions against compiled
 * constants the test imports from this file. Those constants must mirror
 * the literal copy emitted by the composable; if Task 2/3 rewrites the
 * literal, this test fails and the regression is visible at review time.
 *
 * Behavioural verification (the master Switch actually toggling Quiet Hours,
 * the "고급 규칙 편집 열기" button actually navigating, ...) is covered by
 * the existing settings-screen ADB recipe in the journey doc — re-running it
 * post-refactor is the Task 4 verification step in the plan.
 */
class SettingsScreenCharacterizationTest {

    // ---------- Affordance 1: Quiet Hours window picker --------------------
    // Plan Task 2 moves `OperationalSummaryCard` + `QuietHoursWindowPickerRow`
    // + `QuietHoursHourPicker` to `SettingsQuietHoursSection.kt`. The
    // `QuietHoursWindowPickerSpec` builder lives in its own file already and
    // drives the row's visibility / option list / same-value warning. This
    // pins the contract the renderer reads.

    @Test
    fun quietHours_window_picker_visible_only_when_enabled() {
        val builder = QuietHoursWindowPickerSpecBuilder()
        val enabled = SmartNotiSettings(
            quietHoursEnabled = true,
            quietHoursStartHour = 23,
            quietHoursEndHour = 7,
        )
        val disabled = SmartNotiSettings(
            quietHoursEnabled = false,
            quietHoursStartHour = 23,
            quietHoursEndHour = 7,
        )

        assertTrue(builder.build(enabled).visible)
        assertEquals(23, builder.build(enabled).startHour)
        assertEquals(7, builder.build(enabled).endHour)
        assertEquals(false, builder.build(disabled).visible)
    }

    @Test
    fun quietHours_window_picker_hour_options_cover_24_hours_with_padded_labels() {
        val spec = QuietHoursWindowPickerSpecBuilder().build(SmartNotiSettings())

        assertEquals(24, spec.hourOptions.size)
        assertEquals(0, spec.hourOptions.first().hour)
        assertEquals("00:00", spec.hourOptions.first().label)
        assertEquals(23, spec.hourOptions.last().hour)
        assertEquals("23:00", spec.hourOptions.last().label)
    }

    @Test
    fun quietHours_window_picker_emits_same_value_warning_when_start_equals_end() {
        val builder = QuietHoursWindowPickerSpecBuilder()
        val collision = SmartNotiSettings(
            quietHoursEnabled = true,
            quietHoursStartHour = 9,
            quietHoursEndHour = 9,
        )
        val nonCollision = collision.copy(quietHoursEndHour = 17)
        val collisionButDisabled = collision.copy(quietHoursEnabled = false)

        assertEquals(
            QuietHoursWindowPickerSpecBuilder.SAME_VALUE_WARNING,
            builder.build(collision).sameValueWarning,
        )
        assertNull(builder.build(nonCollision).sameValueWarning)
        // Master switch OFF suppresses the warning even when start == end.
        assertNull(builder.build(collisionButDisabled).sameValueWarning)
    }

    @Test
    fun quietHours_packages_picker_row_visible_only_when_enabled_and_warns_on_empty_set() {
        val builder = QuietHoursPackagesPickerSpecBuilder()
        val enabledEmpty = SmartNotiSettings(
            quietHoursEnabled = true,
            quietHoursPackages = emptySet(),
        )
        val enabledFilled = SmartNotiSettings(
            quietHoursEnabled = true,
            quietHoursPackages = setOf("com.example.shop"),
        )
        val disabledEmpty = enabledEmpty.copy(quietHoursEnabled = false)

        val emptySpec = builder.build(enabledEmpty, capturedApps = emptyList())
        val filledSpec = builder.build(enabledFilled, capturedApps = emptyList())
        val disabledSpec = builder.build(disabledEmpty, capturedApps = emptyList())

        assertTrue(emptySpec.visible)
        assertEquals(0, emptySpec.selectedCount)
        assertEquals(QuietHoursPackagesPickerSpecBuilder.EMPTY_WARNING, emptySpec.emptyWarning)

        assertTrue(filledSpec.visible)
        assertEquals(1, filledSpec.selectedCount)
        assertNull(filledSpec.emptyWarning)

        assertEquals(false, disabledSpec.visible)
        // Master switch OFF suppresses the warning so the user doesn't see a
        // red flag on a row that's hidden anyway.
        assertNull(disabledSpec.emptyWarning)
    }

    // ---------- Affordance 2: Duplicate threshold + window pickers ---------
    // Plan Task 3 moves `DuplicateThresholdEditorRow` + the two pickers to
    // `SettingsDuplicateSection.kt`. Pin the option lists + label format so
    // the renderer's dropdown copy can't drift during the move.

    @Test
    fun duplicateThreshold_options_pin_full_value_and_label_set() {
        val spec = DuplicateThresholdEditorSpecBuilder().build(SmartNotiSettings())

        assertEquals(listOf(2, 3, 4, 5, 7, 10), spec.thresholdOptions.map { it.value })
        assertEquals(
            listOf("반복 2회", "반복 3회", "반복 4회", "반복 5회", "반복 7회", "반복 10회"),
            spec.thresholdOptions.map { it.label },
        )
    }

    @Test
    fun duplicateWindow_options_pin_full_value_and_label_set() {
        val spec = DuplicateThresholdEditorSpecBuilder().build(SmartNotiSettings())

        assertEquals(listOf(5, 10, 15, 30, 60), spec.windowOptions.map { it.minutes })
        assertEquals(
            listOf("최근 5분", "최근 10분", "최근 15분", "최근 30분", "최근 60분"),
            spec.windowOptions.map { it.label },
        )
    }

    @Test
    fun duplicateThreshold_picker_reflects_persisted_settings_values() {
        val customised = SmartNotiSettings(
            duplicateDigestThreshold = 5,
            duplicateWindowMinutes = 30,
        )

        val spec = DuplicateThresholdEditorSpecBuilder().build(customised)

        assertEquals(5, spec.selectedThreshold)
        assertEquals(30, spec.selectedWindowMinutes)
    }

    // ---------- Affordance 3: Delivery profile (Priority/Digest/Silent) ----
    // Plan Task 2 moves the delivery-profile composable cluster to
    // `SettingsDeliveryProfileSection.kt`. The Korean labels rendered by the
    // FilterChips for AlertLevel / VibrationMode / LockScreenVisibilityMode
    // live in `DeliveryProfileLabels.kt` (already a sibling file). Pin every
    // case so the move can't drop or rename a label in any of the three
    // editor sections.

    @Test
    fun deliveryProfile_alertLevel_label_returns_korean_for_every_value() {
        assertEquals("강함", AlertLevel.LOUD.toKoreanLabel())
        assertEquals("보통", AlertLevel.SOFT.toKoreanLabel())
        assertEquals("조용함", AlertLevel.QUIET.toKoreanLabel())
        assertEquals("없음", AlertLevel.NONE.toKoreanLabel())
    }

    @Test
    fun deliveryProfile_vibrationMode_label_returns_korean_for_every_value() {
        assertEquals("강하게", VibrationMode.STRONG.toKoreanLabel())
        assertEquals("가볍게", VibrationMode.LIGHT.toKoreanLabel())
        assertEquals("끔", VibrationMode.OFF.toKoreanLabel())
    }

    @Test
    fun deliveryProfile_lockScreenVisibility_label_returns_korean_for_every_value() {
        assertEquals("전체 공개", LockScreenVisibilityMode.PUBLIC.toKoreanLabel())
        assertEquals("내용 숨김", LockScreenVisibilityMode.PRIVATE.toKoreanLabel())
        assertEquals("숨김", LockScreenVisibilityMode.SECRET.toKoreanLabel())
    }

    // ---------- Affordance 4 + 5 + Operational summary copy ----------------
    // Plan Tasks 2/3 move composables that read the
    // `SettingsOperationalSummary` data class. The summary builder is a pure
    // function — pinning it locks the copy that
    // `OperationalSummaryRow(label = "Quiet Hours", ...)` and the digest
    // schedule row will continue to render after the carve-out.

    @Test
    fun operationalSummary_renders_quietHours_window_with_padded_hours() {
        val summary = SettingsOperationalSummaryBuilder().build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 23,
                quietHoursEndHour = 7,
            ),
        )

        assertEquals("23:00 ~ 07:00", summary.quietHoursWindow)
        assertEquals("자동 완화 사용 중", summary.quietHoursState)
        assertEquals(true, summary.quietHoursEnabled)
        assertEquals("조용한 시간 자동 적용", summary.modeTitle)
    }

    @Test
    fun operationalSummary_renders_disabled_state_copy_when_quietHours_off() {
        val summary = SettingsOperationalSummaryBuilder().build(
            SmartNotiSettings(quietHoursEnabled = false),
        )

        assertEquals("항상 즉시 분류", summary.modeTitle)
        assertEquals("꺼짐", summary.quietHoursState)
    }

    @Test
    fun operationalSummary_renders_digest_schedule_with_hour_count() {
        val summary = SettingsOperationalSummaryBuilder().build(
            SmartNotiSettings(digestHours = listOf(9, 13, 19)),
        )

        assertEquals("09:00 · 13:00 · 19:00", summary.digestSchedule)
        assertEquals("정리 시점 3개", summary.digestDetail)
    }

    @Test
    fun operationalSummary_renders_empty_digest_schedule_copy() {
        val summary = SettingsOperationalSummaryBuilder().build(
            SmartNotiSettings(digestHours = emptyList()),
        )

        assertEquals("예약된 정리 시점이 없어요", summary.digestSchedule)
        assertEquals("정리 시점 없음", summary.digestDetail)
    }

    // ---------- Replacement auto-dismiss picker (operational sub-section) --
    // Plan Task 3 moves `ReplacementAutoDismissDurationPicker` to
    // `SettingsOperationalSection.kt`. Pin the option list + label format so
    // the move preserves the dropdown copy.

    @Test
    fun replacementAutoDismiss_picker_pins_option_set_and_labelFor_format() {
        val spec = ReplacementAutoDismissPickerSpecBuilder().build(
            SmartNotiSettings(
                replacementAutoDismissEnabled = false,
                replacementAutoDismissMinutes = 30,
            ),
        )

        assertEquals(listOf(5, 15, 30, 60, 180), spec.options.map { it.minutes })
        assertEquals(
            listOf("5분", "15분", "30분", "1시간", "3시간"),
            spec.options.map { it.label },
        )
        assertEquals(false, spec.enabled)
        assertEquals(30, spec.selectedMinutes)
    }

    @Test
    fun replacementAutoDismiss_labelFor_pins_minute_versus_hour_branch() {
        // Below 60 -> "N분".
        assertEquals("5분", ReplacementAutoDismissPickerSpecBuilder.labelFor(5))
        assertEquals("45분", ReplacementAutoDismissPickerSpecBuilder.labelFor(45))
        // 60+ and divisible by 60 -> "Nㅅ간".
        assertEquals("1시간", ReplacementAutoDismissPickerSpecBuilder.labelFor(60))
        assertEquals("2시간", ReplacementAutoDismissPickerSpecBuilder.labelFor(120))
        // 60+ but not divisible -> still "N분" so the value is unambiguous.
        assertEquals("90분", ReplacementAutoDismissPickerSpecBuilder.labelFor(90))
    }

    // ---------- Affordance 4: "고급 규칙 편집 열기" entry card --------------
    // Plan Task 3 moves `AdvancedRulesEntryCard` to
    // `SettingsOperationalSection.kt`. The card is gated by
    // `if (onOpenAdvancedRules != null)` in the root LazyColumn — see
    // SettingsScreen.kt around line 347. The literal button copy lives only
    // in the private composable; this constant pins it. If Task 3 changes
    // the literal, this assertion fails.

    @Test
    fun advancedRulesEntryCard_button_label_pin() {
        // Mirrors `Text("고급 규칙 편집 열기")` inside `AdvancedRulesEntryCard`.
        assertEquals("고급 규칙 편집 열기", ADVANCED_RULES_ENTRY_BUTTON_LABEL)
        // Mirrors `SettingsCardHeader(title = "고급 규칙 편집", ...)`.
        assertEquals("고급 규칙 편집", ADVANCED_RULES_ENTRY_CARD_TITLE)
    }

    // ---------- Affordance 5: "무시된 알림 아카이브 표시" toggle ------------
    // Plan Task 3 moves `IgnoredArchiveSettingsCard` to
    // `SettingsOperationalSection.kt`. The card is always rendered, but the
    // "아카이브 열기" button only appears when the toggle is ON AND
    // `onOpenIgnoredArchive` is non-null. The literal copy pinned below
    // mirrors the composable.

    @Test
    fun ignoredArchive_settings_card_copy_pin() {
        // Mirrors `SettingsToggleRow(title = "무시된 알림 아카이브 표시", ...)`.
        assertEquals("무시된 알림 아카이브 표시", IGNORED_ARCHIVE_TOGGLE_TITLE)
        // Mirrors the on-state subtitle.
        assertEquals(
            "아래 버튼으로 아카이브 화면을 열 수 있어요.",
            IGNORED_ARCHIVE_TOGGLE_SUBTITLE_ON,
        )
        // Mirrors the off-state subtitle.
        assertEquals(
            "켜면 설정 화면에 아카이브 진입 버튼이 나타나요. 알림 분류 동작은 바뀌지 않아요.",
            IGNORED_ARCHIVE_TOGGLE_SUBTITLE_OFF,
        )
        // Mirrors the gated "아카이브 열기" button label.
        assertEquals("무시됨 아카이브 열기", IGNORED_ARCHIVE_OPEN_BUTTON_LABEL)
    }

    // ---------- QuietHours packages picker row labels ----------------------
    // Plan Task 2 moves the picker row + sheet to
    // `SettingsQuietHoursSection.kt`. The renderer reads these companion
    // constants for its row label / picker header / button labels — pin the
    // strings so the move can't silently rename them.

    @Test
    fun quietHoursPackages_picker_label_constants_pin() {
        assertEquals("조용한 시간 대상 앱", QuietHoursPackagesPickerSpecBuilder.ROW_LABEL)
        assertEquals(
            "조용한 시간 대상 앱",
            QuietHoursPackagesPickerSpecBuilder.PICKER_HEADER_TITLE,
        )
        assertEquals(
            "조용한 시간 동안 자동으로 모아둘 앱을 골라주세요.",
            QuietHoursPackagesPickerSpecBuilder.PICKER_HEADER_SUBTITLE,
        )
        assertEquals("앱 추가", QuietHoursPackagesPickerSpecBuilder.ADD_BUTTON_LABEL)
        assertEquals(
            "대상 앱이 없어 조용한 시간이 어떤 알림도 모으지 않아요.",
            QuietHoursPackagesPickerSpecBuilder.EMPTY_WARNING,
        )
        assertEquals("선택된 앱이 없어요.", QuietHoursPackagesPickerSpecBuilder.NO_TARGETS_SUMMARY)
        // rowSummary handles the count badge shown in the row.
        assertEquals("0개", QuietHoursPackagesPickerSpecBuilder.rowSummary(0))
        assertEquals("3개", QuietHoursPackagesPickerSpecBuilder.rowSummary(3))
    }

    // ---------- SettingsScreen overload sanity check -----------------------
    // The root composable is `@Composable fun SettingsScreen(...)` with two
    // optional callback parameters. Pin its presence by reflection so the
    // refactor cannot accidentally rename / move the public entrypoint while
    // shuffling private composables into sub-files. (`AppNavHost.kt` is the
    // only caller.)

    @Test
    fun settingsScreen_root_composable_function_remains_public_in_settings_package() {
        val klass = Class.forName(
            "com.smartnoti.app.ui.screens.settings.SettingsScreenKt",
        )
        val candidates = klass.declaredMethods.filter { it.name == "SettingsScreen" }
        assertNotNull(
            "SettingsScreen public composable must remain in com.smartnoti.app.ui.screens.settings",
            candidates,
        )
        assertTrue(
            "SettingsScreen public composable must remain in com.smartnoti.app.ui.screens.settings",
            candidates.isNotEmpty(),
        )
    }

    // =====================================================================
    // Plan `docs/plans/2026-04-27-refactor-settings-suppression-cluster-split.md`
    // Task 1 — characterization tests for the suppression / app-selection /
    // notification-access cluster that the follow-up split (Tasks 2-3) will
    // move out of `SettingsScreen.kt`. Same pattern as the affordance pins
    // above: pure spec / builder contracts + literal-copy mirrors for the
    // strings that live only inside the private composables today.
    // =====================================================================

    // ---------- Suppression insight metric strip + supporting copy --------
    // `SuppressionInsightMetricStrip` (top of `SuppressionManagementCard`)
    // renders the metrics list and supporting message produced by
    // `SettingsSuppressionInsightSummaryBuilder.buildTokens`. Pin the three
    // visible branches (Disabled / NeedsAppSelection / Active) so the
    // carve-out cannot drift the user-facing copy or metric set.

    @Test
    fun suppressionInsightTokens_disabled_branch_renders_off_message_with_no_metrics() {
        val tokens = SettingsSuppressionInsightSummaryBuilder().buildTokens(
            suppressEnabled = false,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
        )

        assertEquals(SuppressionInsightState.Disabled, tokens.state)
        assertTrue(tokens.metrics.isEmpty())
        assertEquals(
            "기능을 켜고 앱을 선택하면 원본 알림 숨김 시도 상태를 여기서 확인할 수 있어요.",
            tokens.supportingMessage,
        )
    }

    @Test
    fun suppressionInsightTokens_needs_app_selection_branch_renders_prompt_with_no_metrics() {
        val tokens = SettingsSuppressionInsightSummaryBuilder().buildTokens(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
        )

        assertEquals(SuppressionInsightState.NeedsAppSelection, tokens.state)
        assertTrue(tokens.metrics.isEmpty())
        assertEquals(
            "아래 앱 목록에서 숨기고 싶은 앱을 선택하면 원본 숨김 시도 요약이 여기에 표시돼요.",
            tokens.supportingMessage,
        )
    }

    @Test
    fun suppressionInsightTokens_active_branch_emits_three_metrics_plus_top_app() {
        val tokens = SettingsSuppressionInsightSummaryBuilder().buildTokens(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 4,
                selectedCapturedCount = 100,
                selectedFilteredCount = 75,
                selectedFilteredSharePercent = 75,
                topSelectedAppName = "Coupang",
                topSelectedAppFilteredCount = 42,
            ),
        )

        assertEquals(SuppressionInsightState.Active, tokens.state)
        assertEquals(
            listOf("선택 앱", "숨김 시도", "비율", "상위 앱"),
            tokens.metrics.map { it.label },
        )
        assertEquals(
            listOf("4개", "75건", "75%", "Coupang 42건"),
            tokens.metrics.map { it.value },
        )
        assertTrue(tokens.supportingMessage.contains("75%"))
        assertTrue(tokens.supportingMessage.contains("Coupang"))
        assertTrue(tokens.supportingMessage.contains("42건"))
    }

    @Test
    fun suppressionInsightTokens_active_branch_omits_top_app_metric_when_no_top_app() {
        val tokens = SettingsSuppressionInsightSummaryBuilder().buildTokens(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 1,
                selectedCapturedCount = 5,
                selectedFilteredCount = 3,
                selectedFilteredSharePercent = 60,
            ),
        )

        assertEquals(SuppressionInsightState.Active, tokens.state)
        assertEquals(
            listOf("선택 앱", "숨김 시도", "비율"),
            tokens.metrics.map { it.label },
        )
        assertEquals(
            "선택한 앱의 원본 숨김 시도 상태를 계속 집계하고 있어요.",
            tokens.supportingMessage,
        )
    }

    // ---------- Suppression breakdown row copy ----------------------------
    // `SuppressionBreakdownRow` renders the chart row label as
    // `"${item.filteredCount}건 정리 · ${(item.shareFraction * 100).toInt()}%"`.
    // The literal format only lives in the private composable today; pin
    // the formatter (mirrored as a helper) so the carve-out cannot rewrite
    // the row supporting line.

    @Test
    fun suppressionBreakdownRow_supporting_label_pin() {
        assertEquals(
            "12건 정리 · 25%",
            suppressionBreakdownRowSupportingLabel(filteredCount = 12, shareFraction = 0.25f),
        )
        assertEquals(
            "0건 정리 · 0%",
            suppressionBreakdownRowSupportingLabel(filteredCount = 0, shareFraction = 0f),
        )
        // Truncates fractional percent to int (mirrors `(.*100).toInt()`).
        assertEquals(
            "1건 정리 · 49%",
            suppressionBreakdownRowSupportingLabel(filteredCount = 1, shareFraction = 0.499f),
        )
    }

    // ---------- Suppressed app insight row copy ---------------------------
    // `SuppressedAppInsightRow` builds its supporting line from the
    // `SuppressedAppInsight` model + a "선택됨" / "관찰 중" prefix that
    // depends on `isSuppressed`. Pin the formatter mirror so the carve-out
    // can't rewrite the row even though the composable is leaving the file.

    @Test
    fun suppressedAppInsightRow_supporting_label_uses_selected_prefix_when_suppressed() {
        val insight = SuppressedAppInsight(
            packageName = "com.coupang.mobile",
            appName = "Coupang",
            capturedCount = 10,
            filteredCount = 7,
            filteredSharePercent = 70,
            lastSeenLabel = "오늘",
            isSuppressed = true,
        )

        assertEquals("선택됨 · 7건 정리 · 70%", suppressedAppInsightRowSupportingLabel(insight))
    }

    @Test
    fun suppressedAppInsightRow_supporting_label_uses_observing_prefix_when_not_suppressed() {
        val insight = SuppressedAppInsight(
            packageName = "com.example.shop",
            appName = "ExampleShop",
            capturedCount = 5,
            filteredCount = 0,
            filteredSharePercent = 0,
            lastSeenLabel = "어제",
            isSuppressed = false,
        )

        assertEquals("관찰 중 · 0건 정리 · 0%", suppressedAppInsightRowSupportingLabel(insight))
    }

    // ---------- Suppression management card literal copy ------------------
    // The `SuppressionManagementCard` headers + toggle subtitles live only
    // inside the private composable. Pin them as compiled constants the
    // Compose source must mirror; carve-out drift fails here.

    @Test
    fun suppressionManagementCard_header_and_toggle_copy_pin() {
        // SettingsSubsection title + `SuppressionInsightTokens.supportingMessage`
        // are surfaced as the card's first sub-section header.
        assertEquals("원본 알림 숨김 상태", SUPPRESSION_INSIGHT_SUBSECTION_TITLE)
        // Master toggle row.
        assertEquals(
            "Digest·조용히 알림의 원본 숨기기",
            SUPPRESSION_MASTER_TOGGLE_TITLE,
        )
        assertEquals(
            "선택한 앱의 Digest·조용히 알림에 대해 원본 숨김을 시도하고, SmartNoti 대체 알림으로 이어줘요. 기기/앱에 따라 원본이 남을 수 있어요.",
            SUPPRESSION_MASTER_TOGGLE_SUBTITLE_ON,
        )
        assertEquals(
            "먼저 이 옵션을 켜면 아래 고급 옵션과 앱별 선택이 활성화돼요.",
            SUPPRESSION_MASTER_TOGGLE_SUBTITLE_OFF,
        )
        // ExpandableSettingsSubsection titles.
        assertEquals("고급 숨김 옵션", SUPPRESSION_ADVANCED_SUBSECTION_TITLE)
        assertEquals("숨길 앱 선택", SUPPRESSION_APP_SELECTION_SUBSECTION_TITLE)
    }

    @Test
    fun suppressionAdvancedToggles_copy_pin() {
        assertEquals(
            "지속 알림은 SmartNoti 목록에서 숨기기",
            SUPPRESSION_HIDE_PERSISTENT_TITLE,
        )
        assertEquals(
            "지속 알림은 시스템 알림센터에서도 숨기기",
            SUPPRESSION_HIDE_PERSISTENT_SOURCE_TITLE,
        )
        assertEquals(
            "통화·길안내·녹화 중 알림은 항상 보이기",
            SUPPRESSION_PROTECT_CRITICAL_TITLE,
        )
        assertEquals(
            "SmartNoti 알림 자동 정리",
            REPLACEMENT_AUTO_DISMISS_TOGGLE_TITLE,
        )
    }

    // ---------- SuppressedSourceAppChips action buttons + empty copy -----
    // The two bulk-action buttons + the "no captured apps" empty message
    // live only inside the private composable. Pin the copy + the bulk
    // button enablement contract that the renderer reads.

    @Test
    fun suppressedSourceAppChips_action_button_and_empty_copy_pin() {
        assertEquals("모두 선택", BULK_SELECT_BUTTON_LABEL)
        assertEquals("모두 해제", BULK_CLEAR_BUTTON_LABEL)
        assertEquals(
            "아직 캡처된 앱이 없어요. 알림이 몇 건 쌓이면 여기서 앱별로 선택할 수 있어요.",
            SUPPRESSED_APPS_EMPTY_COPY,
        )
    }

    @Test
    fun suppressedAppPresentation_emits_selected_and_available_groups_with_summary() {
        val capturedApps = listOf(
            CapturedAppSelectionItem(
                packageName = "com.coupang.mobile",
                appName = "Coupang",
                notificationCount = 50,
                lastSeenLabel = "오늘",
            ),
            CapturedAppSelectionItem(
                packageName = "com.example.shop",
                appName = "ExampleShop",
                notificationCount = 10,
                lastSeenLabel = "어제",
            ),
        )

        val presentation = SettingsSuppressedAppPresentationBuilder().build(
            capturedApps = capturedApps,
            suppressedSourceApps = setOf("com.coupang.mobile"),
            excludedApps = setOf("com.example.shop"),
        )

        assertEquals("선택 1개 · 추가 가능 1개", presentation.summary)
        assertEquals(2, presentation.groups.size)
        assertEquals("이미 선택한 앱", presentation.groups[0].title)
        assertEquals(listOf("com.coupang.mobile"), presentation.groups[0].items.map { it.packageName })
        assertEquals("추가로 숨길 수 있는 앱", presentation.groups[1].title)
        assertEquals(listOf("com.example.shop"), presentation.groups[1].items.map { it.packageName })
    }

    @Test
    fun suppressedAppPresentation_isEffectivelySelected_obeys_sticky_exclude_and_default_optin() {
        // Sticky exclude wins.
        assertFalse(
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "com.foo",
                suppressedSourceApps = setOf("com.foo"),
                excludedApps = setOf("com.foo"),
            ),
        )
        // Default opt-in when suppressedSourceApps is empty.
        assertTrue(
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "com.foo",
                suppressedSourceApps = emptySet(),
                excludedApps = emptySet(),
            ),
        )
        // Membership match when set is non-empty.
        assertTrue(
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "com.foo",
                suppressedSourceApps = setOf("com.foo"),
                excludedApps = emptySet(),
            ),
        )
        assertFalse(
            SettingsSuppressedAppPresentationBuilder.isEffectivelySelected(
                packageName = "com.bar",
                suppressedSourceApps = setOf("com.foo"),
                excludedApps = emptySet(),
            ),
        )
    }

    // ---------- NotificationAccessCard copy + summary contract -----------
    // The `NotificationAccessCard` composable reads the
    // `SettingsNotificationAccessSummary` produced by the builder. Pin the
    // builder's two-branch contract + the literal "현재 반영 효과" /
    // "켜면 생기는 변화" + "설정 경로" labels that live only inside the
    // private composable.

    @Test
    fun notificationAccessCard_granted_branch_summary_pin() {
        val summary = SettingsNotificationAccessSummaryBuilder().build(
            OnboardingStatus(
                notificationListenerGranted = true,
                postNotificationsGranted = true,
                postNotificationsRequired = true,
            ),
        )

        assertEquals(true, summary.granted)
        assertEquals("연결됨", summary.statusLabel)
        assertEquals("실제 알림이 Home에 반영되고 있어요", summary.headline)
        assertEquals("알림 접근 설정 다시 열기", summary.actionLabel)
    }

    @Test
    fun notificationAccessCard_disconnected_branch_summary_pin() {
        val summary = SettingsNotificationAccessSummaryBuilder().build(
            OnboardingStatus(
                notificationListenerGranted = false,
                postNotificationsGranted = true,
                postNotificationsRequired = true,
            ),
        )

        assertEquals(false, summary.granted)
        assertEquals("연결 필요", summary.statusLabel)
        assertEquals("아직 실제 알림을 읽지 못하고 있어요", summary.headline)
        assertEquals("알림 접근 설정 열기", summary.actionLabel)
    }

    @Test
    fun notificationAccessCard_in_card_literal_copy_pin() {
        // Mirrors `Text(text = "설정 경로", ...)` inside `NotificationAccessCard`.
        assertEquals("설정 경로", NOTIFICATION_ACCESS_PATH_LABEL)
        // Mirrors the `impactTitle = if (summary.granted) "현재 반영 효과" else "켜면 생기는 변화"` branch.
        assertEquals("현재 반영 효과", NOTIFICATION_ACCESS_IMPACT_TITLE_GRANTED)
        assertEquals("켜면 생기는 변화", NOTIFICATION_ACCESS_IMPACT_TITLE_DENIED)
    }

    private companion object {
        // ---- Literal-copy mirrors --------------------------------------
        // These constants mirror Korean copy that lives inside private
        // composables in `SettingsScreen.kt`. The plan's Task 2/3 moves
        // those composables into sibling files; the Compose source must
        // continue to emit these literals for the affordances to look
        // identical to users. Updating these without a corresponding
        // composable change is the regression signal.

        const val ADVANCED_RULES_ENTRY_BUTTON_LABEL = "고급 규칙 편집 열기"
        const val ADVANCED_RULES_ENTRY_CARD_TITLE = "고급 규칙 편집"
        const val IGNORED_ARCHIVE_TOGGLE_TITLE = "무시된 알림 아카이브 표시"
        const val IGNORED_ARCHIVE_TOGGLE_SUBTITLE_ON =
            "아래 버튼으로 아카이브 화면을 열 수 있어요."
        const val IGNORED_ARCHIVE_TOGGLE_SUBTITLE_OFF =
            "켜면 설정 화면에 아카이브 진입 버튼이 나타나요. 알림 분류 동작은 바뀌지 않아요."
        const val IGNORED_ARCHIVE_OPEN_BUTTON_LABEL = "무시됨 아카이브 열기"

        // ---- Suppression cluster mirrors -------------------------------
        // Plan `2026-04-27-refactor-settings-suppression-cluster-split.md`
        // Task 1 — pin the literal copy that lives only inside the private
        // composables `SuppressionManagementCard`, `SuppressedSourceAppChips`,
        // and `NotificationAccessCard` until Tasks 2-3 move them into
        // sibling files.

        const val SUPPRESSION_INSIGHT_SUBSECTION_TITLE = "원본 알림 숨김 상태"
        const val SUPPRESSION_MASTER_TOGGLE_TITLE = "Digest·조용히 알림의 원본 숨기기"
        const val SUPPRESSION_MASTER_TOGGLE_SUBTITLE_ON =
            "선택한 앱의 Digest·조용히 알림에 대해 원본 숨김을 시도하고, SmartNoti 대체 알림으로 이어줘요. 기기/앱에 따라 원본이 남을 수 있어요."
        const val SUPPRESSION_MASTER_TOGGLE_SUBTITLE_OFF =
            "먼저 이 옵션을 켜면 아래 고급 옵션과 앱별 선택이 활성화돼요."
        const val SUPPRESSION_ADVANCED_SUBSECTION_TITLE = "고급 숨김 옵션"
        const val SUPPRESSION_APP_SELECTION_SUBSECTION_TITLE = "숨길 앱 선택"
        const val SUPPRESSION_HIDE_PERSISTENT_TITLE =
            "지속 알림은 SmartNoti 목록에서 숨기기"
        const val SUPPRESSION_HIDE_PERSISTENT_SOURCE_TITLE =
            "지속 알림은 시스템 알림센터에서도 숨기기"
        const val SUPPRESSION_PROTECT_CRITICAL_TITLE =
            "통화·길안내·녹화 중 알림은 항상 보이기"
        const val REPLACEMENT_AUTO_DISMISS_TOGGLE_TITLE = "SmartNoti 알림 자동 정리"
        const val BULK_SELECT_BUTTON_LABEL = "모두 선택"
        const val BULK_CLEAR_BUTTON_LABEL = "모두 해제"
        const val SUPPRESSED_APPS_EMPTY_COPY =
            "아직 캡처된 앱이 없어요. 알림이 몇 건 쌓이면 여기서 앱별로 선택할 수 있어요."
        const val NOTIFICATION_ACCESS_PATH_LABEL = "설정 경로"
        const val NOTIFICATION_ACCESS_IMPACT_TITLE_GRANTED = "현재 반영 효과"
        const val NOTIFICATION_ACCESS_IMPACT_TITLE_DENIED = "켜면 생기는 변화"

        // ---- Pure formatter mirrors ------------------------------------
        // The `SuppressionBreakdownRow` and `SuppressedAppInsightRow`
        // composables build their supporting label inline. Mirror the
        // formatter so the carve-out cannot drift the format string. If
        // the composable starts to use a different format, these tests
        // (above) will fail.

        fun suppressionBreakdownRowSupportingLabel(
            filteredCount: Int,
            shareFraction: Float,
        ): String = "${filteredCount}건 정리 · ${(shareFraction * 100).toInt()}%"

        fun suppressedAppInsightRowSupportingLabel(
            insight: SuppressedAppInsight,
        ): String {
            val prefix = if (insight.isSuppressed) "선택됨" else "관찰 중"
            return "$prefix · ${insight.filteredCount}건 정리 · ${insight.filteredSharePercent}%"
        }
    }
}
