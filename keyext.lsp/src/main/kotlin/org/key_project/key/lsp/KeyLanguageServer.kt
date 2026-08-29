/* This file is part of jmltoolkit project - https://github.com/jmltoolkit
 * jmltk is licensed under the Lesser GNU General Public License Version 2 and Apache License
 * SPDX-License-Identifier: LGPL-3.0-or-later Apache-2.0
 */
package org.key_project.key.lsp

import de.uka.ilkd.key.util.KeYConstants
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import org.key_project.key.lsp.actions.LspAction
import org.key_project.key.lsp.highlighting.LEGEND
import org.key_project.key.lsp.services.KeyNotebookDocumentServices
import org.key_project.key.lsp.services.KeyTextDocumentService
import org.key_project.key.lsp.services.KeyWorkspaceService
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Language Server implementation for KeY files.
 *
 * Provides LSP features including:
 * - Semantic highlighting
 * - Document symbols
 * - Code lenses
 * - Hover information
 * - Completion providers
 * - Workspace diagnostics
 * - Command execution
 *
 * @author Alexander Weigl
 * @version 1.0
 */
class KeyLanguageServer :
    LanguageServer,
    LanguageClientAware {
    internal val executorService: ExecutorService = ForkJoinPool.commonPool()
    internal val keyTextDocumentService by lazy { KeyTextDocumentService(this) }
    internal val keyWorkspaceService by lazy { KeyWorkspaceService(this) }
    internal val keyNotebookDocumentServices by lazy { KeyNotebookDocumentServices() }

    internal lateinit var client: LanguageClient

    internal lateinit var initParams: InitializeParams

    internal val capabilities: ClientCapabilities
        get() = initParams.capabilities

    /**
     * Loads LSP actions via ServiceLoader pattern.
     *
     * Extensions can register custom actions by implementing [LspAction] interface
     * and adding a META-INF/services/org.key_project.key.lsp.actions.LspAction file
     * with the fully qualified class name.
     */
    internal val actions by lazy {
        ServiceLoader.load(LspAction::class.java).toList()
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        initParams = params
        val keyFiles = DocumentFilter("key", "file", Either.forLeft("**/*.key"))
        val keyFilesJar = DocumentFilter("key", "jar:file", Either.forLeft("**/*.key"))

        val capabilities = ServerCapabilities()
        capabilities.setHoverProvider(true)
        capabilities.signatureHelpProvider = SignatureHelpOptions(listOf("<", "("), listOf("<", "(", ","))
        capabilities.foldingRangeProvider = Either.forRight(FoldingRangeProviderOptions("KeY"))
        capabilities.diagnosticProvider = DiagnosticRegistrationOptions(false, true).also {
            it.identifier = "key-lsp"
            it.documentSelector = listOf(keyFiles, keyFilesJar)
        }

        capabilities.setDocumentSymbolProvider(true)
        capabilities.setDeclarationProvider(DeclarationRegistrationOptions("KeY"))

        capabilities.setCodeActionProvider(CodeActionOptions(listOf("key")))
        capabilities.executeCommandProvider = ExecuteCommandOptions(actions.map { it.id })
        capabilities.codeLensProvider = CodeLensOptions(true)
        capabilities.selectionRangeProvider = Either.forRight(SelectionRangeRegistrationOptions("KeY"))

        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
        capabilities.completionProvider = CompletionOptions(true, listOf(",", "(", ")", "<", ">", "\\"))

        capabilities.semanticTokensProvider = SemanticTokensWithRegistrationOptions(
            LEGEND, SemanticTokensServerFull(true), false,
            listOf(
                keyFiles, keyFilesJar
            )
        )
        capabilities.semanticTokensProvider.id = "key-lsp"
        capabilities.semanticTokensProvider.range = Either.forLeft(true)

        capabilities.documentOnTypeFormattingProvider = null

        capabilities.setDocumentFormattingProvider(true)
        capabilities.setDocumentRangeFormattingProvider(true)
        capabilities.documentLinkProvider = DocumentLinkOptions(false)

        capabilities.setWorkspaceSymbolProvider(true)

        capabilities.textDocument = TextDocumentServerCapabilities()
        capabilities.textDocument.diagnostic = DiagnosticServerCapabilities().also { it.markupMessageSupport = true }

        capabilities.workspace = WorkspaceServerCapabilities()
        capabilities.workspace.workspaceFolders = WorkspaceFoldersOptions().also {
            it.supported = true
            it.changeNotifications = Either.forRight(true)
        }

        capabilities.workspace.textDocumentContent = TextDocumentContentRegistrationOptions(
            listOf("jar:file")
        )


        return CompletableFuture.completedFuture(
            InitializeResult(
                capabilities,
                ServerInfo(
                    "key-lsp",
                    "using${KeYConstants.VERSION} (${KeYConstants.INTERNAL_VERSION})"
                )
            )
        )
    }

    override fun shutdown(): CompletableFuture<Any> {
        executorService.shutdown()
        val c = executorService.awaitTermination(5, TimeUnit.SECONDS)
        val i = executorService.shutdownNow()
        return CompletableFuture.completedFuture("Finish: Waited 5 seconds. $c, ${i.size} jobs killed.")
    }

    override fun exit() {
        shutdown()
        exitProcess(0)
    }

    override fun getNotebookDocumentService(): NotebookDocumentService = keyNotebookDocumentServices

    override fun getTextDocumentService(): TextDocumentService = keyTextDocumentService

    override fun getWorkspaceService(): WorkspaceService = keyWorkspaceService

    override fun connect(client: LanguageClient) {
        this.client = client
    }
}

internal val LOGGER = LoggerFactory.getLogger("key-lsp")
