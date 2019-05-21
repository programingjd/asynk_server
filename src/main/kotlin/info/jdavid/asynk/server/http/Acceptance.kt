package info.jdavid.asynk.server.http

import java.net.InetSocketAddress

/**
 * Acceptance object used to indicate that the handler can handle a specific request.
 * @param remoteAddress specifies the address of the incoming connection.
 * @param bodyAllowed specifies whether the request is allowed to include incoming data.
 * @param bodyRequired specifies whether the request body when allowed is required or not.
 */
open class Acceptance(
  val remoteAddress: InetSocketAddress,
  val bodyAllowed: Boolean,
  val bodyRequired: Boolean
)
