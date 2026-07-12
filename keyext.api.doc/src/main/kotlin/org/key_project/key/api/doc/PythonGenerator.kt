package org.key_project.key.api.doc

import java.io.PrintWriter
import java.io.StringWriter
import java.util.function.Consumer
import java.util.function.Supplier

const val INDENT = "    "
const val THREE_QUOTES = "\"\"\""

/**
 * A structured printer for generating Python code with proper formatting.
 * Provides a fluent API for building Python code structures.
 */
class PythonPrinter {
    private val writer = StringWriter()
    private val out: PrintWriter = PrintWriter(writer)
    private var indentLevel: Int = 0
    private var atLineStart: Boolean = true

    /** Get the generated code as a string */
    fun getResult(): String = writer.toString()

    /** Increase indentation level */
    fun indent() {
        indentLevel++
    }

    /** Decrease indentation level */
    fun dedent() {
        require(indentLevel > 0)
        indentLevel--
    }

    /** Write text with automatic indentation */
    fun write(text: String) {
        text.forEach { char ->
            if (atLineStart) {
                printIndent()
                atLineStart = false
            }
            out.print(char)
            if (char == '\n') atLineStart = true
        }
    }

    private fun printIndent() {
        repeat(indentLevel) { out.print(INDENT) }
    }

    /** Write a single line with newline */
    fun writeln(text: String = "") {
        if (atLineStart) {
            printIndent()
        }
        write(text + "\n")
    }

    /** Write multiple lines */
    fun writeLines(vararg lines: String) {
        lines.forEach { writeln(it) }
    }

    /** Write a blank line */
    fun blankLine() {
        writeln()
    }

    /** Write an import statement */
    fun import(module: String, vararg names: String) {
        if (names.isEmpty()) {
            writeln("import $module")
        } else if (names.size == 1) {
            writeln("from $module import ${names[0]}")
        } else {
            writeln("from $module import (${names.joinToString(", ")})")
        }
    }

    /** Write a class definition */
    fun classDef(name: String, parent: String? = null, body: () -> Unit) {
        val parentClause = if (parent != null) "($parent)" else ""
        writeln("class $name$parentClause:")
        indent()
        body()
        dedent()
        blankLine()
    }

    /** Write a method definition */
    fun method(
        name: String,
        args: List<String> = emptyList(),
        returnType: String? = null,
        decorators: List<String> = emptyList(),
        body: () -> Unit
    ) {
        val args = listOf("self") + args
        decorators.forEach { writeln("@$it") }
        val argsStr = args.joinToString(", ")
        val returnStr = if (returnType != null) " -> $returnType" else ""
        writeln("def $name($argsStr)$returnStr:")
        indent()
        body()
        dedent()
        blankLine()
    }

    /** Write an abstract method */
    fun abstractMethod(
        name: String,
        args: List<String> = emptyList(),
        returnType: String? = null,
        docstring: Metamodel.HelpText? = null
    ) {
        writeln("@abstractmethod")
        val argsStr = args.joinToString(", ")
        val returnStr = if (returnType != null) " -> $returnType" else ""
        writeln("def $name($argsStr)$returnStr:")
        indent()
        if (docstring != null) {
            docstring(docstring)
        }
        writeln("pass")
        dedent()
        blankLine()
    }

    /** Write a docstring */
    fun docstring(text: String) {
        val lines = text.trim().lines()
        if (lines.size <= 1) {
            writeln("${THREE_QUOTES}${text.trim()}${THREE_QUOTES}")
        } else {
            writeln(THREE_QUOTES)
            lines.forEach { line -> writeln(line) }
            writeln(THREE_QUOTES)
        }
    }

    fun docstring(doc: Metamodel.HelpText?) {
        if (doc != null) {
            if ("null" == doc.text) return
            writeln(THREE_QUOTES)
            doc.text.lineSequence().forEach { line ->
                writeln(line)
            }
            if (doc.others.isNotEmpty()) {
                writeln()
                writeln()
                doc.others.forEach { (a, b) ->
                    writeln("@${a}\t$b")
                }
            }
            writeln(THREE_QUOTES)
        }
    }

    /** Write a field/attribute definition */
    fun field(name: String, type: String, docstring: Metamodel.HelpText? = null) {
        writeln()
        writeln("$name : $type")
        if (docstring != null) {
            docstring(docstring)
        }
    }

    /** Write an assignment */
    fun assign(target: String, value: String) {
        writeln("$target = $value")
    }

    /** Write formatted content */
    fun format(format: String, vararg args: Any?) {
        write(format.format(*args))
    }

    /** Execute action with temporary increased indentation */
    fun indented(action: () -> Unit) {
        indent()
        action()
        dedent()
    }
}

/**
 * @author Alexander Weigl
 * @version 1 (29.10.23)
 */
abstract class PythonGenerator(protected val metamodel: Metamodel.KeyApi) : Supplier<String> {
    val out = PythonPrinter()

    override fun get(): String {
        run()
        return out.getResult()
    }

    protected abstract fun run()

    protected fun asPython(typeName: String): String = when (typeName) {
        "int", "long", "INT", "LONG" -> "int"
        "string", "STRING" -> "str"
        "bool", "BOOL" -> "bool"
        "double", "float", "DOUBLE" -> "float"
        else -> {
            val t = findType(typeName)
            val s = asPython(t)
            s
        }
    }

    fun asPython(t: Metamodel.Type): String = when (t) {
        is Metamodel.ListType -> "typing.List[" + asPython(t.componentType) + "]"
        is Metamodel.EitherType -> "typing.Union[" + asPython(t.a) + ", " + asPython(t.b) + "]"
        Metamodel.INT, Metamodel.LONG -> "int"
        Metamodel.STRING -> "str"
        Metamodel.BOOL -> "bool"
        Metamodel.DOUBLE -> "float"
        else -> t.name
    }

