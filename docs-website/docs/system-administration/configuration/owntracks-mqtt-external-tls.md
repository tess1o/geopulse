---
title: External MQTT Broker TLS (OwnTracks)
description: Configure GeoPulse backend to connect to external TLS-secured MQTT brokers for OwnTracks.
---

# External MQTT Broker TLS (OwnTracks)

This page explains how to configure **GeoPulse backend** to connect to an **external MQTT broker over TLS**.

Important scope:
- This affects only the backend connection from GeoPulse to your external broker.
- The bundled GeoPulse Mosquitto deployment remains non-TLS by default (port `1883`) and is unchanged.

## What You Need

At minimum:
- `GEOPULSE_MQTT_ENABLED=true`
- External broker host and port (`GEOPULSE_MQTT_BROKER_HOST`, `GEOPULSE_MQTT_BROKER_PORT`)
- `GEOPULSE_MQTT_TLS_ENABLED=true`

Depending on your broker TLS setup, you may also need truststore/keystore files mounted into `geopulse-backend`.

## Variables

### Core MQTT

| Variable | Required | Default | Notes |
|---|---|---|---|
| `GEOPULSE_MQTT_ENABLED` | Yes | `false` | Must be `true` to enable MQTT integration. |
| `GEOPULSE_MQTT_BROKER_HOST` | Yes | `geopulse-mosquitto` | Set to your external broker host. |
| `GEOPULSE_MQTT_BROKER_PORT` | Yes | `1883` | Use your TLS port (commonly `8883`). |
| `GEOPULSE_MQTT_USERNAME` | Usually | `geopulse_mqtt_admin` | Depends on broker auth policy. |
| `GEOPULSE_MQTT_PASSWORD` | Usually | `geopulse_mqtt_pass_123` | Depends on broker auth policy. |

### TLS

| Variable | Required | Default | Notes |
|---|---|---|---|
| `GEOPULSE_MQTT_TLS_ENABLED` | Yes (for TLS) | `false` | Enables TLS for backend MQTT client (`ssl://`). |
| `GEOPULSE_MQTT_TLS_PROTOCOL` | No | `TLSv1.2` | Typical values: `TLSv1.2`, `TLSv1.3`. |
| `GEOPULSE_MQTT_TLS_TRUSTSTORE_PATH` | Depends | `(empty)` | Required for private CA/self-signed broker certs. Optional for public CA brokers trusted by JVM. |
| `GEOPULSE_MQTT_TLS_TRUSTSTORE_PASSWORD` | If truststore path set | `(empty)` | Password for truststore file. |
| `GEOPULSE_MQTT_TLS_TRUSTSTORE_TYPE` | No | `PKCS12` | Common values: `PKCS12`, `JKS`. |
| `GEOPULSE_MQTT_TLS_KEYSTORE_PATH` | Depends | `(empty)` | Required only when broker enforces mTLS (client certificate auth). |
| `GEOPULSE_MQTT_TLS_KEYSTORE_PASSWORD` | If keystore path set | `(empty)` | Password for client keystore file. |
| `GEOPULSE_MQTT_TLS_KEYSTORE_TYPE` | No | `PKCS12` | Common values: `PKCS12`, `JKS`. |
| `GEOPULSE_MQTT_TLS_INSECURE_SKIP_HOSTNAME_VERIFICATION` | No | `false` | Debugging only. Keep `false` in production. |

## Certificate/Key Requirements

### 1) Broker uses public CA cert, no mTLS

Usually no extra files are needed.

- Set `GEOPULSE_MQTT_TLS_ENABLED=true`
- Set broker host/port
- Leave truststore/keystore paths empty

### 2) Broker uses private CA or self-signed cert, no mTLS

You need:
- A truststore containing the broker CA certificate (or broker cert chain)

Set:
- `GEOPULSE_MQTT_TLS_TRUSTSTORE_PATH`
- `GEOPULSE_MQTT_TLS_TRUSTSTORE_PASSWORD`
- `GEOPULSE_MQTT_TLS_TRUSTSTORE_TYPE`

### 3) Broker enforces mTLS

You need:
- Truststore (CA for broker validation)
- Keystore containing client certificate + private key

Set truststore and keystore variables.

## Docker Compose: Volume Mounts

If you use truststore/keystore paths, mount these files into `geopulse-backend`.

Example (`docker-compose.yml`):

```yaml
services:
  geopulse-backend:
    # ... existing configuration ...
    volumes:
      - ./keys:/app/keys
      - ./import-drop:/data/geopulse-import
      - ./mqtt-certs:/app/mqtt-certs:ro
```

Then reference in `.env`:

```env
GEOPULSE_MQTT_ENABLED=true
GEOPULSE_MQTT_BROKER_HOST=broker.example.com
GEOPULSE_MQTT_BROKER_PORT=8883
GEOPULSE_MQTT_USERNAME=your-mqtt-user
GEOPULSE_MQTT_PASSWORD=your-mqtt-password

GEOPULSE_MQTT_TLS_ENABLED=true
GEOPULSE_MQTT_TLS_PROTOCOL=TLSv1.2

# Private CA / self-signed case
GEOPULSE_MQTT_TLS_TRUSTSTORE_PATH=/app/mqtt-certs/mqtt-truststore.p12
GEOPULSE_MQTT_TLS_TRUSTSTORE_PASSWORD=change-this
GEOPULSE_MQTT_TLS_TRUSTSTORE_TYPE=PKCS12

# mTLS only (leave empty if broker does not require client cert)
GEOPULSE_MQTT_TLS_KEYSTORE_PATH=
GEOPULSE_MQTT_TLS_KEYSTORE_PASSWORD=
GEOPULSE_MQTT_TLS_KEYSTORE_TYPE=PKCS12
```

## Backward Compatibility

Existing configurations continue to work without changes:
- If `GEOPULSE_MQTT_TLS_ENABLED=false` (default), GeoPulse uses plain MQTT (`tcp://`).
- Built-in GeoPulse Mosquitto deployment remains non-TLS by default.

## Troubleshooting

- Backend fails to start MQTT connection after enabling TLS:
  - Verify truststore/keystore files are mounted into `geopulse-backend`.
  - Verify paths point to in-container locations.
  - Verify file passwords and store types (`PKCS12`/`JKS`).
- TLS hostname mismatch:
  - Ensure broker certificate CN/SAN matches `GEOPULSE_MQTT_BROKER_HOST`.
  - Do not disable hostname verification in production.
