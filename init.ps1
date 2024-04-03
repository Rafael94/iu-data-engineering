
# Das Arbeitsverzeichnis muss das gleichen Verzeichnis wie dieses Skript sein
cd $PSScriptRoot

# Syslog Faker erstellen
Write-Host "Docker Image vom Syslog Faker wird erstellt"
cd Projects/SyslogFaker
#docker build -t rc-syslog-faker .

# Zurück zum Root Verzeichnis
cd ../..


# Syslog Faker
if((Test-Path -Path "DockerFiles/SyslogFaker") -eq $false) {
    Write-Host "Syslog Fake Ordner wird erstellt"

    New-Item -Path "DockerFiles/" -Name "SyslogFaker" -ItemType Directory
}

if((Test-Path -Path "DockerFiles/SyslogFaker/appsettings.json") -eq $false) {
    Write-Host "Syslog Fake appsettings.json wird erstellt"

    New-Item -Path "DockerFiles/SyslogFaker/" -Name "appsettings.json" -ItemType File -Value '{
  "Kafka": {
    "BootstrapServers": "kafka1:29092,kafka2:29092,kafka3:29092",
    "WaitingTimeBetweenMessages": 10,
    "Topic": "syslog",
    "ClientId": "syslog-faker"
  }
}'
}

# Nginx
if((Test-Path  -Path "DockerFiles/Nginx") -eq $false) {
    Write-Host "Nginx Ordner wird erstellt"
    New-Item "DockerFiles/Nginx" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/Nginx/htpasswd") -eq $false) {
    Write-Host "htpasswd wird erstellt"
    New-Item "DockerFiles/Nginx/htpasswd" -ItemType File -Value 'iu:$apr1$uz9gtwo1$k7dAckK.5QpswZ1VtNykH1'
}

# Kafka

if((Test-Path  -Path "DockerFiles/Kafka/Kafka1") -eq $false) {
    Write-Host "Kafka1 Ordner wird erstellt"
    New-Item "DockerFiles/Kafka/Kafka1" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/Kafka/Kafka2") -eq $false) {
    Write-Host "Kafka2 Ordner wird erstellt"
    New-Item "DockerFiles/Kafka/Kafka2" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/Kafka/Kafka3") -eq $false) {
    Write-Host "Kafka3 Ordner wird erstellt"
    New-Item "DockerFiles/Kafka/Kafka3" -ItemType Directory
}


# Flink
if((Test-Path  -Path "DockerFiles/Nginx/default.conf") -eq $false) {
    Write-Host "default.con wird erstellt"
    New-Item "DockerFiles/Nginx/default.conf" -ItemType File -Value 'server {
    listen       8080;
    listen  [::]:8080;
    server_name  reverse-proxy;
    client_max_body_size 100M;

    #access_log  /var/log/nginx/host.access.log  main;

    location / {
		auth_basic           "Administrato’’s Area";
		auth_basic_user_file /etc/nginx/conf.d/.htpasswd;
		proxy_pass http://jobmanager:8081;
    }

    error_page   500 502 503 504  /50x.html;
    location = /50x.html {
        root   /usr/share/nginx/html;
    }
}'
}



if((Test-Path  -Path "DockerFiles/Flink/upload" ) -eq $false) {
    Write-Host "Flink upload Ordner wird erstellt"
    New-Item "DockerFiles/Flink/Upload" -ItemType Directory 
}

# ElasticSearch

if((Test-Path  -Path "DockerFiles/ElasticSearch/ESData1") -eq $false) {
    Write-Host "ElasticSearch1 Ordner wird erstellt"
    New-Item "DockerFiles/ElasticSearch/ESData1" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/ElasticSearch/ESData2") -eq $false) {
    Write-Host "ElasticSearch2 Ordner wird erstellt"
    New-Item "DockerFiles/ElasticSearch/ESData2" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/ElasticSearch/ESData3") -eq $false) {
    Write-Host "ElasticSearch3 Ordner wird erstellt"
    New-Item "DockerFiles/ElasticSearch/ESData3" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/ElasticSearch/Certs/") -eq $false) {
    Write-Host "ElasticSearch3 Ordner wird erstellt"
    New-Item "DockerFiles/ElasticSearch/Certs" -ItemType Directory
}

if((Test-Path  -Path "DockerFiles/ElasticSearch/Certs/elastic-certificates.pfx") -eq $false) {
    Write-Host "SSL Zertifikat wird erstellt"
    
    $selfSignedRootCA = New-SelfSignedCertificate -CertStoreLocation 'Cert:\CurrentUser\My' -DnsName elasticsearch -notafter (Get-Date).AddYears(10) -KeyExportPolicy Exportable -KeyUsage CertSign,CRLSign,DigitalSignature -KeySpec KeyExchange -KeyLength 2048 -KeyUsageProperty All -KeyAlgorithm 'RSA' -HashAlgorithm 'SHA256' -Provider 'Microsoft Enhanced RSA and AES Cryptographic Provider'

    $certificatepwd = ConvertTo-SecureString -String 'iu' -Force -AsPlainText
    Export-PfxCertificate -Cert $selfSignedRootCA -FilePath "DockerFiles/ElasticSearch/Certs/elastic-certificates.pfx" -Password $certificatepwd
    $selfSignedRootCA | Remove-Item  
}



Write-Host "Docker Compose wird gestartet"
docker compose up -d
