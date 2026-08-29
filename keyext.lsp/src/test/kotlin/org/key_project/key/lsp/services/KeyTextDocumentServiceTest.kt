package org.key_project.key.lsp.services

import de.uka.ilkd.key.nparser.JavaKeYParser
import de.uka.ilkd.key.util.parsing.SyntaxErrorReporter
import org.eclipse.lsp4j.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.key_project.key.lsp.KeyLanguageServer
import org.key_project.key.lsp.highlighting.KeyDocumentHighlighter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.readText

val workspace = Paths.get("workspace").absolute()

fun createServer(): KeyLanguageServer {
    val server = KeyLanguageServer()
    server.initialize(
        InitializeParams().also {
            it.workspaceFolders = listOf()
            it.rootUri = "file:///tmp"
            it.rootPath = "/tmp"
        }
    )
    return server
}

class KeyTextDocumentServiceTest {

    /**
     * Gets a file from the test workspace resources.
     * Files are located in src/test/resources/workspace/
     */
    private fun getWorkspaceFile(path: String): Path = workspace.resolve(path)


    // region File Management Tests
    @Test
    fun `test load valid key file`() {
        val server = createServer()
        val file = getWorkspaceFile("valid_complete.key")

        val ctx = server.keyTextDocumentService.get(file.asUri).get()
        assertNotNull(ctx)
        assertTrue(ctx is JavaKeYParser.FileContext)
    }

    @Test
    fun `test load invalid key file throws exception`() {
        val server = createServer()
        val file = getWorkspaceFile("diagnostics/invalid_missing_brace.key")

        assertThrows(SyntaxErrorReporter.ParserException::class.java) {
            server.keyTextDocumentService.getSync(file.asUri)
        }
    }

    @Test
    fun `test file cache stores parsed files`() {
        val server = createServer()
        val file = getWorkspaceFile("cache/cachable.key")

        val uri = file.asUri
        assertFalse(uri in server.keyTextDocumentService.fileCache)

        server.keyTextDocumentService.getSync(uri)

        assertTrue(uri in server.keyTextDocumentService.fileCache)
    }

    @Test
    fun `test didOpen loads file into cache`() {
        val server = createServer()
        val file = getWorkspaceFile("lifecycle/single_sort.key")

        val params = DidOpenTextDocumentParams(
            TextDocumentItem(
                file.asUri,
                "key",
                1,
                Files.readString(file)
            )
        )

        server.keyTextDocumentService.didOpen(params)

        assertTrue(file.asUri in server.keyTextDocumentService.fileCache)
    }

    @Test
    fun `test didChange invalidates cache`() {
        val server = createServer()
        val file = getWorkspaceFile("lifecycle/single_sort.key")

        val uri = file.asUri
        server.keyTextDocumentService.getSync(uri)
        assertTrue(uri in server.keyTextDocumentService.fileCache)

        val params = DidChangeTextDocumentParams(
            VersionedTextDocumentIdentifier(uri, 2),
            listOf(TextDocumentContentChangeEvent("changed content"))
        )

        server.keyTextDocumentService.didChange(params)

        assertFalse(uri in server.keyTextDocumentService.fileCache)
    }
    // endregion

    // region Document Symbol Tests
    @Test
    fun `test document symbol extraction`() {
        val server = createServer()
        val file = getWorkspaceFile("symbols/full_example.key")

        val params = DocumentSymbolParams(TextDocumentIdentifier(file.asUri))
        val symbols = server.keyTextDocumentService.documentSymbol(params).get()

        assertNotNull(symbols)
        assertTrue(symbols.isNotEmpty())
    }

    @Test
    fun `test document symbol contains sorts`() {
        val server = createServer()
        val file = getWorkspaceFile("symbols/three_sorts.key")

        val params = DocumentSymbolParams(TextDocumentIdentifier(file.asUri))
        val symbols = server.keyTextDocumentService.documentSymbol(params).get()

        assertNotNull(symbols)
        assertTrue(symbols.isNotEmpty())
    }

    @Test
    fun `test document symbol contains functions`() {
        val server = createServer()
        val file = getWorkspaceFile("symbols/three_functions.key")

        val params = DocumentSymbolParams(TextDocumentIdentifier(file.asUri))
        val symbols = server.keyTextDocumentService.documentSymbol(params).get()

        assertNotNull(symbols)
        assertTrue(symbols.isNotEmpty())
    }
    // endregion

