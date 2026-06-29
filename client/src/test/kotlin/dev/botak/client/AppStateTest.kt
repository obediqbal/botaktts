package dev.botak.client

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [AppState]'s now-playing text flow.
 *
 * Verifies that [AppState.updateNowPlaying] sets the flow value, [AppState.clearNowPlaying]
 * resets it to empty, and the initial value is empty. Reads [kotlinx.coroutines.flow.StateFlow.value]
 * directly, so no coroutine scope is required.
 */
class AppStateTest {
    /** The now-playing text starts empty. */
    @Test
    fun `nowPlayingText is initially empty`() {
        val state = AppState()
        assertEquals("", state.nowPlayingText.value)
    }

    /** `updateNowPlaying` sets the current flow value. */
    @Test
    fun `updateNowPlaying sets the flow value`() {
        val state = AppState()
        state.updateNowPlaying("hello world")
        assertEquals("hello world", state.nowPlayingText.value)
    }

    /** `clearNowPlaying` resets the flow value to empty. */
    @Test
    fun `clearNowPlaying resets the flow value to empty`() {
        val state = AppState()
        state.updateNowPlaying("hello world")
        state.clearNowPlaying()
        assertEquals("", state.nowPlayingText.value)
    }
}