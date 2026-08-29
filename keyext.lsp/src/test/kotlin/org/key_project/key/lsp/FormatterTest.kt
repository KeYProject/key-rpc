package org.key_project.key.lsp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.walk

/**
 * 
 * @author Alexander Weigl
 * @version 1 (29.08.26)
 */
class FormatterTest {
    @TestFactory
    fun formatWorkspace(): Sequence<DynamicTest> {
        return Paths.get("workspace").walk()
            .filter { it.extension == "key" }
            .map { DynamicTest.dynamicTest("$it") { formatPath(it) } }
    }

    private fun formatPath(it: Path) {
        val doc = Formatter().format(it)
        val x = it.readText()
        Assertions.assertThat(doc).isEqualTo(x)
    }

}