package org.key_project.key.api.doc

import com.github.therapi.runtimejavadoc.*
import de.uka.ilkd.key.proof.Proof
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.keyproject.key.api.remoteapi.KeyApi
import org.keyproject.key.api.remoteclient.ClientApi
import java.io.File
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

/**
 * @author Alexander Weigl
 * @version 1 (14.10.23)
 */
class ExtractMetaData : Runnable {
    private val endpoints: MutableList<Metamodel.Endpoint> = mutableListOf()
    private val types: MutableMap<String, Metamodel.Type> = mutableMapOf()
    private val segDocumentation: MutableMap<String, Metamodel.HelpText> = TreeMap()

    val api: Metamodel.KeyApi = Metamodel.KeyApi(endpoints, types, segDocumentation)

    override fun run() {
        // init with default built-in types
        types["int"] = Metamodel.INT
        types["Integer"] = Metamodel.INT
        types["short"] = Metamodel.INT
        types["short"] = Metamodel.INT
        types["byte"] = Metamodel.INT
        types["byte"] = Metamodel.INT
        types["long"] = Metamodel.LONG
        types["Long"] = Metamodel.LONG
        types["boolean"] = Metamodel.BOOL
        types["Boolean"] = Metamodel.BOOL
        types["bool"] = Metamodel.BOOL
        types["Bool"] = Metamodel.BOOL
        types["String"] = Metamodel.STRING
        types["string"] = Metamodel.STRING
        types["double"] = Metamodel.DOUBLE
        types["Double"] = Metamodel.DOUBLE

        for (method in KeyApi::class.java.getMethods()) {
            addServerEndpoint(method)
        }

        for (method in ClientApi::class.java.getMethods()) {
            addClientEndpoint(method)
        }

        for (anInterface in KeyApi::class.java.interfaces) {
            val js = anInterface.getAnnotation(JsonSegment::class.java)
            if (js != null) {
                val key = js.value
                val doc = findDocumentation(anInterface)
                doc?.let { segDocumentation.put(key, it) }
            }
        }
    }

    private fun addServerEndpoint(method: Method) {
        val jsonSegment = method.declaringClass.getAnnotation(JsonSegment::class.java) ?: return
        val segment: String = jsonSegment.value

        val req = method.getAnnotation(JsonRequest::class.java)
        val resp = method.getAnnotation(JsonNotification::class.java)

        val args = translate(method.parameters)

        if (req != null) {
            val mn: String = callMethodName(method.name, segment, req.value, req.useSegment)

            if (method.returnType == Void::class.java) {
                System.err.println("Found void as return type for a request!  $method")
                return
            }

            val retType = getOrFindType(method.genericReturnType)
            Objects.requireNonNull(retType, "No retType found " + method.genericReturnType)
            val documentation = findDocumentation(method)
            val mm = Metamodel.ServerRequest(mn, documentation, args, retType)
            endpoints.add(mm)
            return
        }

        if (resp != null) {
            val mn: String = callMethodName(method.name, segment, resp.value, resp.useSegment)
            val documentation = findDocumentation(method)
            val mm = Metamodel.ServerNotification(mn, documentation, args)
            endpoints.add(mm)
            return
        }

        throw IllegalStateException(
            "Method $method is neither a request nor a notification"
        )
    }

    private fun findDocumentation(method: Method): Metamodel.HelpText? {
        val javadoc = RuntimeJavadoc.getJavadoc(method)
        if (javadoc.isEmpty) return null

        val visitor = ToHtmlStringCommentVisitor()
        javadoc.comment.visit(visitor)

        val t = javadoc.throws.stream()
            .map { it: ThrowsJavadoc? ->
                Metamodel.HelpTextEntry(
                    it!!.name,
                    it.comment.toString()
                )
            }

        val p = javadoc.params.stream()
            .map { it: ParamJavadoc? ->
                Metamodel.HelpTextEntry(
                    it!!.name,
                    it.comment.toString()
                )
            }

        val r = Stream.of(Metamodel.HelpTextEntry("returns", javadoc.returns.toString()))

        return Metamodel.HelpText(
            visitor.build(),
            Stream.concat(r, Stream.concat(p, t)).toList()
        )
    }

