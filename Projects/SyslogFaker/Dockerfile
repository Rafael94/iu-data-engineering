FROM alpine:3.19.1 AS prepare
RUN apk add --no-cache libc6-compat
WORKDIR /app
RUN adduser -u 1000 --disabled-password --gecos "" appuser && chown -R appuser /app
USER appuser

FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
RUN apt update && apt upgrade -y && apt install -y clang zlib1g-dev
WORKDIR /build
COPY ./SyslogFaker.sln ./
COPY ./src ./src
RUN dotnet publish ./src/SyslogFaker/SyslogFaker.csproj -c Release -r linux-x64 -o /app

FROM prepare AS finalalpine

# Labels.
LABEL maintainer="Rafael Carnucc"
LABEL org.label-schema.schema-version="1.0"
LABEL org.label-schema.name="rc/syslog-faker"
LABEL org.label-schema.description="SyslogFake für die IU Projektarbeit"
LABEL org.label-schema.version="1.0"

COPY --chown=appuser --from=build /app ./
ENTRYPOINT ["./SyslogFaker"]