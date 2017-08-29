package info.jdavid.server

class HPack() {

  internal enum class StaticTable(internal val headerName: String, internal val headerValue: String?) {
    NULL("", null),
    AUTHORITY(":authority", null),                                     //  1
    METHOD_GET(":method", "GET"),                                      //  2
    METHOD_POST(":method", "POST"),                                    //  3
    PATH_SLASH(":path", "/"),                                          //  4
    PATH_INDEX(":path", "/index.html"),                                //  5
    SCHEME_HTTP(":scheme", "http"),                                    //  6
    SCHEME_HTTPS(":scheme", "https"),                                  //  7
    STATUS_200(":status", "200"),                                      //  8
    STATUS_204(":status", "204"),                                      //  9
    STATUS_206(":status", "206"),                                      // 10
    STATUS_304(":status", "304"),                                      // 11
    STATUS_400(":status", "400"),                                      // 12
    STATUS_404(":status", "404"),                                      // 13
    STATUS_500(":status", "500"),                                      // 14
    ACCEPT_CHARSET("accept-charset", null),                            // 15
    ACCEPT_ENCODING("accept-encoding", "gzip, deflate"),               // 16
    ACCEPT_LANGUAGE("accept-language", null),                          // 17
    ACCEPT_RANGES("accept-ranges", null),                              // 18
    ACCEPT("accept", null),                                            // 19
    ACCESS_CONTROL_ALLOW_ORIGIN("access-control-allow-origin", null),  // 20
    AGE("age", null),                                                  // 21
    ALLOW("allow", null),                                              // 22
    AUTHORIZATION("authorization", null),                              // 23
    CACHE_CONTROL("cache-control", null),                              // 24
    CONTENT_DISPOSITION("content-disposition", null),                  // 25
    CONTENT_ENCODING("content-encoding", null),                        // 26
    CONTENT_LANGUAGE("content-language", null),                        // 27
    CONTENT_LENGTH("content-length", null),                            // 28
    CONTENT_LOCATION("content-location", null),                        // 29
    CONTENT_RANGE("content-range", null),                              // 30
    CONTENT_TYPE("content-type", null),                                // 31
    COOKIE("cookie", null),                                            // 32
    DATE("date", null),                                                // 33
    ETAG("etag", null),                                                // 34
    EXPECT("expect", null),                                            // 35
    EXPIRES("expires", null),                                          // 36
    FROM("from", null),                                                // 37
    HOST("host", null),                                                // 38
    IF_MATCH("if-match", null),                                        // 39
    IF_MODIFIED_SINCE("if-modified-since", null),                      // 40
    IF_NONE_MATCH("if-none-match", null),                              // 41
    IF_RANGE("if-range", null),                                        // 42
    IF_UNMODIFIED_SINCE("if-unmodified-since", null),                  // 43
    LAST_MODIFIED("last-modified", null),                              // 44
    LINK("link", null),                                                // 45
    LOCATION("location", null),                                        // 46
    MAX_FORWARDS("max-forwards", null),                                // 47
    PROXY_AUTHENTICATE("proxy-authenticate", null),                    // 48
    PROXY_AUTHORIZATION("proxy-authorization", null),                  // 49
    RANGE("range", null),                                              // 50
    REFERER("referer", null),                                          // 51
    REFRESH("refresh", null),                                          // 52
    RETRY_AFTER("retry-after", null),                                  // 53
    SERVER("server", null),                                            // 54
    SET_COOKIE("set-cookie", null),                                    // 55
    STRICT_TRANSPORT_SECURITY("strict-transport-security", null),      // 56
    TRANSFER_ENCODING("transfer-encoding", null),                      // 57
    USER_AGENT("user-agent", null),                                    // 58
    VARY("vary", null),                                                // 59
    VIA("via", null),                                                  // 60
    WWW_AUTHENTICATE("www-authenticate", null),                        // 61
  }

}
