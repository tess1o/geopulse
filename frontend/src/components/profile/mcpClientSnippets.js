/**
 * Ready-to-paste MCP client configurations for the GeoPulse server, matching docs-website/docs/api/mcp.md.
 * Without a token the snippets carry a placeholder the user replaces.
 */
export const MCP_TOKEN_PLACEHOLDER = 'YOUR_API_TOKEN'

export function buildMcpClientSnippet(client, serverUrl, token = '') {
  const secret = token || MCP_TOKEN_PLACEHOLDER

  if (client === 'claudeDesktop') {
    // mcp-remote bridges Claude Desktop's stdio transport to the Streamable HTTP endpoint
    return JSON.stringify({
      mcpServers: {
        geopulse: {
          command: 'npx',
          args: ['-y', 'mcp-remote@latest', serverUrl, '--header', 'Authorization:${MCP_TOKEN}'],
          env: { MCP_TOKEN: `Bearer ${secret}` }
        }
      }
    }, null, 2)
  }

  if (client === 'claudeCode') {
    return `claude mcp add --transport http geopulse ${serverUrl} --header "Authorization: Bearer ${secret}"`
  }

  return `URL:    ${serverUrl}\nHeader: Authorization: Bearer ${secret}`
}
