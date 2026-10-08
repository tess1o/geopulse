# Fake Immich (port 2283) and fake Memos (port 5230) for the GeoPulse demo, in one container.
FROM golang:1.25.14-alpine3.24 AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/fake-immich ./cmd/fake-immich \
 && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/fake-memos ./cmd/fake-memos

FROM alpine:3.22.6
RUN apk add --no-cache tzdata && adduser -D -H -u 10001 demo
COPY --from=build /out/ /usr/local/bin/
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
USER demo
EXPOSE 2283 5230
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s \
  CMD wget -q -O /dev/null http://127.0.0.1:2283/healthz && wget -q -O /dev/null http://127.0.0.1:5230/healthz || exit 1
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
