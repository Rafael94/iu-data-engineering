package rc.syslog

import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import java.time.OffsetDateTime

class SyslogParser : ProcessFunction<String, SyslogEntry>() {
    // Der Syslog Faker fügt die Source IP hinzu. In einer realen Anwendung ist dies die IP-Adresse des Syslog-Senders.
    // https://datatracker.ietf.org/doc/html/rfc5424#section-6
    var rfc5424Regex: Regex =
        Regex("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\|<(\\d{1,3})>1 (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z) ([_\\w-]+) ([_\\w-]+) ([_\\w-]+) ([_\\w-]+) ([_\\w-]+)")


    override fun processElement(value: String?, ctx: Context?, collector: Collector<SyslogEntry>?) {

        // Null oder Leerstring sind fehlerhaft und werden ignoriert.
        if (value == null || value == "") {
            return
        }

        val syslog: SyslogEntry = GetSylogEntry(value) ?: return

        collector!!.collect(syslog)
    }

    private fun GetSylogEntry(value: String): SyslogEntry? {
        val properties: HashMap<String, Any> = HashMap()
        properties["tag"] = "Tag"

        val match: MatchResult = rfc5424Regex.find(value) ?: return null

        // Der vollständige String + 8 Gruppen
        if (match.groups.count() != 9) {
            return null
        }

        // Für Syslog Entry wird die Datenklasse verwendet, was erfordert, dass die Klasse direkt mit den Eigenschaften initialisiert wird. Daher werden lokale Variablen verwendet
        var sourceIp: String? = null
        var facility: String? = null
        var severity: String? = null
        var timestamp: OffsetDateTime? = null
        var hostname: String? = null
        var appName: String? = null
        var procId: String? = null
        var msgId: String? = null

        for ((index, group) in match.groups.withIndex()) {
            if (group == null || group.value == "") {
                return null
            }

            when (index) {
                1 -> {
                    sourceIp = group.value
                }
                // https://datatracker.ietf.org/doc/html/rfc5424#section-6.2.1
                2 -> {
                    val pri = group.value.toIntOrNull() ?: return null

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

                    val intFacility = pri shr 3

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
                }

                3 -> {
                    timestamp = OffsetDateTime.parse(group.value)
                }

                4 -> {
                    hostname = group.value
                }

                5 -> {
                    appName = group.value
                }

                6 -> {
                    procId = group.value
                }

                7 -> {
                    msgId = group.value
                }
            }
        }

        return SyslogEntry(
            sourceIp!!,
            facility!!,
            severity!!,
            properties,
            value,
            timestamp!!,
            hostname!!,
            appName,
            procId,
            msgId
        )
    }
}
