package rc.syslog;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
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
import java.util.HashMap;

/**
 * Job für das Streaming Progressing
 */
public class SyslogParserJob {
    public static void main(String[] args) throws Exception {
        Configuration config = new Configuration();
        config.set(RestartStrategyOptions.RESTART_STRATEGY, "fixed-delay");
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_ATTEMPTS, 3); // number of restart attempts
        config.set(RestartStrategyOptions.RESTART_STRATEGY_FIXED_DELAY_DELAY, Duration.ofSeconds(10)); // delay

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers("kafka1:29092")
                //.setBootstrapServers("localhost:9092")
                .setTopics("syslog")
                .setGroupId("rc-syslog")
                // Nur neue Nachrichten verarbeiten für die Testumgebung
                .setStartingOffsets(OffsetsInitializer.timestamp(System.currentTimeMillis()))
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        SingleOutputStreamOperator<SyslogEntry> syslogMessages = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .name("syslog-messages")
                .process(new SyslogParser())
                .name("SyslogEntry");

        syslogMessages.sinkTo(
                        new Elasticsearch7SinkBuilder<SyslogEntry>()
                                //.setBulkFlushMaxActions(1) // Instructs the sink to emit after every element, otherwise they would be buffered
                                .setHosts(new HttpHost("elasticsearch1", 9200, "http"))
                                //.setHosts(new HttpHost("localhost", 9200, "http"))
                                .setEmitter(
                                        (element, context, indexer) ->
                                                indexer.add(createIndexRequest(element)))
                                .setBulkFlushBackoffStrategy(FlushBackoffType.EXPONENTIAL, 5, 1000)
                                .setBulkFlushMaxActions(100)
                                .setBulkFlushInterval(30000)
                                .build())
                .name("ElasticSearch indexieren");

        env.execute("Fraud Detection");
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
                .index(String.format(String.format("syslog-" + LocalDate.now().toString("yyyy-MM-dd"))))
                .source(json);
    }
}

