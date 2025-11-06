# OwnTracks Advanced Configuration

| Property                                     | Default | Description                                                                                                                                                                                                                                   |
|----------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_OWNTRACKS_PING_TIMESTAMP_OVERRIDE` | `false` | When OwnTracks sends a message with `_type=location` and `p=t` (ping) and this property is `true` -  the system will override OwnTracks's message timestamp to the current time.Otherwise, it will use the timestamp received from OwnTracks. |
