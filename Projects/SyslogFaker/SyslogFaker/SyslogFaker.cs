using Confluent.Kafka;

using Microsoft.Extensions.Logging;

namespace SyslogFaker;

/// <summary>
/// Erzeugt Fake Syslog Einträge und sendet diese zu Kafka
/// </summary>
public sealed partial class SyslogFaker : IDisposable
{
    private readonly CancellationToken _cancellationToken;
    private readonly KafkaSettings _kafkaSettings;

    private Thread? _thread;
    private readonly IProducer<Null, string> _producer;
    private readonly ILogger _logger;
    private bool disposedValue;

    private readonly Random _random = new();


    public SyslogFaker(KafkaSettings kafkaSettings, ILogger logger, CancellationToken cancellationToken)
    {
        _cancellationToken = cancellationToken;
        _kafkaSettings = kafkaSettings;
        _logger = logger;

        LogCreateProducerConfig(logger);

        ProducerConfig config = new()
        {
            Acks = Acks.Leader,
            BootstrapServers = kafkaSettings.BootstrapServers,
            LingerMs = 100,
            CompressionType = CompressionType.Snappy,
            ClientId = kafkaSettings.ClientId,
            SecurityProtocol = SecurityProtocol.Plaintext,
            AllowAutoCreateTopics = true,
        };

        LogCreateProducer(logger);

        _producer = new ProducerBuilder<Null, string>(config)
            .SetLogHandler(LogMassge)
            .Build();
    }

    /// <summary>
    /// Startet einen Thread und führt diesen im Background aus
    /// </summary>
    public void Start()
    {
        _thread = new Thread(SendSyslogs)
        {
            Priority = ThreadPriority.Highest,
        };

        _thread.Start();
    }

    /// <summary>
    /// Sendet Syslogs in einer dauer Schleife
    /// </summary>
    private async void SendSyslogs()
    {

        while (!_cancellationToken.IsCancellationRequested)
        {
            try
            {
                // Zufällige Nachricht erzeugen
                string syslogMessage = GenerateRandomSyslog();

                _producer.Produce(_kafkaSettings.Topic, new Message<Null, string> { Value = syslogMessage }, Handler);

                if (_kafkaSettings.WaitingTimeBetweenMessages.HasValue)
                {
                    await Task.Delay(_kafkaSettings.WaitingTimeBetweenMessages.Value);
                }
            }
            catch (ProduceException<Null, string> ex)
            {
                // Manchmal ist die Warteschlange zu voll. Weshalb das senden manuell vorgenommen werden muss
                if (ex.Message == "Local: Queue full")
                {
                    _logger.LogWarning(ex, "Fehler beim senden. Es wird versucht die Einträge erneut zu senden");
                    _ = _producer.Flush(TimeSpan.FromSeconds(20));
                }
            }
            catch (Exception)
            {
                throw;
            }
        }
    }

    /// <summary>
    /// Wird aufgerufen nach dem Senden einer Nachricht an Kafka
    /// </summary>
    /// <param name="report"></param>
    private void Handler(DeliveryReport<Null, string> report)
    {
        if (report.Error.IsError)
        {
            LogError(_logger, report.Error.ToString());
        }
    }

    /// <summary>
    /// Kafka Nachrichten Loggen
    /// </summary>
    /// <param name="producer"></param>
    /// <param name="message"></param>
    private void LogMassge(IProducer<Null, string> producer, LogMessage message)
    {
        if (message.Level == SyslogLevel.Error)
        {
            LogError(_logger, message.Message);
        }
    }

    /// <summary>
    /// Generiert Syslog Nachrichten mit zufälligen Daten
    /// </summary>
    /// <returns></returns>
    private string GenerateRandomSyslog()
    {
        string sourceIp = GenerateIp();
        int pri = (_random.Next(23) << 3) | _random.Next(7);
        string srcIp = GenerateIp();
        string dstIp = GenerateIp();
        int srcPort = _random.Next(1024, 65535);
        int dstPort = _random.Next(1, 1024);
        string action = _random.Next(0, 1) == 1 ? "Allowed" : "Denied";

        return $"{sourceIp}|<{pri}>1 {DateTimeOffset.UtcNow:yyyy-MM-ddTHH:mm:ssZ} Server-001 SyslogFaker - - - eventtime={DateTimeOffset.Now.ToUnixTimeMilliseconds()} logid=\"0000000020\" type=\"traffic\" subtype=\"forward\" level=\"notice\" srcip={srcIp} srcport={srcPort} dstIp={dstIp} dstPort={dstPort} action=\"{action}\"";
    }

    private string GenerateIp()
    {
        return $"192.168.2.{_random.Next(2, 255)}";
    }

    private void Dispose(bool disposing)
    {
        if (!disposedValue)
        {
            if (disposing)
            {
                _producer.Dispose();
            }

            disposedValue = true;
        }
    }

    public void Dispose()
    {
        Dispose(disposing: true);
        GC.SuppressFinalize(this);
    }

    [LoggerMessage(LogLevel.Information, "ProducerConfig wird erstellt")]
    private static partial void LogCreateProducerConfig(ILogger logger);

    [LoggerMessage(LogLevel.Information, "Producer wird erstellt")]
    private static partial void LogCreateProducer(ILogger logger);

    [LoggerMessage(LogLevel.Error, "Syslog Faker Nachrichtenversand. {message}")]
    private static partial void LogError(ILogger logger, string message);
}
