package rc.syslog


import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import java.time.OffsetDateTime
import java.util.regex.Pattern

class SyslogParser : ProcessFunction<String, SyslogEntry>() {
    // https://datatracker.ietf.org/doc/html/rfc5424#section-6
    // Der Syslog Faker fügt die Source IP hinzu ({IP}|{Syslog-Message}). In einer realen Anwendung ist dies die IP-Adresse des Syslog-Senders.
    private var rfc5424Regex: Pattern =
        Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\|<(\\d{1,3})>1 (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z) ([_\\w-]+) ([_\\w-]+) ([_\\w-]+) ([_\\w-]+) ([_\\w-]+)")

    override fun processElement(value: String?, ctx: Context?, collector: Collector<SyslogEntry>?) {

        // Null oder Leerstring sind fehlerhaft und werden ignoriert.
        if (value == null || value == "") {
            return
        }

        val syslog: SyslogEntry = GetSylogEntry(value) ?: return

        collector!!.collect(syslog)
    }

    private fun GetSylogEntry(message: String): SyslogEntry? {
        val properties: HashMap<String, Any> = HashMap()
        properties["tag"] = "Tag"

        // Mather erstellen
        val matcher = rfc5424Regex.matcher(message)

        if (!matcher.find()) {
            return null
        }

        val pri = matcher.group(2).toIntOrNull() ?: return null

        // severity
        var severity: String? = null
        val intSeverity = pri and 0x07

        when (intSeverity) {
            0 -> severity = "Emergency"
            1 -> severity = "Alert"
            2 -> severity = "Critical"
            3 -> severity = "Error"
            4 -> severity = "Warning"
            5 -> severity = "Notice"
            6 -> severity = "Informational"
            7 -> severity = "Debug"
        }

        // facility
        val intFacility = pri shr 3
        var facility: String? = null

        when (intFacility) {
            0 -> facility = "Kernel"
            1 -> facility = "UserLevel"
            2 -> facility = "MailSystem"
            3 -> facility = "SystemDaemons"
            4 -> facility = "Authorization"
            5 -> facility = "Syslog"
            6 -> facility = "Printer"
            7 -> facility = "News"
            8 -> facility = "Uucp"
            9 -> facility = "Clock"
            10 -> facility = "SecurityAuth"
            11 -> facility = "Ftp"
            12 -> facility = "Ntp"
            13 -> facility = "LogAudit"
            14 -> facility = "LogAlert"
            15 -> facility = "ClockDaemon"
            16 -> facility = "Local0"
            17 -> facility = "Local1"
            18 -> facility = "Local2"
            19 -> facility = "Local3"
            20 -> facility = "Local4"
            21 -> facility = "Local5"
            22 -> facility = "Local6"
            23 -> facility = "Local7"
        }

        return SyslogEntry(
            matcher.group(1),
            facility!!,
            severity!!,
            properties,
            message,
            OffsetDateTime.parse(matcher.group(3)),
            matcher.group(4),
            matcher.group(5),
            matcher.group(6),
            matcher.group(7)
        )
    }
}
