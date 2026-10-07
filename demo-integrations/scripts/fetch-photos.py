#!/usr/bin/env python3
"""Download the demo photo set from Wikimedia Commons and prepare it for the fake Immich server.

"timeOfDay" ("morning" or "evening") keeps sunsets and breakfasts at plausible hours.

Only CC0 and public-domain files are accepted, so the demo needs no attribution UI.
The license is re-checked on every run; a file whose license changed is rejected.

Requires Pillow:  python3 -m venv .venv && .venv/bin/pip install pillow
Run from anywhere: .venv/bin/python demo-integrations/scripts/fetch-photos.py
"""
import io
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

USER_AGENT = "GeoPulseDemoAssets/1.0 (https://github.com/tess1o/geopulse)"
ALLOWED_LICENSES = {"cc0", "public domain", "pdm-owner", "pd"}
PREVIEW_BOX = 1280
THUMB_BOX = 480

root = Path(__file__).resolve().parent.parent
sources = json.loads((root / "scripts" / "photo-sources.json").read_text())
photos_dir = root / "internal" / "catalog" / "photos"
photos_dir.mkdir(parents=True, exist_ok=True)


def http_get(url):
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            return urllib.request.urlopen(request, timeout=60).read()
        except Exception as error:  # noqa: BLE001 - retry any transient network error
            if attempt == 3:
                raise
            print(f"  retrying after {error}", file=sys.stderr)
            time.sleep(3 * (attempt + 1))


def image_info(title):
    params = {
        "action": "query", "format": "json", "titles": title, "prop": "imageinfo",
        "iiprop": "url|size|extmetadata", "iiurlwidth": str(PREVIEW_BOX),
        "iiextmetadatafilter": "LicenseShortName|Artist",
    }
    data = json.loads(http_get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)))
    page = next(iter(data["query"]["pages"].values()))
    if "imageinfo" not in page:
        raise RuntimeError(f"{title} not found on Commons")
    return page["imageinfo"][0]


def save_jpeg(image, box, path, quality):
    resized = image.copy()
    resized.thumbnail((box, box), Image.LANCZOS)
    resized.save(path, "JPEG", quality=quality, optimize=True, progressive=True)
    return resized.size


catalog = []
for source in sources:
    info = image_info(source["commonsTitle"])
    metadata = info.get("extmetadata", {})
    license_name = metadata.get("LicenseShortName", {}).get("value", "")
    if license_name.strip().lower() not in ALLOWED_LICENSES:
        raise SystemExit(f"{source['commonsTitle']}: license '{license_name}' is not CC0/public domain")

    print(f"{source['id']}: {license_name}")
    image = ImageOps.exif_transpose(Image.open(io.BytesIO(http_get(info["thumburl"])))).convert("RGB")
    width, height = save_jpeg(image, PREVIEW_BOX, photos_dir / f"{source['id']}.jpg", 80)
    save_jpeg(image, THUMB_BOX, photos_dir / f"{source['id']}.thumb.jpg", 74)

    catalog.append({
        **{key: source[key] for key in ("id", "region", "category", "caption", "timeOfDay") if key in source},
        "width": width,
        "height": height,
        "license": license_name,
        "author": re.sub(r"<[^>]+>", "", metadata.get("Artist", {}).get("value", "")).strip(),
        "sourceUrl": info["descriptionurl"],
    })
    time.sleep(0.5)

(root / "internal" / "catalog" / "photos.json").write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")
print(f"Prepared {len(catalog)} photos in {photos_dir}")
