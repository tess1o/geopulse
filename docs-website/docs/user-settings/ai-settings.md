---
title: AI Assistant Settings
description: Configure your personal AI Assistant in GeoPulse.
---

# AI Assistant Settings

GeoPulse includes an optional **AI Assistant** that helps you explore your movement data and travel patterns using
natural language.  
Each user can configure their own assistant independently in the **Profile → AI Assistant** page.

---

## Location

Open the settings page by navigating to:

**Menu → Profile → AI Assistant** or go directly to `https://geopulse.mydomain.com/app/profile`

![AI Assistant Settings Page](./img/ai-settings.png)

---

## Enabling or Disabling the AI Assistant

At the top of the page, you’ll find the **Enable AI Assistant** switch.

- When **disabled**, the assistant and all AI-related features are hidden from the interface.
- When **enabled**, you can customize connection settings and test communication with your preferred AI model.

---

## Configuration Options

Once the AI Assistant is enabled, the following settings become available:

### **API Key Required**

- **Type:** Boolean (`true` / `false`)
- **Description:**  
  Controls whether your **OpenAI API Key** must be sent to the language model (LLM) during requests.
    - If set to **true**, the API key will be included in each request to the LLM.
    - If set to **false**, requests are sent without the key.

---

### **OpenAI API Key**

- **Type:** String
- **Description:**  
  The API key used to authenticate with your selected OpenAI-compatible API provider.
    - Required only if **API Key Required** is set to **true**.
    - The key is stored securely and encrypted using the application’s encryption key (`ai-encryption-key`).

---

### **API Base URL**

- **Type:** String
- **Default:** `https://api.openai.com/v1`
- **Description:**  
  The base URL of the OpenAI-compatible API endpoint.  
  You can change this if you use a **self-hosted** or **third-party** LLM (e.g., local model or proxy).

---

### **Model**

- **Type:** String
- **Description:**  
  The name of the model used for AI queries (e.g., `gpt-4o`, `gpt-3.5-turbo`, or any custom model your endpoint
  supports).  
  You can use the **Fetch Models** button next to this input to retrieve available models from the configured API.
  > Note: The API Key and Base URL must be provided before fetching models.

---

### **Test Connection**

- **Button Action:**  
  Sends a test request to the configured LLM using the current settings.  
  This allows you to verify that your API key, base URL, and model are correct before saving.

---

### **Save AI Settings**

- **Action:**  
  Saves the configured AI Assistant settings **for your user only**.  
  All AI settings are encrypted using the `ai-encryption-key` defined in
  the [AI Configuration](../system-configuration/ai-config.md) section.

---

## Security and Storage

- All AI Assistant settings are stored **per user**.
- Sensitive data (like API keys) is encrypted before being saved to the database.
- Only the currently logged-in user can view or modify their AI Assistant configuration.

---

## Example Use Case

Once configured, you can interact with the AI Assistant on the **AI Chat** page — for example:

> “Show me my total travel distance in October.”  
> “How many cities did I visit last month?”  
> “Summarize my longest trip this year.”

The assistant uses your location history and statistics to generate natural-language insights.

---

:::info
Each user can connect to a different LLM provider or model. For system-wide AI configuration (encryption settings),
see [AI Configuration → System Configuration](../system-configuration/ai-config.md).
:::