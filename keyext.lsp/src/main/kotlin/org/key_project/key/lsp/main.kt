/* This file is part of jmltoolkit project - https://github.com/jmltoolkit
 * jmltk is licensed under the Lesser GNU General Public License Version 2 and Apache License
 * SPDX-License-Identifier: LGPL-3.0-or-later Apache-2.0
 */
package org.key_project.key.lsp

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.keyproject.key.api.KeyApiImpl
import org.keyproject.key.api.StartServer
import org.keyproject.key.api.remoteclient.ClientApi
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.Future
import kotlin.concurrent.thread

/**
 * @author Alexander Weigl
 * @version 1 (10.07.22)
 */
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        KeyLspCommand().main(args)
    }
}

val executorService: ExecutorService = ForkJoinPool.commonPool()

interface RemoteApi :
    ClientApi,
    LanguageClient

class KeyLspCommand : CliktCommand() {
    private val traceEnabled by option("--trace")
    private val stdioMode by option("--stdio").flag()
    private val serverMode by option("--server").int()
    private val client by option("--client").int()

    override fun run() {
        try {
            when {
                stdioMode -> launchLanguageServer(System.`in`, System.out)
                serverMode != null -> runAsServer(serverMode!!)
                client != null -> runAsClient(client!!)
            }
        } catch (e: Exception) {
            LOGGER.error("Error at starting LSP server", e)
        }
    }

    private fun launchLanguageServer(input: InputStream, output: OutputStream): Future<*> {
        val languageServer = KeyLanguageServer()
        val keyApiServer = KeyApiImpl()

        val launcher = createServerLauncher(
            languageServer,
            listOf(keyApiServer),
            input, output
        )

        val client = launcher.remoteProxy
        languageServer.connect(client)
        keyApiServer.setClientApi(client)
        return launcher.startListening()
    }

    private fun createServerLauncher(
        lsp: LanguageServer,
        furtherServices: List<Any>,
        input: InputStream,
        output: OutputStream
    ): Launcher<RemoteApi> {
        val l = LSPLauncher.Builder<RemoteApi>()
            .setLocalServices(listOf(lsp) + furtherServices)
            .setRemoteInterface(RemoteApi::class.java)
            .setInput(input)
            .setOutput(output)
            .setExecutorService(executorService)
            .validateMessages(true)
            // .wrapMessages(wrapper)
            .configureGson(StartServer::configureJson)

        traceEnabled?.let {
            if (it == "-") {
                l.traceMessages(PrintWriter(System.err))
            } else {
                l.traceMessages(PrintWriter(it))
            }
        }

        return l.create()
    }

    private fun runAsClient(port: Int) {
        val socket = Socket("localhost", port)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        launchLanguageServer(socket.getInputStream(), socket.getOutputStream())
    }

    private fun runAsServer(port: Int) {
        try {
            ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use { serverSocket ->
                while (true) {
                    LOGGER.info("Listening on {}", serverSocket.localSocketAddress)
                    val socket = serverSocket.accept()
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    thread(start = true, isDaemon = true, name = "connection-worker") {
                        launchLanguageServer(socket.getInputStream(), socket.getOutputStream()).get()
                    }
                }
            }
        } catch (e: Exception) {
            LOGGER.error("", e)
        }
    }
}
