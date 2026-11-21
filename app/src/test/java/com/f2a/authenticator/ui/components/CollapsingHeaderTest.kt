package com.f2a.authenticator.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollapsingHeaderTest {

    @Test
    fun `scrolling up collapses header and never passes its height`() {
        assertEquals(-40f, nextCollapsingOffset(0f, -40f, 200, canCollapse = true), 0f)
        assertEquals(-200f, nextCollapsingOffset(-180f, -90f, 200, canCollapse = true), 0f)
    }

    @Test
    fun `scrolling down expands header and never passes zero`() {
        assertEquals(-50f, nextCollapsingOffset(-120f, 70f, 200, canCollapse = true), 0f)
        assertEquals(0f, nextCollapsingOffset(-30f, 90f, 200, canCollapse = true), 0f)
    }

    @Test
    fun `header stays put when list cannot scroll but can still expand`() {
        assertEquals(0f, nextCollapsingOffset(0f, -60f, 200, canCollapse = false), 0f)
        assertEquals(-20f, nextCollapsingOffset(-80f, 60f, 200, canCollapse = false), 0f)
    }

    @Test
    fun `offset is clamped when header has no height or shrinks`() {
        assertEquals(0f, nextCollapsingOffset(-50f, -10f, 0, canCollapse = true), 0f)
        assertEquals(-100f, nextCollapsingOffset(-180f, 0f, 100, canCollapse = true), 0f)
    }

    @Test
    fun `state reports only the consumed part of a scroll`() {
        val state = CollapsingHeaderState().apply { heightPx = 100 }
        assertEquals(-60f, state.scrollBy(-60f, canCollapse = true), 0f)
        assertEquals(-40f, state.scrollBy(-90f, canCollapse = true), 0f)
        assertEquals(-100f, state.offsetPx, 0f)
        assertEquals(0f, state.scrollBy(-10f, canCollapse = true), 0f)
        state.expand()
        assertEquals(0f, state.offsetPx, 0f)
    }

    @Test
    fun `copy snackbar is only needed when no countdown banner will show`() {
        assertTrue(CopyFeedback.needsSnackbar(0))
        assertTrue(CopyFeedback.needsSnackbar(-1))
        assertFalse(CopyFeedback.needsSnackbar(30))
    }
}
