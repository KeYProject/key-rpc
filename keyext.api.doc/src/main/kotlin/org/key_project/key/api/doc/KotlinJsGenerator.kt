package org.key_project.key.api.doc

import java.util.function.Supplier

/**
 * Generator for Kotlin/JS programs based on a given meta model.
 * Avoids JVM constructs as far as possible.
 *
 * @author Alexander Weigl
 * @version 1 (2026)
 */
abstract class KotlinJsGenerator(protected val metamodel: Metamodel.KeyApi) : Supplier<String> {
    val BASE_PACKAGE = "org.key_project.key.api.client"
    val PACKAGE = "$BASE_PACKAGE.stubs"

    protected fun adKotlin(typeName: String): String = when (typeName) {
        Metamodel.INT.name, "INT" -> "Int"
        Metamodel.LONG.name, "LONG" -> "Long"
        Metamodel.STRING.name, "STRING" -> "String"
        Metamodel.BOOL.name, "BOOL" -> "Boolean"
        Metamodel.DOUBLE.name, "DOUBLE" -> "Double"
        else -> {
            val t = findType(typeName)
            adKotlin(t)
        }
    }

    fun listType(t: String) = "List<$t>"
    fun eitherType(a: String, b: String) = "Either<$a,$b>"

    fun adKotlin(t: Metamodel.Type): String = when (t) {
        is Metamodel.ListType -> listType(adKotlin(t.componentType))
        is Metamodel.EitherType -> eitherType(adKotlin(t.a), adKotlin(t.b))
        Metamodel.INT -> "Int"
        Metamodel.LONG -> "Long"
        Metamodel.STRING -> "String"
        Metamodel.BOOL -> "Boolean"
        Metamodel.DOUBLE -> "Double"
        is Metamodel.EnumType,
        is Metamodel.ObjectType -> t.name
    }

    fun findType(typeName: String?): Metamodel.Type = this.metamodel.types.values
        .firstOrNull {
            if (it is Metamodel.ListType) {
                it.componentType.name == typeName
            } else {
                it.name == typeName
            }
        } ?: Metamodel.ObjectType("Any", "Any", listOf(), null)

    protected fun escapeKdoc(text: String): String {
        return text.replace("*/", "* /").replace("/*", "/ *").replace("\n", "\n     * ")
    }

    /**
     * Generates Kotlin/JS API server stubs for remote calls.
     */
    class KotlinJsApiGenServer(metamodel: Metamodel.KeyApi) : KotlinJsGenerator(metamodel) {
        override fun get(): String {
            val sorted = metamodel.endpoints.asSequence()
                .filter { it is Metamodel.ServerRequest || it is Metamodel.ServerNotification }
                .sortedBy { it.name }
                .groupBy { it.segment() }
                .toSortedMap()

            val sb = StringBuilder()
            sb.appendLine("package $PACKAGE")
            sb.appendLine()
            sb.appendLine("import $BASE_PACKAGE.*")
            sb.appendLine()
            sb.appendLine("/** Remote API client for KeY server communication. */")
            sb.appendLine("class KeyRemote(private val rpcLayer: RPCLayer) {")
            sb.appendLine()

            sorted.forEach { (name, endpoints) ->
                val cname = "Segment${name.replaceFirstChar { it.uppercase() }}"
                sb.appendLine("    val $name: $cname = $cname()")
                sb.appendLine()
            }

            sorted.forEach { (name, endpoints) ->
                val cname = "Segment${name.replaceFirstChar { it.uppercase() }}"
                sb.appendLine("    inner class $cname {")

                metamodel.segmentDocumentation[name]?.let { doc ->
                    sb.appendLine("        /** ${escapeKdoc(doc.text)} */")
                }

                endpoints.forEach { endpoint ->
                    sb.append(generateServerMethod(endpoint))
                }
                sb.appendLine("    }")
                sb.appendLine()
            }

            sb.appendLine("}")
            return sb.toString()
        }

