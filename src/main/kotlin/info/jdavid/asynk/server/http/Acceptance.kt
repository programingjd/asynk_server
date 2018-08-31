package info.jdavid.asynk.server.http

/**
 * Acceptance object used to indicate that the handler can handle a specific request.
 * @param bodyAllowed specifies whether the request is allowed to include incoming data
 */
open class Acceptance(val bodyAllowed: Boolean, val bodyRequired: Boolean)
