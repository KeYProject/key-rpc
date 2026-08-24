package org.key_project.key.lsp.services

import de.uka.ilkd.key.util.parsing.SyntaxErrorReporter
import io.github.jmltoolkit.lsp.KeyLanguageServer
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.WorkspaceService
import org.key_project.key.lsp.symbols.KeyCatchSymbols
import org.key_project.util.java.IOUtil
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.walk

val Path.asUri: String
    get() {
        if(FileSystems.getDefault() != this.fileSystem) {
            return toUri().toString()
        }
        return "file://${this.absolutePathString()}"
    }

class KeyWorkspaceService(val server: KeyLanguageServer) : WorkspaceService {
    val addKeyStandardFiles = true
    internal val keyStandardFiles by lazy {
        if (addKeyStandardFiles) {
            val uri = javaClass.getResource("/de/uka/ilkd/key/proof/rules/ldt.key")!!.toURI()
            val folder = IOUtil.openFileInJar(uri).parent
            folder.walk().filter { it.extension == "key" }
        } else {
            emptySequence()
        }
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}

    override fun diagnostic(params: WorkspaceDiagnosticParams): CompletableFuture<WorkspaceDiagnosticReport> =
        findKeyFiles().thenApplyAsync { it.map { parse(it) } }
            .thenApplyAsync { it.map { WorkspaceDocumentDiagnosticReport(it.get()) } }
            .thenApply { WorkspaceDiagnosticReport(it.toList()) }

    var version: Int = 0
    private fun parse(path: Path) =
        server.keyTextDocumentService.get(path.asUri)
            .thenApply {
                WorkspaceFullDocumentDiagnosticReport(listOf(), path.asUri, version++)
            }.exceptionally { e ->
                var items = mutableListOf<Diagnostic>()
                if (e is SyntaxErrorReporter.ParserException) {
                    items = e.errors.map {
                        Diagnostic(
                            it.location.toRange,
                            it.message,
                            DiagnosticSeverity.Error,
                            "KeY-Parser"
                        )
                    }.toMutableList()
                }
                WorkspaceFullDocumentDiagnosticReport(items, path.asUri, version++)
            }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<in Any> =
        CompletableFuture.supplyAsync {
            val action = server.actions.firstOrNull { it.id == params.command }
            action?.execute(params)
        }

    override fun resolveWorkspaceSymbol(workspaceSymbol: WorkspaceSymbol): CompletableFuture<WorkspaceSymbol> =
        CompletableFuture.completedFuture(workspaceSymbol)

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> =
        findKeyFiles()
            .thenApplyAsync { it.map { getSymbol(it) } }
            .thenApplyAsync { seq -> seq.flatMap { it.get() }.toList() }
            .thenApply { Either.forRight(it) }

    private fun getSymbol(path: Path) =
        server.keyTextDocumentService.get(path.asUri)
            .thenApplyAsync {
                val uri = path.asUri
                val v = KeyCatchSymbols()
                val symbols = it.accept(v)
                symbols?.map {
                    WorkspaceSymbol(
                        it.name, it.kind, Either.forLeft(Location(uri, it.range)),
                    )
                }?:listOf()
            }

    private fun findKeyFiles(): CompletableFuture<Sequence<Path>> =
        CompletableFuture.supplyAsync {
            keyStandardFiles +
                server.initParams.workspaceFolders.asSequence()
                    .map { it.uri.toPath() }
                    .flatMap { it.walk().filter { it.extension == "key" } }
        }
}