        private fun generateServerMethod(endpoint: Metamodel.Endpoint): String {
            val sb = StringBuilder()

            endpoint.documentation?.let { doc ->
                sb.appendLine("        /** ${escapeKdoc(doc.text)} */")
            }

            val methodName = endpoint.name.substringAfterLast("/")
            val params = endpoint.args.joinToString(", ") { "${it.name}: ${adKotlin(it.type)}" }
            val returnType = if (endpoint is Metamodel.ServerRequest) {
                adKotlin(endpoint.returnType)
            } else {
                "Unit"
            }

            sb.appendLine("        suspend fun $methodName($params): $returnType {")
            val args = endpoint.args.joinToString(", ") { it.name }
            if (endpoint is Metamodel.ServerRequest) {
                sb.appendLine("            return rpcLayer.callSync(\"${endpoint.name}\", $args)")
            } else {
                sb.appendLine("            rpcLayer.callAsync(\"${endpoint.name}\", $args)")
            }
            sb.appendLine("        }")
            return sb.toString()
        }
    }

    /**
     * Generates Kotlin/JS client interface for local implementation.
     */
    class KotlinJsApiGenClient(metamodel: Metamodel.KeyApi) : KotlinJsGenerator(metamodel) {
        override fun get(): String = buildString {
            appendLine("package $PACKAGE")
            appendLine()
            appendLine("import $BASE_PACKAGE.*")
            appendLine("import kotlinx.coroutines.*")
            appendLine()
            appendLine("/** Client interface for implementing local KeY functionality. */")
            appendLine("interface KeyClient {")

            metamodel.endpoints.asSequence()
                .filter { it is Metamodel.ClientRequest || it is Metamodel.ClientNotification }
                .sortedBy { it.name }
                .forEach { endpoint ->
                    append(generateClientMethod(endpoint))
                }

            appendLine("}")
            appendLine()
        }

        private fun generateClientMethod(endpoint: Metamodel.Endpoint): String = buildString {
            endpoint.documentation?.let { doc ->
                appendLine("    /** ${escapeKdoc(doc.text)} */")
            }

            val methodName = endpoint.name.substringAfterLast("/")
            val params = endpoint.args.joinToString(", ") { "${it.name}: ${adKotlin(it.type)}" }

            if (endpoint is Metamodel.ClientRequest) {
                appendLine("    suspend fun $methodName($params): ${adKotlin(endpoint.returnType)}")
            } else {
                appendLine("    suspend fun $methodName($params)")
            }
            appendLine()
        }
    }

    /**
     * Generates Kotlin/JS data classes from the metamodel.
     * Uses kotlinx.serialization for JSON support.
     */
    class KotlinJsDataGen(metamodel: Metamodel.KeyApi) : KotlinJsGenerator(metamodel) {
        override fun get(): String = buildString {
            appendLine("package $PACKAGE")
            appendLine()
            appendLine("import $BASE_PACKAGE.*")
            appendLine("import kotlinx.serialization.*")

            metamodel.types.values.forEach { type ->
                append(printType(type))
            }

            appendLine("    val TYPE_REGISTRY: Map<String, String> = mapOf(")
            val typeEntries = metamodel.types.values.map {
                "        \"${it.identifier?.replace("$", "\\\$")}\" to \"${it.name}\""
            }.joinToString(",\n")
            appendLine(typeEntries)
            appendLine("    )")
            appendLine()
        }

        private fun printType(type: Metamodel.Type): String = buildString {
            when (type) {
                is Metamodel.ObjectType -> {
                    appendLine("    @Serializable")
                    appendLine("    data class ${type.name}(")

                    val fields = type.fields.map { field ->
                        val fieldType = adKotlin(field.type)
                        "        val ${field.name}: $fieldType"
                    }.joinToString(",\n")

                    appendLine(fields)
                    appendLine("    )")
                    appendLine()
                }

                is Metamodel.EnumType -> {
                    appendLine("    @Serializable")
                    appendLine("    enum class ${type.name} {")

                    val values = type.values.joinToString(",\n") { constant ->
                        "        ${constant.value}"
                    }
                    appendLine(values)
                    appendLine("    }")
                    appendLine()
                }

                else -> {}
            }
        }
    }
}
