---
id: intro
title: REST API
description: Use GeoPulse's REST API for integrations, scripts, and automation.
---

GeoPulse exposes its backend REST API for integrations, scripts, reporting jobs, and automation that need to work with
the same data available in the web application.

## API structure

The reference is generated from the backend OpenAPI specification and is grouped by audience:

- **Public API** - endpoints that do not require a logged-in GeoPulse session, such as login, registration, health,
  shared links, and GPS integration ingest endpoints.
- **User API** - endpoints available to authenticated users and user-owned API tokens.
- **Admin API** - endpoints that require an administrator account.

## Authentication options

Browser users authenticate through the normal GeoPulse login flow with secure cookies and JWTs.

External API clients should use a user API token. A token acts as the user that created it and can call the same
authenticated REST endpoints that user can access. See [API Tokens](./api-tokens.md) for creation and usage details.

Send a token with either header:

```http
X-API-Key: <your-api-token>
```

or

```http
Authorization: Bearer <your-api-token>
```

Use `X-API-Key` for scripts and service clients when possible. `Authorization: Bearer` is also supported for tools that
expect bearer-token authentication.

## Base URL

In a local development environment the API is usually available at:

```text
http://localhost:8080
```

For deployed environments, use the public URL of your GeoPulse backend or reverse proxy.

REST endpoints are versioned under `/api/v1`, for example `http://localhost:8080/api/v1/timeline`.

## OpenAPI specification

The API reference in this section is generated from the GeoPulse OpenAPI specification. You can also use the
specification directly to generate clients, import the API into tools such as Postman or Insomnia, or check request and
response schemas:

- [openapi.json](https://github.com/tess1o/geopulse/blob/main/docs/openapi/openapi.json)
- [openapi.yaml](https://github.com/tess1o/geopulse/blob/main/docs/openapi/openapi.yaml)

## Responses

Successful responses are not wrapped in a common envelope. Each endpoint returns its own resource directly: a JSON
object, a JSON array, a paged list, a file download, or an empty `204 No Content` response. Check the endpoint page in
the generated reference or the OpenAPI specification for the exact request and response schema.

Paginated list endpoints return the items together with paging metadata, for example:

```json
{
  "items": [],
  "page": 1,
  "size": 50,
  "totalElements": 1234,
  "totalPages": 25
}
```

GPS integration ingest endpoints (OwnTracks, Overland, Dawarich, and others) respond in the format their client app
expects.

## Errors

All errors use one format: [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457), produced by
[quarkus-http-problem](https://github.com/quarkiverse/quarkus-http-problem) and returned with the
`application/problem+json` content type. GeoPulse adds a few stable fields to the standard ones:

```json
{
  "type": "urn:geopulse:error:EXPORT_NOT_FOUND",
  "title": "Not Found",
  "status": 404,
  "detail": "Export job not found",
  "instance": "/api/v1/exports/0b9f6c1e-1d2a-4a8e-9c55-6a3e1f7d2b10",
  "code": "EXPORT_NOT_FOUND",
  "errorId": "5f1c2a7e-8e0b-4f43-b2a4-7d9f0c3e6a21",
  "requestId": "c7d1e5f2-3a4b-4c6d-8e9f-0a1b2c3d4e5f"
}
```

| Field        | Description                                                                                       |
|--------------|---------------------------------------------------------------------------------------------------|
| `type`       | URI that identifies the error type, always `urn:geopulse:error:<code>`.                           |
| `title`      | Short HTTP status description.                                                                    |
| `status`     | HTTP status code.                                                                                 |
| `detail`     | Human-readable explanation. Do not parse it; it may change.                                       |
| `instance`   | Request path that produced the error.                                                             |
| `code`       | Stable, machine-readable error code. Use this field to handle specific errors in scripts.         |
| `errorId`    | Unique ID of this error occurrence. Also returned in the `X-Error-Id` response header.            |
| `requestId`  | Request correlation ID. Also returned in the `X-Request-Id` response header.                      |
| `parameters` | Optional. Values related to the error, such as limits or the offending field.                     |
| `violations` | Optional. Present on validation errors (`code: VALIDATION_FAILED`), one entry per invalid field. |

A validation error lists every invalid field:

```json
{
  "type": "urn:geopulse:error:VALIDATION_FAILED",
  "title": "Bad Request",
  "status": 400,
  "instance": "/api/v1/registrations",
  "code": "VALIDATION_FAILED",
  "errorId": "9a3e7c51-2b6d-4f0e-8a1c-4d5e6f7a8b9c",
  "requestId": "1e2d3c4b-5a69-4788-9abc-def012345678",
  "violations": [
    {
      "field": "password",
      "in": "body",
      "code": "SIZE",
      "parameters": { "min": 3, "max": 128 },
      "detail": {
        "key": "validation.size",
        "parameters": { "min": 3, "max": 128 },
        "fallback": "Password must be between 3 and 128 characters"
      }
    }
  ]
}
```

When reporting a problem, include the `errorId` or `requestId`; administrators can use them to find the matching entry in
the backend logs.

## Examples

See [API Examples](./examples.md) for `curl` examples that read the timeline, fetch raw GPS points, and import or export
GPX and OwnTracks files.
