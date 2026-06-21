package org.key_project.key.api.doc

import de.uka.ilkd.key.util.KeYConstants
import de.uka.ilkd.key.util.KeYResourceManager
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import org.intellij.markdown.flavours.space.SFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.key_project.key.api.doc.Metamodel.EnumType
import org.key_project.key.api.doc.Metamodel.ObjectType
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal val RE_METADATA = "^--- *$(.*?)^--- *$".toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

interface PageResource {
    fun write(target: Path, metamodel: Metamodel.KeyApi, pages: List<PageResource>)
    val relativeHtmlPath: String
    val title: String
    val order: Int
}

data class Link(
    override val relativeHtmlPath: String,
    override val title: String,
    override val order: Int
) : PageResource {
    override fun write(
        target: Path,
        metamodel: Metamodel.KeyApi,
        pages: List<PageResource>
    ) {
        // Nothing to be done
    }
}

private const val REFERENCE_HTML = "reference.html"

class ReferencePageResource() : PageResource {
    override fun write(target: Path, metamodel: Metamodel.KeyApi, pages: List<PageResource>) =
        target.writeText(ReferencePage(metamodel, pages).render())

    override val relativeHtmlPath = REFERENCE_HTML
    override val title = "API Reference"
    override val order: Int
        get() = 99999
}

data class MdPageResource(val path: Path) : PageResource {
    private val content by lazy { path.readText() }

    val metadata by lazy {
        meta.trim(' ', '-', '\n').splitToSequence('\n')
            .map {
                val (a, b) = it.split(':', limit = 2)
                a.trim() to b.trim('"', ' ')
            }.associate { it }
    }

    val meta by lazy {
        RE_METADATA.find(content)?.run {
            groupValues[1]
        } ?: ""
    }

    val text by lazy {
        RE_METADATA.find(content)?.run {
            content.substring(range.last + 1)
        } ?: content
    }

    val nameWithoutExtension = path.nameWithoutExtension
    override val relativeHtmlPath = nameWithoutExtension + ".html"
    override val title: String
        get() = metadata["title"] ?: nameWithoutExtension
    override val order: Int
        get() = metadata["menuOrder"]?.toInt() ?: 0

    override fun write(
        target: Path,
        metamodel: Metamodel.KeyApi,
        pages: List<PageResource>
    ) {
        val flavour = SFMFlavourDescriptor() // Markdown + Github + Jetbrains
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(text)
        val html = HtmlGenerator(text, parsedTree, flavour).generateHtml()
        target.writeText(MarkdownPage(html, metamodel, pages).render())
    }
}


abstract class BasePage(
    metamodel: Metamodel.KeyApi,
    val pages: List<PageResource>,
    val pageTitle: String = "KeY API Documentation"
) {
    val segmentDocumentation: Map<String, Metamodel.HelpText> = metamodel.segmentDocumentation
    val types: List<Metamodel.Type> =
        metamodel.types.values
            .sortedBy { it.name }
            .toList()
    val endpoints: List<Metamodel.Endpoint> = metamodel.endpoints.sortedBy { it.name }.toList()
    val endpointsBySegment: Map<String, List<Metamodel.Endpoint>> =
        metamodel.endpoints
            .sortedBy { it.name }
            .groupBy { it.segment() }
            .toSortedMap()

    val date: String = java.util.Date().toString()
    private val version: String =
        KeYResourceManager.getManager().getVersion() +
            " (" + KeYConstants.INTERNAL_VERSION.substring(0, 8) + ")"

    fun render() =
        buildString(1024 * 1024) {
            appendHTML(true).html {
                head {
                    meta(charset = "utf-8")
                    title { +pageTitle }
                    link("default.css", rel = "stylesheet")
                    link("custom.css", rel = "stylesheet")
                }
                body {
                    div {
                        id = "main"
                        nav()

                        div {
                            id = "content"
                            content()
                        }
                    }
                }
            }
        }

    protected abstract fun DIV.content()

    protected open fun DIV.nav() {
        nav {
            id = "nav"
            h1 { +"Documentation: KeY JSON-RPC API" }
            navMetaData()
            navPages()
            navTypes()
            navProcedures()
        }
    }

    protected open fun NAV.navPages() {
        h2 { +"Pages" }
        ul {
            pages.forEach {
                li {
                    a(it.relativeHtmlPath) { +it.title }
                }
            }
        }
    }

    protected open fun NAV.navProcedures() {
        h2 { +"Procedures" }
        ul("segments") {
            endpointsBySegment.forEach { (segname, seq) ->
                li {
                    a(href = "${REFERENCE_HTML}#${segname}") { +segname }
                    ul {
                        seq.forEach { ep ->
                            li {
                                a(href = "${REFERENCE_HTML}#${ep.name}") { +ep.name }
                            }
                        }
                    }
                }
            }
        }
    }

    protected open fun NAV.navTypes() {
        h2 { +"Types" }
        ul {
            types.forEach { type ->
                li {
                    a(href = "${REFERENCE_HTML}#${type.name}") { +type.name }
                }
            }
        }
    }

    protected open fun NAV.navMetaData() {
        div {
            +"""Version: $version<br>
                                    Date: $date}"""
        }
    }

}

class MarkdownPage(val html: String, metamodel: Metamodel.KeyApi, pages: List<PageResource>) :
    BasePage(metamodel, pages) {
    override fun DIV.content() {
        unsafe { +html }
        script {
            type = "module"
            +"""
            import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
            mermaid.initialize({ startOnLoad: true });
            mermaid.run({ querySelector: '.language-mermaid' });
            """
        }
    }
}

