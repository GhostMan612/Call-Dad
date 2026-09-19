// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
// Phase 1 host-side smoke: pure-JVM routes contract. No Android deps.
package com.calldad

import com.calldad.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutesTest {

    @Test
    fun routes_areDistinct() {
        val all = setOf(Routes.HOME, Routes.CALL, Routes.PTT, Routes.GAME, Routes.HELPER)
        assertEquals(5, all.size)
    }

    @Test
    fun home_isStartDestination() {
        assertEquals("home", Routes.HOME)
    }

    @Test
    fun allRoutes_nonBlank() {
        listOf(Routes.HOME, Routes.CALL, Routes.PTT, Routes.GAME, Routes.HELPER)
            .forEach { assertTrue(it.isNotBlank()) }
    }
}
