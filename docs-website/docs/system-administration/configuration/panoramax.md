---
title: Panoramax
description: Configure Panoramax coverage and photo viewing on GeoPulse Timeline maps.
---

# Panoramax

Panoramax adds public street-level imagery coverage to authenticated Timeline maps. It is enabled by default and uses
the public Panoramax STAC API by default. It is a map overlay only: GeoPulse does not copy Panoramax imagery into your
database or use it to generate the timeline.

## Configure the Endpoint

Administrators can disable Panoramax or change its endpoint in **Admin -> System Settings -> Panoramax**. The default
endpoint is:

```text
https://api.panoramax.xyz/api
```

Select **Test endpoint** after changing the URL. GeoPulse verifies that the endpoint responds with a Panoramax
vector-tile link.

The endpoint is sent to users' browsers so they can load coverage tiles and open the photo viewer. Use a public,
browser-accessible endpoint only. Do not put credentials, signed URLs, or private service addresses in this setting.

## Use the Overlay

Once enabled, signed-in users with a vector map see the images button in Timeline map controls. Turn it on to display
Panoramax coverage. Hovering a covered feature can show a photo preview; selecting it opens the Panoramax viewer at the
selected image or sequence.

The control is not available in public/shared Timeline views or raster-map mode. If it does not appear, confirm that the
endpoint test passes and the active map uses vector rendering.

## Operational Notes

- The default public endpoint is suitable for most installations; no additional Docker service is required.
- Coverage and image availability are owned by the configured Panoramax service and can vary by area.
- Disabling Panoramax removes the overlay without changing any stored GPS or timeline data.