class ReferencePage(metamodel: Metamodel.KeyApi, pages: List<PageResource>) : BasePage(metamodel, pages) {
    override fun DIV.content() {
        h2 { +"Base Definitions" }
        p {
            +"This API builds upon and uses the same convention as the "
            a(href = "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/") {
                +"Language Server Protocol"
            }
            +". In basic, the protocol builds upon a simple HTTP protocol with a JSON payload. A request message looks as"
        }
        div("highlight") {
            pre {
                +"""Content-Length: 80\r\n
    Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n
    \r\n
    {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "env/version",
        "params": {
            ...
        }
    }"""
            }
        }
        p {
            +"Header "
            code { +"Content-Type" }
            +" is optional. The "
            code { +"""\\r\\n""" }
            +" are mandatory. The field "
            code { +"id" }
            +" make the difference between a request or a notification. "
            +"Later do not result into a response. A response message contains the fields "
            code { +"result" }
            +" or "
            code { +"error" }
            +", depending on a normal or exceptional execution of the request."
        }
        p {
            +"The communication is always asynchronous and duplex. "
            +"Meaning you can send and receive messages at any time. "
            +"For synchronous calls, the client and server library need to implement a waiting mechanism."
        }

        // ── Types ────────────────────────────────────────────────────────────────
        h2 { +"Types" }
        div("data-type") {
            for (type in types) {
                val isEnum = type is EnumType
                val kind = if (isEnum) "enum" else "type"

                div {
                    id = type.name
                    classes = setOf("data-type", kind)

                    h3 {
                        +type.name
                        +" "
                        span("kind") { +" $kind" }
                    }

                    div("highlight") {
                        span("k") { +kind }
                        +" "
                        span("kc") {
                            a(href = "#${type.name}") { +type.name }
                        }
                        +" { "
                        br()

                        // Struct fields
                        if (type is ObjectType) {
                            type.fields.forEach { field ->
                                div("entry field") {
                                    field.documentation?.let { doc ->
                                        div("cm") { +"/* ${doc.text} */" }
                                    }
                                    div {
                                        span("kc") {
                                            a(href = "#${type.name}") { +type.name }
                                        }
                                        +" "
                                        span("nv") { +field.name }
                                        +";"
                                    }
                                }
                            }
                        }
                        // Enum values
                        if (type is EnumType) {
                            type.values.sortedBy { it.value }.forEach { value ->
                                value.documentation?.let { doc ->
                                    +"/* ${doc.text} */"
                                }
                                +value.value
                            }
                        }

                        +"}"
                    }

                    type.documentation?.let { doc ->
                        div("documentation") {
                            p { +doc.text }
                            if (doc.others.isNotEmpty()) {
                                ul {
                                    doc.others.forEach { entry ->
                                        li {
                                            strong { +"${entry.name}:" }
                                            +" ${entry.value}"
                                        }
                                    }
                                }
                            }
                        }

                        if (type is ObjectType) {
                            type.jsonExample()?.let { example ->
                                div {
                                    details {
                                        summary { +"Example" }
                                        code("highlight") {
                                            style = "white-space: pre"
                                            +example
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Endpoints ────────────────────────────────────────────────────────────
        h2 {
            id = "endpoints"
            +"Endpoints"
        }

        div("endpoints") {
            val grouped = endpoints.groupBy { it.segment() }

            for ((segment, endpoints) in grouped) {
                h3 {
                    id = segment
                    +"Segment: $segment"
                }

                segmentDocumentation[segment]?.let { doc ->
                    div("documentation") {
                        p { +doc.text }
                        if (doc.others.isNotEmpty()) {
                            ul {
                                doc.others.forEach { entry ->
                                    li {
                                        strong { +"${entry.name}:" }
                                        +" ${entry.value}"
                                    }
                                }
                            }
                        }
                    }
                }

                endpoints.sortedBy { it.name }.forEach { ep ->

                    val kindClass = if (ep.isAsync) "async" else "sync"
                    val direction =
                        when {
                            ep.sender == "Server" && ep.isAsync -> "server ~~> client"
                            ep.sender == "Server" && !ep.isAsync -> "server --> client"
                            ep.sender == "Client" && !ep.isAsync -> "client --> server"
                            else -> "client ~~> server"
                        }

                    div {
                        id = ep.name
                        classes = setOf("endpoint", kindClass, ep.sender)

                        h4 {
                            if (ep.isAsync) {
                                span("async") { +"Notification:" }
                            } else {
                                span("sync") { +"Request:" }
                            }
                            +" ${ep.name} "
                            span("direction") { +direction }
                        }

                        code("highlight") {
                            +ep.name
                            span("p") { +"(" }
                            ep.args.forEachIndexed { index, arg ->
                                span("kc") { a(href = "#${arg.type}") { +arg.type } }
                                +" "
                                span("nv") { +arg.name }
                                if (index < ep.args.lastIndex) span("p") { +"," }
                            }
                            span("p") { +")" }
                            if (ep is Metamodel.Request) {
                                ep.returnType.let { ret ->
                                    span("p") { +":" }
                                    +" "
                                    span("kc") { a(href = "#${ret.name}") { +ret.name } }
                                }
                            }
                        }

                        ep.documentation?.let { doc ->
                            div("documentation") {
                                p { +doc.text }
                                if (doc.others.isNotEmpty()) {
                                    ul {
                                        doc.others.forEach { entry ->
                                            li {
                                                strong { +"${entry.name}:" }
                                                +" ${entry.value}"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