    fun findType(typeName: String): Metamodel.Type {
        if (typeName.endsWith("[]")) {
            val t = findType(typeName.substringBeforeLast('['))
            return Metamodel.ListType(t)
        }

        this.metamodel.types[typeName]?.let { return it }

        this.metamodel.types.values.firstOrNull {
            if (it is Metamodel.ListType) {
                it.componentType.name == typeName
            } else {
                it.name == typeName
            }
        }?.let { return it }
        error("Type not found: $typeName: ${metamodel.types.keys}")
    }

    /**
     * Python API generator using the structured PythonPrinter.
     * Generates client and server stubs for the RPC API.
     */
    class PyApiGen(metamodel: Metamodel.KeyApi) : PythonGenerator(metamodel) {
        private val py = PythonPrinter()

        override fun run() {
            // Write imports
            py.writeLines(
                "from __future__ import annotations",
                "from .keydata import *",
                "from .rpc import ServerBase, LspEndpoint",
                "",
                "import enum",
                "import abc",
                "import typing",
                "from abc import abstractmethod"
            )
            py.blankLine()

            // Generate server code
            generateServer(
                metamodel.endpoints.asSequence()
                    .filter { it is Metamodel.ServerRequest || it is Metamodel.ServerNotification }
                    .sortedBy { it.name }
            )

            // Generate client code
            generateClient(
                metamodel.endpoints.asSequence()
                    .filter { it is Metamodel.ClientRequest || it is Metamodel.ClientNotification }
                    .sortedBy { it.name }
            )
        }

        private fun generateClient(sorted: Sequence<Metamodel.Endpoint>) {
            py.classDef("Client", "abc.ABCMeta") {
                sorted.forEach { clientEndpoint(it) }
            }
        }

        private fun clientEndpoint(endpoint: Metamodel.Endpoint) {
            val args = endpoint.args.map { "${it.name}: ${asPython(it.type)}" }
            val methodName = endpoint.name.replace("/", "_")

            if (endpoint is Metamodel.ClientRequest) {
                py.abstractMethod(
                    name = methodName,
                    args = args,
                    returnType = asPython(endpoint.returnType),
                    docstring = endpoint.documentation
                )
            } else {
                py.abstractMethod(
                    name = methodName,
                    args = args,
                    docstring = endpoint.documentation
                )
            }
        }

        private fun generateServer(sorted: Sequence<Metamodel.Endpoint>) {
            py.classDef("KeyServer", "ServerBase") {
                py.method(
                    name = "__init__",
                    args = listOf("endpoint : LspEndpoint"),
                    body = {
                        py.writeln("super().__init__(endpoint)")
                    }
                )
                sorted.forEach { serverEndpoint(it) }
            }
        }

        private fun serverEndpoint(endpoint: Metamodel.Endpoint) {
            val args = listOf("self") + endpoint.args.map { "${it.name}: ${asPython(it.type)}" }
            val params = if (endpoint.args.isEmpty()) "[]"
            else endpoint.args.joinToString(" , ", "[", "]") { it.name }
            val methodName = endpoint.name.replace("/", "_")

            if (endpoint is Metamodel.ServerRequest) {
                py.method(
                    name = methodName,
                    args = args,
                    returnType = asPython(endpoint.returnType),
                    body = {
                        py.docstring(endpoint.documentation)
                        py.writeln("return self._call_sync(\"${endpoint.name}\", $params)")
                    }
                )
            } else {
                py.method(
                    name = methodName,
                    args = args,
                    body = {
                        py.docstring(endpoint.documentation)
                        py.writeln("return self._call_async(\"${endpoint.name}\", $params)")
                    }
                )
            }
        }
    }


    class PyDataGen(metamodel: Metamodel.KeyApi) : PythonGenerator(metamodel) {
        override fun run() {
            out.write(
                """
                    from __future__ import annotations
                    import enum
                    import abc
                    import typing
                    from abc import abstractmethod, ABCMeta

                    """.trimIndent()
            )
            metamodel.types.values.forEach(Consumer { type: Metamodel.Type? -> this.printType(type) })

            val names: String =
                metamodel.types.values.joinToString(", ") {
                    "\"%s\": %s".format(
                        it.identifier,
                        it.name
                    )
                }
            out.format("KEY_DATA_CLASSES = { %s }%n%n", names)

            val namesReverse: String =
                metamodel.types.values.joinToString(",") {
                    "\"%s\": \"%s\"".format(it.name, it.identifier)
                }
            out.format("KEY_DATA_CLASSES_REV = { %s }%n%n", namesReverse)
        }

        private fun printType(type: Metamodel.Type?) {
            if (type is Metamodel.ObjectType) {
                out.classDef(type.name) {
                    out.docstring(type.documentation)

                    type.fields.forEach {
                        out.field(it.name, asPython(it.type), it.documentation)
                    }

                    out.writeln()
                    out.method("__init__", type.fields.map { it.name + ":" + asPython(it.type) }) {
                        if (type.fields.isEmpty()) out.writeln("pass")

                        for (field in type.fields) {
                            out.writeln("self.${field.name} = ${field.name}")
                        }
                    }

                }
            } else if (type is Metamodel.EnumType) {
                out.classDef(type.name, "enum.Enum") {
                    out.docstring(type.documentation)
                    type.values.forEach {
                        out.writeln()
                        out.docstring(it.documentation)
                        out.writeln("${it.value} = None")
                    }
                }
            }
            out.writeln()
        }
    }
}