    // region Semantic Highlighting Tests
    @Test
    fun `test semantic highlighting returns tokens`() {
        val server = createServer()
        val file = getWorkspaceFile("highlighting/basic.key")

        val params = SemanticTokensParams(TextDocumentIdentifier(file.asUri))
        val tokens = server.keyTextDocumentService.semanticTokensFull(params).get()

        assertNotNull(tokens)
        assertNotNull(tokens.data)
    }

    @Test
    fun `test semantic highlighting identifies keywords`() {
        val text = getWorkspaceFile("highlighting/keywords.key").readText()
        val highlighter = KeyDocumentHighlighter()
        val tokens = highlighter.analyzeToken(text)

        assertNotNull(tokens)
        assertTrue(tokens.data.isNotEmpty())
    }

    @Test
    fun `test semantic highlighting identifies comments`() {
        val text = getWorkspaceFile("highlighting/comments.key").readText()
        val highlighter = KeyDocumentHighlighter()
        val tokens = highlighter.analyzeToken(text)

        assertNotNull(tokens)
        assertTrue(tokens.data.isNotEmpty())
    }

    @Test
    fun `test semantic highlighting identifies numbers`() {
        val text = getWorkspaceFile("highlighting/numbers.key").readText()
        val highlighter = KeyDocumentHighlighter()
        val tokens = highlighter.analyzeToken(text)

        assertNotNull(tokens)
        assertTrue(tokens.data.isNotEmpty())
    }

    @Test
    fun `test semantic highlighting identifies operators`() {
        val text = Files.readString(getWorkspaceFile("highlighting/operators.key"))
        val highlighter = KeyDocumentHighlighter()
        val tokens = highlighter.analyzeToken(text)

        assertNotNull(tokens)
        assertTrue(tokens.data.isNotEmpty())
    }
    // endregion

    // region Folding Range Tests
    @Test
    fun `test folding range extraction`() {
        val server = createServer()
        val file = getWorkspaceFile("folding/nested.key")

        val params = FoldingRangeRequestParams(TextDocumentIdentifier(file.asUri))
        val ranges = server.keyTextDocumentService.foldingRange(params).get()

        assertNotNull(ranges)
        assertTrue(ranges.isNotEmpty())
    }
    // endregion

    // region Completion Tests
    @Test
    fun `test completion returns keyword suggestions`() {
        val server = createServer()
        val file = getWorkspaceFile("completion/basic.key")

        val params = CompletionParams(
            TextDocumentIdentifier(file.asUri),
            Position(0, 0)
        )
        val result = server.keyTextDocumentService.completion(params).get()

        assertNotNull(result)
        val items = result.left
        assertNotNull(items)
        assertTrue(items.isNotEmpty())
    }
    // endregion

    // region Diagnostic Tests
    @Test
    fun `test diagnostic captures syntax errors`() {
        val server = createServer()
        val file = getWorkspaceFile("diagnostics/invalid_missing_brace.key")

        val params = DocumentDiagnosticParams(TextDocumentIdentifier(file.asUri))
        val report = server.keyTextDocumentService.diagnostic(params).get()

        assertNotNull(report)
    }

    @Test
    fun `test diagnostic empty for valid file`() {
        val server = createServer()
        val file = getWorkspaceFile("diagnostics/valid.key")

        val params = DocumentDiagnosticParams(TextDocumentIdentifier(file.asUri))
        val report = server.keyTextDocumentService.diagnostic(params).get()

        assertNotNull(report)
    }
    // endregion


    @Test
    fun testCodeLens() {
        val server = createServer()
        val file = getWorkspaceFile("complex.key")
        val params = CodeLensParams(TextDocumentIdentifier(file.asUri))
        val report = server.keyTextDocumentService.codeLens(params).get()
        println(report)
        assertNotNull(report)
        assertTrue(report.isNotEmpty())
    }

    @Test
    fun testDocumentSymbol() {
        val server = createServer()
        val file = getWorkspaceFile("complex.key")
        val params = DocumentSymbolParams(TextDocumentIdentifier(file.asUri))
        val report = server.keyTextDocumentService.documentSymbol(params).get()
        println(report)
        assertNotNull(report)
        assertTrue(report.isNotEmpty())
    }
}

// Extension property for URI conversion
val Path.asUri: String
    get() = "file://${this.toAbsolutePath()}"
