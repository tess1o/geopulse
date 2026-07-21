---
title: Authelia OIDC Setup
description: Configure Authelia as an OpenID Connect provider for GeoPulse.
---

# Authelia OIDC Setup

This guide configures [Authelia](https://www.authelia.com/) as an OpenID Connect provider for GeoPulse.

It uses these example values:

| Setting | Example value |
|---------|---------------|
| GeoPulse URL | `https://geopulse.example.com` |
| Authelia URL | `https://auth.example.com` |
| Client ID | `geopulse` |
| GeoPulse callback URL | `https://geopulse.example.com/oidc/callback` |

Replace these values with your own domains before applying the configuration.

## Prerequisites

- GeoPulse is reachable from the browser at its final public HTTPS URL.
- Authelia is reachable at its final public HTTPS URL.
- Authelia's OpenID Connect provider is enabled and configured.
- GeoPulse can reach Authelia's discovery endpoint from the backend container:
  `https://auth.example.com/.well-known/openid-configuration`.

If you already have Authelia OIDC configured for other apps, do not replace your existing `hmac_secret` or `jwks`
unless you intend to rotate them. Add the GeoPulse client and claims policy to the existing `identity_providers.oidc`
section instead.

## 1. Generate Authelia Secrets

Run these commands on the machine where you manage Authelia. The Docker examples use the official Authelia image so
they also work before Authelia is running. If Authelia is installed directly on the host, remove the
`docker run --rm authelia/authelia:latest` prefix.

If you already have a Docker Compose service named `authelia`, you can run the same Authelia commands with
`docker compose exec authelia authelia ...` instead.

### Generate the HMAC Secret

Authelia uses `hmac_secret` to sign some OIDC values:

```bash
docker run --rm authelia/authelia:latest authelia crypto rand --length 64 --charset rfc3986
```

Copy the output into `identity_providers.oidc.hmac_secret`.

### Generate the Client Secret Hash

GeoPulse must store the plaintext client secret. Authelia should store the generated hash.

```bash
docker run --rm authelia/authelia:latest authelia crypto hash generate pbkdf2 \
  --variant sha512 \
  --random \
  --random.length 72 \
  --random.charset rfc3986
```

The command prints a random plaintext password and a PBKDF2 hash:

- Copy the plaintext password into GeoPulse as the client secret.
- Copy the `$pbkdf2-sha512$...` hash into Authelia as `client_secret`.

:::warning
Do not put the hashed value in GeoPulse. GeoPulse sends the plaintext secret to Authelia during the token exchange.
:::

### Generate the JWKS Private Key

Authelia needs at least one signing key for OIDC tokens. This command writes `private.pem` and `public.pem` to a local
folder:

```bash
mkdir -p authelia-oidc-keys
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -v "$PWD/authelia-oidc-keys:/keys" \
  authelia/authelia:latest authelia crypto pair rsa generate --directory /keys
```

Open `authelia-oidc-keys/private.pem` and copy the full private key into the Authelia `jwks[].key` field. Use the
private key, not `public.pem`.

```bash
cat authelia-oidc-keys/private.pem
```

## 2. Configure Authelia

Add or merge this into `configuration.yml`. Keep the indentation under your existing `identity_providers.oidc` section.

```yaml
identity_providers:
  oidc:
    hmac_secret: "paste-the-generated-hmac-secret-here"

    jwks:
      - key_id: geopulse
        algorithm: RS256
        use: sig
        key: |
          -----BEGIN PRIVATE KEY-----
          paste the full generated private.pem content here
          -----END PRIVATE KEY-----

    claims_policies:
      geopulse:
        id_token:
          - email
          - email_verified
          - name
          - preferred_username

    clients:
      - client_id: geopulse
        client_name: GeoPulse
        client_secret: "$pbkdf2-sha512$310000$replace-with-generated-hash"
        public: false
        authorization_policy: one_factor

        redirect_uris:
          - https://geopulse.example.com/oidc/callback

        scopes:
          - openid
          - profile
          - email

        grant_types:
          - authorization_code
        response_types:
          - code
        response_modes:
          - query

        token_endpoint_auth_method: client_secret_post
        claims_policy: geopulse
        require_pkce: false
```

Replace these values in the example:

| Value | Replace with |
|-------|--------------|
| `paste-the-generated-hmac-secret-here` | The `hmac_secret` value generated in step 1 |
| The sample `jwks[].key` private key block | The full contents of `authelia-oidc-keys/private.pem`, including the `BEGIN PRIVATE KEY` and `END PRIVATE KEY` lines |
| `$pbkdf2-sha512$310000$replace-with-generated-hash` | The PBKDF2 hash printed by the client secret command |
| `https://geopulse.example.com/oidc/callback` | Your exact GeoPulse callback URL |
| `client_id: geopulse` | Your chosen client ID, if you do not use the example client ID. The GeoPulse Client ID must match. |
| `one_factor` | Your preferred Authelia authorization policy, for example `two_factor` to require MFA |

Use `two_factor` instead of `one_factor` if you want Authelia to require MFA for GeoPulse logins.

GeoPulse requires the `email` claim in the ID token. The `claims_policy` entry is important because scopes alone may not
include all claims GeoPulse expects in the ID token.

`require_pkce` must be `false` because GeoPulse does not send a PKCE `code_challenge` or `code_verifier` in the OIDC
authorization code flow.

Validate the Authelia configuration before restarting:

```bash
docker run --rm \
  -v "$PWD/authelia:/config" \
  authelia/authelia:latest authelia config validate --config /config/configuration.yml
```

For an existing Docker Compose deployment, you can usually run the same validation inside the Authelia service:

```bash
docker compose exec authelia authelia config validate --config /config/configuration.yml
```

Restart Authelia after validation succeeds.

## 3. Configure GeoPulse

Configure GeoPulse with the Authelia discovery URL and the plaintext client secret generated in step 1.

### Environment Variables

```bash
GEOPULSE_OIDC_ENABLED=true
GEOPULSE_OIDC_CALLBACK_BASE_URL=https://geopulse.example.com

GEOPULSE_OIDC_PROVIDER_AUTHELIA_ENABLED=true
GEOPULSE_OIDC_PROVIDER_AUTHELIA_NAME=Authelia
GEOPULSE_OIDC_PROVIDER_AUTHELIA_CLIENT_ID=geopulse
GEOPULSE_OIDC_PROVIDER_AUTHELIA_CLIENT_SECRET=the-generated-plaintext-secret
GEOPULSE_OIDC_PROVIDER_AUTHELIA_DISCOVERY_URL=https://auth.example.com/.well-known/openid-configuration
GEOPULSE_OIDC_PROVIDER_AUTHELIA_ICON=https://cdn.jsdelivr.net/gh/selfhst/icons@main/svg/authelia-light.svg
```

Restart GeoPulse after changing environment variables.

### Admin UI

You can also configure Authelia in the GeoPulse Admin UI:

1. Go to `Admin Dashboard > OIDC Providers`.
2. Add a provider named `authelia`.
3. Set the display name to `Authelia`.
4. Set the client ID to `geopulse`.
5. Set the client secret to the generated plaintext secret.
6. Set the discovery URL to `https://auth.example.com/.well-known/openid-configuration`.
7. Optionally set the icon URL to `https://cdn.jsdelivr.net/gh/selfhst/icons@main/svg/authelia-light.svg`.
8. Use `Test Connection`, save the provider, and try logging in.

## Troubleshooting

### Authelia returns `invalid_client`

Make sure GeoPulse uses the plaintext client secret and Authelia uses the generated hash. Also confirm the Authelia
client uses:

```yaml
token_endpoint_auth_method: client_secret_post
```

If the secret contains characters that are being modified by your shell, environment file, or deployment tooling,
regenerate it with a stricter charset:

```bash
docker run --rm authelia/authelia:latest authelia crypto hash generate pbkdf2 \
  --variant sha512 \
  --random \
  --random.length 72 \
  --random.charset alphanumeric
```

### GeoPulse reports a callback error

Check that the redirect URI in Authelia exactly matches the callback URL generated by GeoPulse:

```text
https://geopulse.example.com/oidc/callback
```

For stable callbacks, set `GEOPULSE_OIDC_CALLBACK_BASE_URL` explicitly to the public GeoPulse URL without a trailing
slash.

### GeoPulse reports a missing email claim

Confirm the Authelia client has `claims_policy: geopulse` and that the matching `claims_policies.geopulse.id_token`
list includes `email`.

### The Authelia icon does not appear

The icon URL is optional:

```text
https://cdn.jsdelivr.net/gh/selfhst/icons@main/svg/authelia-light.svg
```

If the browser cannot load the external SVG, GeoPulse falls back to the default sign-in icon.

## Related Documentation

- [OIDC Authentication](./oidc-sso)
- [Authelia Immich OIDC example](https://www.authelia.com/integration/openid-connect/clients/immich/)
- [Authelia OIDC client secrets FAQ](https://www.authelia.com/integration/openid-connect/frequently-asked-questions/#how-do-i-generate-a-client-identifier-or-client-secret)
- [Authelia OIDC provider configuration](https://www.authelia.com/configuration/identity-providers/openid-connect/provider/)
