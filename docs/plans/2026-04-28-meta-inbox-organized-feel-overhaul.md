---
status: planned
type: meta
kind: meta-plan
related-rule: ../../.claude/rules/ui-improvement.md
related-journey: ../journeys/inbox-unified.md
---

# Meta — 정리함이 "정돈된 느낌" 으로 보이도록 inbox 통합 탭 시각 정합 overhaul

> **For Hermes:** This plan is **decision-first** and **discovery-first**. The user's complaint is qualitative ("정리함이 정돈된 느낌이 전혀 없어 / the inbox doesn't feel organized at all"). Translating that into shippable tasks requires (1) confirming the audit findings on a fresh device, (2) the user picks which 2–3 of the proposed fixes to actually implement, and (3) **only then** a follow-up implementation plan (or plans) is written and routed through `plan-implementer`. This PR is purely the meta-plan markdown + the GitHub issue referenced below. Apply `kind: meta-plan` carve-out at PM review.

**Goal:** Make the `Routes.Inbox` 통합 정리함 탭 (Digest / 보관 중 / 처리됨 세 서브탭) read as **calm, scannable, intentional** — not as a stack of generic Material cards with redundant chrome and competing focal points. The product intent in `.claude/rules/ui-improvement.md` is explicit: *"Make Digest information feel organized and scannable"* (UX priority #2). Empirically the user is reporting the opposite. This plan enumerates the specific visual debt the audit (2026-04-28, emulator-5554, post-PR #418/#452) found and proposes 5 concrete, scope-bounded fixes. The user picks which to ship; each chosen fix becomes its own small implementation plan and PR.

**Architecture:** No production code in this PR. Each fix below is sized to be **one screen, one theme, one PR** so that `plan-implementer` can ship them independently and `journey-tester` can re-verify `inbox-unified.md` per ship. No DB, no DAO, no settings, no behavior change — purely Compose surface treatment.

**Tech stack:** Kotlin, Jetpack Compose (Material 3), existing `SmartSurfaceCard` / `ScreenHeader` / `InboxTabRow` / `DigestGroupCard` primitives, existing dark theme tokens.

---

## Product intent / assumptions

- The user's complaint is the **qualitative experience** of the 정리함 탭 — not any specific bug. SmartNoti's product intent: *calm, trustworthy, efficient. Favor clarity over decoration.* The audit shows the inbox is currently **busy** (many simultaneous focal weights, mixed accent usage, redundant text, three different card visual styles on one screen).
- This is a meta-plan because (a) the user has not chosen which fixes to apply, (b) the fixes range from 1-hour styling tweaks to a half-day card-system rework, and (c) we should not silently ship 5 visual changes at once — each should be a separately reviewable PR so any single regression is easy to revert.
- The audit is grounded in **emulator-5554 screenshots captured 2026-04-28 03:48 KST** with seeded data (4 Digest groups, 11 archived, 12 processed). All findings reference visible artifacts — no speculation.
- Fixes that touch behavior (sort default, count semantics, bulk-action confirmations) are **out of scope** for this overhaul — they go into separate behavior plans because they require contract decisions, not visual judgement.
- The Digest sub-tab is the **dominant pain surface** (it shows complex group cards with previews + chips + bulk action buttons). 보관 중 / 처리됨 are already calmer (single summary card + collapsed group rows). The fixes prioritize Digest first.

### 사용자 판단이 필요한 결정 (Risks / open questions 으로 옮김)

- Which 2–3 of the 5 proposed fixes to actually ship in a single sprint vs defer.
- Whether to also reduce `PREVIEW_LIMIT` from 3 to 2 inside `DigestGroupCard` (each preview row is itself a full Compose Card with chips and metadata — even 3 previews per group fills the screen with cards inside cards).
- Whether the orange "Digest" status chip on every preview row should be removed entirely when the row is *inside* a group card whose header already shows count `3건` in the same orange accent (currently appears 3× per group + 1× group header = 4× same accent per group).

---

## Audit findings (2026-04-28, emulator-5554)

Captured screenshots: `/tmp/ui-emu-inbox-digest.png`, `/tmp/ui-emu-inbox-digest-scroll.png`, `/tmp/ui-emu-archived.png`, `/tmp/ui-emu-processed.png`, `/tmp/ui-emu-digest-fresh.png`.

### F1 — Digest sub-tab: cards inside cards (highest impact)

**Evidence:** `/tmp/ui-emu-inbox-digest.png` middle section — the Shell group card (outer rounded border) contains 3 preview "cards" (`Shell / 광고 / 30%` each with its own outlined surface, chips, timestamp). Outer card + inner cards + chip row create three nested rectangle layers in 600px of vertical space.

**Rule violated:** *"Replace default-looking cards with more intentional container styling"* + *"Use section headers and grouped cards for information density"* (NOT "cards inside cards"). Also: *"Improve typography hierarchy before adding more color"*.

**Why it feels cluttered:** Every preview row is structurally a full notification card — header app name, title, body, status chip, reason chips, timestamp. Three of those stacked inside a parent card with its own header + helper text + bulk action row reads as a mini-screen, not as a preview list.

**Proposed fix (small):** Replace each preview row inside `DigestGroupCard` with a flat **divider-separated row** — no inner card surface, no inner padding box, just `Title / body (1 line) / metadata` in `title-medium / body-small / label-small` typography with a 1dp `outlineVariant` divider between rows. Outer card border and bulk actions stay. Reason chips on preview rows can be reduced to a single dominant tag (or hidden — they already appear on the Detail screen).

### F2 — Header chrome eats 30% of first screen

**Evidence:** `/tmp/ui-emu-digest-fresh.png` top — `정리함` eyebrow (40dp tall row) → `알림 정리함` title (48dp) → `Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요.` subtitle (52dp wrapped to 2 lines) → padding → `정렬: 최신순 ▼` row (right-aligned, 48dp) → padding → `InboxTabRow` (88dp). Total header zone ≈ 600 / 2400 px = **25% of the screen**, before any content.

**Rule violated:** *"Cleaner hierarchy, tighter spacing rhythm"* + *"Keep dense screens readable with grouped sections"*. The subtitle is essentially a doc string repeating the eyebrow + title; on every visit the user re-reads the same explanation.

**Why it feels cluttered:** First content card (Shell group header) sits below a massive header. A returning user — who already knows what 정리함 means — pays the explanatory tax every visit.

**Proposed fix (smallest):** Drop the subtitle line on the Inbox screen specifically (keep eyebrow + title). Move `정렬: <mode>` dropdown into the same row as the title (right-aligned next to it) instead of its own row below. Estimated header height drops from ~600px to ~280px. The eyebrow + title alone already establishes context.

### F3 — Tab segments and group header chips share the same orange accent

**Evidence:** `/tmp/ui-emu-inbox-digest.png` — selected `Digest · 4건` segment uses primary blue tint (correct). But each group card header shows a **bright orange `3건` badge** (e.g. Shell group), and each preview row also shows an **orange `Digest` status chip**. On a single screen with 2 group cards and 4 previews, orange appears 6 times. Per `.claude/rules/ui-improvement.md`: *"Use accent color sparingly for selection, status, and primary actions"*.

**Rule violated:** Accent restraint. Orange is being used for both *count* (group `3건`) and *status* (per-row `Digest` chip) on rows that are already inside a Digest sub-tab — the status chip is redundant.

**Why it feels cluttered:** Orange dots scattered across the screen create visual noise that competes with the actual content (titles, bodies). Orange should be reserved for one job.

**Proposed fix (small):** Remove the per-row orange `Digest` status chip when the row appears inside the Digest sub-tab — the parent context (sub-tab name, group card header) already declares status. Keep the per-group count badge (`3건`) but downgrade it from orange-filled to a neutral outlined chip (`outlineVariant` border, `onSurfaceVariant` text). Result: orange appears once on the screen — the selected sub-tab segment.

### F4 — Three different card visual languages on one screen

**Evidence:** Within `/tmp/ui-emu-archived.png` alone:
1. **Summary card** (`1개 앱에서 11건을 보관 중이에요.` + helper text + outlined `전체 숨긴 알림 모두 지우기` button) — large, navy-tinted, ~280px tall.
2. **Group row card** (`Shell` + `Shell 숨긴 알림 11건` + `11건` count chip + `▼` chevron, collapsed) — outlined, ~200px tall.
3. **Bottom nav** — pill-shaped tonal background.

In `/tmp/ui-emu-inbox-digest.png` add a 4th: **preview row card** (inside group card). Each card uses different corner radii, different border treatments, different padding scales.

**Rule violated:** *"Favor reusable polished containers over one-off styling"* + *"Use consistent corner radii, spacing, and card elevation/shadow treatment"*.

**Why it feels cluttered:** No visual rhythm. The eye has to recalibrate at every section because card systems don't carry forward.

**Proposed fix (medium):** Audit and consolidate to **two** card primitives across the inbox:
- `InboxSectionCard` — for summary + group cards (large, padded, corner radius 16dp, 1dp `outlineVariant` border, no fill).
- `InboxRowItem` — for preview rows inside groups (NO card surface — just typography + divider, see F1).

Promote both to `ui/components/inbox/` and replace ad-hoc `Card { ... }` calls. Same primitives reused on Hidden too.

### F5 — Summary card on 보관 중 / 처리됨 says the same thing twice

**Evidence:** `/tmp/ui-emu-archived.png` — the tab segment already shows `보관 중 · 11건`. The summary card directly below repeats: `1개 앱에서 11건을 보관 중이에요.` Then the group card row below repeats: `Shell 숨긴 알림 11건` + `11건` chip. The number `11` appears **four times** in the top half of the screen.

**Rule violated:** *"Improve typography hierarchy before adding more color"* + *"Replace default-looking cards with more intentional container styling"*. The summary card is currently a large surface that holds redundant text + a tertiary action (`전체 숨긴 알림 모두 지우기`).

**Why it feels cluttered:** Repetition reads as poor editing. The summary card occupies 280px of premium real estate to repeat what the tab already said.

**Proposed fix (small):** Either (a) demote the summary card to a single-line `assist chip` + a text-button `모두 지우기` placed inside the InboxTabRow row's trailing slot, OR (b) remove the count restatement entirely and keep only the helper line `같은 앱의 여러 알림은 한 카드로 모아서 보여줘요.` as compact body-small under the tab row. Decision left to the implementation plan.

---

## Tasks (decision phase)

This is a meta-plan. The first decision is **which fixes to ship and in what order**.

### Decision 1 — Pick the cohort

User picks 2–3 of {F1, F2, F3, F4, F5} to ship first. Recommendation order by impact/effort:

1. **F2** (header trim) — smallest change, biggest first-impression payoff. ~30 min.
2. **F3** (accent restraint) — small change, high "calm" payoff. ~1 hour.
3. **F1** (cards-inside-cards collapse) — medium change, biggest "organized" payoff. Half day.
4. **F5** (summary card demotion on archived/processed) — small. ~1 hour.
5. **F4** (card primitive consolidation) — medium-large refactor that locks in F1+F5. Half day. Best done **after** F1+F5 ship to avoid relitigating designs mid-refactor.

### Decision 2 — Per-fix implementation plans

Once cohort is picked, for each fix create `docs/plans/2026-04-28-inbox-organized-fix-<f-id>.md` with:
- Single-screen scope.
- Test-first task list (Compose UI test or contract helper test).
- ADB verification recipe pointing back to `inbox-unified.md`.
- `journey-tester` re-runs `inbox-unified.md` recipe after each PR ships.

### Decision 3 — Defer or reject

Any fix the user does not pick gets logged in `inbox-unified.md` Known gaps with date stamp + a pointer back to this meta-plan, so future audits don't re-flag the same finding.

---

## Risks / open questions

- **Q1:** Does removing the per-row `Digest` chip (F3) reduce trust ("how do I know this row was bundled")? Counter: the row is already inside the Digest sub-tab + inside a Digest group card; the status is over-declared. Safety net: chip is preserved on Detail screen and on standalone `Routes.Digest` deep-link entries (since deep-link users may arrive without the sub-tab context).
- **Q2:** F4's consolidation may collide with `SmartSurfaceCard` (already a polished primitive used widely). Question for implementer: extend `SmartSurfaceCard` with optional row mode, or add new `InboxRowItem`? Decided in F4's implementation plan, not here.
- **Q3:** F1's flat-divider preview rows lose the per-row tap target's *visual* affordance (no card surface = doesn't look tappable). Counter: a chevron icon at row end + ripple on tap is the standard Material list-item pattern; affordance is preserved without the box. Add a 1dp divider tinted `outlineVariant` for separation.
- **Q4:** Standalone `Routes.Digest` deep-link entry currently renders the full `DigestScreen` Standalone mode (see `DigestScreenMode`). Any visual change to `DigestGroupCard` body (F1, F3) flows through to standalone too — this is intentional and consistent, but `journey-tester` should sanity-check standalone post-ship.
- **Q5:** Should the Inbox sub-tab counts (`Digest · 4건`) become tonal pills instead of inline-text-with-bullet? Out of scope of this meta-plan but worth flagging — the bullet `·` is decoration; a pill is more scannable. Defer to a separate plan if user wants it.

---

## Verification (per fix, post-ship)

`journey-tester` runs `docs/journeys/inbox-unified.md` Verification recipe in full against emulator-5554. `last-verified` bumps only on a clean recipe run. UI sweep follows: `ui-ux-inspector` re-captures `/tmp/ui-emu-inbox-digest.png` and confirms the targeted finding no longer reproduces. Add `### YYYY-MM-DD (ui-ux sweep, post-F<n>)` entry to `docs/journeys/README.md` Verification log with `CLEAN` verdict for the targeted finding.

---

## Why this is a meta-plan, not a regular plan

- The change is **qualitative product judgment**, not a contract or behavior fix.
- It bundles 5 candidate fixes that need user prioritization; a regular plan would either be too big (do-all-five) or arbitrary (pick-one-without-asking).
- Each candidate fix, once approved, becomes its own regular plan that `plan-implementer` can pick up via the issue-driven loop.

---

## Change log

- 2026-04-28: Drafted by `ui-ux-inspector` after audit of emulator-5554 screenshots in response to user complaint "정리함이 정돈된 느낌이 전혀 없어". Five findings (F1–F5) sized for separate PRs. Awaiting user decision on cohort.
