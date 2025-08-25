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
    val isDev = System.getProperty("isDev", "false").toBoolean()
    println(isDev)
    if (!isDev && !isRunningAsAdmin()) {
        JOptionPane.showMessageDialog(
            null,
            "This application requires Administrator rights.\nPlease right-click and choose 'Run as administrator'.",
            "Administrator Required",
            JOptionPane.ERROR_MESSAGE,
        )
        kotlin.system.exitProcess(1)
    }

    dev.botak.client.start()
}
