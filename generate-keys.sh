#!/bin/sh
set -e

echo "🔐 GeoPulse JWT Key Generator"
echo "=============================="

KEYS_DIR="/keys"
PRIVATE_KEY="$KEYS_DIR/jwt-private-key.pem"
PUBLIC_KEY="$KEYS_DIR/jwt-public-key.pem"

# Create keys directory if it doesn't exist
mkdir -p "$KEYS_DIR"

# Check if keys already exist
if [ -f "$PRIVATE_KEY" ] && [ -f "$PUBLIC_KEY" ]; then
    echo "✅ JWT keys already exist, skipping generation"
    echo "   Private key: $PRIVATE_KEY"
    echo "   Public key:  $PUBLIC_KEY"
    exit 0
fi

echo "🔄 Generating new JWT keys..."

# Install OpenSSL if not available
if ! command -v openssl >/dev/null 2>&1; then
    echo "📦 Installing OpenSSL..."
    apk add --no-cache openssl
fi

# Generate private key
echo "🔑 Generating private key..."
openssl genpkey -algorithm RSA -out "$PRIVATE_KEY"

# Generate public key from private key
echo "🔑 Generating public key..."
openssl rsa -pubout -in "$PRIVATE_KEY" -out "$PUBLIC_KEY"

# Set proper permissions
echo "🔒 Setting file permissions..."
chmod 644 "$PRIVATE_KEY" "$PUBLIC_KEY"

# Verify keys were created successfully
if [ -f "$PRIVATE_KEY" ] && [ -f "$PUBLIC_KEY" ]; then
    echo "✅ JWT keys generated successfully!"
    echo "   Private key: $PRIVATE_KEY"
    echo "   Public key:  $PUBLIC_KEY"
    
    # Show key info for verification
    echo ""
    echo "🔍 Key verification:"
    echo "   Private key size: $(openssl rsa -in "$PRIVATE_KEY" -text -noout | grep "Private-Key" | sed 's/.*(\([0-9]*\) bit).*/\1/' || echo 'unknown') bits"
    echo "   Public key size:  $(openssl rsa -pubin -in "$PUBLIC_KEY" -text -noout | grep "Public-Key" | sed 's/.*(\([0-9]*\) bit).*/\1/' || echo 'unknown') bits"
else
    echo "❌ Failed to generate JWT keys!"
    exit 1
fi

# Generate AI encryption key
AI_ENCRYPTION_KEY="$KEYS_DIR/ai-encryption-key.txt"

echo ""
echo "🤖 Generating AI encryption key..."

if [ -f "$AI_ENCRYPTION_KEY" ]; then
    echo "✅ AI encryption key already exists, skipping generation"
    echo "   AI encryption key: $AI_ENCRYPTION_KEY"
else
    echo "🔑 Generating AI encryption key..."
    openssl rand -base64 32 > "$AI_ENCRYPTION_KEY"
    chmod 644 "$AI_ENCRYPTION_KEY"
    echo "✅ AI encryption key generated: $AI_ENCRYPTION_KEY"
fi

echo ""
echo "🎉 Key generation completed successfully!"