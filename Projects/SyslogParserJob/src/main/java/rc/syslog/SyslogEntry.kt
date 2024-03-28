package rc.syslog

data class SyslogEntry(
    var sourceIp: String,
    var facility: String,
    var severity: String,
    var properties: Map<String, Any>,
    var message: String? = null,
    var timestamp: java.time.OffsetDateTime? = null,
    var hostName: String? = null,
    var appName: String? = null,
    var procId: String? = null,
    var msgId: String? = null
)