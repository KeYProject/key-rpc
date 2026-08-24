/* This file is part of jmltoolkit project - https://github.com/jmltoolkit
 * jmltk is licensed under the Lesser GNU General Public License Version 2 and Apache License
 * SPDX-License-Identifier: LGPL-3.0-or-later Apache-2.0
 */
package org.key_project.key.lsp.symbols

import de.uka.ilkd.key.nparser.JavaKeYParser
import de.uka.ilkd.key.nparser.JavaKeYParserBaseVisitor
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.*

internal val Token.asRange: Range
    get() = Range(asStartPosition, asStopPosition)
internal val Token.asStopPosition: Position
    get() = Position(line, charPositionInLine + startIndex - stopIndex)
internal val Token.asStartPosition: Position
    get() = Position(line, charPositionInLine)
internal val ParserRuleContext.asRange: Range
    get() = Range(start.asStartPosition, stop.asStopPosition)

/**
 *
 * @author Alexander Weigl
 * @version 1 (04.02.24)
 */
class KeyCatchSymbols : JavaKeYParserBaseVisitor<List<DocumentSymbol>?>() {
    val declaredFunctions = mutableListOf<FunctionSignature>()

    override fun visitFile(ctx: JavaKeYParser.FileContext): List<DocumentSymbol> = acceptAll(ctx.children)

    @JvmName("acceptAllPt")
    private fun acceptAll(children: List<ParseTree>?): List<DocumentSymbol> =
        children?.filterIsInstance<ParserRuleContext>()?.flatMap { it.accept(this) ?: listOf() }?.toMutableList()
            ?: listOf()

    private fun acceptAll(children: List<ParserRuleContext>): MutableList<DocumentSymbol> =
        children.flatMap { it.accept(this) ?: listOf() }.toMutableList()

    override fun visitDecls(ctx: JavaKeYParser.DeclsContext): List<DocumentSymbol> = acceptAll(ctx.children)

    override fun visitSort_decls(ctx: JavaKeYParser.Sort_declsContext) = listOf(
        DocumentSymbol(
            "Sorts", SymbolKind.Namespace, ctx.asRange,
            Range(),
            null,
            acceptAll(ctx.one_sort_decl())
        )
    )

    override fun visitOne_sort_decl(ctx: JavaKeYParser.One_sort_declContext): List<DocumentSymbol> =
        ctx.sortIds?.simple_ident_dots_with_docs()?.flatMap {
            symbol(it.text, SymbolKind.Class, it.asRange, it.asRange, ctx.doc?.text)
        } ?: listOf()

    private fun symbol(
        name: String,
        kind: SymbolKind,
        range: Range,
        selectionRange: Range,
        detail: String? = null,
        children: MutableList<DocumentSymbol>? = null
    ): List<DocumentSymbol> = listOf(
        DocumentSymbol(name, kind, range, selectionRange, detail, children)
    )

    override fun visitPreferences(ctx: JavaKeYParser.PreferencesContext): List<DocumentSymbol> =
        symbol("Preferences", SymbolKind.String, ctx.KEYSETTINGS().symbol.asRange, ctx.asRange, ctx.text)

    override fun visitFunc_decls(ctx: JavaKeYParser.Func_declsContext): List<DocumentSymbol> {
        return symbol(
            "Functions", SymbolKind.Function,
            ctx.start.asRange, Range(), null,
            acceptAll(ctx.func_decl())
        )
    }

    override fun visitFunc_decl(ctx: JavaKeYParser.Func_declContext): List<DocumentSymbol> {
        declaredFunctions.add(FunctionSignature.from(ctx))
        return symbol(ctx.func_name.name.text, SymbolKind.Function, ctx.asRange, ctx.func_name.asRange, ctx.text)
    }

    override fun visitRulesOrAxioms(ctx: JavaKeYParser.RulesOrAxiomsContext) = symbol(
        (if (ctx.RULES() != null) "Rules" else "Axioms") + " ${ctx.choices?.text ?: ""}",
        SymbolKind.Namespace, ctx.start.asRange, ctx.asRange, ctx.doc?.text,
        acceptAll(ctx.taclet())
    )

    override fun visitTaclet(ctx: JavaKeYParser.TacletContext) =
        symbol("Taclet: ${ctx.name}", SymbolKind.Object, ctx.start.asRange, ctx.asRange, ctx.doc?.text)
}

data class FunctionSignature(
    val name: String,
    val documentation: String,
    val sortArgs: List<ParameterInformation>,
    val args: List<ParameterInformation>
) {
    companion object {
        fun from(ctx: JavaKeYParser.Func_declContext): FunctionSignature {
            val sortArgs = ctx.formal_sort_param_decls()
                ?.formal_sort_param_decl()
                ?.map { ParameterInformation(it.simple_ident().text) }
                ?: listOf()
            val args = ctx.argSorts.sortId().map { ParameterInformation(it.text) }
            return FunctionSignature(
                ctx.func_name.name.text,
                ctx.DOC_COMMENT()?.text ?: "",
                sortArgs, args
            )
        }
    }

}
