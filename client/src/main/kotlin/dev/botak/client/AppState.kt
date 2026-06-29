package dev.botak.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Client-side shared UI state holder.
 *
 * Owns cross-window reactive state. Currently exposes the "now playing" text so the subtitle
 * window can mirror what the TTS engine is currently speaking without coupling to the input
 * window ([AppMainWindow]). Designed to be extensible for future shared UI state.
 *
 * `MutableStateFlow.value` assignments are thread-safe, so updates originating on
 * `Dispatchers.IO` in the playback coroutine require no extra dispatcher hop.
 */
class AppState {
    private val _nowPlayingText = MutableStateFlow("")

    /** The text currently being spoken, or empty when nothing is playing. */
    val nowPlayingText: StateFlow<String> = _nowPlayingText.asStateFlow()

    /**
     * Sets the now-playing text. Called by [AppMainWindow] after synthesis completes and
     * immediately before audio streaming begins.
     *
     * @param text The text to display while playback runs.
     */
    fun updateNowPlaying(text: String) {
        _nowPlayingText.value = text
    }

    /** Clears the now-playing text. Called when playback ends or is cancelled. */
    fun clearNowPlaying() {
        _nowPlayingText.value = ""
    }
}