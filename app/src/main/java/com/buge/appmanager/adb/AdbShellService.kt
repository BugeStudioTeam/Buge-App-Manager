// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buge Studio

package com.buge.appmanager.adb

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

object AdbShellService {

    private const val DEFAULT_PORT = 15793
    private const val MAGIC = "BUGE_ADB_SHELL"
    private const val PORT_FILE_NAME = "adb_shell_port"
    private const val APP_DIR = "/sdcard/Android/data/com.buge.appmanager/files"

    @JvmStatic
    fun main(args: Array<String>) {
        val uid = try {
            File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("Uid:") }
                ?.split("\\s+".toRegex())
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: -1
        } catch (e: Exception) {
            -1
        }
        if (uid != 2000 && uid != 0) {
            System.err.println("AdbShellService: must run as shell (2000) or root (0), got $uid")
            System.exit(1)
        }

        val server = try {
            ServerSocket(DEFAULT_PORT)
        } catch (e: Exception) {
            try {
                ServerSocket(0)
            } catch (e2: Exception) {
                System.err.println("AdbShellService: cannot bind port: ${e2.message}")
                System.exit(1)
                return
            }
        }

        val actualPort = server.localPort
        writePortFile(actualPort)
        println("AdbShellService: listening on 127.0.0.1:$actualPort, uid=$uid")

        while (true) {
            try {
                val client = server.accept()
                thread(start = true) { handleClient(client) }
            } catch (e: Exception) {
                System.err.println("AdbShellService: accept failed: ${e.message}")
            }
        }
    }

    private fun writePortFile(port: Int) {
        try {
            val dir = File(APP_DIR)
            if (!dir.exists()) dir.mkdirs()
            File(dir, PORT_FILE_NAME).writeText(port.toString())
        } catch (e: Exception) {
            System.err.println("AdbShellService: cannot write port file: ${e.message}")
        }
    }

    private fun handleClient(client: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))

            val magic = reader.readLine()
            if (magic != MAGIC) {
                writer.write("ERROR: bad magic\n")
                writer.flush()
                client.close()
                return
            }

            while (true) {
                val command = reader.readLine() ?: break
                if (command == "EXIT") {
                    writer.write("OK\n")
                    writer.flush()
                    break
                }

                val result = executeCommand(command)
                writer.write(result)
                writer.write("\n---END---\n")
                writer.flush()
            }

            client.close()
        } catch (e: Exception) {
            try {
                client.close()
            } catch (e2: Exception) {
            }
        }
    }

    private fun executeCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            "EXIT_CODE:$exitCode\n$output"
        } catch (e: Exception) {
            "EXIT_CODE:-1\nERROR: ${e.message}"
        }
    }
}
