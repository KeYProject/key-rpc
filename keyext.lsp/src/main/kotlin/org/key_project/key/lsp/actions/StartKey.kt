package org.key_project.key.lsp.actions

import com.google.auto.service.AutoService
import de.uka.ilkd.key.gui.MainWindow
import de.uka.ilkd.key.nparser.JavaKeYParser
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import org.key_project.key.lsp.symbols.asRange
import java.nio.file.Paths
import javax.swing.SwingUtilities

@AutoService(LspAction::class)
class StartKey : LspAction {
    fun createCodeLens(ctx: JavaKeYParser.ProblemContext, uri: String): CodeLens =
        CodeLens(ctx.asRange, createCommand(uri), null)

    fun createCommand(uri: String) = Command(
        "Start KeY", id,
        listOf(
            Paths.get(uri).toAbsolutePath().toString()
        )
    )

    override fun execute(params: ExecuteCommandParams): Any? {
        val path = Paths.get(params.arguments.first().toString())
        SwingUtilities.invokeLater {
            val mainWindow = MainWindow.getInstance(true)
            mainWindow.userInterface.loadProblem(path)
        }
        return null
    }
}