    private fun translate(parameters: Array<Parameter>) =
        parameters.map { this.translate(it) }.toList()

    private fun translate(parameter: Parameter): Metamodel.Argument {
        val type = getOrFindType(parameter.getType()).name
        return Metamodel.Argument(parameter.name, type)
    }

    private fun getOrFindType(type: Class<*>): Metamodel.Type {
        if (type == String::class.java) return Metamodel.STRING
        if (type == Int::class.java) return Metamodel.INT
        if (type == java.lang.Double::class.java) return Metamodel.DOUBLE
        if (type == java.lang.Long::class.java) return Metamodel.LONG
        if (type == Char::class.java) return Metamodel.LONG
        if (type == File::class.java) return Metamodel.STRING
        if (type == java.lang.Boolean::class.java) return Metamodel.BOOL
        if (type == java.lang.Boolean.TYPE) return Metamodel.BOOL

        if (type == Integer.TYPE) return Metamodel.INT
        if (type == java.lang.Double.TYPE) return Metamodel.DOUBLE
        if (type == java.lang.Long.TYPE) return Metamodel.LONG
        if (type == Character.TYPE) return Metamodel.LONG

        if (type == CompletableFuture::class.java) {
            return getOrFindType(type.getTypeParameters()[0].javaClass)
        }

        if (type == List::class.java) {
            // TODO try to get the type below.
            val subType = getOrFindType(type.getTypeParameters()[0])
            return Metamodel.ListType(subType)
        }

        check(!(type == Class::class.java || type == Constructor::class.java || type == Proof::class.java)) { "Forbidden class reached!" }

        val t = types[type.name] ?: types[type.simpleName]
        if (t != null) return t
        val a = createType(type)
        return a
    }

    private fun createType(type: Class<*>): Metamodel.Type {
        val documentation = findDocumentation(type)
        if (type.isEnum) {
            val constants = arrayListOf<Metamodel.EnumConstant>()
            val mtype = Metamodel.EnumType(type.getSimpleName(), type.getName(), constants, documentation)
            types[mtype.name] = mtype

            type.getEnumConstants().map {
                Metamodel.EnumConstant(it.toString(), findDocumentationEnum(type, it))
            }.forEach { constants.add(it) }

            return mtype
        }

        val fields = arrayListOf<Metamodel.Field>()
        val mtype = Metamodel.ObjectType(type.getSimpleName(), type.getName(), fields, documentation)
        types[mtype.name] = mtype

        type.getDeclaredFields().asSequence()
            .map {
                Metamodel.Field(
                    it.name, getOrFindType(it.genericType).name,
                    if (type.isRecord) {
                        findDocumentationRecord(type, it.name)
                    } else {
                        findDocumentation(it)
                    }
                )
            }.forEach { fields.add(it) }

        return mtype
    }

    private fun findDocumentation(it: Field): Metamodel.HelpText? {
        val javadoc = RuntimeJavadoc.getJavadoc(it)
        if (javadoc.isEmpty) return null
        return printFieldDocumentation(javadoc)
    }

    private fun findDocumentationEnum(type: Class<*>, enumConstant: Any): Metamodel.HelpText? {
        val javadoc = RuntimeJavadoc.getJavadoc(type)
        if (javadoc.isEmpty) return null
        for (cdoc in javadoc.enumConstants) {
            if (cdoc.name.equals(enumConstant.toString(), ignoreCase = true)) {
                return printFieldDocumentation(cdoc)
            }
        }
        return null
    }

