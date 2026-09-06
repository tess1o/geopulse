---
id: mcp
title: MCP Server
description: Connect an MCP client to GeoPulse to query the authenticated user's timeline and shared friend data.
---

# GeoPulse MCP Server

GeoPulse exposes a Streamable HTTP [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) server at `/mcp`.
It gives an AI client read-only tools for the authenticated user's timeline, stays, trips, route statistics, and eligible
friend timeline or live-location data.

## Before You Start

1. Create an API token in **Profile -> Security -> API Tokens**. The MCP server authenticates the token's owner.
2. Use the public GeoPulse URL followed by `/mcp`.
3. Send the token as the custom `Authorization` header on every MCP request:

   ```text
   Authorization: Bearer <your-api-token>
   ```

For example, a public GeoPulse deployment can expose:

```text
https://geopulse.example.com/mcp
```

Treat the API token as a password. Use a dedicated, revocable token for each MCP client, and do not commit it to a
configuration file or repository. See [API Tokens](/docs/api/api-tokens) for creation and rotation.

## Available Tools

The server can:

- Get the GeoPulse server's current date for resolving relative dates.
- Query detailed timelines, stays, trips, and route or stay statistics for a date range.
- List timeline and live-location sharing relationships, then query data a friend has shared with the token owner.

It cannot modify timeline data, locations, or sharing settings. Friend data remains subject to the existing sharing
permissions.

## Claude Desktop

Add a server to Claude's MCP configuration, replacing the URL and token. `npx` runs `mcp-remote` without a separate
installation:

```json
{
  "mcpServers": {
    "geopulse-local": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote@latest",
        "https://geopulse.example.com/mcp",
        "--header",
        "Authorization:${MCP_TOKEN}"
      ],
      "env": {
        "MCP_TOKEN": "Bearer <your-api-token>"
      }
    }
  }
}
```

Restart Claude Desktop after saving the configuration. The header is required: a request without it receives `401
Unauthorized`.

## ChatGPT Through an OpenAI Tunnel

### 1. Create the OpenAI Resources

1. Create a `CONTROL_PLANE_API_KEY` at [OpenAI API keys](https://platform.openai.com/api-keys).
2. Create a tunnel at [OpenAI Tunnels](https://platform.openai.com/settings/organization/tunnels) and note its tunnel ID.
3. In [ChatGPT Security settings](https://chatgpt.com/#settings/Security), scroll down and enable **Developer mode**.

### 2. Run the Tunnel Client with Docker

This is the recommended setup. The tunnel client sends `MCP_EXTRA_HEADERS` on MCP requests and
`MCP_DISCOVERY_EXTRA_HEADERS` on discovery requests. Both are required because GeoPulse authenticates both paths with
the bearer token.

Set `CONTROL_PLANE_API_KEY`, `CONTROL_PLANE_TUNNEL_ID`, and `GEOPULSE_MCP_TOKEN` in the shell (or load them from a local
`.env` file that is not committed), then run:

```bash
docker run -d \
  --name geopulse-mcp-tunnel \
  --restart unless-stopped \
  -e CONTROL_PLANE_API_KEY \
  -e CONTROL_PLANE_TUNNEL_ID \
  -e MCP_SERVER_URL="https://geopulse.example.com/mcp" \
  -e MCP_EXTRA_HEADERS="Authorization: Bearer ${GEOPULSE_MCP_TOKEN}" \
  -e MCP_DISCOVERY_EXTRA_HEADERS="Authorization: Bearer ${GEOPULSE_MCP_TOKEN}" \
  -e LOG_LEVEL=info \
  -e LOG_FORMAT=json \
  -e HEALTH_LISTEN_ADDR=:8080 \
  -p 127.0.0.1:8080:8080 \
  ghcr.io/openai/tunnel-client:v0.0.14
```

For production, pin a released image version or digest. Check the container logs before connecting ChatGPT to confirm the
tunnel can reach the GeoPulse endpoint.

### 3. Add the MCP Server in ChatGPT

Open [ChatGPT Plugins](https://chatgpt.com/plugins) and select the **+** button in the upper-right corner. Enter:

1. **Name:** `Geopulse MCP`
2. **Description:** optional; for example, `Read your GeoPulse timeline, stays, trips, route statistics, and shared friend locations.`
3. **Connection:** select **Tunnel**.
4. **Tunnel ID:** select the tunnel from the dropdown, then choose **Use tunnel ID instead**.
5. **Authentication:** select **No auth**. GeoPulse authentication is handled by the headers the tunnel client sends.

## Host Installation (Alternative)

If you prefer to run the tunnel client directly on the host, create a profile with the same public URL:

```bash
tunnel-client init \
  --sample sample_mcp_remote_no_auth \
  --profile local-http \
  --tunnel-id tunnel_<your-tunnel-id> \
  --mcp-server-url https://geopulse.example.com/mcp
```

In `~/.config/tunnel-client/local-http.yaml`, add or update the `mcp` section:

```yaml
mcp:
  server_urls:
    - channel: main
      url: "https://geopulse.example.com/mcp"
  extra_headers:
    Authorization: "Bearer <your-api-token>"
  discovery_extra_headers:
    Authorization: "Bearer <your-api-token>"
```

Start it with:

```bash
tunnel-client run --profile local-http
```

Keep the profile readable only by its owner because it contains a credential.

## Troubleshooting

- **401 Unauthorized:** The `Authorization` header is missing, the value does not start with `Bearer `, or the token has
  expired or been revoked.
- **404 or connection refused:** Confirm the URL ends in `/mcp` and is reachable from the tunnel client.
- **No friend data:** The token owner must have access through GeoPulse's existing timeline or live-location sharing
  settings. MCP does not bypass those permissions.
