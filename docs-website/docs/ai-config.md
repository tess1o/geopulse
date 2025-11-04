# AI Configuration

GeoPulse includes an AI chat assistant that can analyze your location data and provide intelligent insights.

## AI Encryption Key

The AI encryption key is automatically generated during the initial setup process and stored securely in the keys
directory. For advanced users who need to customize the key location:

```bash
# Optional: Custom AI encryption key location (advanced users only)
GEOPULSE_AI_ENCRYPTION_KEY_LOCATION=file:/app/keys/ai-encryption-key.txt
```