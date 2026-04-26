package com.smartnoti.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 1.
 *
 * Pins the contract for the new `quietHoursPackages` settings field and its
 * three setters (`setQuietHoursPackages` / `addQuietHoursPackage` /
 * `removeQuietHoursPackage`):
 *  - The default mirrors the previously-hardcoded `setOf("com.coupang.mobile")`
 *    in the classifier wiring so existing users see identical behavior.
 *  - The bulk setter persists arbitrary sets including `emptySet()`.
 *  - `addQuietHoursPackage` is dedup-idempotent.
 *  - `removeQuietHoursPackage` allows the set to drain to empty.
 *  - The new stringSet key does not cross-contaminate the existing
 *    `suppressedSourceApps` / `suppressedSourceAppsExcluded` keys.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryQuietHoursPackagesTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        SettingsRepository.clearInstanceForTest()
        repository = SettingsRepository.getInstance(context)
        repository.clearAllForTest()
    }

    @After
    fun tearDown() {
        SettingsRepository.clearInstanceForTest()
    }

    @Test
    fun default_quietHoursPackages_mirrors_legacy_hardcoded_set() = runBlocking {
        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.coupang.mobile"), settings.quietHoursPackages)
    }

    @Test
    fun setQuietHoursPackages_persists_and_round_trips() = runBlocking {
        repository.setQuietHoursPackages(setOf("com.baemin", "com.aliexpress"))

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.baemin", "com.aliexpress"), settings.quietHoursPackages)
    }

    @Test
    fun addQuietHoursPackage_dedups_existing_member() = runBlocking {
        repository.setQuietHoursPackages(setOf("com.coupang.mobile"))

        repository.addQuietHoursPackage("com.coupang.mobile")

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.coupang.mobile"), settings.quietHoursPackages)
    }

    @Test
    fun addQuietHoursPackage_appends_new_member() = runBlocking {
        repository.setQuietHoursPackages(setOf("com.coupang.mobile"))

        repository.addQuietHoursPackage("com.baemin")

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.coupang.mobile", "com.baemin"), settings.quietHoursPackages)
    }

    @Test
    fun removeQuietHoursPackage_drops_member_and_allows_empty_set() = runBlocking {
        repository.setQuietHoursPackages(setOf("com.coupang.mobile"))

        repository.removeQuietHoursPackage("com.coupang.mobile")

        val settings = repository.observeSettings().first()
        assertEquals(emptySet<String>(), settings.quietHoursPackages)
    }

    @Test
    fun removeQuietHoursPackage_unknown_is_noop() = runBlocking {
        repository.setQuietHoursPackages(setOf("com.coupang.mobile"))

        repository.removeQuietHoursPackage("com.never-added")

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.coupang.mobile"), settings.quietHoursPackages)
    }

    @Test
    fun setQuietHoursPackages_empty_set_persists_explicitly() = runBlocking {
        repository.setQuietHoursPackages(emptySet())

        val settings = repository.observeSettings().first()
        assertEquals(emptySet<String>(), settings.quietHoursPackages)
    }

    @Test
    fun quietHoursPackages_does_not_contaminate_suppressedSourceApps() = runBlocking {
        repository.setSuppressedSourceApps(setOf("com.foo"))
        repository.setSuppressedSourceAppExcluded("com.bar", excluded = true)

        repository.setQuietHoursPackages(setOf("com.baemin", "com.aliexpress"))

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.baemin", "com.aliexpress"), settings.quietHoursPackages)
        assertEquals(setOf("com.foo"), settings.suppressedSourceApps)
        assertTrue("com.bar" in settings.suppressedSourceAppsExcluded)
        assertFalse("quietHoursPackages should not leak into suppressedSourceApps", "com.baemin" in settings.suppressedSourceApps)
    }

    @Test
    fun suppressedSourceApps_changes_do_not_affect_quietHoursPackages() = runBlocking {
        repository.setQuietHoursPackages(setOf("com.baemin"))
        repository.setSuppressedSourceApps(setOf("com.foo", "com.bar"))

        val settings = repository.observeSettings().first()
        assertEquals(setOf("com.baemin"), settings.quietHoursPackages)
    }
}
