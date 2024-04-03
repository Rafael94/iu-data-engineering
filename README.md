- [Vorwort](#vorwort)
    * [Syslog Faker](#syslog-faker)
        + [Kafka Client (C#)](#kafka-client--c--)
    * [Apache Kafka](#apache-kafka)
    * [Apache Flink](#apache-flink)
    * [ElasticSearch](#elasticsearch)
    * [Kibana](#kibana)
- [Vorteile der Infrastruktur](#vorteile-der-infrastruktur)
- [Einrichtung](#einrichtung)
    * [Infrastruktur](#infrastruktur)
    * [Syslog Parser Job](#syslog-parser-job)
        + [Parameters](#parameters)
            - [Apache Flink](#apache-flink-1)
            - [Debug (IDE)](#debug--ide-)
    * [ElasticSearch/Kibana](#elasticsearch-kibana)
        + [Automatisches löschen](#automatisches-l-schen)
- [Anmeldedaten](#anmeldedaten)
    * [Apache Flink](#apache-flink-2)
    * [Kibana](#kibana-1)
- [Verbesserungen](#verbesserungen)
    * [Syslog Parser Job](#syslog-parser-job-1)
        + [Syslog Format](#syslog-format)
        + [Auslesen der zusätzlichen Daten](#auslesen-der-zus-tzlichen-daten)
        + [Parsen optimieren](#parsen-optimieren)
        + [Daten minimieren](#daten-minimieren)

# Vorwort

![Übersicht](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/Infrastruktur.png)

Dieses Projekt wurde im Rahmen einer Projektarbeit an der IU entwickelt.

Das Hauptziel dieser Projektarbeit besteht darin, Syslog-Meldungen zu verarbeiten und zentral zu speichern. Syslog-Meldungen können von verschiedenen Anwendungen und Netzwerkkomponenten an einen Syslog-Server gesendet werden, der für ihre Aufbereitung zuständig ist. Jede Syslog-Meldung besteht aus einem zusammenhängenden String gemäß den Standards RFC5424 oder RFC3164 und muss für eine weiterführende Analyse so strukturiert werden, dass gezielt nach bestimmten Eigenschaften gesucht und aggregiert werden kann.

Im Rahmen dieser Projektarbeit wird ein Syslog-Producer entwickelt, der kontinuierlich Syslog-Meldungen versendet. Diese Meldungen werden an einen Kafka-Cluster übermittelt, der ausschließlich als Message Broker fungiert. Zur Verarbeitung der Syslog-Meldungen wird Apache Flink eingesetzt. Eine Java-Applikation, die auf Apache Flink läuft, extrahiert die Meldungen aus Kafka und bereitet sie auf, indem sie den String analysiert und in seine Bestandteile zerlegt. Diese Bestandteile umfassen feste Informationen wie Hostname und AppName sowie variable Komponenten, die von der jeweiligen Anwendung abhängen. Die aufbereiteten Meldungen werden anschließend in einem ElasticSearch-Cluster gespeichert, wo sie für weitere Auswertungen zur Verfügung stehen. Kibana dient als Schnittstelle zu ElasticSearch, um die Meldungen zu visualisieren und zu analysieren.

Die Anwendungen werden in Docker-Containern ausgeführt und mittels Docker-Compose konfiguriert. Da Apache Flink keine integrierte Authentifizierung für das Interface bietet, wird Nginx als Reverse Proxy eingesetzt, um die Authentifizierung zu übernehmen.

## Syslog Faker

Der Syslog Faker generiert zufällige Syslog-Meldungen im RFC 5424 Format. Vor jeder Meldung wird eine `Source IP` mit einem nachfolgenden `|` angegeben. In einer realen Anwendung entspricht die Source IP der IP-Adresse des Senders der Syslog-Meldung.

Eine Beispiel-Meldung könnte wie folgt aussehen: `192.168.2.150|<52>1 2024-04-03T17:33:05Z Server-001 SyslogFaker - - - eventtime=1712165585007 logid="0000000020" type="traffic" subtype="forward" level="notice" srcip=192.168.2.78 srcport=18288 dstIp=192.168.2.250 dstPort=380 action="Denied"`

Im produktiven Einsatz würden die Meldungen über UDP oder TCP empfangen und dann an Kafka weitergeleitet. Durch einen vorgeschalteten Load-Balancer könnte die Skalierbarkeit des Syslog-Empfängers verbessert und die Ausfallsicherheit erhöht werden, falls ein Syslog-Empfänger ausfällt.

In der `appsettings.json`, die nach Ausführung des PowerShell-Skripts `init.ps1` im Verzeichnis `Dockerhttps://github.com/Rafael94/iu-data-engineering/blob/main/Images/SyslogFaker` erstellt wird, kann die Wartezeit (`WaitingTimeBetweenMessages`) zwischen zwei Meldungen geändert werden. Standardmäßig wird eine Wartezeit von 10 Millisekunden eingestellt.

Der Quellcode befindet sich im Verzeichnis `Projects/SyslogFaker` und kann mit Visual Studio geöffnet werden. Zur Ausführung des Programms wird das [.NET 8 SDK](https://dotnet.microsoft.com/en-us/download/dotnet/8.0) benötigt. Eine manuelle Kompilierung des Projekts ist erforderlich, sodass die genannten Abhängigkeiten nicht installiert werden müssen. Das Programm wird in einem Docker-Container kompiliert und als Image im lokalen Repository gespeichert.

Alternativ könnte das fertige Image direkt in Docker Hub hochgeladen werden, um die manuelle Kompilierung zu umgehen. Zum Testen ist jedoch ein lokales Image ausreichend, und es ist kein Docker-Hub-Konto zum Hochladen des Images erforderlich.

Der Syslog Faker wird mit [AOT](https://learn.microsoft.com/en-us/aspnet/core/fundamentals/native-aot?view=aspnetcore-8.0) kompiliert, um eine verbesserte Startzeit und eine kleinere Anwendung zu erreichen.

### Kafka Client (C#)

Um AOT im Syslog Faker zu ermöglichen, wurde der Kafka-Client (C#) aus einem anderen [Branch](https://github.com/latop2604/confluent-kafka-dotnet/tree/feat/aot) heruntergeladen. Sobald der [Pull-Request](https://github.com/confluentinc/confluent-kafka-dotnet/issues/2146) akzeptiert ist, können die offiziellen NuGet-Pakete verwendet werden, um die neueste Version des Kafka-Clients einzubinden.

## Apache Kafka

Kafka fungiert als dedizierter Message-Broker, der Syslog-Meldungen empfängt, während Apache Flink diese Meldungen abruft, wodurch ein Nachrichtenpuffer entsteht. Selbst bei einem Ausfall des Apache Flink-Jobs gehen keine Meldungen verloren, da Kafka sie nur für eine begrenzte Zeit zwischenspeichert.

Darüber hinaus ermöglicht Kafka eine effiziente Skalierung. Selbst bei mehreren Jobs werden die Meldungen nicht doppelt verarbeitet, da Kafka anhand der `group-id` erkennt, welche Meldungen bereits abgerufen wurden.

In früheren Versionen wurde Zookeeper zur Clusterverwaltung eingesetzt. In den [neueren Versionen](https://kafka.apache.org/blog) wird stattdessen das KRaft-Protokoll verwendet. Dadurch entfällt die Notwendigkeit eines separaten Zookeeper-Clusters. Der Kafka-Cluster wird stattdessen als Verbund aus drei Servern betrieben.

## Apache Flink

Apache Flink wird verwendet, um den Syslog-Parser auszuführen. Der Quellcode für den Syslog-Parser befindet sich im Verzeichnis `Projects/SyslogParserJob` und ist teilweise in Java und Kotlin programmiert. Der Cluster besteht aus drei Servern: ein Server fungiert als Jobmanager und zwei als Taskmanager. Jeder Taskmanager kann gleichzeitig bis zu drei Jobs ausführen.

Da Apache Flink keine integrierte Authentifizierung unterstützt, wird Nginx als Reverse-Proxy eingesetzt.

Der Syslog-Parser-Job speichert die verarbeiteten Daten in Elasticsearch. Nach der Verarbeitung sieht die oben gezeigte Meldung wie folgt aus:

```
{
   "hostName":[
      "Server-001"
   ],
   "properties.dstIp.keyword":[
      "192.168.2.250"
   ],
   "msgId.keyword":[
      "-"
   ],
   "properties.srcip":[
      "192.168.2.78"
   ],
   "sourceIp.keyword":[
      "192.168.2.150"
   ],
   "procId":[
      "-"
   ],
   "properties.subtype":[
      "forward"
   ],
   "msgId":[
      "-"
   ],
   "properties.srcip.keyword":[
      "192.168.2.78"
   ],
   "properties.srcport":[
      18288
   ],
   "severity.keyword":[
      "Warning"
   ],
   "facility.keyword":[
      "Printer"
   ],
   "properties.dstIp":[
      "192.168.2.250"
   ],
   "hostName.keyword":[
      "Server-001"
   ],
   "properties.dstPort":[
      380
   ],
   "properties.logid":[
      "0000000020"
   ],
   "properties.eventtime":[
      1712165600000
   ],
   "timestamp":[
      "2024-04-03T17:33:05.000Z"
   ],
   "properties.subtype.keyword":[
      "forward"
   ],
   "severity":[
      "Warning"
   ],
   "properties.action":[
      "Denied"
   ],
   "properties.action.keyword":[
      "Denied"
   ],
   "properties.logid.keyword":[
      "0000000020"
   ],
   "appName":[
      "SyslogFaker"
   ],
   "properties.level":[
      "notice"
   ],
   "properties.level.keyword":[
      "notice"
   ],
   "message":[
      "192.168.2.150|<52>1 2024-04-03T17:33:05Z Server-001 SyslogFaker - - - eventtime=1712165585007 logid=\"0000000020\" type=\"traffic\" subtype=\"forward\" level=\"notice\" srcip=192.168.2.78 srcport=18288 dstIp=192.168.2.250 dstPort=380 action=\"Denied\""
   ],
   "properties.type.keyword":[
      "traffic"
   ],
   "appName.keyword":[
      "SyslogFaker"
   ],
   "sourceIp":[
      "192.168.2.150"
   ],
   "message.keyword":[
      "192.168.2.150|<52>1 2024-04-03T17:33:05Z Server-001 SyslogFaker - - - eventtime=1712165585007 logid=\"0000000020\" type=\"traffic\" subtype=\"forward\" level=\"notice\" srcip=192.168.2.78 srcport=18288 dstIp=192.168.2.250 dstPort=380 action=\"Denied\""
   ],
   "procId.keyword":[
      "-"
   ],
   "facility":[
      "Printer"
   ],
   "properties.type":[
      "traffic"
   ]
}
```

In den Syslog-Meldungen sind feste Bestandteile wie `HostName` als separate Felder in der obersten Ebene enthalten. Dynamische Bestandteile, die vom Syslog-Sender hinzugefügt werden, stehen unter dem Feld `properties`. Der Parser verarbeitet derzeit nur Key-Value-Paare wie `type="traffic"` als zusätzliche Informationen.

## ElasticSearch

Elasticsearch wird in der Version 7.x verwendet, da zum Zeitpunkt des Projekts noch kein Paket für die Anbindung von Apache Flink an Elasticsearch im Maven-Repository verfügbar war. Der Cluster besteht aus einem Verbund von drei Servern.

Elasticsearch ist für das automatische Löschen der Daten verantwortlich.

## Kibana

Kibana ist ein Web-Dashboard, das zur Abfrage der verarbeiteten und gespeicherten Daten aus Elasticsearch verwendet wird. Bei einem Ausfall dieser Komponente besteht keine Gefahr für die Datenintegrität, weshalb kein Cluster-Betrieb erforderlich ist.

# Vorteile der Infrastruktur

Der oben beschriebene Aufbau bietet mehrere Vorteile. Zum einen ist die Struktur durch die Containerisierung reproduzierbar. Es werden keine weiteren Abhängigkeiten benötigt, außer der Docker-Umgebung, was potenzielle Fehler bei der Installation einzelner Anwendungen vermeidet.

Ein Installationsproblem könnte auftreten, wenn eine Anwendung in einer falschen Version installiert wird. Dies könnte dazu führen, dass der Syslog-Parser-Job aufgrund einer inkonsistenten Apache Flink-Version nicht ausgeführt werden kann.

Ein weiterer Vorteil liegt in der Skalierbarkeit. Obwohl dieses Projekt die gesamte Struktur mit Hilfe einer Docker-Compose-Datei realisiert, ist dies nicht zwingend erforderlich. Es können einzelne Docker-Container ausgewählt und auf verschiedenen Servern betrieben werden, was eine horizontale Skalierung ermöglicht. Darüber hinaus kann die Sicherheit durch die Aufteilung der Dienste auf verschiedene Server erhöht werden. Die einzelnen Komponenten können in verschiedenen Netzwerken betrieben werden, wodurch die Daten besser geschützt sind. Beispielsweise kann der Syslog-Server in einem weniger abgesicherten Netzwerk betrieben werden, während Elasticsearch in einem besser geschützten Netzwerk mit strengeren Restriktionen läuft. Diese Aufteilung trägt dazu bei, die Daten besser zu schützen, insbesondere wenn ein Server kompromittiert ist.

Des Weiteren führt die Verwendung von Clustern und die Verteilung auf mehrere Server zu einer erhöhten Ausfallsicherheit. Die meisten Anwendungen werden als Cluster betrieben oder können mehrere Instanzen einer Anwendung haben. Sollte eine Anwendung ausfallen, ist der Betrieb theoretisch weiterhin möglich, vorausgesetzt, dass die Anwendungen nicht alle auf dem gleichen Server laufen. Durch das Clustern und den Betrieb auf mehreren Servern/Festplatten wird auch die Datensicherheit erhöht, da die Daten redundant vorhanden sind und ein Hardwareausfall nicht zu einem Datenverlust führt. Es ist jedoch wichtig zu beachten, dass dies keine Backups ersetzt und der Betrieb von Festplatten in einem RAID-Verbund weiterhin sinnvoll ist.

Darüber hinaus wird durch diese Umsetzung eine hohe Modularität erreicht. Jede Schicht hat maximal zwei Abhängigkeiten: eine zur vorherigen und eine zur nächsten Schicht. Dies ermöglicht den Austausch einzelner Komponenten, ohne dass Änderungen an anderen Schichten vorgenommen werden müssen. Zum Beispiel kann der Syslog-Server ausgetauscht werden, ohne dass Änderungen am Syslog-Parser erforderlich sind. Wenn Apache Kafka ausgetauscht wird, müssen jedoch der Syslog-Server und der Syslog-Parser angepasst werden, während keine Änderungen an Elasticsearch erforderlich sind.

# Einrichtung

Das Projekt wurde unter Windows mit Docker-Desktop (WSL 2) getestet. Theoretisch sollte das Projekt auch unter Linux ausführbar sein. Für die Ausführung des PowerShell-Skripts wird PowerShell (Core) und Docker benötigt.

Um das Projekt auszuführen, muss das gesamte Repository heruntergeladen werden. Eine manuelle Kompilierung der Artefakte (SyslogFaker und Syslog Parser Job) ist nicht erforderlich. Dadurch entfällt die Installation zusätzlicher SDKs und IDEs.

Die festgelegten [Anmeldedaten](#anmeldedaten) sind am Ende dieser Datei zu finden.

## Infrastruktur

Die Einrichtung der Infrastruktur erfolgt automatisiert über das Powershell-Skript `init.ps1`, das über die Powershell-Kommandozeile gestartet werden muss. Das Skript befindet sich im Root-Verzeichnis.

Dieses Skript erstellt zunächst das Docker-Image für den Syslog-Faker. Dazu wird ein Build-Container erstellt und der Quellcode in diesen Container kopiert. Dort wird er mit `dotnet build (AOT)` kompiliert und die kompilierte Anwendung in das fertige Image übertragen. Das Image befindet sich im lokalen Repository unter `rc-syslog-faker`.

Anschließend werden die benötigten Ordner und Dateien für alle Docker Container unter `DockerFiles` angelegt. In diesem Ordner befinden sich Konfigurationsdateien und Daten für die Persistenz.

Zuletzt startet das Skript die Docker Container mit `docker compose up`. Es kann einige Zeit dauern, bis alle Anwendungen lauffähig sind. Während dieser Zeit können Fehlermeldungen in der Ausgabe erscheinen. Diese können während der Startphase ignoriert werden.

## Syslog Parser Job

Der Syslog Parser wird in Apache Flink ausgeführt und befindet sich als `syslog.jar` Datei im Ordner `Projects/SyslogParserJob/out/artifacts/syslog_jar`. Die Jar-Datei muss in [Apache Flink](http://localhost:8080#/submit) hochgeladen werden.

![Apache Flink Job Submit](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/apache-flink-job-submit.png)

Vor der Ausführung müssen einige Parameter angegeben werden.

### Parameters

Durch die Parametrisierung entfällt die Notwendigkeit, feste Konfigurationen im Quellcode zu hinterlegen und bei Änderungen neu zu kompilieren. Außerdem erleichtert es die Ausführung des Programms in der Entwicklungsumgebung und in Apache Flink, da keine Änderungen am Quellcode erforderlich sind.

#### Apache Flink

Diese Parameter werden für die Ausführung in Apache Flink benötigt und werden nach dem Hochladen des Jobs unter `Program Arguments` angegeben. Als Hostname wird der jeweilige Containername verwendet und muss bei Verwendung des mitgelieferten Docker-Compose nicht angepasst werden.

`--kafka-bootstrap-servers kafka1:29092,kafka2:29092,kafka3:29092 --elasticsearch http:elasticsearch1:9200,http:elasticsearch2:9200,http:elasticsearch3:9200 --elasticsearch-user elastic --elasticsearch-password iu`


#### Debug (IDE)

Die Debug-Parameter werden für die Ausführung in der IDE verwendet. Wo diese genau zu setzen sind, entnehmen Sie bitte dem Handbuch der jeweiligen IDE. Bei der Ausführung ohne Docker kann der Hostname `localhost` verwendet werden.

`--kafka-bootstrap-servers localhost:9092,localhost:9093,localhost:9094 --elasticsearch http:localhost:9200 --elasticsearch-user elastic --elasticsearch-password iu`

## ElasticSearch/Kibana

Beim ersten Aufruf von [Kibana Discover](http://localhost:5601/app/discover#/) muss ein `Index Pattern` angegeben werden.

![Index-Pattern](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/ElasticSearch/Index-Pattern.png)

Die Daten aus dem Syslog Parser werden standardmäßig unter dem Index `syslog-{yyyy-MM-dd}` gespeichert. Daher geben wir als Namen `syslog*` und als Zeitstempel `timestamp` an.

Anschließend können die aufbereiteten Meldungen ausgewertet werden.

![Discover](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/ElasticSearch/Discover.png)

### Automatisches löschen

Die automatische Löschung wird über die [Kibana Ui](http://localhost:5601) konfiguriert. Dort muss zunächst zum [Index Management](http://localhost:5601/app/management/data/index_management/indices) navigiert werden. Anschließend muss der Reiter `Index Templates` ausgewählt werden.

![Index Templates](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/ElasticSearch/Index-Template.png)

Dort wird ein neues Template erstellt und konfiguriert. Der Name kann frei gewählt werden. Wie im vorherigen Kapitel erwähnt, werden die Templates standardmäßig unter dem Index `syslog-{yyyy-MM-dd}` gespeichert, daher muss im Feld "Index Pattern" der String `syslog*` eingetragen werden. Danach kann das Template gespeichert werden.

![Index-Vorlage erstellen](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/ElasticSearch/Create-Index-Template.png)

Im Menüpunkt `Index Lifecycle Policies` können Richtlinien verwaltet werden.

![Index-Lebenszyklus-Richtlinien](https://github.com/Rafael94/iu-data-engineering/blob/main/Images/ElasticSearch/Index-Lifecycle-Policies.png?raw=true)

Zum Beispiel können alte Daten nach 7 Tagen gelöscht werden. Dazu kann die Policy `7-days-default` verwendet werden. Auf der rechten Seite des Datensatzes befindet sich ein `Plus`. Nach dem Anklicken erscheint ein Dialog. Hier kann das zuvor erstellte `Index Template` hinterlegt werden.

# Anmeldedaten

Das Projekt enthält fest codierte Login-Daten und Passwörter, die ausschließlich Demonstrationszwecken dienen und die Reproduzierbarkeit erleichtern sollen. Es wird ausdrücklich darauf hingewiesen, dass diese Daten nicht in einer produktiven Umgebung verwendet werden dürfen.
## Apache Flink

Url: [Apache Flink Ui](http://localhost:8080)

Username: `iu`

Password: `iu`

## Kibana

Url: [Kibana Ui](http://localhost:5601)

Username: `elastic`

Password: `iu`

# Verbesserungen

## Syslog Parser Job

Ich bin mit .NET und C# vertraut und habe keine Erfahrung mit Java/Kotlin. Es ist wichtig anzumerken, dass dieser Job lediglich zur Demonstration von Streaming-Processing dient.

### Syslog Format

Zur Zeit wird nur Syslog im Format [RFC 5424](https://datatracker.ietf.org/doc/html/rfc5424) unterstützt. Es gibt noch das ältere Format [RFC 3164](https://datatracker.ietf.org/doc/html/rfc3164). Dieses wird vom Syslog Parser nicht unterstützt.
### Auslesen der zusätzlichen Daten

Die Anwendung unterstützt nur Key-Value-Daten (`key="value"`). Die Daten können aber auch im Format `[key Value]` oder als JSON vorliegen. Diese Daten werden zur Zeit ignoriert.

### Parsen optimieren

Die Syslog-Meldungen werden mit zwei Regex-Ausdrücken gelesen. Es ist zu prüfen, ob ein manuelles Iterieren über den String die Auswertung beschleunigt.

### Daten minimieren

Die verarbeitete Syslog-Meldung enthält den ursprünglichen String. Nach dem Parsen könnte dieser ignoriert werden oder alternativ könnten nur die nicht verarbeiteten Teile des Strings gespeichert werden. Bei der derzeitigen Implementierung wird etwa doppelt so viel Speicher pro Nachricht benötigt. In diesem Beispiel wurde der String absichtlich gespeichert, um die Verarbeitung zu demonstrieren.