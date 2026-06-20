package org.key_project.key.api.doc

import java.util.function.Supplier

/**
 * Generation of Markdown documentation.
 *
 * @author Alexander Weigl
 * @version 1 (29.10.23)
 */
class DocGen(private val metamodel: Metamodel.KeyApi) : Supplier<String> {
    override fun get() = HtmlDocs(metamodel).render()
}