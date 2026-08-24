package org.key_project.key.lsp.services

import com.google.common.cache.CacheBuilder
import de.uka.ilkd.key.nparser.JavaKeYLexer
import de.uka.ilkd.key.nparser.JavaKeYParser
import de.uka.ilkd.key.nparser.ParsingFacade
import de.uka.ilkd.key.util.parsing.SyntaxErrorReporter
import de.uka.ilkd.key.util.parsing.SyntaxErrorReporter.SyntaxError
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.key_project.key.lsp.KeyLanguageServer
import org.key_project.key.lsp.LOGGER
import org.key_project.key.lsp.highlighting.KeyDocumentHighlighter
import org.key_project.key.lsp.symbols.KeyCatchSymbols
import org.key_project.key.lsp.symbols.asRange
import org.key_project.util.java.IOUtil
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.supplyAsync
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.math.max
import kotlin.math.min

private fun TerminalNode.parentSequence(): Sequence<ParseTree> = generateSequence(this as ParseTree) { it.parent }

val org.key_project.util.parsing.Location.toRange: Range
    get() = Range(Position(position.line(), position.column()), Position(position.line(), position.column()))

class KeyTextDocumentService(val server: KeyLanguageServer) : TextDocumentService {
    // region file management
    val fileErrors = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .expireAfterAccess(Duration.ofMinutes(1))
        .maximumSize(250)
        .initialCapacity(25)
        .build<String, Exception>()
        .asMap()

    val fileCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .expireAfterAccess(Duration.ofMinutes(1))
        .maximumSize(250)
        .initialCapacity(25)
        .build<String, JavaKeYParser.FileContext>()
        .asMap()

    fun getSync(uri: String): JavaKeYParser.FileContext {
        if (uri !in fileCache) {
            load(uri)
        }
        return fileCache[uri] ?: throw fileErrors[uri]!!
    }

    fun get(uri: String): CompletableFuture<JavaKeYParser.FileContext> = supplyAsync {
        getSync(uri)
    }

    private fun load(uri: String): JavaKeYParser.FileContext? {
        val path = uri.toPath()
        val ctx =
            try {
                ParsingFacade.parseFile(path)
            } catch (e: Exception) {
                fileErrors[uri] = e
                throw e
            }

        val c = ParsingFacade.getParseRuleContext(ctx)
        fileCache[uri] = c
        return c
    }

