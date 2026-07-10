/**
 * Gisti MCP server — worker entry.
 *
 * @cloudflare/workers-oauth-provider is the OAuth 2.1 server to the MCP client; it
 * routes /mcp to the McpAgent (Streamable HTTP) and everything else to the Google
 * upstream handler. The GistiMCP Durable Object class is re-exported so the runtime
 * can bind it.
 */
import { OAuthProvider } from "@cloudflare/workers-oauth-provider";
import { GistiMCP } from "./mcp";
import googleHandler from "./google-handler";

export { GistiMCP };

export default new OAuthProvider({
  apiRoute: "/mcp",
  apiHandler: GistiMCP.serve("/mcp") as never,
  defaultHandler: googleHandler as never,
  authorizeEndpoint: "/authorize",
  tokenEndpoint: "/token",
  clientRegistrationEndpoint: "/register",
});
