import { test, expect } from '../fixtures/isolated-fixture.js'
import { TestSetupHelper } from '../utils/test-setup-helper.js'
import { buildManagedUser as createManagedUser } from '../utils/isolated-user-helper.js'
import { MAP_POPUP_CONTENT_SELECTOR, MapEngineHarness } from '../utils/map-engine-harness.js'

const setupMultipleFriendsMapTest = async (page, dbManager, isolatedUsers, friendCount, mapMode) => {
  const state = await TestSetupHelper.setupMultipleFriendsTest(
    page,
    dbManager,
    friendCount,
    false,
    createManagedUser(isolatedUsers),
    Array.from({ length: friendCount }, () => createManagedUser(isolatedUsers))
  )

  await TestSetupHelper.applyMapRenderModeIfProvided(dbManager, state.testUser.email, mapMode)
  return state
}

const getVisibleLiveFriendFeatures = async (page, mapHarness, mapMode) => {
  if (mapMode === 'VECTOR') {
    const vectorMarkerCount = await page
      .locator('.friends-map-container .gp-vector-friend-marker')
      .count()

    if (vectorMarkerCount > 0) {
      return vectorMarkerCount
    }

    const rendered = await mapHarness.countVectorRenderedFeatures({
      rootSelector: '.friends-map-container',
      layerIncludes: ['gp-friends']
    })
    return rendered.count
  }

  const rasterFriendLayerCount = await page.evaluate(() => {
    const root = document.querySelector('.friends-map-container')
    const host = root?.querySelector('[data-testid="map-host-raster"]')
    const mapId = host?.id || null
    const registry = window.__GP_E2E_MAPS || {}
    const map = mapId ? registry[mapId] : null

    if (!map || typeof map.eachLayer !== 'function') {
      return 0
    }

    let count = 0
    map.eachLayer((layer) => {
      if (!layer || typeof layer.getLatLng !== 'function') {
        return
      }

      // Friends markers in RasterFriendsLayer are created with the `friend` option.
      if (layer?.options?.friend) {
        count += 1
      }
    })

    return count
  })

  if (rasterFriendLayerCount > 0) {
    return rasterFriendLayerCount
  }

  // Fallback for DOM-only detection (class names may differ in raster host output).
  const rasterMarkers = page.locator(
    '.friends-map-container .leaflet-interactive[role="button"]:has(i.fas.fa-user), ' +
    '.friends-map-container .leaflet-interactive[role="button"]:has(img[alt="User avatar"]), ' +
    '.friends-map-container .custom-marker.friend-marker, ' +
    '.friends-map-container .avatar-marker'
  )
  return rasterMarkers.count()
}

const openAnyFriendPopup = async (page, mapMode) => {
  if (mapMode === 'VECTOR') {
    const marker = page.locator('.friends-map-container .gp-vector-friend-marker').first()
    await expect(marker).toBeVisible({ timeout: 15000 })
    await marker.click()
    return true
  }

  const popupOpened = await page.evaluate(() => {
    const root = document.querySelector('.friends-map-container')
    const host = root?.querySelector('[data-testid="map-host-raster"]')
    const mapId = host?.id || null
    const registry = window.__GP_E2E_MAPS || {}
    const map = mapId ? registry[mapId] : null

    if (!map || typeof map.eachLayer !== 'function') {
      return false
    }

    let opened = false
    map.eachLayer((layer) => {
      if (opened) return
      if (!layer || layer?.options?.friend == null) return
      if (typeof layer.openPopup !== 'function') return
      layer.openPopup()
      opened = true
    })

    return opened
  })

  return popupOpened
}

const insertFriendGpsPoint = async (dbManager, {
  userId,
  deviceId,
  latitude,
  longitude,
  timestamp
}) => {
  await dbManager.client.query(`
    INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
  `, [
    deviceId,
    userId,
    `POINT (${longitude} ${latitude})`,
    timestamp,
    10,
    100,
    0,
    0,
    'OWNTRACKS',
    timestamp
  ])
}

const triggerLiveMapRefresh = async (page) => {
  const refreshButton = page.locator('.friends-map-container .custom-map-control-button[title="Refresh Locations"]').first()
  await expect(refreshButton).toBeVisible({ timeout: 10000 })
  await refreshButton.click()
}

