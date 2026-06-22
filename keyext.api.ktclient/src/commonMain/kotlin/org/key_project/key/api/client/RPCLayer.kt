package org.key_project.key.api.client

/**
 * 
 * @author Alexander Weigl 
 * @version 1 (22.06.26)
 */
expect class RPCLayer {
    suspend inline fun <reified T> callSync(methodName: String, vararg params: Any): T
    inline fun callAsync(methodName: String, vararg params: Any)
}