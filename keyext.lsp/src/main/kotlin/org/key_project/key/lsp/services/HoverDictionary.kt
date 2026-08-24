package org.key_project.key.lsp.services

import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import java.util.concurrent.ConcurrentHashMap

object HoverDictionary {
    private val dynamic: Map<String, () -> String> = ConcurrentHashMap()

    private val dictionary: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        val lines = javaClass.getResourceAsStream("/doc.md")
            ?.bufferedReader()?.readLines()
            ?: error("Could not load /doc.md")

        var keys = setOf<String>()
        val builder = StringBuilder()
        for (line in lines) {
            if (line.startsWith("%keywords")) {
                keys = line.substringAfter(' ').splitToSequence(' ').toSet()
            } else {
                if (line.startsWith("-----")) {
                    val s = builder.toString().intern()
                    for (key in keys) {
                        map[key] = s
                    }
                    builder.clear()
                } else {
                    builder.appendLine(line)
                }
            }
        }
        ConcurrentHashMap(map)
    }

    fun get(text: String) = dynamic[text]?.let { it() } ?: dictionary[text]
}
