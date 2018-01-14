package info.jdavid.server.http

class Status private constructor() {

  @Suppress("unused")
  companion object {

    val CONTINUE = 100
    val SWITCHING_PROTOCOLS = 100
    val OK = 200
    val CREATED = 201
    val ACCEPTED = 202
    val NO_CONTENT = 204
    val PARTIAL_CONTENT = 206
    val MOVED_PERMANENTLY = 301
    val FOUND = 302
    val SEE_OTHER = 303
    val NOT_MODIFIED = 304
    val TEMPORARY_REDIRECT = 307
    val PERMANENT_REDIRECT = 308
    val BAD_REQUEST = 400
    val UNAUTHORIZED = 401
    val FORBIDDEN = 403
    val NOT_FOUND = 404
    val METHOD_NOT_ALLOWED = 405
    val NOT_ACCEPTABLE = 406
    val REQUEST_TIMEOUT = 408
    val CONFLICT = 409
    val GONE = 410
    val PRECONDITION_FAILED = 412
    val PAYLOAD_TOO_LARGE = 413
    val URI_TOO_LONG = 414
    val UNSUPPORTED_MEDIA_TYPE = 415
    val REQUESTED_RANGE_NOT_SATISFIABLE = 416
    val EXPECTATION_FAILED = 417
    val PRECONDITION_REQUIRED = 428
    val TOO_MANY_REQUESTS = 429
    val REQUEST_HEADER_FIELDS_TOO_LARGE = 431
    val INTERNAL_SERVER_ERROR = 500
    val NOT_IMPLEMENTED = 501
    val BAD_GATEWAY = 502
    val SERVICE_UNAVAILABLE = 503
    val GATEWAY_TIMEOUT = 504

    val HTTP_STATUSES = mapOf(
      100 to "Continue",
      101 to "Switching Protocols",
      200 to "OK",
      201 to "Created",
      202 to "Accepted",
      204 to "No Content",
      206 to "Partial Content",
      301 to "Moved Permanently",
      302 to "Found",
      303 to "See Other",
      304 to "Not Modified",
      307 to "Temporary Redirect",
      308 to "Permanent Redirect",
      400 to "Bad Request",
      401 to "Unauthorized",
      403 to "Forbidden",
      404 to "Not Found",
      405 to "Method Not Allowed",
      406 to "Not Acceptable",
      408 to "Request Timeout",
      409 to "Conflict",
      410 to "Gone",
      412 to "Precondition Failed",
      413 to "Payload Too Large",
      414 to "URI Too Long",
      415 to "Unsupported Media Type",
      416 to "Requested Range Not Satisfiable",
      417 to "Expectation Failed",
      428 to "Precondition Required",
      429 to "Too Many Requests",
      431 to "Request Header Fields Too Large",
      500 to "Internal Server Error",
      501 to "Not Implemented",
      502 to "Bad Gateway",
      503 to "Service Unavailable",
      504 to "Gateway Timeout"
    )

    internal fun message(status: Int) = HTTP_STATUSES[status]

    internal val ERROR_RESPONSE =
      "HTTP/1.1 ${INTERNAL_SERVER_ERROR} ${HTTP_STATUSES[INTERNAL_SERVER_ERROR]}\r\n" +
      "Content-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"

  }

}
