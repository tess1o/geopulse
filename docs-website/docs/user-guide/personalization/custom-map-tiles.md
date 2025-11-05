# Custom Map Tiles

GeoPulse allows you to customize the map appearance by using tiles from different providers. By default, GeoPulse uses
OpenStreetMap tiles, but you can switch to satellite imagery, different map styles, or any custom tile provider that
supports the standard XYZ tile format.

## Why Use Custom Map Tiles?

- **Satellite imagery** - See actual satellite photos of your locations
- **Different map styles** - Choose from hundreds of map designs (dark mode, terrain, outdoors, etc.)
- **Personal preference** - Pick a style that works best for you

## Setting Up Custom Tiles (MapTiler Example)

MapTiler offers a free tier that's perfect for personal use. Here's how to set it up:

**Step 1: Create a Free MapTiler Account**

1. Go to [MapTiler.com](https://www.maptiler.com/) and sign up for a free account
2. The free tier includes 100,000 tile requests per month - more than enough for regular personal use

**Step 2: Choose Your Map Style**

1. Navigate to [MapTiler Maps](https://cloud.maptiler.com/maps/)
2. Browse through the available map styles:
    - **Satellite** - Aerial imagery
    - **Streets** - Classic street map
    - **Outdoor** - Topographic style
    - **Hybrid** - Satellite with street labels
    - And many more!
3. Click on the map style you want to use

**Step 3: Get Your Tile URL**

1. On the map style page, scroll down to find the **"Raster tiles"** section
2. Look for the **XYZ tiles** URL format
3. Copy the URL - it should look like this:
   ```
   https://api.maptiler.com/maps/satellite/{z}/{x}/{y}.jpg?key=YOUR_PERSONAL_KEY_HERE
   ```
4. **Important:** Make sure the URL contains `{z}/{x}/{y}` placeholders - these are required!

**Step 4: Configure GeoPulse**

1. In GeoPulse, go to **Profile** → **Custom Map Tile URL**
2. Paste your MapTiler tile URL (with your API key included)
3. Click **Save**
4. Navigate to any map page - your new tiles will load automatically!

**Step 5: Switching Back to Default**

To return to the default OpenStreetMap tiles, simply:

1. Go to **Profile** → **Custom Map Tile URL**
2. Clear the field (leave it empty)
3. Click **Save**

#### Security Note

⚠️ **Never share your tile URL with anyone!** The URL contains your personal API key. If someone else uses your key, it
will count against your quota and could exhaust your free tier limits.

## Supported Tile Providers

GeoPulse works with any tile provider that supports the standard XYZ tile format. Some popular options:

- **[MapTiler](https://www.maptiler.com/)** - Free tier available, satellite imagery, many styles
- **[Mapbox](https://www.mapbox.com/)** - Free tier available, beautiful styles
- **[Thunderforest](https://www.thunderforest.com/)** - Specialized maps (cycling, transport, outdoors)
- **[Stadia Maps](https://stadiamaps.com/)** - Free tier for non-commercial use
- **ESRI World Imagery** - Free satellite imagery (no API key required)
  ```
  https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}
  ```

## Troubleshooting

**Tiles not loading after changing URL:**

- Hard refresh your browser (Ctrl+Shift+R or Cmd+Shift+R) to clear cached tiles
- Verify the URL contains `{z}`, `{x}`, and `{y}` placeholders
- Check that your API key is valid and hasn't expired

**Mixed tile styles appearing:**

- This is a browser cache issue - hard refresh to clear cached tiles
- The version parameter in the URL should prevent this, but may require a cache clear on first use

**Tiles load slowly:**

- Free tier providers may have rate limits
- Consider using a provider with better free tier limits
- Check your internet connection