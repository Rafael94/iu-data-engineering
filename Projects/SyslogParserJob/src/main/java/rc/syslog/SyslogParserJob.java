package rc.syslog;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestartStrategyOptions;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch7SinkBuilder;
import org.apache.flink.connector.elasticsearch.sink.FlushBackoffType;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.joda.time.LocalDate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Job für das Streaming Progressing
 */
public class SyslogParserJob {
    private static String IndexName = "syslog";

    public static void main(String[] args) throws Exception {

        // Parameter auslesen und überprüfen
        ParameterTool parameters = ParameterTool.fromArgs(args);
        CheckParameters(parameters);

        IndexName = parameters.get("index", IndexName);

        // Apache Flink Konfigurationen setzen
        Configuration config = new Configuration();
        config.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 3); // number of restart attempts
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofSeconds(10)); // delay

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);

        // Apache Kafka Konfigurationen
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(parameters.get("kafka-bootstrap-servers"))
                .setTopics("syslog")
                .setGroupId("rc-syslog")
                // Nur neue Nachrichten verarbeiten für die Testumgebung
                .setStartingOffsets(OffsetsInitializer.timestamp(System.currentTimeMillis()))
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // Transformationen setzen
        SingleOutputStreamOperator<SyslogEntry> syslogMessages = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("syslog-messages")
                .process(new SyslogParser())
                .name("SyslogEntry");

        // ElasticSearch Konfigurationen
        String[] elasticSearchServers = parameters.get("elasticsearch").split(",");

        List<HttpHost> elasticSearchHosts = new ArrayList<HttpHost>();
        for (String elasticSearchServer : elasticSearchServers) {
            String[] elasticSearchServerParts = elasticSearchServer.split(":");

            if (elasticSearchServerParts.length != 3) {
                throw new Exception("Der Wert '" + elasticSearchServer + "' liegt im falschen Format vor {schema}:{hostname}:{Port}");
            }

            elasticSearchHosts.add(new HttpHost(elasticSearchServerParts[1], Integer.parseInt(elasticSearchServerParts[2]), elasticSearchServerParts[0]));
        }

        syslogMessages.sinkTo(
                        new Elasticsearch7SinkBuilder<SyslogEntry>()
                                .setHosts(elasticSearchHosts.toArray(HttpHost[]::new))
                                .setConnectionUsername(parameters.get("elasticsearch-user"))
                                .setConnectionPassword(parameters.get("elasticsearch-password"))
                                .setEmitter(
                                        (element, context, indexer) ->
                                                indexer.add(createIndexRequest(element)))
                                .setBulkFlushBackoffStrategy(FlushBackoffType.EXPONENTIAL, 5, 1000)
                                .setBulkFlushMaxActions(100)
                                .setBulkFlushInterval(30000)
                                .build())
                .name("ElasticSearch indexieren");

        // Ausführen
        env.execute("Syslog Parser");
    }

    /**
     * Aus SyslogEntry ein IndexRequest erstellen
     *
     * @param element
     * @return
     */
    private static IndexRequest createIndexRequest(SyslogEntry element) {
        HashMap<String, Object> json = new HashMap<>();

        json.put("sourceIp", element.getSourceIp());
        json.put("facility", element.getFacility());
        json.put("severity", element.getSeverity());
        json.put("message", element.getMessage());
        json.put("hostName", element.getHostName());
        json.put("procId", element.getProcId());
        json.put("msgId", element.getMsgId());
        json.put("appName", element.getAppName());
        json.put("properties", element.getProperties());
        json.put("timestamp", element.getTimestamp());

        return Requests.indexRequest()
                // Aktuelles Datum setzen
                .index(String.format(String.format(IndexName + '-' + LocalDate.now().toString("yyyy-MM-dd"))))
                .source(json);
    }

    /**
     * Überprüft die übergebene Parameter
     *
     * @param parameters
     * @throws Exception
     */
    private static void CheckParameters(ParameterTool parameters) throws Exception {
        String parameter = parameters.get("kafka-bootstrap-servers", "");

        if (Objects.equals(parameter, "")) {
            throw new Exception("Parameter --kafka-bootstrap-servers fehlt");
        }

        parameter = parameters.get("elasticsearch", "");

        if (Objects.equals(parameter, "")) {
            throw new Exception("Parameter --elasticsearch fehlt");
        }

        parameter = parameters.get("elasticsearch-password");

        if (Objects.equals(parameter, "")) {
            throw new Exception("Parameter --elasticsearch-password fehlt");
        }

        parameter = parameters.get("elasticsearch-user");

        if (Objects.equals(parameter, "")) {
            throw new Exception("Parameter --elasticsearch-user fehlt");
        }
    }
}

