package org.key_project.key.lsp.services

import org.key_project.key.lsp.KeyLanguageServer
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KeyWorkspaceServiceTest {
    @Test
    fun findKeyFiles() {
        val files = KeyLanguageServer().keyWorkspaceService.keyStandardFiles.toList()
        Assertions.assertTrue(files.isNotEmpty())
    }

    @Test
    fun symbols() {
        val server = KeyLanguageServer()
        server.initialize(
            InitializeParams().also {
            it.workspaceFolders = listOf()
            it.rootUri = "file:///tmp"
            it.rootPath = "/tmp"
        }
        )
        val files = server.keyWorkspaceService.symbol(WorkspaceSymbolParams(""))
        println(files.get().right.size)
    }
}
