package net.openmanet.perfapp.rpc

import com.connectrpc.ResponseMessage

fun <T> ResponseMessage<T>.toResult(): Result<T> = when (this) {
    is ResponseMessage.Success -> Result.success(message)
    is ResponseMessage.Failure -> Result.failure(cause)
}
