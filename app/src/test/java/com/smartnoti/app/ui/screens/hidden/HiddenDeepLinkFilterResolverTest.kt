package com.smartnoti.app.ui.screens.hidden

import com.smartnoti.app.domain.usecase.SilentGroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Resolver contract for turning Hidden 화면 deep-link query params into a [SilentGroupKey]
 * the screen can use as `initialFilter`. Pinned here because the listener-side group
 * summary's `contentIntent` serialises these exact two params (see
 * `SilentHiddenSummaryNotifier.createGroupContentIntent`) and any drift silently breaks
 * the tray → app deep-link.
 *
 * Scope of the resolver:
 * - Sender wins over packageName when both are present (mirrors [SilentGroupKey] precedence).
 * - Blank (or whitespace-only) values are treated as absent so percent-encoded spaces don't
 *   accidentally materialise as a `Sender("  ")` group.
 * - Both absent ⇒ no filter (null).
 */
class HiddenDeepLinkFilterResolverTest {

    @Test
    fun resolve_returns_null_when_both_params_absent() {
        assertNull(HiddenDeepLinkFilterResolver.resolve(sender = null, packageName = null))
    }

    @Test
    fun resolve_returns_null_when_both_params_blank() {
        assertNull(HiddenDeepLinkFilterResolver.resolve(sender = "   ", packageName = ""))
    }

    @Test
    fun resolve_builds_sender_filter_from_sender_param() {
        assertEquals(
            SilentGroupKey.Sender("엄마"),
            HiddenDeepLinkFilterResolver.resolve(sender = "엄마", packageName = null),
        )
    }

    @Test
    fun resolve_trims_whitespace_from_sender() {
        assertEquals(
            SilentGroupKey.Sender("엄마"),
            HiddenDeepLinkFilterResolver.resolve(sender = "  엄마 ", packageName = null),
        )
    }

    @Test
    fun resolve_builds_app_filter_from_package_param_when_sender_absent() {
        assertEquals(
            SilentGroupKey.App("com.kakao.talk"),
            HiddenDeepLinkFilterResolver.resolve(sender = null, packageName = "com.kakao.talk"),
        )
    }

    @Test
    fun resolve_prefers_sender_when_both_params_present() {
        assertEquals(
            SilentGroupKey.Sender("엄마"),
            HiddenDeepLinkFilterResolver.resolve(sender = "엄마", packageName = "com.kakao.talk"),
        )
    }

    @Test
    fun resolve_falls_back_to_package_when_sender_is_blank() {
        assertEquals(
            SilentGroupKey.App("com.kakao.talk"),
            HiddenDeepLinkFilterResolver.resolve(sender = "   ", packageName = "com.kakao.talk"),
        )
    }

    @Test
    fun resolve_returns_null_when_package_is_blank_and_sender_is_null() {
        assertNull(HiddenDeepLinkFilterResolver.resolve(sender = null, packageName = "   "))
    }
}