    private fun findDocumentationRecord(type: Class<*>, name: String?): Metamodel.HelpText? {
        val javadoc = RuntimeJavadoc.getJavadoc(type)
        if (javadoc.isEmpty) return null
        for (cdoc in javadoc.recordComponents) {
            if (cdoc.name.equals(name, ignoreCase = true)) {
                return Metamodel.HelpText(cdoc.comment.toString(), mutableListOf())
            }
        }
        return null
    }

    private fun findDocumentation(type: Class<*>): Metamodel.HelpText? {
        val classDoc = RuntimeJavadoc.getJavadoc(type)
        if (!classDoc.isEmpty) { // optionally skip absent documentation
            val other = classDoc.other
                .stream().map { it: OtherJavadoc? ->
                    Metamodel.HelpTextEntry(
                        it!!.name,
                        it.comment.toString()
                    )
                }
            val also = classDoc.seeAlso
                .stream().map { it: SeeAlsoJavadoc? ->
                    Metamodel.HelpTextEntry(
                        it!!.seeAlsoType.toString(),
                        it.stringLiteral
                    )
                }

            return Metamodel.HelpText(
                classDoc.comment.toString(),
                Stream.concat(other, also).toList()
            )
        }
        return null
    }

    private fun addClientEndpoint(method: Method) {
        val jsonSegment = method.declaringClass.getAnnotation(JsonSegment::class.java)
        val segment: String? = if (jsonSegment == null) "" else jsonSegment.value

        val req = method.getAnnotation(JsonRequest::class.java)
        val resp = method.getAnnotation(JsonNotification::class.java)

        val args = translate(method.parameters)

        if (req != null) {
            val retType = getOrFindType(method.genericReturnType)
            Objects.requireNonNull(retType)
            val mn: String = callMethodName(method.name, segment, req.value, req.useSegment)
            val documentation = findDocumentation(method)
            val mm = Metamodel.ClientRequest(mn, documentation, args, retType)
            endpoints.add(mm)
            return
        }

        if (resp != null) {
            val mn: String = callMethodName(method.name, segment, resp.value, resp.useSegment)
            val documentation = findDocumentation(method)
            val mm = Metamodel.ClientNotification(mn, documentation, args)
            endpoints.add(mm)
        }
    }

    fun getOrFindType(type: Type): Metamodel.Type = when (type) {
        is Class<*> -> getOrFindType(type)

        is ParameterizedType -> {
            when (val typeName = type.rawType.typeName) {
                CompletableFuture::class.java.name -> getOrFindType(type.actualTypeArguments[0])

                List::class.java.name -> Metamodel.ListType(getOrFindType(type.actualTypeArguments[0]))

                Either::class.java.name ->
                    Metamodel.EitherType(
                        getOrFindType(type.actualTypeArguments[0]),
                        getOrFindType(type.actualTypeArguments[1])
                    )

                else -> error("unsupported parameterized type: $typeName")
            }
        }

        else -> error("Could not determine type for $type")
    }

    companion object {
        private fun printFieldDocumentation(javadoc: FieldJavadoc): Metamodel.HelpText {
            val visitor = ToHtmlStringCommentVisitor()
            javadoc.comment.visit(visitor)
            val t = javadoc.other.map {
                Metamodel.HelpTextEntry(it.name, it.comment.toString())
            }
            val p = javadoc.seeAlso.map {
                Metamodel.HelpTextEntry(it.seeAlsoType.toString(), it.stringLiteral)
            }
            return Metamodel.HelpText(visitor.build(), (p + t).toMutableList())
        }

        private fun callMethodName(
            method: String, segment: String?,
            userValue: String?,
            useSegment: Boolean
        ): String {
            return if (!useSegment) {
                if (userValue.isNullOrBlank()) {
                    method
                } else {
                    userValue
                }
            } else {
                if (userValue.isNullOrBlank()) {
                    "$segment/$method"
                } else {
                    "$segment/$userValue"
                }
            }
        }
    }
}