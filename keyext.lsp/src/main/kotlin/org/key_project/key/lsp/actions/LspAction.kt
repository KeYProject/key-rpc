package org.key_project.key.lsp.actions

import org.eclipse.lsp4j.ExecuteCommandParams

interface LspAction {
    val id: String
        get() = this::class.java.name

    fun execute(params: ExecuteCommandParams): Any?
}
