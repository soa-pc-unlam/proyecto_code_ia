package utils

import org.eclipse.jetty.websocket.api.WriteCallback

object NoOpWriteCallback : WriteCallback {
    override fun writeSuccess() = Unit
    override fun writeFailed(x: Throwable) = Unit
}