    private fun invalidate(uri: String) {
        fileCache.remove(uri)
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        // read in, in advance
        getSync(params.textDocument.uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        invalidate(params.textDocument.uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        // Nothing to do
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        invalidate(params.textDocument.uri)
        getSync(params.textDocument.uri)
    }

    //endregion

    //region Hover
    override fun hover(params: HoverParams): CompletableFuture<Hover?> =
        get(params.textDocument.uri)
            .thenApply { symbolicLocation(it, params.position) }
            .thenApply { describeAsHover(it) }
            .exceptionally { e ->
                LOGGER.warn("Error computing hover information", e)
                null
            }

    private tailrec fun symbolicLocation(ctx: ParserRuleContext, position: Position): TerminalNode {
        for (tree in ctx.children) {
            if (position in tree) {
                return tree as? TerminalNode ?: symbolicLocation(tree as ParserRuleContext, position)
            }
        }
        throw IllegalStateException()
    }

    private fun describeAsHover(it: TerminalNode) =
        HoverDictionary.get(it.symbol.text)?.let { Hover(MarkupContent(MarkupKind.MARKDOWN, it)) }

    override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp?> =
        get(params.textDocument.uri)
            .thenApplyAsync { file ->
                val v = KeyCatchSymbols()
                file.accept(v)

                val tn = symbolicLocation(file, params.position)
                val accessTerm = tn.parentSequence().filterIsInstance<JavaKeYParser.AccesstermContext>().firstOrNull()
                if (accessTerm != null) {
                    val declaredFunctions = v.declaredFunctions.filter { it.name == accessTerm.firstName.text }
                    val formalSortArgs =
                        tn.parentSequence().filterIsInstance<JavaKeYParser.Formal_sort_argsContext>().firstOrNull()
                    val callArgs =
                        tn.parentSequence().filterIsInstance<JavaKeYParser.Argument_listContext>().firstOrNull()

                    if (formalSortArgs != null) {
                        val signatures = declaredFunctions.map {
                            SignatureInformation(it.name, it.documentation, it.sortArgs)
                        }
                        val pos =
                            formalSortArgs.COMMA().indexOfLast {
                                (it.symbol.line to it.symbol.charPositionInLine) <=
                                    (params.position.line to params.position.character)
                            }
                        val p = signatures.indexOfFirst { it.parameters.size > pos }
                        SignatureHelp(signatures, max(0, pos), max(0, p))
                    } else if (callArgs != null) {
                        val signatures = declaredFunctions.map {
                            SignatureInformation(it.name, it.documentation, it.args)
                        }
                        val pos =
                            callArgs.COMMA().indexOfLast {
                                (it.symbol.line to it.symbol.charPositionInLine) <=
                                    (params.position.line to params.position.character)
                            }
                        val p = signatures.indexOfFirst { it.parameters.size > pos }
                        SignatureHelp(signatures, max(0, pos), max(0, p))
                    } else {
                        null
                    }
                } else {
                    null
                }
            }.exceptionally {
                LOGGER.info("", it)
                null
            }
    //endregion

    //region Folding
    private val rulesOfFoldingInterests = mutableMapOf<Class<*>, (ParserRuleContext) -> String?>()
    fun <T : ParserRuleContext> registerFoldingContext(ctx: Class<T>, fn: (T) -> String?) {
        rulesOfFoldingInterests[ctx] = fn as (ParserRuleContext) -> String?
    }

    init {
        registerFoldingContext(JavaKeYParser.ProblemContext::class.java) {
            "Problem"
        }
        registerFoldingContext(JavaKeYParser.PreferencesContext::class.java) {
            "Preferences"
        }
        registerFoldingContext(JavaKeYParser.ProofContext::class.java) {
            "Proof"
        }
        registerFoldingContext(JavaKeYParser.RulesOrAxiomsContext::class.java) {
            "Rules (${it.taclet().size} under ${it.option_list().text})"
        }

        registerFoldingContext(JavaKeYParser.Sort_declsContext::class.java) { "Sorts" }
        registerFoldingContext(JavaKeYParser.Func_declsContext::class.java) { "Functions" }
        registerFoldingContext(JavaKeYParser.Pred_declsContext::class.java) { "Predicates" }
        registerFoldingContext(JavaKeYParser.Transform_declsContext::class.java) { "Transformers" }
        registerFoldingContext(JavaKeYParser.Datatype_declsContext::class.java) { "Datatypes" }
        registerFoldingContext(JavaKeYParser.Datatype_declContext::class.java) { "${it.name}" }
        registerFoldingContext(JavaKeYParser.TacletContext::class.java) { "Taclet: ${it.name}" }
        registerFoldingContext(JavaKeYParser.GoalspecsContext::class.java) { "Goals" }
        registerFoldingContext(JavaKeYParser.GoalspecContext::class.java) { "Goal ${it.name}" }
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> =
        get(params.textDocument.uri)
            .thenApplyAsync {
                val result = ArrayList<FoldingRange>(128)
                val queue = LinkedList<ParserRuleContext>()
                queue += it
                while (queue.isNotEmpty()) {
                    val n = queue.pollFirst()
                    if (n.start.line == n.stop.line) continue // one line ParserRuleContext, nothing to gain by folding
                    rulesOfFoldingInterests[n.javaClass]?.let {
                        it(n)?.let { text ->
                            val range = FoldingRange(n.start.line, n.stop.line)
                            range.collapsedText = text
                            result.add(range)
                        }
                    }
                    queue.addAll(n.children.filterIsInstance<ParserRuleContext>())
                }
                result as List<FoldingRange>
            }
            .exceptionally { e ->
                LOGGER.error("Error in finding folding ranges", e)
                listOf()
            }
    //endregion

    //region diagnostics
    override fun diagnostic(params: DocumentDiagnosticParams): CompletableFuture<DocumentDiagnosticReport> =
        get(params.textDocument.uri)
            .thenApply {
                DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport())
            }
            .exceptionally { e ->
                var items = mutableListOf<Diagnostic>()
                if (e is SyntaxErrorReporter.ParserException) {
                    items = e.errors.map {
                        Diagnostic(
                            it.location.toRange,
                            it.message,
                            DiagnosticSeverity.Error,
                            "KeY-Parser"
                        )
                    }
                        .toMutableList()
                }
                DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(items))
            }
    //endregion

    // region documentSymbol
    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        get(params.textDocument.uri)
            .thenApplyAsync<List<Either<SymbolInformation, DocumentSymbol>>> {
                val v = KeyCatchSymbols()
                it.accept(v)?.map { Either.forRight(it) } ?: listOf()
            }
            .exceptionally {
                LOGGER.error("Error in documentSymbol", it)
                listOf()
            }
    //endregion

    // region Declaration
    override fun declaration(params: DeclarationParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        get(params.textDocument.uri)
            .thenApplyAsync {
                val tn = symbolicLocation(it, params.position)
                val isSortId = tn.parentSequence().any { it is JavaKeYParser.SortIdContext }
                val v = KeyCatchSymbols()
                val symbols = it.accept(v)?.filter { it.name == tn.symbol.text } ?: listOf()
                Either.forLeft(symbols.map { Location(params.textDocument.uri, it.selectionRange) })
            }
    // endregion

    //region Actions
    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> =
        get(params.textDocument.uri)
            .thenApplyAsync { it.accept(CodeActionVisitor(params.range)) ?: listOf() }
            .thenApply { it.map { it: Command -> Either.forLeft(it) } }

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> =
        get(params.textDocument.uri).thenApplyAsync { it.accept(CodeLensVisitor(params)) ?: listOf() }
    //endregion

    //region Selection Range
    override fun selectionRange(params: SelectionRangeParams): CompletableFuture<List<SelectionRange>> =
        get(params.textDocument.uri)
            .thenApplyAsync {
                params.positions.map { pos ->
                    try {
                        symbolicLocation(it, pos)
                    } catch (e: IllegalStateException) {
                        null
                    }
                }
                    .map { tn -> buildChangeSelectionChain(tn) }
            }

    fun buildChangeSelectionChain(x: TerminalNode?): SelectionRange =
        if (x == null) {
            SelectionRange()
        } else {
            val parents = x.parentSequence().toList().foldRight(null as SelectionRange?) { tree, acc ->
                val range = (tree as ParserRuleContext).asRange
                if (range == acc?.range) {
                    acc
                } else {
                    SelectionRange(range, acc)
                }
            }
            SelectionRange(x.symbol.asRange, parents)
        }
    //endregion

    //region Semantic Tokens
    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> =
        supplyAsync { params.textDocument.uri.toPath().readText() }
            .thenApplyAsync { KeyDocumentHighlighter().analyzeToken(it) }

    override fun semanticTokensRange(params: SemanticTokensRangeParams): CompletableFuture<SemanticTokens> =
        supplyAsync { (params.textDocument.uri).toPath().readText().substring(params.range.start, params.range.end) }
            .thenApplyAsync { KeyDocumentHighlighter().analyzeToken(it) }
    //endregion

    //region Completion
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
        supplyAsync { params.textDocument.uri.toPath().readText() }
            .thenApply<Either<List<CompletionItem>, CompletionList>> {
                var prefix = ""
                val index = it.indexOf(params.position)
                if (index >= 0) {
                    val delim = max(index, it.lastIndexOfAny(" \n\t".toCharArray(), index))
                    prefix = it.substring(delim, index)
                }
                Either.forLeft(getEscapeKeywords(prefix))

                // val tokens = ParsingFacade.createLexer(CharStreams.fromString(it)).allTokens
                // val completions = mutableListOf<CompletionItem>()
                // Add keyword completions based on parser context
                // completions.addAll(getKeywordCompletions(contextNode))

                // Add symbol completions from the parsed AST
                // completions.addAll(getSymbolCompletions(file))
                // Either.forLeft(listOf())
            }
            .exceptionally {
                LOGGER.warn("Error computing completions", it)
                Either.forLeft(listOf())
            }

    private fun getEscapeKeywords(prefix: String): List<CompletionItem> =
        (0..JavaKeYLexer.VOCABULARY.maxTokenType).asSequence()
            .mapNotNull { JavaKeYLexer.VOCABULARY.getLiteralName(it) }
            .filter { it.startsWith(prefix) }
            .toSortedSet()
            .map { CompletionItem(it) }
    //endregion
}

private fun String.indexOf(position: Position): Int {
    var currentLine = 0
    for ((index, ch) in withIndex()) {
        if (ch == '\n') currentLine++
        if (position.line == currentLine) {
            return index + position.character
        }
    }
    return -1
}

fun String.toPath(): Path = if (startsWith("jar:file:")) {
    IOUtil.openFileInJar(URI.create(this))
} else {
    Paths.get(this.replace("file://", ""))
}

private fun String.substring(startIndex: Position, endIndex: Position): String {
    var start: Int = -1
    var end: Int = -1
    var currentLine = 0
    for ((index, ch) in this.withIndex()) {
        if (ch == '\n') currentLine++
        if (startIndex.line == currentLine) {
            start = index + startIndex.character
        }
        if (endIndex.line == currentLine) {
            end = index + endIndex.character
        }
        if (start != -1 && end != -1) break
    }
    return substring(min(start, end), max(start, end))
}

private operator fun ParseTree.contains(position: Position): Boolean =
    when (this) {
        is ParserRuleContext -> start <= position && position <= stop
        is TerminalNode -> symbol <= position && position <= symbol
        else -> false
    }

private operator fun Pair<Int, Int>.compareTo(o: Pair<Int, Int>): Int {
    val (a, b) = this
    val (x, y) = o

    val q = a - x
    if (q != 0) {
        return q
    }
    return b - y
}

private operator fun Token.compareTo(position: Position): Int =
    (line to charPositionInLine).compareTo(position.line to position.character)

private operator fun Position.compareTo(tok: Token): Int {
    val x = line - tok.line
    if (x != 0) return x
    return character - tok.charPositionInLine
}
