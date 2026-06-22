package org.key_project.key.api.client

actual class RPCLayer {
    actual suspend inline fun <reified T> callSync(methodName: String, vararg params: Any): T {
        TODO("Not yet implemented")
    }

    actual inline fun callAsync(methodName: String, vararg params: Any) {
    }
}