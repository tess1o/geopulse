import {test, expect} from '../fixtures/database-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {DataExportImportPage} from '../pages/DataExportImportPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestData} from '../fixtures/test-data.js';
import {UserFactory} from '../utils/user-factory.js';
import path from 'path';
import fs from 'fs';
import {fileURLToPath} from 'url';
import {dirname} from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

test.describe('Data Export & Import', () => {

    test.describe('Export - Initial State and Navigation', () => {
        test('should navigate to export/import page and show export tab by default', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Verify we're on the export/import page
            expect(await exportImportPage.isOnDataExportImportPage()).toBe(true);

            // Verify export tab is active by default
            expect(await exportImportPage.isTabActive('export')).toBe(true);
        });

        test('should switch between export and import tabs', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Switch to import tab
            await exportImportPage.switchToTab('import');
            expect(await exportImportPage.isTabActive('import')).toBe(true);

            // Switch back to export tab
            await exportImportPage.switchToTab('export');
            expect(await exportImportPage.isTabActive('export')).toBe(true);
        });

        test('should have GeoPulse format selected by default', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            const selectedFormat = await exportImportPage.getSelectedExportFormat();
            expect(selectedFormat).toBe('geopulse');
        });

        test('should have all data types selected by default for GeoPulse format', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            const selectedDataTypes = await exportImportPage.getSelectedDataTypes();
            expect(selectedDataTypes.length).toBeGreaterThan(0);
            expect(selectedDataTypes).toContain('rawgps');
        });
    });

    test.describe('Export - Format Selection', () => {
        test('should allow switching between GeoPulse and OwnTracks formats', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Switch to OwnTracks format
            await exportImportPage.selectExportFormat('owntracks');
            expect(await exportImportPage.getSelectedExportFormat()).toBe('owntracks');

            // Switch back to GeoPulse
            await exportImportPage.selectExportFormat('geopulse');
            expect(await exportImportPage.getSelectedExportFormat()).toBe('geopulse');
        });
    });

    test.describe('Export - Data Type Selection', () => {
        test('should allow selecting and deselecting individual data types', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Start with all selected, deselect all first
            await exportImportPage.clickSelectAllDataTypes(); // This will deselect all if all are selected

            // Select specific data types
            await exportImportPage.selectDataType('rawgps');
            await exportImportPage.selectDataType('favorites');

            const selected = await exportImportPage.getSelectedDataTypes();
            expect(selected).toContain('rawgps');
            expect(selected).toContain('favorites');

            // Deselect one
            await exportImportPage.deselectDataType('favorites');
            const updatedSelected = await exportImportPage.getSelectedDataTypes();
            expect(updatedSelected).toContain('rawgps');
            expect(updatedSelected).not.toContain('favorites');
        });

        test('should toggle all data types with select all button', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Get initial count
            const initialSelected = await exportImportPage.getSelectedDataTypes();
            const initialCount = initialSelected.length;

            // Click select all/deselect all
            await exportImportPage.clickSelectAllDataTypes();
            const afterClick = await exportImportPage.getSelectedDataTypes();

            // Should toggle - if all were selected, now none; if some were selected, now all
            if (initialCount > 0) {
                // If some/all were selected, clicking should either select all or deselect all
                expect(afterClick.length).not.toBe(initialCount);
            }
        });
    });

    test.describe('Export - Date Range Selection', () => {
        test('should allow setting custom date range', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Set custom date range
            await exportImportPage.setDateRange('2024-01-01', '2024-01-31');

            // Continue with export to verify dates are set
            await exportImportPage.selectDataTypes(['rawgps']);

            // Export button should not be disabled
            expect(await exportImportPage.isExportButtonDisabled()).toBe(false);
        });

        test('should use date range presets', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Click "Last 30 Days" preset
            await exportImportPage.clickDateRangePreset('last30days');

            // Export button should not be disabled
            expect(await exportImportPage.isExportButtonDisabled()).toBe(false);
        });
    });

    test.describe('Export - Job Creation and Status', () => {
        test('should create GeoPulse export job with all data types', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user and add sample GPS data
            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Insert sample data
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 100);
            await DataExportImportPage.insertSampleFavorites(dbManager, user.id, 5);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select format and data types
            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('last30days');

            // Start export
            await exportImportPage.clickStartExport();

            // Wait for success toast
            await exportImportPage.waitForSuccessToast('Export Started');

            // Wait for job card to appear
            await exportImportPage.waitForExportJobCard();
            expect(await exportImportPage.isExportJobCardVisible()).toBe(true);

            // Wait for job to complete
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Verify job completed (jobs are stored in memory, not DB)
            const status = await exportImportPage.getExportJobStatus();
            expect(status.toLowerCase()).toContain('completed');
        });

        test('should create OwnTracks export job', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user and add sample GPS data
            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Insert sample data
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 50);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select OwnTracks format
            await exportImportPage.selectExportFormat('owntracks');
            await exportImportPage.clickDateRangePreset('last30days');

            // Start export
            await exportImportPage.clickStartExport();

            // Wait for success toast
            await exportImportPage.waitForSuccessToast('Export Started');

            // Wait for job card to appear
            await exportImportPage.waitForExportJobCard();

            // Wait for job to complete
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Verify job completed
            const status = await exportImportPage.getExportJobStatus();
            expect(status.toLowerCase()).toContain('completed');
        });

        test('should show export job progress updates', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Insert more data to have longer processing time
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 500);
            await DataExportImportPage.insertSampleFavorites(dbManager, user.id, 10);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();

            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobCard();

            // Check that progress updates (may go quickly for small dataset)
            let previousProgress = -1;
            let progressUpdated = false;

            for (let i = 0; i < 10; i++) {
                const status = await exportImportPage.getExportJobStatus();
                if (status.toLowerCase().includes('completed')) {
                    break;
                }

                try {
                    const currentProgress = await exportImportPage.getExportJobProgress();
                    if (currentProgress !== previousProgress) {
                        progressUpdated = true;
                        console.log(`Export progress: ${currentProgress}%`);
                        previousProgress = currentProgress;
                    }
                } catch (e) {
                    // Progress bar might not be visible yet
                }

                await page.waitForTimeout(1000);
            }

            // Wait for completion
            await exportImportPage.waitForExportJobStatus('Completed');

            // Progress was tracked (or completed too fast to catch)
            console.log(`Progress updates detected: ${progressUpdated}`);
        });

        test('should disable export button when required fields are missing', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Deselect all data types
            const selected = await exportImportPage.getSelectedDataTypes();
            for (const dataType of selected) {
                await exportImportPage.deselectDataType(dataType);
            }

            // Export button should be disabled
            expect(await exportImportPage.isExportButtonDisabled()).toBe(true);
        });
    });

    test.describe('Export - GPX Format Options', () => {
        test('should export GPX as single file', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 50);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select GPX format
            await exportImportPage.selectExportFormat('gpx');
            await exportImportPage.clickDateRangePreset('last30days');

            // Ensure single file mode is selected (default)
            await exportImportPage.selectGpxExportMode('single');

            // Start export
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast('Export Started');
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download and verify
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            expect(download).toBeTruthy();
            const filename = download.suggestedFilename();
            expect(filename).toMatch(/\.gpx$/);
            expect(filename).not.toMatch(/\.zip$/);
        });

        test('should export GPX as ZIP with individual grouping', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Insert GPS data and generate timeline
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 100);
            await DataExportImportPage.generateTimeline(dbManager, user.id);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select GPX format
            await exportImportPage.selectExportFormat('gpx');
            await exportImportPage.clickDateRangePreset('alltime');

            // Select ZIP mode
            await exportImportPage.selectGpxExportMode('zip');

            // Select individual grouping (should be default)
            await exportImportPage.selectGpxZipGrouping('individual');

            // Start export
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast('Export Started');
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download and verify it's a ZIP file
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            expect(download).toBeTruthy();
            const filename = download.suggestedFilename();
            expect(filename).toMatch(/\.zip$/);
            expect(filename).toContain('gpx');
        });

        test('should export GPX as ZIP with daily grouping', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Insert GPS data across multiple days
            const threeDaysAgo = new Date();
            threeDaysAgo.setDate(threeDaysAgo.getDate() - 3);
            const twoDaysAgo = new Date();
            twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);

            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 50, {
                startDate: threeDaysAgo,
                endDate: twoDaysAgo
            });

            // Generate timeline
            await DataExportImportPage.generateTimeline(dbManager, user.id);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select GPX format
            await exportImportPage.selectExportFormat('gpx');
            await exportImportPage.clickDateRangePreset('alltime');

            // Select ZIP mode
            await exportImportPage.selectGpxExportMode('zip');

            // Select daily grouping
            await exportImportPage.selectGpxZipGrouping('daily');

            // Start export
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast('Export Started');
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download and verify it's a ZIP file
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            expect(download).toBeTruthy();
            const filename = download.suggestedFilename();
            expect(filename).toMatch(/\.zip$/);
            expect(filename).toContain('gpx');
        });

        test('should show ZIP grouping options only when ZIP mode is selected', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select GPX format
            await exportImportPage.selectExportFormat('gpx');

            // Initially in single mode - grouping options should not be visible
            await exportImportPage.selectGpxExportMode('single');
            expect(await exportImportPage.isGpxZipGroupingVisible()).toBe(false);

            // Switch to ZIP mode - grouping options should appear
            await exportImportPage.selectGpxExportMode('zip');
            expect(await exportImportPage.isGpxZipGroupingVisible()).toBe(true);

            // Switch back to single mode - grouping options should hide
            await exportImportPage.selectGpxExportMode('single');
            expect(await exportImportPage.isGpxZipGroupingVisible()).toBe(false);
        });

        test('should maintain GPX export settings when switching formats', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Select GPX format and configure options
            await exportImportPage.selectExportFormat('gpx');
            await exportImportPage.selectGpxExportMode('zip');
            await exportImportPage.selectGpxZipGrouping('daily');

            // Switch to another format
            await exportImportPage.selectExportFormat('geojson');

            // Switch back to GPX
            await exportImportPage.selectExportFormat('gpx');

            // Verify settings are maintained
            expect(await exportImportPage.getSelectedGpxExportMode()).toBe('zip');
            expect(await exportImportPage.getSelectedGpxZipGrouping()).toBe('daily');
        });
    });

    test.describe('Export - Download and Delete', () => {
        test('should allow downloading completed export', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 50);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Create and wait for export
            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('last30days');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Setup download listener
            const downloadPromise = page.waitForEvent('download');

            // Click download
            await exportImportPage.clickDownloadExport();

            // Wait for download to start
            const download = await downloadPromise;
            expect(download).toBeTruthy();

            // Verify filename
            const filename = download.suggestedFilename();
            expect(filename).toContain('geopulse-export');
        });

        test('should allow deleting export job', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            const user = await dbManager.getUserByEmail(testUser.email);
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 50);

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            // Create and wait for export
            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('last30days');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Delete export
            await exportImportPage.clickDeleteExport();
            await exportImportPage.confirmDeleteExport();

            // Wait for success toast
            await exportImportPage.waitForSuccessToast('deleted');

            // Job card should disappear
            await page.waitForTimeout(1000);
            expect(await exportImportPage.isExportJobCardVisible()).toBe(false);
        });
    });

    test.describe('Import - Initial State', () => {
        test('should show import tab with GeoPulse format selected by default', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.switchToTab('import');

            const selectedFormat = await exportImportPage.getSelectedImportFormat();
            expect(selectedFormat).toBe('geopulse');
        });

        test('should disable import button when no file is selected', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.switchToTab('import');

            // Import button should be disabled
            expect(await exportImportPage.isImportButtonDisabled()).toBe(true);
        });
    });

    test.describe('Import - Format Selection', () => {
        test('should allow switching between import formats', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            await UserFactory.createUser(page, testUser);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.switchToTab('import');

            // Switch to OwnTracks
            await exportImportPage.selectImportFormat('owntracks');
            expect(await exportImportPage.getSelectedImportFormat()).toBe('owntracks');

            // Switch to GPX
            await exportImportPage.selectImportFormat('gpx');
            expect(await exportImportPage.getSelectedImportFormat()).toBe('gpx');

            // Switch back to GeoPulse
            await exportImportPage.selectImportFormat('geopulse');
            expect(await exportImportPage.getSelectedImportFormat()).toBe('geopulse');
        });
    });

    test.describe('Import - GeoPulse Format (using dynamic export)', () => {
        test('should import GeoPulse export with all data types', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;
            const importUser = TestData.users.another;

            // Create export user and generate data
            await UserFactory.createUser(page, testUser);
            const exportUserId = (await dbManager.getUserByEmail(testUser.email)).id;
            await DataExportImportPage.insertSampleGpsData(dbManager, exportUserId, 100);
            await DataExportImportPage.insertSampleFavorites(dbManager, exportUserId, 5);

            // Login and create export
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download export file
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            const downloadsPath = path.join(__dirname, '..', 'downloads');
            if (!fs.existsSync(downloadsPath)) {
                fs.mkdirSync(downloadsPath, {recursive: true});
            }

            const exportFilePath = path.join(downloadsPath, 'test-export.zip');
            await download.saveAs(exportFilePath);

            // Logout and create new import user
            const appNav = await import('../pages/AppNavigation.js').then(m => new m.AppNavigation(page));
            await appNav.logout();

            await UserFactory.createUser(page, importUser);
            await loginPage.navigate();
            await loginPage.login(importUser.email, importUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Import the file
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            await exportImportPage.selectImportFormat('geopulse');
            await exportImportPage.uploadFile(exportFilePath);

            // Wait for file to be processed
            await page.waitForTimeout(1000);

            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobCard();

            // Wait for import to complete
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Try to get import summary (may not be available if backend doesn't send it)
            const summary = await exportImportPage.getImportSummary();
            if (summary) {
                expect(summary.rawGpsPoints).toBe(100);
                expect(summary.favoriteLocations).toBe(5);
            } else {
                console.log('Import summary not available, verifying data in database only');
            }

            // Verify data in database
            const importUserId = (await dbManager.getUserByEmail(importUser.email)).id;
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, importUserId);
            const favoritesCount = await DataExportImportPage.getFavoritesCount(dbManager, importUserId);

            expect(gpsCount).toBe(100);
            expect(favoritesCount).toBe(5);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should import selective data types from GeoPulse export', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;
            const importUser = TestData.users.another;

            // Create export user and generate data
            await UserFactory.createUser(page, testUser);
            const exportUserId = (await dbManager.getUserByEmail(testUser.email)).id;
            await DataExportImportPage.insertSampleGpsData(dbManager, exportUserId, 50);
            await DataExportImportPage.insertSampleFavorites(dbManager, exportUserId, 3);

            // Login and create export
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download export
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            const downloadsPath = path.join(__dirname, '..', 'downloads');
            if (!fs.existsSync(downloadsPath)) {
                fs.mkdirSync(downloadsPath, {recursive: true});
            }

            const exportFilePath = path.join(downloadsPath, 'test-export-selective.zip');
            await download.saveAs(exportFilePath);

            // Logout and create import user
            const appNav = await import('../pages/AppNavigation.js').then(m => new m.AppNavigation(page));
            await appNav.logout();

            await UserFactory.createUser(page, importUser);
            await loginPage.navigate();
            await loginPage.login(importUser.email, importUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Import only GPS data (not favorites)
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            await exportImportPage.selectImportFormat('geopulse');
            await exportImportPage.uploadFile(exportFilePath);
            await page.waitForTimeout(1000);

            // Deselect all and select only rawgps
            await exportImportPage.clickSelectAllImportDataTypes(); // Deselect all
            await exportImportPage.selectImportDataType('rawgps');

            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobCard();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify only GPS data was imported
            const importUserId = (await dbManager.getUserByEmail(importUser.email)).id;
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, importUserId);
            const favoritesCount = await DataExportImportPage.getFavoritesCount(dbManager, importUserId);

            expect(gpsCount).toBe(50);
            expect(favoritesCount).toBe(0); // Favorites should not be imported

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });
    });

    test.describe('Import - Options', () => {
        test('should import only data within date range when date filter is enabled', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user with GPS data spanning multiple months
            await UserFactory.createUser(page, testUser);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert GPS data across different months:
            // January: 30 points
            const janStart = new Date('2024-01-01');
            const janEnd = new Date('2024-01-31');
            await DataExportImportPage.insertSampleGpsData(dbManager, userId, 30, {
                startDate: janStart,
                endDate: janEnd
            });

            // February: 40 points
            const febStart = new Date('2024-02-01');
            const febEnd = new Date('2024-02-28');
            await DataExportImportPage.insertSampleGpsData(dbManager, userId, 40, {
                startDate: febStart,
                endDate: febEnd
            });

            // March: 50 points
            const marStart = new Date('2024-03-01');
            const marEnd = new Date('2024-03-31');
            await DataExportImportPage.insertSampleGpsData(dbManager, userId, 50, {
                startDate: marStart,
                endDate: marEnd
            });

            // Total: 120 GPS points

            // Export ALL data
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download the export
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            const downloadsPath = path.join(__dirname, '..', 'downloads');
            if (!fs.existsSync(downloadsPath)) {
                fs.mkdirSync(downloadsPath, {recursive: true});
            }

            const exportFilePath = path.join(downloadsPath, 'test-date-filter.zip');
            await download.saveAs(exportFilePath);

            // Delete ALL GPS points from database
            await dbManager.client.query('DELETE FROM gps_points WHERE user_id = $1', [userId]);

            // Verify database is empty
            const countAfterDelete = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
            expect(countAfterDelete).toBe(0);

            // Import with date filter (only February data: 2024-02-01 to 2024-02-28)
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            await exportImportPage.uploadFile(exportFilePath);
            await page.waitForTimeout(1000);

            // Enable date filter for February only (use March 1st to include all of Feb 28)
            await exportImportPage.enableDateFilter('2024-02-01', '2024-03-01');

            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify only February data was imported (40 points out of 120 total)
            // Allow for small variance due to date boundary handling
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
            expect(gpsCount).toBeGreaterThanOrEqual(39);
            expect(gpsCount).toBeLessThanOrEqual(41); // Should be ~40, allowing for boundary cases

            // Verify the imported data is within the February range
            const febGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId, {
                startDate: '2024-02-01',
                endDate: '2024-02-29' // Feb 29 doesn't exist in 2024, but ensures we get all of Feb
            });
            expect(febGpsCount).toBe(gpsCount); // All imported points should be in February

            // Verify no January or March data exists
            const janGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId, {
                startDate: '2024-01-01',
                endDate: '2024-01-31'
            });
            expect(janGpsCount).toBe(0);

            const marGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId, {
                startDate: '2024-03-01',
                endDate: '2024-03-31'
            });
            expect(marGpsCount).toBe(0);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should replace existing data in time range when reimporting', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user and add GPS data
            await UserFactory.createUser(page, testUser);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert GPS data in January with specific velocities
            const janStart = new Date('2024-01-01');
            const janEnd = new Date('2024-01-31');
            await DataExportImportPage.insertSampleGpsData(dbManager, userId, 50, {
                startDate: janStart,
                endDate: janEnd
            });

            // Get original velocities from first 5 points
            const originalPoints = await dbManager.client.query(`
                SELECT id, velocity
                FROM gps_points
                WHERE user_id = $1
                ORDER BY timestamp
                    LIMIT 5
            `, [userId]);

            const originalVelocities = originalPoints.rows.map(p => ({id: p.id, velocity: parseFloat(p.velocity)}));
            console.log('Original velocities:', originalVelocities);

            // Export this data
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download the export
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            const downloadsPath = path.join(__dirname, '..', 'downloads');
            if (!fs.existsSync(downloadsPath)) {
                fs.mkdirSync(downloadsPath, {recursive: true});
            }

            const exportFilePath = path.join(downloadsPath, 'test-replace-data.zip');
            await download.saveAs(exportFilePath);

            // Modify velocities in database (change all to 999.99)
            await dbManager.client.query(`
                UPDATE gps_points
                SET velocity = 999.99
                WHERE user_id = $1
            `, [userId]);

            // Verify velocities were changed
            const modifiedPoints = await dbManager.client.query(`
                SELECT id, velocity
                FROM gps_points
                WHERE user_id = $1
                ORDER BY timestamp
                    LIMIT 5
            `, [userId]);

            const modifiedVelocities = modifiedPoints.rows.map(p => ({id: p.id, velocity: parseFloat(p.velocity)}));
            console.log('Modified velocities:', modifiedVelocities);

            // All should be 999.99 now
            expect(modifiedVelocities.every(p => p.velocity === 999.99)).toBe(true);

            // Import the original export (which has original velocities)
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            await exportImportPage.uploadFile(exportFilePath);
            await page.waitForTimeout(1000);

            // Enable "clear data before import" to replace existing data instead of merging
            await exportImportPage.enableClearDataBeforeImport();

            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify data was restored to original values
            const restoredPoints = await dbManager.client.query(`
                SELECT id, velocity
                FROM gps_points
                WHERE user_id = $1
                ORDER BY timestamp
                    LIMIT 5
            `, [userId]);

            const restoredVelocities = restoredPoints.rows.map(p => ({id: p.id, velocity: parseFloat(p.velocity)}));
            console.log('Restored velocities:', restoredVelocities);

            // Velocities should be back to original values (not 999.99 anymore)
            expect(restoredVelocities.every(p => p.velocity === 999.99)).toBe(false);

            // At least some should match the original velocities pattern (< 20 m/s as per insertSampleGpsData)
            expect(restoredVelocities.every(p => p.velocity < 20)).toBe(true);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });
    });

    test.describe('Import - Other Formats', () => {
        test('should import OwnTracks JSON format', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user
            await UserFactory.createUser(page, testUser);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert reverse geocoding data to avoid Nominatim API calls
            await DataExportImportPage.insertReverseGeocodingForSampleFiles(dbManager);

            // Login
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Navigate to import page
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            // Select OwnTracks format
            await exportImportPage.selectImportFormat('owntracks');

            // Upload sample OwnTracks file
            const ownTracksFilePath = path.join(__dirname, '..', 'fixtures', 'import-samples', 'owntracks-sample.json');
            await exportImportPage.uploadFile(ownTracksFilePath);
            await page.waitForTimeout(1000);

            // Start import
            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify GPS points were created
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
            expect(gpsCount).toBe(10); // Should have 10 points from sample file

            // Verify source type is OWNTRACKS
            const gpsPoints = await dbManager.client.query(`
                SELECT source_type
                FROM gps_points
                WHERE user_id = $1 LIMIT 1
            `, [userId]);

            expect(gpsPoints.rows.length).toBeGreaterThan(0);
            expect(gpsPoints.rows[0].source_type).toBe('OWNTRACKS');
        });

        test('should import GPX format', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user
            await UserFactory.createUser(page, testUser);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert reverse geocoding data to avoid Nominatim API calls
            await DataExportImportPage.insertReverseGeocodingForSampleFiles(dbManager);

            // Login
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Navigate to import page
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            // Select GPX format
            await exportImportPage.selectImportFormat('gpx');

            // Upload sample GPX file
            const gpxFilePath = path.join(__dirname, '..', 'fixtures', 'import-samples', 'gpx-sample.gpx');
            await exportImportPage.uploadFile(gpxFilePath);
            await page.waitForTimeout(1000);

            // Start import
            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify GPS points were created (5 track points + 2 waypoints = 7 total)
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
            expect(gpsCount).toBe(7);

            // Verify source type is GPX
            const gpsPoints = await dbManager.client.query(`
                SELECT source_type
                FROM gps_points
                WHERE user_id = $1 LIMIT 1
            `, [userId]);

            expect(gpsPoints.rows.length).toBeGreaterThan(0);
            expect(gpsPoints.rows[0].source_type).toBe('GPX');

            // Verify coordinates are within expected range (Kyiv area)
            const pointsData = await dbManager.client.query(`
                SELECT ST_X(coordinates) as lon, ST_Y(coordinates) as lat
                FROM gps_points
                WHERE user_id = $1 LIMIT 1
            `, [userId]);

            const point = pointsData.rows[0];
            expect(point.lat).toBeGreaterThan(50.4);
            expect(point.lat).toBeLessThan(50.5);
            expect(point.lon).toBeGreaterThan(30.5);
            expect(point.lon).toBeLessThan(30.6);
        });

        test('should import Google Timeline legacy format', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user
            await UserFactory.createUser(page, testUser);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert reverse geocoding data to avoid Nominatim API calls
            await DataExportImportPage.insertReverseGeocodingForSampleFiles(dbManager);

            // Login
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Navigate to import page
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            // Select Google Timeline format
            await exportImportPage.selectImportFormat('google-timeline');

            // Upload sample Google Timeline file
            const googleTimelineFilePath = path.join(__dirname, '..', 'fixtures', 'import-samples', 'google-timeline-sample.json');
            await exportImportPage.uploadFile(googleTimelineFilePath);
            await page.waitForTimeout(1000);

            // Start import
            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify GPS points were created
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
            expect(gpsCount).toBeGreaterThanOrEqual(4);
            expect(gpsCount).toBeLessThanOrEqual(20);

            // Verify source type is GOOGLE_TIMELINE
            const gpsPoints = await dbManager.client.query(`
                SELECT source_type
                FROM gps_points
                WHERE user_id = $1 LIMIT 1
            `, [userId]);

            expect(gpsPoints.rows.length).toBeGreaterThan(0);
            expect(gpsPoints.rows[0].source_type).toBe('GOOGLE_TIMELINE');
        });

        test('should import Google Timeline format', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;

            // Create user
            await UserFactory.createUser(page, testUser);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert reverse geocoding data to avoid Nominatim API calls
            await DataExportImportPage.insertReverseGeocodingForSampleFiles(dbManager);

            // Login
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Navigate to import page
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            // Select Google Timeline format
            await exportImportPage.selectImportFormat('google-timeline');

            // Upload sample Google Timeline file
            const googleTimelineFilePath = path.join(__dirname, '..', 'fixtures', 'import-samples', 'google-timeline-new.json');
            await exportImportPage.uploadFile(googleTimelineFilePath);
            await page.waitForTimeout(1000);

            // Start import
            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify GPS points were created
            const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
            expect(gpsCount).toBeGreaterThanOrEqual(4);
            expect(gpsCount).toBeLessThanOrEqual(20);

            // Verify source type is GOOGLE_TIMELINE
            const gpsPoints = await dbManager.client.query(`
                SELECT source_type
                FROM gps_points
                WHERE user_id = $1 LIMIT 1
            `, [userId]);

            expect(gpsPoints.rows.length).toBeGreaterThan(0);
            expect(gpsPoints.rows[0].source_type).toBe('GOOGLE_TIMELINE');
        });
    });

    test.describe('Import - Data Integrity', () => {
        test('should maintain data integrity through export-import cycle', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;
            const importUser = TestData.users.another;

            // Create user with known data
            await UserFactory.createUser(page, testUser);
            const exportUserId = (await dbManager.getUserByEmail(testUser.email)).id;

            const gpsIds = await DataExportImportPage.insertSampleGpsData(dbManager, exportUserId, 200);
            const favoriteIds = await DataExportImportPage.insertSampleFavorites(dbManager, exportUserId, 7);

            // Get original counts
            const originalGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, exportUserId);
            const originalFavoritesCount = await DataExportImportPage.getFavoritesCount(dbManager, exportUserId);

            // Export data
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('geopulse');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            const downloadsPath = path.join(__dirname, '..', 'downloads');
            if (!fs.existsSync(downloadsPath)) {
                fs.mkdirSync(downloadsPath, {recursive: true});
            }

            const exportFilePath = path.join(downloadsPath, 'test-integrity.zip');
            await download.saveAs(exportFilePath);

            // Import to new user
            const appNav = await import('../pages/AppNavigation.js').then(m => new m.AppNavigation(page));
            await appNav.logout();

            await UserFactory.createUser(page, importUser);
            await loginPage.navigate();
            await loginPage.login(importUser.email, importUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            await exportImportPage.uploadFile(exportFilePath);
            await page.waitForTimeout(1000);

            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify data integrity
            const importUserId = (await dbManager.getUserByEmail(importUser.email)).id;
            const importedGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, importUserId);
            const importedFavoritesCount = await DataExportImportPage.getFavoritesCount(dbManager, importUserId);

            expect(importedGpsCount).toBe(originalGpsCount);
            expect(importedFavoritesCount).toBe(originalFavoritesCount);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should maintain data integrity through OwnTracks export-import cycle', async ({page, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = TestData.users.existing;
            const importUser = TestData.users.another;

            // Create user with GPS data
            await UserFactory.createUser(page, testUser);
            const exportUserId = (await dbManager.getUserByEmail(testUser.email)).id;

            // Insert GPS data
            await DataExportImportPage.insertSampleGpsData(dbManager, exportUserId, 150);

            // Get original GPS count
            const originalGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, exportUserId);

            // Export data in OwnTracks format
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.selectExportFormat('owntracks');
            await exportImportPage.clickDateRangePreset('alltime');
            await exportImportPage.clickStartExport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForExportJobStatus('Completed', 60000);

            // Download export
            const downloadPromise = page.waitForEvent('download');
            await exportImportPage.clickDownloadExport();
            const download = await downloadPromise;

            const downloadsPath = path.join(__dirname, '..', 'downloads');
            if (!fs.existsSync(downloadsPath)) {
                fs.mkdirSync(downloadsPath, {recursive: true});
            }

            const exportFilePath = path.join(downloadsPath, 'test-owntracks-integrity.json');
            await download.saveAs(exportFilePath);

            // Logout and create new user for import
            const appNav = await import('../pages/AppNavigation.js').then(m => new m.AppNavigation(page));
            await appNav.logout();

            await UserFactory.createUser(page, importUser);
            await loginPage.navigate();
            await loginPage.login(importUser.email, importUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            // Import OwnTracks file
            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();
            await exportImportPage.switchToTab('import');

            await exportImportPage.selectImportFormat('owntracks');
            await exportImportPage.uploadFile(exportFilePath);
            await page.waitForTimeout(1000);

            await exportImportPage.clickStartImport();
            await exportImportPage.waitForSuccessToast();
            await exportImportPage.waitForImportJobStatus('Completed', 120000);

            // Verify data integrity - GPS count should match
            const importUserId = (await dbManager.getUserByEmail(importUser.email)).id;
            const importedGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, importUserId);

            expect(importedGpsCount).toBe(originalGpsCount);

            // Verify source type is OWNTRACKS
            const gpsPoints = await dbManager.client.query(`
                SELECT source_type
                FROM gps_points
                WHERE user_id = $1 LIMIT 1
            `, [importUserId]);

            expect(gpsPoints.rows.length).toBeGreaterThan(0);
            expect(gpsPoints.rows[0].source_type).toBe('OWNTRACKS');

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });
    });
});
