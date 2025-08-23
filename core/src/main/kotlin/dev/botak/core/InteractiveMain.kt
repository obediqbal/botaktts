package dev.botak.core

import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.Locale.getDefault

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
                    val speech = ttsService.synthesizeSpeech(input)
                    println("synthesized speech")
                    job =
                        launch(Dispatchers.IO) {
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
