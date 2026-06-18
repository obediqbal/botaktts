package dev.botak.client

import javax.swing.JOptionPane

/**
 * Returns `true` if the current process is running with Windows administrator privileges.
 *
 * Probes by invoking `net session`, which succeeds (exit code `0`) only when elevated.
 * Any failure is treated as non-administrator.
 */
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

/** Application entry point for the Compose Desktop client. Delegates to [start]. */
fun main() {
    dev.botak.client.start()
}
