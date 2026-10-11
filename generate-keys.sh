#!/bin/sh
set -e

echo "🔐 GeoPulse Key Generator"
echo "========================="

KEYS_DIR="/keys"
ENCRYPTION_KEY="$KEYS_DIR/ai-encryption-key.txt"

# Create keys directory if it doesn't exist
mkdir -p "$KEYS_DIR"

# The installation encryption key protects stored secrets (OIDC client secrets, GPS source and
# integration credentials, AI API keys, backups). Keep it: losing it makes those secrets unreadable.
# Sign-in sessions are stored server-side and need no signing keys.
if [ -f "$ENCRYPTION_KEY" ]; then
    echo "✅ Encryption key already exists, skipping generation"
    echo "   Encryption key: $ENCRYPTION_KEY"
else
    # Install OpenSSL if not available
    if ! command -v openssl >/dev/null 2>&1; then
        echo "📦 Installing OpenSSL..."
        apk add --no-cache openssl
    fi

    echo "🔑 Generating encryption key..."
    openssl rand -base64 32 > "$ENCRYPTION_KEY"
    chmod 644 "$ENCRYPTION_KEY"
    echo "✅ Encryption key generated: $ENCRYPTION_KEY"
fi

echo ""
echo "🎉 Key generation completed successfully!"
