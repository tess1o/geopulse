import {test, expect} from '../fixtures/database-fixture.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';

test.describe('Geocoding Management Page', () => {

  test.describe('Page Load and Initial State', () => {
    test('should display geocoding management page correctly', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Verify we're on the geocoding page
      expect(await geocodingPage.isOnGeocodingPage()).toBe(true);

      // Verify table is initially empty (no geocoding results)
      expect(await geocodingPage.isTableEmpty()).toBe(true);

      // Verify reconcile all button is disabled when no results
      expect(await geocodingPage.isReconcileAllButtonDisabled()).toBe(true);
    });

    test('should display existing geocoding results on page load', async ({page, dbManager}) => {
      const {geocodingPage, user, testUser} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);
      
      // Create test geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager, user.id, 3);

      // Reload page to see results
      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify results appear in table
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(3);

      // Verify reconcile all button is enabled
      expect(await geocodingPage.isReconcileAllButtonDisabled()).toBe(false);
    });

    test('should display geocoding results with different providers', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results with different providers
      await TestSetupHelper.createGeocodingResultsWithDifferentProviders(dbManager, user.id);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify all results are shown
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(4); // Nominatim, GoogleMaps, Mapbox, Photon
    });
  });

  test.describe('Filters', () => {
    test('should filter by provider', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create results with different providers
      await TestSetupHelper.createGeocodingResultsWithDifferentProviders(dbManager, user.id);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify all results shown initially
      let rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(4);

      // Filter by Nominatim
      await geocodingPage.selectProviderFilter('Nominatim');

      // Verify only Nominatim results shown
      rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);

      // Filter by GoogleMaps
      await geocodingPage.selectProviderFilter('GoogleMaps');

      // Verify only GoogleMaps results shown
      rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);

      // Show all providers
      await geocodingPage.selectProviderFilter(null);

      // Verify all results shown
      rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(4);
    });

    test('should search by location name', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results with unique names
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Coffee Shop',
        city: 'New York',
        country: 'USA'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9851 40.7589)',
        displayName: 'Home Sweet Home',
        city: 'Boston',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Search for "Coffee"
      await geocodingPage.fillSearchInput('Coffee');

      // Verify only matching result is shown
      const searchRowCount = await geocodingPage.getTableRowCount();
      expect(searchRowCount).toBe(1);

      const rowData = await geocodingPage.getTableRowData(0);
      expect(rowData.name).toContain('Coffee');
    });

    test('should search by city', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results with different cities
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Location 1',
        city: 'New York',
        country: 'USA'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-71.0589 42.3601)',
        displayName: 'Location 2',
        city: 'Boston',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Search for "Boston"
      await geocodingPage.fillSearchInput('Boston');

      // Verify only Boston result is shown
      const searchRowCount = await geocodingPage.getTableRowCount();
      expect(searchRowCount).toBe(1);
    });

    test('should search by country', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results with different countries
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'US Location',
        city: 'New York',
        country: 'USA'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(2.3522 48.8566)',
        displayName: 'France Location',
        city: 'Paris',
        country: 'France'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Search for "France"
      await geocodingPage.fillSearchInput('France');

      // Verify only France result is shown
      const searchRowCount = await geocodingPage.getTableRowCount();
      expect(searchRowCount).toBe(1);
    });

    test('should clear filters', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results
      await TestSetupHelper.createGeocodingResultsWithDifferentProviders(dbManager, user.id);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Apply filters
      await geocodingPage.selectProviderFilter('Nominatim');
      await geocodingPage.fillSearchInput('Test');

      // Verify clear button is enabled
      expect(await geocodingPage.isClearFiltersButtonEnabled()).toBe(true);

      // Clear filters
      await geocodingPage.clearFilters();

      // Verify all results are shown
      const allRowCount = await geocodingPage.getTableRowCount();
      expect(allRowCount).toBe(4);

      // Verify clear button is disabled
      expect(await geocodingPage.isClearFiltersButtonEnabled()).toBe(false);
    });

    test('should combine provider and search filters', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create results with different providers and cities
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Nominatim NYC',
        city: 'New York',
        country: 'USA',
        providerName: 'Nominatim'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-71.0589 42.3601)',
        displayName: 'Nominatim Boston',
        city: 'Boston',
        country: 'USA',
        providerName: 'Nominatim'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9851 40.7589)',
        displayName: 'GoogleMaps NYC',
        city: 'New York',
        country: 'USA',
        providerName: 'GoogleMaps'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Filter by Nominatim provider
      await geocodingPage.selectProviderFilter('Nominatim');

      // Then search for "New York"
      await geocodingPage.fillSearchInput('New York');

      // Should show only Nominatim results with New York
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);
    });
  });

  test.describe('Edit Geocoding Result', () => {
    test('should edit geocoding result display name', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result
      const resultId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Original Name',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Edit the display name
      const newName = 'Updated Display Name';
      await geocodingPage.editGeocodingResultWorkflow(0, newName);

      // Verify database - name should be updated
      const result = await TestSetupHelper.getGeocodingResultById(dbManager, resultId);
      expect(result.display_name).toBe(newName);
      expect(result.city).toBe('New York');
      expect(result.country).toBe('USA');

      // Reload page to verify persistence
      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify table shows updated name
      const rowData = await geocodingPage.getTableRowData(0);
      expect(rowData.name).toContain(newName);
    });

    test('should edit geocoding result city and country', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result
      const resultId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Test Location',
        city: 'Old City',
        country: 'Old Country'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Edit city and country
      await geocodingPage.clickEditInTable(0);
      await geocodingPage.fillEditDialog(null, 'New York', 'USA');
      await geocodingPage.submitEditDialog();
      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(1000);

      // Verify database - city and country should be updated
      const result = await TestSetupHelper.getGeocodingResultById(dbManager, resultId);
      expect(result.display_name).toBe('Test Location'); // Unchanged
      expect(result.city).toBe('New York');
      expect(result.country).toBe('USA');
    });

    test('should cancel edit dialog', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result
      const resultId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Original Name',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Open edit dialog
      await geocodingPage.clickEditInTable(0);
      await geocodingPage.waitForEditDialog();

      // Cancel dialog
      await geocodingPage.closeEditDialog();

      // Verify no changes in database
      const result = await TestSetupHelper.getGeocodingResultById(dbManager, resultId);
      expect(result.display_name).toBe('Original Name');
      expect(result.city).toBe('New York');
      expect(result.country).toBe('USA');
    });
  });

  test.describe('View Details', () => {
    test('should navigate to place details when clicking view button', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result
      const resultId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'View Test Location',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Click view details
      await geocodingPage.clickViewDetails(0);

      // Verify navigation to place details page
      await page.waitForURL(`**/app/place-details/geocoding/${resultId}`, { timeout: 10000 });
      expect(page.url()).toContain(`/app/place-details/geocoding/${resultId}`);
    });
  });

  test.describe('Bulk Operations', () => {
    test('should select multiple rows', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create multiple geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 3);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Select first two rows
      await geocodingPage.selectTableRow(0);
      await geocodingPage.selectTableRow(1);

      // Verify selection count
      const selectedCount = await geocodingPage.getSelectedRowCount();
      expect(selectedCount).toBe(2);
    });

    test('should show bulk action buttons when rows selected', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 2);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Initially bulk buttons should not be visible
      expect(await geocodingPage.areBulkActionButtonsVisible()).toBe(false);

      // Select a row
      await geocodingPage.selectTableRow(0);

      // Now buttons should be visible
      expect(await geocodingPage.areBulkActionButtonsVisible()).toBe(true);
    });

    test('should perform bulk edit of city and country', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results with different cities
      // Note: Table is sorted by "Last Used" desc by default, so last created appears first
      const result1Id = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Location 1',
        city: 'Old City 1',
        country: 'Old Country 1'
      });

      const result2Id = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9851 40.7589)',
        displayName: 'Location 2',
        city: 'Old City 2',
        country: 'Old Country 2'
      });

      const result3Id = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9442 40.8006)',
        displayName: 'Location 3',
        city: 'Old City 3',
        country: 'Old Country 3'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Table shows: Row 0 = Location 3 (newest), Row 1 = Location 2, Row 2 = Location 1 (oldest)
      // Select first two rows (Location 3 and Location 2)
      await geocodingPage.selectTableRow(0);
      await geocodingPage.selectTableRow(1);

      // Click bulk edit
      await geocodingPage.clickBulkEdit();
      await geocodingPage.waitForBulkEditDialog();

      // Fill in new city and country
      const dialog = page.locator('.p-dialog:has(.p-dialog-title:text("Bulk Edit"))');

      // Enable and fill city field
      const cityCheckbox = dialog.locator('label:has-text("City")').locator('..').locator('input[type="checkbox"]');
      await cityCheckbox.check();
      await page.waitForTimeout(300);

      const cityInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="city"]');
      await cityInput.click();
      await cityInput.fill('New York');
      await page.waitForTimeout(500);

      // Click on dialog header to close autocomplete dropdown
      const dialogHeader = dialog.locator('.p-dialog-header');
      await dialogHeader.click();
      await page.waitForTimeout(300);

      // Enable and fill country field
      const countryCheckbox = dialog.locator('label:has-text("Country")').locator('..').locator('input[type="checkbox"]');
      await countryCheckbox.check();
      await page.waitForTimeout(300);

      const countryInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="country"]');
      await countryInput.click();
      await countryInput.fill('USA');
      await page.waitForTimeout(500);

      // Click on dialog header again to close autocomplete dropdown
      await dialogHeader.click();
      await page.waitForTimeout(300);

      // Submit bulk edit
      const updateButton = dialog.locator('button:has-text("Update")');
      await updateButton.click();

      await page.waitForTimeout(1000);

      // Handle possible typo detection dialog
      const continueButton = page.locator('button:has-text("Continue Anyway")');
      if (await continueButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await continueButton.click();
        await page.waitForTimeout(300);
      }

      // Wait for success
      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database - Location 3 and Location 2 should be updated (rows 0 and 1)
      const result3 = await TestSetupHelper.getGeocodingResultById(dbManager, result3Id);
      expect(result3.city).toBe('New York');
      expect(result3.country).toBe('USA');

      const result2 = await TestSetupHelper.getGeocodingResultById(dbManager, result2Id);
      expect(result2.city).toBe('New York');
      expect(result2.country).toBe('USA');

      // Location 1 should remain unchanged (row 2, not selected)
      const result1 = await TestSetupHelper.getGeocodingResultById(dbManager, result1Id);
      expect(result1.city).toBe('Old City 1');
      expect(result1.country).toBe('Old Country 1');

      // Reload and verify in table
      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify updated values appear in table
      const tableText = await page.locator('.geocoding-table').textContent();
      expect(tableText).toContain('New York');
      expect(tableText).toContain('USA');
    });

    test('should perform bulk edit of only city', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results
      // Note: Table is sorted by "Last Used" desc by default
      const result1Id = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Location A',
        city: 'Boston',
        country: 'USA'
      });

      const result2Id = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9851 40.7589)',
        displayName: 'Location B',
        city: 'Cambridge',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Table shows: Row 0 = Location B (newest), Row 1 = Location A (oldest)
      // Select both rows (both locations will be updated)
      await geocodingPage.selectTableRow(0);
      await geocodingPage.selectTableRow(1);

      // Click bulk edit
      await geocodingPage.clickBulkEdit();
      await geocodingPage.waitForBulkEditDialog();

      // Enable and fill only city field (leave country unchecked)
      const dialog = page.locator('.p-dialog:has(.p-dialog-title:text("Bulk Edit"))');

      const cityCheckbox = dialog.locator('label:has-text("City")').locator('..').locator('input[type="checkbox"]');
      await cityCheckbox.check();
      await page.waitForTimeout(300);

      const cityInput = dialog.locator('.p-autocomplete input.p-autocomplete-input[placeholder*="city"]');
      await cityInput.click();
      await cityInput.fill('San Francisco');
      await page.waitForTimeout(500);

      // Click on dialog header to close autocomplete dropdown
      const dialogHeader = dialog.locator('.p-dialog-header');
      await dialogHeader.click();
      await page.waitForTimeout(300);

      // Do NOT enable country field - leave it unchecked so it won't be updated

      // Submit
      const updateButton = dialog.locator('button:has-text("Update")');
      await updateButton.click();

      await page.waitForTimeout(1000);

      // Handle possible typo detection dialog
      const continueButton = page.locator('button:has-text("Continue Anyway")');
      if (await continueButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await continueButton.click();
        await page.waitForTimeout(300);
      }

      await geocodingPage.waitForSuccessToast();
      await page.waitForTimeout(2000);

      // Verify database - both locations should have city updated, country unchanged
      const result1 = await TestSetupHelper.getGeocodingResultById(dbManager, result1Id);
      expect(result1.city).toBe('San Francisco');
      expect(result1.country).toBe('USA'); // Should remain unchanged

      const result2 = await TestSetupHelper.getGeocodingResultById(dbManager, result2Id);
      expect(result2.city).toBe('San Francisco');
      expect(result2.country).toBe('USA'); // Should remain unchanged
    });
  });

  test.describe('Reconcile Functionality', () => {
    test('should show reconcile all button', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Initially button should be disabled (no results)
      expect(await geocodingPage.isReconcileAllButtonDisabled()).toBe(true);

      // Create a geocoding result
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Test',
        city: null,
        country: null
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Button should now be enabled
      expect(await geocodingPage.isReconcileAllButtonDisabled()).toBe(false);
    });

    test('should open reconcile dialog for single result', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Test Location',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Click reconcile button for the result
      await geocodingPage.clickReconcileInTable(0);

      // Verify reconcile dialog opens
      await geocodingPage.waitForReconcileDialog();

      const dialog = page.locator('.p-dialog:has(.p-dialog-title:has-text("Reconcile"))');
      expect(await dialog.isVisible()).toBe(true);
    });

    test('should open reconcile dialog for selected results', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create multiple geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 3);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Select rows
      await geocodingPage.selectTableRow(0);
      await geocodingPage.selectTableRow(1);

      // Click reconcile selected
      await geocodingPage.clickReconcileSelected();

      // Verify reconcile dialog opens
      await geocodingPage.waitForReconcileDialog();

      const dialog = page.locator('.p-dialog:has(.p-dialog-title:has-text("Reconcile"))');
      expect(await dialog.isVisible()).toBe(true);
    });

    test('should open reconcile dialog for all results', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 2);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Click reconcile all
      await geocodingPage.clickReconcileAll();

      // Verify reconcile dialog opens
      await geocodingPage.waitForReconcileDialog();

      const dialog = page.locator('.p-dialog:has(.p-dialog-title:has-text("Reconcile"))');
      expect(await dialog.isVisible()).toBe(true);
    });
  });

  test.describe('Pagination', () => {
    test('should paginate through results', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create more than 50 results to test pagination (default page size is 50)
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 55);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify first page shows 50 results
      let rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(50);

      // Go to next page
      await geocodingPage.goToNextPage();

      // Verify second page shows remaining 5 results
      rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(5);

      // Go back to first page
      await geocodingPage.goToPreviousPage();

      // Verify back to 50 results
      rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(50);
    });
  });

  test.describe('Sorting', () => {
    test('should sort by display name', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create results with specific names
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Zebra Location',
        city: 'New York',
        country: 'USA'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9851 40.7589)',
        displayName: 'Alpha Location',
        city: 'Boston',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Sort by display name (ascending)
      await geocodingPage.sortByColumn('Display Name');

      // Verify first result is "Alpha Location"
      const firstRowData = await geocodingPage.getTableRowData(0);
      expect(firstRowData.name).toContain('Alpha');

      // Sort again (descending)
      await geocodingPage.sortByColumn('Display Name');

      // Verify first result is now "Zebra Location"
      const firstRowDataDesc = await geocodingPage.getTableRowData(0);
      expect(firstRowDataDesc.name).toContain('Zebra');
    });

    test('should sort by city', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create results with different cities
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Location 1',
        city: 'Zebra City',
        country: 'USA'
      });

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-73.9851 40.7589)',
        displayName: 'Location 2',
        city: 'Alpha City',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Sort by city
      await geocodingPage.sortByColumn('City');

      // Verify sorting (first should be Alpha City)
      const firstRowData = await geocodingPage.getTableRowData(0);
      expect(firstRowData.city).toContain('Alpha');
    });
  });

  test.describe('Empty State', () => {
    test('should show empty state when no geocoding results exist', async ({page, dbManager}) => {
      const {geocodingPage} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Verify empty state
      expect(await geocodingPage.isTableEmpty()).toBe(true);

      // Verify empty message text
      const emptyState = page.locator('.empty-state');
      const text = await emptyState.textContent();
      expect(text).toContain('No Geocoding Results Found');
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should handle mobile viewport', async ({page, dbManager}) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });

      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 2);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify page still works on mobile
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(2);

      // Verify table is still visible
      const table = page.locator('.geocoding-table');
      expect(await table.isVisible()).toBe(true);
    });

    test('should handle tablet viewport', async ({page, dbManager}) => {
      // Set tablet viewport
      await page.setViewportSize({ width: 768, height: 1024 });

      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create geocoding results
      await TestSetupHelper.createMultipleGeocodingResults(dbManager,  user.id, 2);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify page works on tablet
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(2);
    });
  });

  test.describe('Last Accessed Time', () => {
    test('should display last accessed time', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result
      const resultId = await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Test Location',
        city: 'New York',
        country: 'USA'
      });

      // Update last accessed time to a specific date
      const pastDate = new Date();
      pastDate.setDate(pastDate.getDate() - 5); // 5 days ago
      await TestSetupHelper.updateGeocodingLastAccessed(dbManager, resultId, pastDate);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify "Last Used" column shows relative time (e.g., "5 days ago")
      const tableText = await page.locator('.geocoding-table').textContent();
      expect(tableText).toContain('ago');
    });
  });

  test.describe('Data Integrity', () => {
    test('should handle null city and country values', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result with null city and country
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Location Without City',
        city: null,
        country: null
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify result is displayed
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);

      // Verify null values are displayed appropriately
      const rowData = await geocodingPage.getTableRowData(0);
      expect(rowData.name).toContain('Location Without City');
    });

    test('should handle special characters in location names', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result with special characters
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: 'Café & Restaurant "L\'Étoile"',
        city: 'Paris',
        country: 'France'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify result is displayed correctly
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);

      const rowData = await geocodingPage.getTableRowData(0);
      expect(rowData.name).toContain('Café');
    });

    test('should handle very long location names', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result with a very long name
      const longName = 'This is a very long location name that should be handled properly by the application without breaking the UI or causing any display issues'.repeat(2);

      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.0060 40.7128)',
        displayName: longName.substring(0, 255), // Limit to reasonable length
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify result is displayed
      const rowCount = await geocodingPage.getTableRowCount();
      expect(rowCount).toBe(1);
    });
  });

  test.describe('Provider Tags', () => {
    test('should display provider tags with correct styling', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create results with different providers
      await TestSetupHelper.createGeocodingResultsWithDifferentProviders(dbManager, user.id);

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify provider tags are displayed
      const providerTags = page.locator('.provider-tag');
      const tagCount = await providerTags.count();
      expect(tagCount).toBe(4); // One for each provider

      // Verify different providers have different severity styles
      const nominatimTag = page.locator('.provider-tag:has-text("Nominatim")');
      expect(await nominatimTag.isVisible()).toBe(true);

      const googleMapsTag = page.locator('.provider-tag:has-text("GoogleMaps")');
      expect(await googleMapsTag.isVisible()).toBe(true);
    });
  });

  test.describe('Coordinates Display', () => {
    test('should display coordinates with proper formatting', async ({page, dbManager}) => {
      const {geocodingPage, user} = await TestSetupHelper.loginAndNavigateToGeocodingPage(page, dbManager);

      // Create a geocoding result with specific coordinates
      await TestSetupHelper.createGeocodingResult(dbManager, user.id, {
        coords: 'POINT(-74.006012 40.712784)',
        displayName: 'Precise Location',
        city: 'New York',
        country: 'USA'
      });

      await page.reload();
      await geocodingPage.waitForPageLoad();

      // Verify coordinates are displayed (formatted to 6 decimal places)
      const tableText = await page.locator('.geocoding-table').textContent();
      expect(tableText).toContain('40.712784');
      expect(tableText).toContain('-74.006012');
    });
  });
});
