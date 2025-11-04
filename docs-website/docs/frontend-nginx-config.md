# Frontend Nginx Configuration

## Max Upload File Size
To increase max upload file size from default 200MB change the following environment variable: `CLIENT_MAX_BODY_SIZE`.

## OSM Resolver DNS Servers
`OSM_RESOLVER` variable defines which DNS servers Nginx should use to resolve the OpenStreetMap tile subdomains (
a.tile.openstreetmap.org, b.tile.openstreetmap.org, c.tile.openstreetmap.org). Default value `127.0.0.11 8.8.8.8`.
If you want to use your own DNS servers, you can set it to your DNS servers, otherwise default value will be used.
