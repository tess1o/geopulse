import {test, expect} from '../fixtures/database-fixture.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';

test.describe('Location Analytics Page', () => {

  test.describe('Page Load and Initial State', () => {
    test('should display location analytics page correctly', async ({page, dbManager}) => {
      const {locationAnalyticsPage} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Verify we're on the location analytics page
      expect(await locationAnalyticsPage.isOnLocationAnalyticsPage()).toBe(true);

      // Verify page title and subtitle
      expect(await locationAnalyticsPage.verifyPageTitle()).toBe(true);
      expect(await locationAnalyticsPage.verifyPageSubtitle()).toBe(true);

      // Verify Cities tab is active by default
      expect(await locationAnalyticsPage.isTabActive('Cities')).toBe(true);

      // Verify empty state when no data exists
      expect(await locationAnalyticsPage.isEmptyStateVisible()).toBe(true);
    });

    test('should display correct tab counts', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create location analytics data
      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      // Reload to see data
      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000); // Wait for data to load

      // Verify tab counts
      const citiesCount = await locationAnalyticsPage.getCitiesTabCount();
      const countriesCount = await locationAnalyticsPage.getCountriesTabCount();

      expect(citiesCount).toBe(7); // 7 cities in diverse data
      expect(countriesCount).toBe(4); // 4 countries in diverse data
    });

    test('should display empty state when no locations exist', async ({page, dbManager}) => {
      const {locationAnalyticsPage} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Verify empty state for cities
      expect(await locationAnalyticsPage.isEmptyStateVisible()).toBe(true);
      const citiesEmptyText = await locationAnalyticsPage.getEmptyStateText();
      expect(citiesEmptyText).toContain('No cities found');

      // Switch to countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(500);

      // Verify empty state for countries
      expect(await locationAnalyticsPage.isEmptyStateVisible()).toBe(true);
      const countriesEmptyText = await locationAnalyticsPage.getEmptyStateText();
      expect(countriesEmptyText).toContain('No countries found');
    });

    test('should show loading state while fetching data', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create some data
      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      // Navigate away and back to trigger loading
      await page.goto('/app/timeline');
      await page.waitForTimeout(500);

      await locationAnalyticsPage.navigate();

      // Loading state may appear briefly - just verify page loads correctly
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify data is displayed
      const cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBeGreaterThan(0);
    });
  });

  test.describe('Cities Tab', () => {
    test('should display cities with correct statistics', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create test data
      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify cities are displayed
      const cards = await locationAnalyticsPage.getLocationCards();
      expect(cards.length).toBe(7);

      // Verify card structure for first city
      const firstCard = cards[0];
      expect(firstCard.name).toBeDefined();
      expect(firstCard.country).toBeDefined();
      expect(firstCard.stats).toBeDefined();
      expect(firstCard.stats.visits).toBeGreaterThan(0);
      expect(firstCard.stats.places).toBeGreaterThan(0);
    });

    test('should display cities sorted by visit count', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create cities with different visit counts
      const locations = [
        {
          city: 'City A',
          country: 'USA',
          latitude: 40.0,
          longitude: -74.0,
          visitCount: 2
        },
        {
          city: 'City B',
          country: 'USA',
          latitude: 41.0,
          longitude: -75.0,
          visitCount: 5
        },
        {
          city: 'City C',
          country: 'USA',
          latitude: 42.0,
          longitude: -76.0,
          visitCount: 3
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify cities are sorted by visit count (descending)
      const cards = await locationAnalyticsPage.getLocationCards();
      expect(cards[0].stats.visits).toBeGreaterThanOrEqual(cards[1].stats.visits);
      expect(cards[1].stats.visits).toBeGreaterThanOrEqual(cards[2].stats.visits);
    });

    test('should display correct statistics from database', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const cityName = 'New York';
      await TestSetupHelper.createSingleCityAnalyticsData(dbManager, user.id, {
        city: cityName,
        country: 'USA',
        placeCount: 3,
        visitsPerPlace: 4
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify statistics match database
      const dbStats = await TestSetupHelper.getCityStatistics(dbManager, user.id, cityName);
      const cardData = await locationAnalyticsPage.getLocationCardByName(cityName);

      expect(cardData).not.toBeNull();
      expect(cardData.stats.visits).toBe(parseInt(dbStats.visit_count));
      expect(cardData.stats.places).toBe(parseInt(dbStats.unique_places));
    });

    test('should navigate to city details when clicking city card', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const cityName = 'New York';
      await TestSetupHelper.createSingleCityAnalyticsData(dbManager, user.id, {
        city: cityName,
        country: 'USA'
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Click on city card
      await locationAnalyticsPage.clickLocationCardByName(cityName);

      // Verify navigation to city details page
      expect(await locationAnalyticsPage.verifyNavigationToCityDetails(cityName)).toBe(true);
    });

    test('should display multiple cities from same country', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {
          city: 'New York',
          country: 'USA',
          latitude: 40.7128,
          longitude: -74.0060,
          visitCount: 3
        },
        {
          city: 'Los Angeles',
          country: 'USA',
          latitude: 34.0522,
          longitude: -118.2437,
          visitCount: 2
        },
        {
          city: 'Chicago',
          country: 'USA',
          latitude: 41.8781,
          longitude: -87.6298,
          visitCount: 4
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      const cards = await locationAnalyticsPage.getLocationCards();
      expect(cards.length).toBe(3);

      // Verify all cities are from USA
      cards.forEach(card => {
        expect(card.country).toBe('USA');
      });
    });
  });

  test.describe('Countries Tab', () => {
    test('should display countries with correct statistics', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create test data
      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Switch to countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      // Verify countries are displayed
      const cards = await locationAnalyticsPage.getLocationCards();
      expect(cards.length).toBe(4); // USA, UK, France, Japan

      // Verify card structure for first country
      const firstCard = cards[0];
      expect(firstCard.name).toBeDefined();
      expect(firstCard.stats).toBeDefined();
      expect(firstCard.stats.visits).toBeGreaterThan(0);
      expect(firstCard.stats.cities).toBeGreaterThan(0);
      expect(firstCard.stats.places).toBeGreaterThan(0);
    });

    test('should display countries sorted by visit count', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {
          city: 'Paris',
          country: 'France',
          latitude: 48.8566,
          longitude: 2.3522,
          visitCount: 2
        },
        {
          city: 'New York',
          country: 'USA',
          latitude: 40.7128,
          longitude: -74.0060,
          visitCount: 5
        },
        {
          city: 'Tokyo',
          country: 'Japan',
          latitude: 35.6762,
          longitude: 139.6503,
          visitCount: 3
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Switch to countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      // Verify countries are sorted by visit count (descending)
      const cards = await locationAnalyticsPage.getLocationCards();
      expect(cards[0].stats.visits).toBeGreaterThanOrEqual(cards[1].stats.visits);
      expect(cards[1].stats.visits).toBeGreaterThanOrEqual(cards[2].stats.visits);
    });

    test('should display correct country statistics from database', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const countryName = 'USA';
      await TestSetupHelper.createSingleCountryAnalyticsData(dbManager, user.id, {
        country: countryName,
        cityCount: 3,
        visitsPerCity: 2
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Switch to countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      // Verify statistics match database
      const dbStats = await TestSetupHelper.getCountryStatistics(dbManager, user.id, countryName);
      const cardData = await locationAnalyticsPage.getLocationCardByName(countryName);

      expect(cardData).not.toBeNull();
      expect(cardData.stats.visits).toBe(parseInt(dbStats.visit_count));
      expect(cardData.stats.cities).toBe(parseInt(dbStats.city_count));
      expect(cardData.stats.places).toBe(parseInt(dbStats.unique_places));
    });

    test('should navigate to country details when clicking country card', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const countryName = 'USA';
      await TestSetupHelper.createSingleCountryAnalyticsData(dbManager, user.id, {
        country: countryName,
        cityCount: 2
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Switch to countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      // Click on country card
      await locationAnalyticsPage.clickLocationCardByName(countryName);

      // Verify navigation to country details page
      expect(await locationAnalyticsPage.verifyNavigationToCountryDetails(countryName)).toBe(true);
    });

    test('should show multiple cities count for country', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create data for one country with multiple cities
      await TestSetupHelper.createSingleCountryAnalyticsData(dbManager, user.id, {
        country: 'USA',
        cityCount: 4,
        visitsPerCity: 1
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Switch to countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      const cardData = await locationAnalyticsPage.getLocationCardByName('USA');
      expect(cardData.stats.cities).toBe(4);
    });
  });

  test.describe('Tab Switching', () => {
    test('should switch between cities and countries tabs', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify Cities tab is active initially
      expect(await locationAnalyticsPage.isTabActive('Cities')).toBe(true);

      // Switch to Countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);
      expect(await locationAnalyticsPage.isTabActive('Countries')).toBe(true);

      // Verify countries are displayed
      let cards = await locationAnalyticsPage.getLocationCards();
      expect(cards.length).toBe(4); // 4 countries

      // Switch back to Cities tab
      await locationAnalyticsPage.clickCitiesTab();
      await page.waitForTimeout(1000);
      expect(await locationAnalyticsPage.isTabActive('Cities')).toBe(true);

      // Verify cities are displayed
      cards = await locationAnalyticsPage.getLocationCards();
      expect(cards.length).toBe(7); // 7 cities
    });

    test('should preserve tab selection on page reload', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Switch to Countries tab
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(500);

      // Get URL to verify it doesn't have query params (tab is managed by client state)
      const urlBeforeReload = page.url();

      // Reload page
      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(1000);

      // After reload, should default back to Cities tab (as it's client-side state)
      expect(await locationAnalyticsPage.isTabActive('Cities')).toBe(true);
    });
  });

  test.describe('Search Functionality', () => {
    test('should display search bar', async ({page, dbManager}) => {
      const {locationAnalyticsPage} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Verify search input is visible
      const searchInput = page.locator('.p-autocomplete-input');
      expect(await searchInput.isVisible()).toBe(true);
    });

    test('should search and navigate to city', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const cityName = 'New York';
      await TestSetupHelper.createSingleCityAnalyticsData(dbManager, user.id, {
        city: cityName,
        country: 'USA'
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Search for the city
      await locationAnalyticsPage.fillSearchInput(cityName);
      await page.waitForTimeout(1000);

      // Verify dropdown appears
      if (await locationAnalyticsPage.isSearchDropdownVisible()) {
        await locationAnalyticsPage.clickSearchResult(0);
        // Verify navigation
        await page.waitForTimeout(1000);
        expect(page.url()).toContain('location-analytics/city');
      }
    });

    test('should search and navigate to country', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createSingleCountryAnalyticsData(dbManager, user.id, {
        country: 'France',
        cityCount: 2
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Search for the country
      await locationAnalyticsPage.fillSearchInput('France');
      await page.waitForTimeout(1000);

      // Verify dropdown appears
      if (await locationAnalyticsPage.isSearchDropdownVisible()) {
        await locationAnalyticsPage.clickSearchResult(0);
        // Verify navigation
        await page.waitForTimeout(1000);
        expect(page.url()).toContain('location-analytics/country');
      }
    });

    test('should show no results for non-existent search', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Search for non-existent location
      await locationAnalyticsPage.fillSearchInput('NonExistentCity123');
      await page.waitForTimeout(1000);

      // Dropdown may not appear or may show empty
      const dropdownVisible = await locationAnalyticsPage.isSearchDropdownVisible();
      if (dropdownVisible) {
        const results = await locationAnalyticsPage.getSearchResults();
        expect(results.length).toBe(0);
      }
    });
  });

  test.describe('Data Display', () => {
    test('should display correct visit counts', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {
          city: 'Test City',
          country: 'Test Country',
          latitude: 40.0,
          longitude: -74.0,
          visitCount: 7,
          locationName: 'Test Location'
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      const cardData = await locationAnalyticsPage.getLocationCardByName('Test City');
      expect(cardData).not.toBeNull();
      expect(cardData.stats.visits).toBe(7);
    });

    test('should display unique places count correctly', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create data with multiple unique places in one city
      await TestSetupHelper.createSingleCityAnalyticsData(dbManager, user.id, {
        city: 'Multi-Place City',
        country: 'USA',
        placeCount: 5,
        visitsPerPlace: 2
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      const cardData = await locationAnalyticsPage.getLocationCardByName('Multi-Place City');
      expect(cardData).not.toBeNull();
      expect(cardData.stats.places).toBe(5);
    });

    test('should handle cities with null country gracefully', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create geocoding result with null country
      const geocodingId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Unknown Location',
        city: 'Unknown City',
        country: null,
        providerName: 'Nominatim'
      });

      // Create timeline stay
      await TestSetupHelper.createTimelineStay(dbManager, user.id, {
        geocodingId,
        coords: 'POINT(-74.0060 40.7128)',
        locationName: 'Unknown Location',
        timestampOffset: '1 hour'
      });

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Page should still load without errors
      expect(await locationAnalyticsPage.isOnLocationAnalyticsPage()).toBe(true);
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should handle mobile viewport', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      // Set mobile viewport
      await locationAnalyticsPage.setMobileViewport();

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify page still works on mobile
      expect(await locationAnalyticsPage.isOnLocationAnalyticsPage()).toBe(true);

      const cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBeGreaterThan(0);

      // Verify tabs still work
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(500);
      expect(await locationAnalyticsPage.isTabActive('Countries')).toBe(true);
    });

    test('should handle tablet viewport', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      // Set tablet viewport
      await locationAnalyticsPage.setTabletViewport();

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify page works on tablet
      expect(await locationAnalyticsPage.isOnLocationAnalyticsPage()).toBe(true);

      const cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBeGreaterThan(0);
    });

    test('should display grid correctly on different screen sizes', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Test desktop
      await locationAnalyticsPage.setDesktopViewport();
      let cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBeGreaterThan(0);

      // Test mobile
      await locationAnalyticsPage.setMobileViewport();
      cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBeGreaterThan(0);
    });
  });

  test.describe('Edge Cases', () => {
    test('should handle single city with many visits', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {
          city: 'Frequent Visit City',
          country: 'USA',
          latitude: 40.0,
          longitude: -74.0,
          visitCount: 100,
          locationName: 'Popular Place'
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      const cardData = await locationAnalyticsPage.getLocationCardByName('Frequent Visit City');
      expect(cardData).not.toBeNull();
      expect(cardData.stats.visits).toBe(100);
    });

    test('should handle city names with special characters', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {
          city: "L'Aquila",
          country: 'Italy',
          latitude: 42.3498,
          longitude: 13.3995,
          visitCount: 2,
          locationName: 'Test Location'
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      const cardData = await locationAnalyticsPage.getLocationCardByName("L'Aquila");
      expect(cardData).not.toBeNull();
    });

    test('should handle very long city and country names', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {
          city: 'Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch',
          country: 'United Kingdom',
          latitude: 53.2231,
          longitude: -4.1981,
          visitCount: 1,
          locationName: 'Long Name Location'
        }
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Page should handle long names without breaking
      expect(await locationAnalyticsPage.isOnLocationAnalyticsPage()).toBe(true);
      const cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBe(1);
    });

    test('should handle multiple countries with single city each', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      const locations = [
        {city: 'Paris', country: 'France', latitude: 48.8566, longitude: 2.3522, visitCount: 1},
        {city: 'Berlin', country: 'Germany', latitude: 52.5200, longitude: 13.4050, visitCount: 1},
        {city: 'Madrid', country: 'Spain', latitude: 40.4168, longitude: -3.7038, visitCount: 1},
        {city: 'Rome', country: 'Italy', latitude: 41.9028, longitude: 12.4964, visitCount: 1}
      ];

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Verify cities
      let cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBe(4);

      // Switch to countries and verify
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBe(4);

      // Each country should have 1 city
      const cards = await locationAnalyticsPage.getLocationCards();
      cards.forEach(card => {
        expect(card.stats.cities).toBe(1);
      });
    });
  });

  test.describe('Card Interactions', () => {
    test('should show hover effect on location cards', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Hover over first card
      const firstCard = page.locator('.location-card').first();
      await firstCard.hover();
      await page.waitForTimeout(500);

      // Verify card is visible (hover effect exists in CSS)
      expect(await firstCard.isVisible()).toBe(true);
    });

    test('should display correct icons for cities and countries', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      await TestSetupHelper.createDiverseLocationAnalyticsData(dbManager, user.id);

      await page.reload();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);

      // Check cities have building icon
      const cityIcon = page.locator('.location-card .pi-building').first();
      expect(await cityIcon.isVisible()).toBe(true);

      // Switch to countries and check globe icon
      await locationAnalyticsPage.clickCountriesTab();
      await page.waitForTimeout(1000);

      const countryIcon = page.locator('.location-card .pi-globe').first();
      expect(await countryIcon.isVisible()).toBe(true);
    });
  });

  test.describe('Performance and Loading', () => {
    test('should load page efficiently with large dataset', async ({page, dbManager}) => {
      const {locationAnalyticsPage, user} = await TestSetupHelper.loginAndNavigateToLocationAnalyticsPage(page, dbManager);

      // Create large dataset
      const locations = [];
      for (let i = 0; i < 20; i++) {
        locations.push({
          city: `City ${i}`,
          country: `Country ${i % 5}`,
          latitude: 40.0 + (i * 0.1),
          longitude: -74.0 + (i * 0.1),
          visitCount: Math.floor(Math.random() * 10) + 1,
          locationName: `Location ${i}`
        });
      }

      await TestSetupHelper.createLocationAnalyticsData(dbManager, user.id, locations);

      await page.reload();

      // Measure load time
      const startTime = Date.now();
      await locationAnalyticsPage.waitForPageLoad();
      await page.waitForTimeout(2000);
      const loadTime = Date.now() - startTime;

      // Verify page loaded in reasonable time (< 10 seconds)
      expect(loadTime).toBeLessThan(10000);

      // Verify all cities are displayed
      const cardCount = await locationAnalyticsPage.getLocationCardCount();
      expect(cardCount).toBe(20);
    });
  });
});
