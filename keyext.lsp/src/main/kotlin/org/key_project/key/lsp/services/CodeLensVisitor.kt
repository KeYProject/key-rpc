package org.key_project.key.lsp.services

import de.uka.ilkd.key.nparser.JavaKeYParser
import de.uka.ilkd.key.nparser.JavaKeYParserBaseVisitor
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.key_project.key.lsp.actions.StartKey

class CodeLensVisitor(val params: CodeLensParams) : JavaKeYParserBaseVisitor<List<CodeLens>?>() {
    override fun aggregateResult(
        aggregate: List<CodeLens>?,
        nextResult: List<CodeLens>?
    ): List<CodeLens>? {
        return if (aggregate == null) nextResult
        else if (nextResult == null) aggregate
        else aggregate + nextResult
    }

    override fun visitPreferences(ctx: JavaKeYParser.PreferencesContext?): List<CodeLens> {
        return listOf()
    }

    override fun visitProblem(ctx: JavaKeYParser.ProblemContext): List<CodeLens> {
        return listOf(StartKey().createCodeLens(ctx, params.textDocument.uri))
    }
}
