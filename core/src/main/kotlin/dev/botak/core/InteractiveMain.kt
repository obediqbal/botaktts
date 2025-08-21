package dev.botak.core

import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService

private enum class Command {
    SYNTH,
    SETSPEED,
    SETPITCH,
    SETVOICE,
    SETLANGUAGE,
    GETVOICENAMES,
}

fun main() {
    val ttsService = TTSService()
    val audioStreamService = AudioStreamService()

    while (true) {
        val command = readln()
        val input = readln()
        when (command) {
            Command.SYNTH.name -> {
                val speech = ttsService.synthesizeSpeech(input)
                audioStreamService.streamToVirtualAudio(speech, ttsService.sampleRateHz.toFloat())
            }
            Command.SETSPEED.name -> ttsService.speed = input.toDouble()
            Command.SETPITCH.name -> ttsService.pitch = input.toDouble()
            Command.SETVOICE.name -> {
                val splitted = input.split(" ")
                ttsService.selectVoice(splitted[0], splitted[1])
            }
            Command.GETVOICENAMES.name -> ttsService.fetchListVoiceNames(input)
        }
    }
}
