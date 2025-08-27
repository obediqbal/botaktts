package dev.botak.client

import javax.swing.JOptionPane

private fun isRunningAsAdmin(): Boolean =
    try {
        val process =
            ProcessBuilder("net", "session")
                .redirectErrorStream(true)
                .start()
        process.waitFor()
        process.exitValue() == 0
    } catch (e: java.lang.Exception) {
        false
    }

fun main() {
    dev.botak.client.start()
}
