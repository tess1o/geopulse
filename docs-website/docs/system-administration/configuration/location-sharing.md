# Sharing

GeoPulse supports sharing of your data with other users. In some cases you might want to create a separate nginx
instance with more control (e.g. on nginx level disable some endpoints, add additional logging, etc)

| Environment Variable      | Default   | Description                                                                                                                                                               |
|---------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GEOPULSE_SHARE_BASE_URL` | _(empty)_ | Overrides the base URL for share links. If not set, the current base URL is used. <br/> Useful for custom proxies or when you want to have a separate domain for sharing. |
