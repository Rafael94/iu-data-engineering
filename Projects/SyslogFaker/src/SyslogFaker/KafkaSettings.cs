namespace SyslogFaker;
public class KafkaSettings
{
    public string BootstrapServers { get; set; } = null!;

    public string ClientId { get; set; } = "syslog-faker";

    public string Topic { get; set; } = "syslog";

    /// <summary>
    /// Die Wartezeit in millesekunden zwischen den einzelnen Nachrichten um ein Thortteling zu erzielen
    /// </summary>
    public int? WaitingTimeBetweenMessages { get; set; }
}