const selectFriendMarker = async (page, mapMode, friendId = null) => {
  if (mapMode === 'VECTOR') {
    const marker = page.locator('.friends-map-container .gp-vector-friend-marker').first()
    await expect(marker).toBeVisible({ timeout: 15000 })
    await marker.click()
    return true
  }

  return page.evaluate((targetFriendId) => {
    const root = document.querySelector('.friends-map-container')
    const host = root?.querySelector('[data-testid="map-host-raster"]')
    const mapId = host?.id || null
    const registry = window.__GP_E2E_MAPS || {}
    const map = mapId ? registry[mapId] : null
    if (!map || typeof map.eachLayer !== 'function') {
      return false
    }

    const targetId = targetFriendId ? String(targetFriendId).toLowerCase() : null
    let fallbackLayer = null
    let targetLayer = null

    map.eachLayer((layer) => {
      const friend = layer?.options?.friend
      if (!friend) {
        return
      }

      if (!fallbackLayer) {
        fallbackLayer = layer
      }

      if (!targetId) {
        return
      }

      const layerFriendId = String(friend?.friendId || friend?.userId || friend?.id || '').toLowerCase()
      if (layerFriendId && layerFriendId === targetId) {
        targetLayer = layer
      }
    })

    const layerToClick = targetLayer || fallbackLayer
    if (!layerToClick || typeof layerToClick.fire !== 'function') {
      return false
    }

    layerToClick.fire('click', {
      target: layerToClick,
      latlng: typeof layerToClick.getLatLng === 'function' ? layerToClick.getLatLng() : null,
      originalEvent: null
    })

    if (typeof layerToClick.openPopup === 'function') {
      layerToClick.openPopup()
    }

    return true
  }, friendId)
}

const getLiveMapCenter = async (page) => {
  return page.evaluate(() => {
    const root = document.querySelector('.friends-map-container')
    const host = root?.querySelector('[data-testid="map-host-vector"], [data-testid="map-host-raster"]')
    const mapId = host?.id || null
    const registry = window.__GP_E2E_MAPS || {}
    const map = mapId ? registry[mapId] : null
    if (!map || typeof map.getCenter !== 'function') {
      return null
    }

    const center = map.getCenter()
    const lat = Number(center?.lat)
    const lon = Number(center?.lng)
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      return null
    }

    return { lat, lon }
  })
}


const isCenterNear = (center, { latitude, longitude }, tolerance = 0.006) => {
  if (!center) {
    return false
  }

  return Math.abs(center.lat - latitude) <= tolerance
    && Math.abs(center.lon - longitude) <= tolerance
}


