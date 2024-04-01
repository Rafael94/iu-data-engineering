# Vorwort

Dieses Projekt ist aufgrund einer Projektarbeit an der IU entstanden.

Ziel dieser Projektarbeit ist es, Syslog-Meldungen aufzubereiten und zentral zu speichern. Syslog-Meldungen können von verschiedenen Anwendungen und Netzwerkkomponenten an einen Syslog-Server gesendet werden. Dieser ist für die Aufbereitung der Meldungen verantwortlich. Eine Syslog-Meldung besteht nur aus einem zusammenhängenden String (RFC5424 oder RFC3164) und muss für die weitere Auswertung so aufbereitet werden, dass gezielt nach Eigenschaften gesucht und aggregiert werden kann.

In dieser Projektarbeit wird ein Syslog-Producer erstellt, der kontinuierlich Syslog-Meldungen sendet. Die Syslog-Nachrichten werden an einen Kafka-Cluster gesendet. Der Kafka Cluster wird als reiner Message Broker verwendet. Zur Verarbeitung der Syslog-Meldungen wird Apache-Flink verwendet. Die dort laufende Java-Applikation holt die Nachrichten von Kafka ab und bereitet sie auf. Dazu wird der String geparst und in seine Bestandteile zerlegt. Zum einen gibt es feste Bestandteile wie Hostname und AppName, zum anderen variable Bestandteile, die von der Applikation abhängen. Anschließend werden die aufbereiteten Nachrichten in einem ElasticSearch-Cluster gespeichert. Dort können die Nachrichten ausgewertet werden. Als Schnittstelle zu ElasticSearch wird Kibana verwendet.

Die Anwendungen werden als Docker ausgeführt und mit Docker-Compose aufgesetzt. Apache Flink bietet keine Authentifizierung für das Interface. Daher wird Nginx als Reverse Proxy vorgeschaltet, der die Authentifizierung übernimmt.

## Kafka Client (C#)

Für die Verwendung von AOT (Sylog Faker) musste der Kafka Client von einen anderen [Branch](https://github.com/latop2604/confluent-kafka-dotnet/tree/feat/aot) heruntergeladen werden. Sobald der Pull-Request akzeptiert ist, kann wieder der offizielle Client verwendet werden.

## Syslog Faker

ToDo: Inhalt
AOT 

## ElasticSearch

ToDo: Wieso Version 7?

## Sonstiges

ToDo: Wieso feste Versionsnummer?

# Einrichtung

Das Projekt wurde unter Windows und Docker-Desktop (WSL 2) getestet. Theoretisch sollte das Projekt auch unter Linux ausführbar sein. Für die Ausführung des Powershell-Skripts wird Powershell-Core benötigt und Docker.

Für die Ausführung muss das ganze Projekt heruntergeladen werden. Eine manuelle Kompilierung der Artefakte (SyslogFaker und Syslog Parser Job) ist nicht notwendig. Somit entfällt die Installation zusätzlicher SDKs und IDEs.

## Infrastruktur

Die Einrichtung der Infrastruktur ist über das Powershell-Skript `init.ps1` automatisiert und dieses muss über die Powershell Kommandozeile gestartet werden. 

Dieses Skript erstellt zuerst das Docker-Image für den Syslog-Faker. Dafür wird ein Build-Container erstellt und der Quellcode wird in diesen Container kopiert. Dort wird es mit `dotnet build (AOT)` kompiliert und die kompilierte Anwendung wird in das endgültige Image transferiert. Das Image ist im lokalen Repository unter `rc-syslog-faker` zu finden.

Im Anschluss werden die benötigten Ordner und Dateien für alle Docker-Container unter `DockerFiles` angelegt. In diesen Ordner befindet sich entweder Konfigurationsdateien und Daten für eine Persistenz.

Zuletzt startet das Skript über `docker compose up` die Docker Container. Es kann einen Moment dauern, bis alle Anwendung funktionstüchtig sind. Währenddessen können Fehlermeldungen in den Ausgaben angezeigt werden. Diese können während der Startphase ignoriert werden.

## Syslog Parser Job

Der Syslog Parser wird in Apache Flink ausgeführt und liegt als `syslog.jar` Datei im Ordner `Projects\SyslogParserJob\out\artifacts`. Die Jar Datei muss in [Apache Flink](http://localhost:8080) hochgeladen werden.
Vor der Ausführung müssen einige Parameter angegeben werden.

### Parameters

Mit der Parametrisierung entfällt das hinerlegen von festen Konfiguration im Quellcode und eine neu Kompilierung bei einer Änderung entfällt. Zudem erleichtert es die Ausführung des Programms in der Entwicklungsumgebung und in Apache Flink, weil keine Quellcodeänderungen notwendig sind.

#### Debug

Die Debug-Parameter werden für die Ausführung in der IDE verwendet. Wo diese genau zu setzen sind, ist der Anleitung des jeweiligen IDEs zu entnehmen. Bei einer Ausführung ohne Docker kann der Hostname `localhost` verwendet werden.

`--kafka-bootstrap-servers localhost:9092,localhost:9093,localhost:9094 --elasticsearch http:localhost:9200 --elasticsearch-user elastic --elasticsearch-password iu`

#### Apache Flink

Diese Parameter werden bei der Ausführung in Apache Flink benötigt. Als Hostname wird der jeweilige Container Name verwender.

`--kafka-bootstrap-servers kafka1:29092,kafka2:29092,kafka3:29092 --elasticsearch http:elasticsearch1:9200,http:elasticsearch2:9200,http:elasticsearch3:9200 --elasticsearch-user elastic --elasticsearch-password iu`

# Anmeldedaten

Das Projekt enthält fest codierte Anmeldedaten und Passwörter, die ausschließlich zu Demonstrationszwecken dienen und die Reproduzierbarkeit erleichtern sollen. Es ist ausdrücklich darauf hinzuweisen, dass diese Daten nicht in einer produktiven Umgebung verwendet werden dürfen.

## Apache Flink

Url: [Apache Flink Ui](http://localhost:8080)

Username: `iu`

Password: `iu`

## Kibana

Url: [Apache Flink Ui](http://localhost:5601)

Username: `elastic`

Password: `iu`

