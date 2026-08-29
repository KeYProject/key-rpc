package org.key_project.key.lsp.actions

import org.eclipse.lsp4j.ExecuteCommandParams
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.key_project.key.lsp.services.createServer

/**
 * 
 * @author Alexander Weigl 
 * @version 1 (29.08.26)
 */
class StartKeyTest {
    @Test
    @Disabled
    fun test() {
        val x = createServer()
        x.workspaceService.executeCommand(
            ExecuteCommandParams(
                "org.key_project.key.lsp.actions.StartKey",
                listOf<Any>("file:///home/weigl/work/key-rpc/keyext.lsp/workspace/complex.key")
            )
        ).get()
    }
}