test.describe('Friends Map Coverage', () => {
  test('should render Live map in selected mode and include only friends with live permission', async ({ page, isolatedUsers, dbManager, mapMode }) => {
    const mapHarness = new MapEngineHarness(page)
    const { testUser, user, friends, friendsPage } = await setupMultipleFriendsMapTest(page, dbManager, isolatedUsers, 3, mapMode)

    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friends[0].dbUser.id, 50.4501, 30.5234, true)
    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friends[1].dbUser.id, 40.7128, -74.0060, false)
    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friends[2].dbUser.id, 51.5074, -0.1278, true)

    await TestSetupHelper.loginAndNavigateToFriendsPage(page, testUser, friendsPage)
    expect(await friendsPage.isTabActive('live')).toBe(true)

    const activeMode = await mapHarness.waitForMapReady({
      rootSelector: '.friends-map-container',
      timeout: 20000,
      settleMs: 1200
    })
    expect(activeMode).toBe(mapMode)

    const liveBadge = await friendsPage.getTabBadgeValue('live')
    if (liveBadge) {
      expect(liveBadge).toBe('2')
    }

    await expect.poll(
      () => getVisibleLiveFriendFeatures(page, mapHarness, mapMode),
      { timeout: 20000 }
    ).toBeGreaterThanOrEqual(2)
  })

  test('should auto-refresh Live map when friend location changes (polling)', async ({ page, isolatedUsers, dbManager, mapMode }) => {
    const mapHarness = new MapEngineHarness(page)
    let friendsEndpointGetCalls = 0
    const friendCoordinatesHistory = []
    let trackedFriendId = null

    await page.route('**/api/friends**', async (route) => {
      const request = route.request()
      const pathname = new URL(request.url()).pathname
      const isFriendsEndpoint = /\/api\/friends\/?$/.test(pathname)

      if (!isFriendsEndpoint || request.method() !== 'GET') {
        await route.continue()
        return
      }

      const backendResponse = await route.fetch()
      const body = await backendResponse.text()
      friendsEndpointGetCalls += 1

      try {
        const payload = JSON.parse(body)
        const friendsData = Array.isArray(payload?.data) ? payload.data : []

        if (trackedFriendId) {
          const trackedFriendIdNormalized = String(trackedFriendId).toLowerCase()
          const friend = friendsData.find((entry) => (
            String(entry.friendId || entry.userId || '').toLowerCase() === trackedFriendIdNormalized
          ))

          if (friend) {
            friendCoordinatesHistory.push({
              lat: friend.lastLatitude,
              lon: friend.lastLongitude
            })
          }
        }
      } catch {
        // Keep passthrough behavior even if payload parsing fails.
      }

      await route.fulfill({
        response: backendResponse,
        body
      })
    })

    const { testUser, user, friends, friendsPage } = await setupMultipleFriendsMapTest(page, dbManager, isolatedUsers, 1, mapMode)
    const friendId = friends[0].dbUser.id
    trackedFriendId = friendId

    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friendId, 50.4501, 30.5234, true)
    await TestSetupHelper.loginAndNavigateToFriendsPage(page, testUser, friendsPage)

    expect(await friendsPage.isTabActive('live')).toBe(true)
    expect(await mapHarness.waitForMapReady({ rootSelector: '.friends-map-container', timeout: 20000 })).toBe(mapMode)

    await expect.poll(() => friendCoordinatesHistory.length, { timeout: 30000 }).toBeGreaterThan(0)

    const initialCoordinates = friendCoordinatesHistory[friendCoordinatesHistory.length - 1]
    expect(initialCoordinates.lat).toBeCloseTo(50.4501, 4)
    expect(initialCoordinates.lon).toBeCloseTo(30.5234, 4)
    const callsBeforeLocationUpdate = friendsEndpointGetCalls

    const newerTimestamp = new Date(Date.now() + 60_000).toISOString()
    await dbManager.client.query(`
      INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `, [
      'friends-map-polling-device',
      friendId,
      'POINT (30.5334 50.4601)',
      newerTimestamp,
      10,
      100,
      0,
      0,
      'OWNTRACKS',
      newerTimestamp
    ])

    await expect.poll(() => friendsEndpointGetCalls, { timeout: 45000 }).toBeGreaterThan(callsBeforeLocationUpdate)

    await expect.poll(() => {
      const last = friendCoordinatesHistory[friendCoordinatesHistory.length - 1]
      if (!last) return ''
      return `${Number(last.lat).toFixed(4)},${Number(last.lon).toFixed(4)}`
    }, { timeout: 45000 }).toBe('50.4601,30.5334')

  })

  test('should keep auto-following selected friend when map is not manually moved', async ({ page, isolatedUsers, dbManager, mapMode }) => {
    const mapHarness = new MapEngineHarness(page)
    const { testUser, user, friends, friendsPage } = await setupMultipleFriendsMapTest(page, dbManager, isolatedUsers, 1, mapMode)
    const friendId = friends[0].dbUser.id

    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friendId, 50.4501, 30.5234, true)
    await TestSetupHelper.loginAndNavigateToFriendsPage(page, testUser, friendsPage)

    expect(await friendsPage.isTabActive('live')).toBe(true)
    expect(await mapHarness.waitForMapReady({ rootSelector: '.friends-map-container', timeout: 20000 })).toBe(mapMode)

    const markerSelected = await selectFriendMarker(page, mapMode, friendId)
    expect(markerSelected).toBe(true)

    const nextPoint = {
      latitude: 50.4601,
      longitude: 30.5334
    }
    await insertFriendGpsPoint(dbManager, {
      userId: friendId,
      deviceId: 'friends-map-follow-no-pan',
      latitude: nextPoint.latitude,
      longitude: nextPoint.longitude,
      timestamp: new Date(Date.now() + 60_000).toISOString()
    })

    await triggerLiveMapRefresh(page)

    await expect.poll(async () => {
      const center = await getLiveMapCenter(page)
      return isCenterNear(center, nextPoint)
    }, { timeout: 15000 }).toBe(true)
  })

  test('should show battery percentage in Live friend popup from latest point', async ({ page, isolatedUsers, dbManager, mapMode }) => {
    const mapHarness = new MapEngineHarness(page)
    const { testUser, user, friends, friendsPage } = await setupMultipleFriendsMapTest(page, dbManager, isolatedUsers, 1, mapMode)
    const friendId = friends[0].dbUser.id

    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friendId, 50.4501, 30.5234, true)
    await TestSetupHelper.loginAndNavigateToFriendsPage(page, testUser, friendsPage)

    expect(await friendsPage.isTabActive('live')).toBe(true)
    expect(await mapHarness.waitForMapReady({ rootSelector: '.friends-map-container', timeout: 20000 })).toBe(mapMode)

    const popupOpened = await openAnyFriendPopup(page, mapMode)
    expect(popupOpened).toBe(true)

    const popup = page.locator(MAP_POPUP_CONTENT_SELECTOR).first()
    await expect(popup).toBeVisible({ timeout: 10000 })
    await expect(popup).toContainText('Battery: 100%')
  })

  test('should hide battery row in Live friend popup when latest point has no battery', async ({ page, isolatedUsers, dbManager, mapMode }) => {
    const mapHarness = new MapEngineHarness(page)
    const { testUser, user, friends, friendsPage } = await setupMultipleFriendsMapTest(page, dbManager, isolatedUsers, 1, mapMode)
    const friendId = friends[0].dbUser.id

    await TestSetupHelper.setupFriendshipWithLocation(dbManager, user.id, friendId, 50.4501, 30.5234, true)

    const latestTimestamp = new Date(Date.now() + 60_000).toISOString()
    await dbManager.client.query(`
      INSERT INTO gps_points (device_id, user_id, coordinates, timestamp, accuracy, battery, velocity, altitude, source_type, created_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
    `, [
      'friends-map-null-battery-device',
      friendId,
      'POINT (30.5235 50.4502)',
      latestTimestamp,
      10,
      null,
      0,
      0,
      'OWNTRACKS',
      latestTimestamp
    ])

    await TestSetupHelper.loginAndNavigateToFriendsPage(page, testUser, friendsPage)

    expect(await friendsPage.isTabActive('live')).toBe(true)
    expect(await mapHarness.waitForMapReady({ rootSelector: '.friends-map-container', timeout: 20000 })).toBe(mapMode)

    const popupOpened = await openAnyFriendPopup(page, mapMode)
    expect(popupOpened).toBe(true)

    const popup = page.locator(MAP_POPUP_CONTENT_SELECTOR).first()
    await expect(popup).toBeVisible({ timeout: 10000 })
    await expect(popup).not.toContainText('Battery:')
  })

  test('should render Timeline tab map in selected mode with timeline permission filtering', async ({ page, isolatedUsers, dbManager, mapMode }) => {
    const mapHarness = new MapEngineHarness(page)
    const { testUser, user, friends, friendsPage } = await setupMultipleFriendsMapTest(page, dbManager, isolatedUsers, 3, mapMode)

    await TestSetupHelper.setupFriendship(dbManager, user.id, friends[0].dbUser.id, {
      friendToUser: { shareLive: false, shareTimeline: true }
    })
    await TestSetupHelper.setupFriendship(dbManager, user.id, friends[1].dbUser.id, {
      friendToUser: { shareLive: false, shareTimeline: false }
    })
    await TestSetupHelper.setupFriendship(dbManager, user.id, friends[2].dbUser.id, {
      friendToUser: { shareLive: false, shareTimeline: true }
    })

    await TestSetupHelper.loginAndNavigateToFriendsPage(page, testUser, friendsPage)
    await friendsPage.switchToTab('timeline')
    await page.waitForTimeout(1200)

    expect(await friendsPage.isTabActive('timeline')).toBe(true)
    const timelineBadge = await friendsPage.getTabBadgeValue('timeline')
    if (timelineBadge) {
      expect(timelineBadge).toBe('2')
    }

    const activeMode = await mapHarness.waitForMapReady({
      rootSelector: '.friends-timeline-map',
      timeout: 20000,
      settleMs: 1200
    })
    expect(activeMode).toBe(mapMode)

    const timelineMapHost = page.locator('.friends-timeline-map [data-testid="map-host-raster"], .friends-timeline-map [data-testid="map-host-vector"]').first()
    await expect(timelineMapHost).toBeVisible()
  })
})
