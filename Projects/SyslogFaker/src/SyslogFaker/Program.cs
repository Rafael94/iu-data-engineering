// CancellationToken zum sauberen Beenden der Anwendung erstellen
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

using SyslogFaker;

#region Initialisierung
// Cancellation Token erstellen zum beenden des Producers
CancellationTokenSource cts = new();
ManualResetEvent exitEvent = new(initialState: false);
Console.CancelKeyPress += (s, e) =>
{
    cts.Cancel();
    e.Cancel = true;
    _ = exitEvent.Set();
};

// Logging Konfigurieren
using ILoggerFactory factory = LoggerFactory.Create(builder => builder.AddSimpleConsole(options =>
{
    options.IncludeScopes = true;
    options.SingleLine = true;
    options.TimestampFormat = "HH:mm:ss ";
    options.ColorBehavior = Microsoft.Extensions.Logging.Console.LoggerColorBehavior.Enabled;
}));

ILogger logger = factory.CreateLogger("Program");
logger.LogInformation("Syslog Faker wird gestartet");

Console.ForegroundColor = ConsoleColor.Yellow;
Console.WriteLine(@"Zum beenden ""Strg + C"" drücken");
Console.ForegroundColor = ConsoleColor.White;


// Konfigurationsobjekt erstellen
logger.LogInformation("Appsettings werden geladen");

IConfigurationRoot config = new ConfigurationBuilder()
    .AddJsonFile("appsettings.json", false)
    .AddEnvironmentVariables()
    .Build();

// Konfigurationsobjekt erstellen
logger.LogInformation("Appsettings wurden geladen");

#endregion

logger.LogInformation("KafkaSettings werden geprüft");
KafkaSettings kafkaSettings = config.GetSection("Kafka").Get<KafkaSettings>() ?? throw new Exception("Kafka Einstellungen konnten nicht geladen werden.");

if (string.IsNullOrEmpty(kafkaSettings.BootstrapServers))
{
    logger.LogError("Kafka:BootstrapServers in appsettings.json ist erforderlich");
    throw new Exception("Kafka:BootstrapServers in appsettings.json ist erforderlich");
}

logger.LogInformation("Syslog Faker wird erstellt");
SyslogFaker.SyslogFaker syslogFaker = new(kafkaSettings, logger, cts.Token);

logger.LogInformation("Syslog Nachrichten werden gesendet");
syslogFaker.Start();

exitEvent.WaitOne();
syslogFaker.Dispose();