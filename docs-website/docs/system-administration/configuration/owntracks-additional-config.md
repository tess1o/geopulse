# OwnTracks Advanced Configuration

| Property                                     | Default | Description                                                                                                                                                                                                                                   |
|----------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE` | `false` | When OwnTracks sends a message with `_type=location` and `p=t` (ping) and this property is `true` -  the system will override OwnTracks's message timestamp to the current time.Otherwise, it will use the timestamp received from OwnTracks. |

## Kubernetes / Helm Configuration

Configure in `values.yaml`:

```yaml
config:
  owntracks:
    pingTimestampOverride: true
```

Apply with: `helm upgrade geopulse ./helm/geopulse -f custom-values.yaml`

For more details, see the [Helm Configuration Guide](/docs/getting-started/deployment/helm-deployment#owntracks).
