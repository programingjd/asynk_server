package info.jdavid.asynk.server.http

/**
 * Acceptance object used to indicate that the handler can handle a specific request.
 * @param bodyAllowed specifies whether the request is allowed to include incoming data.
 * @param bodyRequired specifies whether the request body when allowed is required or not.
 */
open class Acceptance(val bodyAllowed: Boolean, val bodyRequired: Boolean)
