---
title: AI Assistant
description: Use natural language to explore your location data and travel patterns with GeoPulse's AI Assistant.
---

# AI Assistant

GeoPulse includes an optional **AI Assistant** that helps you explore your movement data and travel patterns using natural language queries. Ask questions about your travels, get insights into your journeys, and discover patterns in your location history—all through a conversational interface.

---

## What Can the AI Assistant Do?

The AI Assistant can analyze your location data and answer questions like:

- **Travel Statistics**: "How many kilometers did I travel last month?"
- **Location Insights**: "Which cities did I visit in 2024?"
- **Journey Analysis**: "What was my longest trip this year?"
- **Time-Based Queries**: "How much time did I spend at work last week?"
- **Pattern Discovery**: "When do I typically visit the gym?"

The assistant has access to your complete location history, timeline events (stays and trips), favorite places, and travel statistics, allowing it to provide personalized insights about your movement patterns.

---

## How It Works

The AI Assistant uses your personal OpenAI-compatible API key to process queries. GeoPulse never stores or has access to your API key—it's encrypted and used only for your requests.

**Key Features:**
- **Privacy-First**: Your API key is encrypted and stored securely
- **Flexible Models**: Works with OpenAI, local models, or any OpenAI-compatible endpoint
- **Personal Configuration**: Each user can use their own API provider and model
- **Context-Aware**: Understands your location data, places, and travel history

---

## Getting Started

To use the AI Assistant, you'll need:

1. **An OpenAI-compatible API key** from one of these providers:
   - OpenAI (GPT-4, GPT-3.5, etc.)
   - Local models (via tools like LM Studio, Ollama with OpenAI-compatible endpoints)
   - Third-party providers (Azure OpenAI, Anthropic proxies, etc.)

2. **Configure your settings** in the GeoPulse interface

### Configuration

Navigate to **Menu → Profile → AI Assistant** (or `/app/profile`) to configure your AI Assistant.

You'll need to provide:
- **API Key**: Your OpenAI or compatible API key
- **API Base URL**: The endpoint for your chosen provider (default: `https://api.openai.com/v1`)
- **Model**: The model to use (e.g., `gpt-4o`, `gpt-3.5-turbo`)

For detailed configuration instructions, see the [AI Assistant Settings](/docs/user-guide/personalization/ai-assistant-settings) page.

---

## Using the AI Assistant

Once configured, access the AI Assistant from the main menu or navigate to the **AI Chat** page.

### Example Queries

**Travel Distance:**
> "Show me my total travel distance in October."

**Location History:**
> "How many cities did I visit last month?"

**Journey Insights:**
> "Summarize my longest trip this year."

**Time Analysis:**
> "How much time did I spend at the office last week?"

**Pattern Recognition:**
> "What days of the week do I usually go to the gym?"

The assistant will analyze your location data and provide natural-language responses based on your actual travel history.

---

## Privacy & Security

- **Encrypted Storage**: API keys are encrypted using the application's encryption key before being stored
- **Per-User Settings**: Each user manages their own AI configuration independently
- **No Data Sharing**: Your location data and queries are sent directly to your chosen API provider—GeoPulse doesn't store or analyze them
- **Optional Feature**: The AI Assistant is completely optional and can be disabled at any time

For system administrators managing encryption keys, see the [AI Configuration](/docs/system-administration/configuration/ai-assistant) documentation.

---

## Supported Providers

The AI Assistant works with any OpenAI-compatible API, including:

### OpenAI
- **Models**: GPT-4, GPT-4o, GPT-3.5-turbo
- **Base URL**: `https://api.openai.com/v1`

### Azure OpenAI
- **Models**: Your deployed models
- **Base URL**: `https://YOUR-RESOURCE.openai.azure.com/openai/deployments/YOUR-DEPLOYMENT`

### Local Models
Run models locally using:
- **LM Studio**: Provides OpenAI-compatible API
- **Ollama**: Use with OpenAI-compatible proxies
- **LocalAI**: Self-hosted OpenAI alternative

### Third-Party Providers
Any service offering OpenAI-compatible endpoints, such as:
- Together AI
- Anyscale
- Replicate (with compatible proxies)

---

## Troubleshooting

### AI Assistant Not Responding

1. **Check API Key**: Ensure your API key is correct and has sufficient credits
2. **Verify Base URL**: Confirm the API endpoint is accessible
3. **Test Connection**: Use the "Test Connection" button in settings
4. **Check Model Name**: Ensure the model name matches what your provider supports

### Incorrect or Incomplete Answers

- **Update Timeline**: Ensure your timeline is up-to-date by checking the Timeline page
- **Sync Location Data**: Verify recent location data has been processed
- **Model Limitations**: Some models perform better than others—try upgrading to GPT-4 or newer models

### Configuration Issues

For detailed troubleshooting and configuration help, see:
- [AI Assistant Settings](/docs/user-guide/personalization/ai-assistant-settings)
- [AI System Configuration](/docs/system-administration/configuration/ai-assistant) (for administrators)

---

## Next Steps

- **Configure your AI Assistant**: [AI Assistant Settings](/docs/user-guide/personalization/ai-assistant-settings)
- **Explore your Timeline**: [Understanding Your Timeline](/docs/user-guide/core-features/timeline)
- **Review Journey Insights**: [Journey Insights](/docs/user-guide/core-features/journey-insights)
