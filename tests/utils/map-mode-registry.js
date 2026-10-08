// The map render mode each Playwright page runs with (the `mapMode` fixture option).
// Setup helpers apply it to the test user before the first navigation, while the page is still about:blank,
// so it cannot be read back from the page's window at that point.
const pageMapModes = new WeakMap();

const normalizeMapMode = (mode) => {
  if (!mode) {
    return null;
  }
  return String(mode).toUpperCase() === 'VECTOR' ? 'VECTOR' : 'RASTER';
};

export const registerPageMapMode = (page, mode) => {
  pageMapModes.set(page, normalizeMapMode(mode));
};

export const resolvePageMapMode = async (page) => {
  const registered = pageMapModes.get(page);
  if (registered) {
    return registered;
  }

  // Pages created outside the isolated fixture: fall back to the debug bootstrap value, if the page has one.
  try {
    return normalizeMapMode(await page.evaluate(() => window.__GP_E2E_MAP_DEBUG__?.mode || null));
  } catch {
    return null;
  }
};
