package dev.botak.core

import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * The commands supported by the interactive CLI protocol used by [main].
 *
 * Each command is read from standard input as a name, followed by a single argument line.
 */
private enum class Command {
    SYNTH,
    SETSPEED,
    SETPITCH,
    SETVOICE,
    SETVOLUME,
    STOP,
    GETVOICENAMES,
}

private val LOGGER = LoggerFactory.getLogger("MainInteractive")

/**
 * Interactive CLI entry point for the core module.
 *
 * Reads a line-delimited protocol from standard input: each request consists of a command name
 * followed by an argument line. Both synthesis and playback are performed on a single tracked
 * background coroutine launched on [Dispatchers.IO], so the command loop stays responsive while a
 * synthesis request is in flight. [Command.STOP] cancels and joins that job.
 *
 * Cancellation caveat: the Google Cloud TTS client call is a blocking gRPC request that does not
 * cooperate with coroutine cancellation while in flight. Cancelling the job will not abort the
 * network call itself, but the cancelled coroutine skips playback once the call returns, so STOP
 * prevents any audio from being written after a synthesis in progress.
 */
fun main() =
    runBlocking {
        val ttsService = TTSService()
        val audioStreamService = AudioStreamService()
        var job: Job? = null

        while (true) {
            val command = readln()
            val input = readln()
            when (command) {
                Command.SYNTH.name -> {
                    println("Command SYNTH received")
                    // Synthesis and playback run together inside the tracked job so the command
                    // loop stays free to read a subsequent STOP. The Google TTS client call is a
                    // blocking gRPC request that does not cooperate with coroutine cancellation
                    // while in flight; cancelling the job will not abort the network call itself,
                    // but once it returns the cancelled coroutine skips playback before any audio
                    // is written. STOP therefore interrupts synthesis at the first cancellation
                    // point (between the API call returning and streaming starting).
                    job =
                        launch(Dispatchers.IO) {
                            val speech = ttsService.synthesizeSpeech(input)
                            println("synthesized speech")
                            audioStreamService.streamToVirtualAudio(speech, ttsService.sampleRateHz.toFloat())
                        }
                }
                Command.SETSPEED.name -> ttsService.speed = input.toDouble()
                Command.SETPITCH.name -> ttsService.pitch = input.toDouble()
                Command.SETVOLUME.name -> audioStreamService.volumeFactor = input.toFloat()
                Command.SETVOICE.name -> {
                    val splitted = input.split(" ")
                    ttsService.selectVoice(splitted[0], splitted[1])
                }
                Command.STOP.name -> job?.cancelAndJoin()
                Command.GETVOICENAMES.name -> ttsService.fetchListVoiceNames(input).forEach { println(it) }
                else -> println("Unknown command")
            }
        }
    }
