# Immich Integration

GeoPulse supports integration with your **Immich** server, allowing you to see your personal photos directly on the *
*Timeline** map, aligned with your GPS history.

---

## 🔧 Configuration

You can set up Immich integration from your **Profile → Immich** tab (`/app/profile`).

![Immich Settings Page](/img/immich.png)

**Available settings:**

- **Enable Immich Integration** — turn the feature on or off.
- **Immich Server URL** — the full URL of your Immich instance (must be accessible from your GeoPulse server).
- **API Key** — the API key you generated in Immich for authentication. The API key should have at least these 3
  permissions: **asset.read**, **asset.download**, **asset.view**

Once you provide the **Immich URL** and **API Key**, GeoPulse will automatically test the connection and display the
status.

---

## 🗺️ Using Immich on the Timeline

When Immich integration is enabled and properly configured:

- A new **camera icon button** appears on the **Timeline** page.
- By enabling this button, GeoPulse fetches available images from Immich that match the current **Timeline timeframe**.
- The fetched photos are shown as icons on the map.

You can click any photo icon to:

- View the associated image directly within GeoPulse.
- Optionally download the **original photo** from your Immich server.

---

## 💾 Notes

- GeoPulse only requests image metadata and thumbnails within the selected timeline range to optimize performance.
- Original photos remain hosted on your Immich server — GeoPulse does not store or copy them.