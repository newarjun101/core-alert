package com.arjun.core_alert.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRouteTest {

    @Test
    fun `fromRoute resolves each known route`() {
        assertEquals(AppRoute.HOME, AppRoute.fromRoute("home"))
        assertEquals(AppRoute.SETTINGS, AppRoute.fromRoute("settings"))
        assertEquals(AppRoute.PRIVACY, AppRoute.fromRoute("privacy"))
    }

    @Test
    fun `fromRoute falls back to home for unknown or missing routes`() {
        assertEquals(AppRoute.HOME, AppRoute.fromRoute("unknown"))
        assertEquals(AppRoute.HOME, AppRoute.fromRoute(null))
    }

    @Test
    fun `entries expose one route per drawer destination`() {
        assertEquals(listOf("home", "settings", "privacy"), AppRoute.entries.map { it.route })
    }
}
