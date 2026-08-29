package org.key_project.key.lsp.actions

import de.uka.ilkd.key.core.Main
import de.uka.ilkd.key.gui.MainWindow
import de.uka.ilkd.key.nparser.JavaKeYParser
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.key_project.key.lsp.services.asUri
import org.key_project.key.lsp.services.toPath
import org.key_project.key.lsp.symbols.asRange
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.io.path.absolute


class StartKey : LspAction {
    fun createCodeLens(ctx: JavaKeYParser.ProblemContext, uri: String): CodeLens =
        CodeLens(ctx.asRange, createCommand(uri), null)

    fun createCommand(uri: String) = Command(
        "Start KeY", id,
        listOf(
            uri.toPath().toAbsolutePath().asUri
        )
    ).also {
        it.tooltip = "Start the KeY Theorem Prover with this problem definition"
    }

    override fun execute(params: ExecuteCommandParams): Any? {
        Main::class.java.getDeclaredField("workingDir").also {
            it.isAccessible = true
            it.set(null, Paths.get(".").absolute())
        }
        val path = Paths.get(params.arguments.first().toString())
        val mainWindow = MainWindow.getInstance(true)
        mainWindow.userInterface.loadProblem(path)
        return null
    }
}
