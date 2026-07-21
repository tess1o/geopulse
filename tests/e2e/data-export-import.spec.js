import { test, expect } from '../fixtures/isolated-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {DataExportImportPage} from '../pages/DataExportImportPage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import path from 'path';
import fs from 'fs';
import {fileURLToPath} from 'url';
import {dirname} from 'path';
import {DateFormatValues} from '../utils/date-format-test-helper.js';
import {GeocodingFactory} from '../utils/geocoding-factory.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const hashString = (value) => {
    let hash = 0;
    for (let i = 0; i < value.length; i += 1) {
        hash = ((hash << 5) - hash) + value.charCodeAt(i);
        hash |= 0;
    }
    return Math.abs(hash);
};

const createUniquePoint = (token, salt) => {
    const hash = hashString(`${token}-${salt}`);
    const lon = 20 + ((hash % 1000000) / 100000);
    const lat = 40 + ((Math.floor(hash / 97) % 1000000) / 100000);
    return {
        lon: Number(lon.toFixed(6)),
        lat: Number(lat.toFixed(6)),
        wkt: `POINT(${lon.toFixed(6)} ${lat.toFixed(6)})`
    };
};

const ensureDownloadsPath = () => {
    const downloadsPath = path.join(__dirname, '..', 'downloads');
    if (!fs.existsSync(downloadsPath)) {
        fs.mkdirSync(downloadsPath, {recursive: true});
    }
    return downloadsPath;
};

const crcTable = Array.from({length: 256}, (_, index) => {
    let crc = index;
    for (let bit = 0; bit < 8; bit += 1) {
        crc = (crc & 1) ? (0xedb88320 ^ (crc >>> 1)) : (crc >>> 1);
    }
    return crc >>> 0;
});

const crc32 = (buffer) => {
    let crc = 0xffffffff;
    for (const byte of buffer) {
        crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
};

const createStoredZip = (files) => {
    const localParts = [];
    const centralParts = [];
    let offset = 0;
    const dosTime = 0;
    const dosDate = 33; // 1980-01-01

    for (const [fileName, content] of files) {
        const nameBuffer = Buffer.from(fileName);
        const dataBuffer = Buffer.isBuffer(content) ? content : Buffer.from(content);
        const checksum = crc32(dataBuffer);

        const localHeader = Buffer.alloc(30);
        localHeader.writeUInt32LE(0x04034b50, 0);
        localHeader.writeUInt16LE(20, 4);
        localHeader.writeUInt16LE(0, 6);
        localHeader.writeUInt16LE(0, 8);
        localHeader.writeUInt16LE(dosTime, 10);
        localHeader.writeUInt16LE(dosDate, 12);
        localHeader.writeUInt32LE(checksum, 14);
        localHeader.writeUInt32LE(dataBuffer.length, 18);
        localHeader.writeUInt32LE(dataBuffer.length, 22);
        localHeader.writeUInt16LE(nameBuffer.length, 26);
        localHeader.writeUInt16LE(0, 28);

        localParts.push(localHeader, nameBuffer, dataBuffer);

        const centralHeader = Buffer.alloc(46);
        centralHeader.writeUInt32LE(0x02014b50, 0);
        centralHeader.writeUInt16LE(20, 4);
        centralHeader.writeUInt16LE(20, 6);
        centralHeader.writeUInt16LE(0, 8);
        centralHeader.writeUInt16LE(0, 10);
        centralHeader.writeUInt16LE(dosTime, 12);
        centralHeader.writeUInt16LE(dosDate, 14);
        centralHeader.writeUInt32LE(checksum, 16);
        centralHeader.writeUInt32LE(dataBuffer.length, 20);
        centralHeader.writeUInt32LE(dataBuffer.length, 24);
        centralHeader.writeUInt16LE(nameBuffer.length, 28);
        centralHeader.writeUInt16LE(0, 30);
        centralHeader.writeUInt16LE(0, 32);
        centralHeader.writeUInt16LE(0, 34);
        centralHeader.writeUInt16LE(0, 36);
        centralHeader.writeUInt32LE(0, 38);
        centralHeader.writeUInt32LE(offset, 42);
        centralParts.push(centralHeader, nameBuffer);

        offset += localHeader.length + nameBuffer.length + dataBuffer.length;
    }

    const centralOffset = offset;
    const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0);

    const endRecord = Buffer.alloc(22);
    endRecord.writeUInt32LE(0x06054b50, 0);
    endRecord.writeUInt16LE(0, 4);
    endRecord.writeUInt16LE(0, 6);
    endRecord.writeUInt16LE(files.length, 8);
    endRecord.writeUInt16LE(files.length, 10);
    endRecord.writeUInt32LE(centralSize, 12);
    endRecord.writeUInt32LE(centralOffset, 16);
    endRecord.writeUInt16LE(0, 20);

    return Buffer.concat([...localParts, ...centralParts, endRecord]);
};

const buildFailingGeoPulseZip = (filePath, testIdentity, label) => {
    const token = `${testIdentity.uniqueToken}-${label}`;
    const now = '2024-01-01T00:00:00Z';
    const startDate = '2024-01-01T00:00:00Z';
    const endDate = '2024-01-31T23:59:00Z';
    const startPoint = createUniquePoint(token, 'start');
    const endPoint = createUniquePoint(token, 'end');
    const reverseDisplayName = `E2E Failed Import Geocode ${token}`;
    const favoriteName = `E2E Failed Import Favorite ${token}`;

    const metadata = {
        exportJobId: '00000000-0000-0000-0000-000000000101',
        userId: '00000000-0000-0000-0000-000000000102',
        exportDate: now,
        dataTypes: ['reversegeocodinglocation', 'favorites', 'rawgps', 'userinfo'],
        startDate,
        endDate,
        format: 'geopulse',
        version: '1.0'
    };

    const reverseGeocoding = {
        dataType: 'reverseGeocodingLocation',
        exportDate: now,
        locations: [startPoint, endPoint].map((point, index) => ({
            id: index + 1,
            requestLatitude: point.lat,
            requestLongitude: point.lon,
            resultLatitude: point.lat,
            resultLongitude: point.lon,
            displayName: index === 0 ? reverseDisplayName : `${reverseDisplayName} End`,
            providerName: 'e2e',
            createdAt: now,
            lastAccessedAt: now,
            city: 'Boundary City',
            country: 'Boundary Country',
            boundingBoxNorthEastLatitude: point.lat + 0.001,
            boundingBoxNorthEastLongitude: point.lon + 0.001,
            boundingBoxSouthWestLatitude: point.lat - 0.001,
            boundingBoxSouthWestLongitude: point.lon - 0.001
        }))
    };

    const favorites = {
        dataType: 'favorites',
        exportDate: now,
        points: [{
            id: 1,
            name: favoriteName,
            city: 'Boundary City',
            country: 'Boundary Country',
            latitude: startPoint.lat,
            longitude: startPoint.lon
        }],
        areas: []
    };

    const rawGpsData = {
        dataType: 'rawGps',
        exportDate: now,
        startDate,
        endDate,
        points: [
            {
                id: 1,
                timestamp: startDate,
                latitude: startPoint.lat,
                longitude: startPoint.lon,
                accuracy: 5,
                altitude: 100,
                speed: 1.2,
                source: 'OWNTRACKS',
                deviceId: 'e2e-boundary-import',
                battery: 90
            },
            {
                id: 2,
                timestamp: endDate,
                latitude: endPoint.lat,
                longitude: endPoint.lon,
                accuracy: 5,
                altitude: 101,
                speed: 1.4,
                source: 'OWNTRACKS',
                deviceId: 'e2e-boundary-import',
                battery: 89
            }
        ]
    };

    const files = [
        ['metadata.json', JSON.stringify(metadata)],
        ['reverse-geocoding.json', JSON.stringify(reverseGeocoding)],
        ['favorites.json', JSON.stringify(favorites)],
        ['raw-gps-data.json', JSON.stringify(rawGpsData)],
        ['user-info.json', '{"dataType":"userinfo","user":']
    ];

    fs.writeFileSync(filePath, createStoredZip(files));

    return {
        filePath,
        reverseDisplayName,
        favoriteName,
        startDate,
        endDate
    };
};

const insertReverseGeocodingTimelineStayForExport = async (dbManager, userId, testIdentity, label) => {
    const token = `${testIdentity.uniqueToken}-${label}`;
    const point = createUniquePoint(token, 'timeline-stay');
    const displayName = `E2E Exported Reverse Geocode ${token}`;
    const geocodingId = await GeocodingFactory.insertOrGetGeocodingLocation(
        dbManager,
        point.wkt,
        displayName,
        'Boundary City',
        'Boundary Country'
    );

    await dbManager.client.query(`
        INSERT INTO timeline_stays (user_id, timestamp, stay_duration, location, location_name, geocoding_id, created_at, last_updated)
        VALUES ($1, $2, 3600, ST_GeomFromText($3, 4326), $4, $5, NOW(), NOW())
    `, [userId, '2024-01-15T12:00:00Z', point.wkt, displayName, geocodingId]);

    return {displayName, geocodingId};
};

test.describe('Data Export & Import', () => {

    test.describe('Export - Initial State and Navigation', () => {
        test('should navigate to export/import page and show export tab by default', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should switch between export and import tabs', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should have GeoPulse format selected by default', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            const selectedFormat = await exportImportPage.getSelectedExportFormat();
            expect(selectedFormat).toBe('geopulse');
        });

        test('should have all data types selected by default for GeoPulse format', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should allow switching between GeoPulse and OwnTracks formats', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should allow selecting and deselecting individual data types', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should toggle all data types with select all button', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should allow setting custom date range', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should display export date range inputs using user date format', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
            const user = await dbManager.getUserByEmail(testUser.email);
            await dbManager.client.query(
                'UPDATE users SET date_format = $1 WHERE id = $2',
                [DateFormatValues.DMY, user.id]
            );

            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.setDateRange('2025-09-21', '2025-09-24');

            const startValue = await page.locator(exportImportPage.selectors.export.startDate).inputValue();
            const endValue = await page.locator(exportImportPage.selectors.export.endDate).inputValue();

            expect(startValue).toMatch(/21\/09\/(?:20)?25/);
            expect(startValue).not.toMatch(/09\/21\/(?:20)?25/);
            expect(endValue).toMatch(/24\/09\/(?:20)?25/);
            expect(endValue).not.toMatch(/09\/24\/(?:20)?25/);
        });

        test('should use date range presets', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should create GeoPulse export job with all data types', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and add sample GPS data
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

        test('should create OwnTracks export job', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and add sample GPS data
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

        test('should show export job progress updates', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
                        previousProgress = currentProgress;
                    }
                } catch (e) {
                    // Progress bar might not be visible yet
                }

                await page.waitForTimeout(1000);
            }

            // Wait for completion
            await exportImportPage.waitForExportJobStatus('Completed');
        });

        test('should disable export button when required fields are missing', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should export GPX as single file', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should export GPX as ZIP with individual grouping', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
            const user = await dbManager.getUserByEmail(testUser.email);

            // Insert GPS data and generate timeline
            await DataExportImportPage.insertSampleGpsData(dbManager, user.id, 100);

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

        test('should export GPX as ZIP with daily grouping', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should show ZIP grouping options only when ZIP mode is selected', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should maintain GPX export settings when switching formats', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should allow downloading completed export', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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

        test('should allow deleting export job', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should show import tab with GeoPulse format selected by default', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
            await loginPage.navigate();
            await loginPage.login(testUser.email, testUser.password);
            await TestHelpers.waitForNavigation(page, '**/app/timeline');

            await exportImportPage.navigate();
            await exportImportPage.waitForPageLoad();

            await exportImportPage.switchToTab('import');

            const selectedFormat = await exportImportPage.getSelectedImportFormat();
            expect(selectedFormat).toBe('geopulse');
        });

        test('should disable import button when no file is selected', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should allow switching between import formats', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
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
        test('should import GeoPulse export with all data types', async ({ page, isolatedUsers, dbManager, testIdentity}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create export user and generate data
            const exportUserId = (await dbManager.getUserByEmail(testUser.email)).id;
            await DataExportImportPage.insertSampleGpsData(dbManager, exportUserId, 100);
            await DataExportImportPage.insertSampleFavorites(dbManager, exportUserId, 5);
            const exportedGeocoding = await insertReverseGeocodingTimelineStayForExport(
                dbManager,
                exportUserId,
                testIdentity,
                'all-data'
            );

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

            const importUser = await isolatedUsers.create(page);
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
            const reverseGeocodingCount = await DataExportImportPage.getReverseGeocodingLocationCount(
                dbManager,
                importUserId,
                exportedGeocoding.displayName
            );

            expect(gpsCount).toBe(100);
            expect(favoritesCount).toBe(5);
            expect(reverseGeocodingCount).toBe(1);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should import selective data types from GeoPulse export', async ({ page, isolatedUsers, dbManager, testIdentity}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create export user and generate data
            const exportUserId = (await dbManager.getUserByEmail(testUser.email)).id;
            await DataExportImportPage.insertSampleGpsData(dbManager, exportUserId, 50);
            await DataExportImportPage.insertSampleFavorites(dbManager, exportUserId, 3);
            const exportedGeocoding = await insertReverseGeocodingTimelineStayForExport(
                dbManager,
                exportUserId,
                testIdentity,
                'selective'
            );

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

            const importUser = await isolatedUsers.create(page);
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
            const reverseGeocodingCount = await DataExportImportPage.getReverseGeocodingLocationCount(
                dbManager,
                importUserId,
                exportedGeocoding.displayName
            );

            expect(gpsCount).toBe(50);
            expect(favoritesCount).toBe(0); // Favorites should not be imported
            expect(reverseGeocodingCount).toBe(0); // Reverse geocoding was not selected for import

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });
    });

    test.describe('Import - Options', () => {
        test('should import only data within date range when date filter is enabled', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user with GPS data spanning multiple months
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

            // Verify the imported data is within the date range used for import
            // Import filter was '2024-02-01' to '2024-03-01', so verify same range
            const febGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId, {
                startDate: '2024-02-01',
                endDate: '2024-03-01' // Match the import filter range
            });
            expect(febGpsCount).toBe(gpsCount); // All imported points should be within the filter range

            // Verify no January data exists (before filter start)
            const janGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId, {
                startDate: '2024-01-01',
                endDate: '2024-01-31'
            });
            expect(janGpsCount).toBe(0);

            // Verify no data after March 1st exists (after filter end)
            // Note: March 1st itself may have data due to filter being '2024-03-01' (inclusive)
            const marGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId, {
                startDate: '2024-03-02',  // Start from March 2 to exclude March 1
                endDate: '2024-03-31'
            });
            expect(marGpsCount).toBe(0);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should replace existing data in time range when reimporting', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user and add GPS data
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

            // Velocities should be back to original values (not 999.99 anymore)
            expect(restoredVelocities.every(p => p.velocity === 999.99)).toBe(false);

            // At least some should match the original velocities pattern (< 20 m/s as per insertSampleGpsData)
            expect(restoredVelocities.every(p => p.velocity < 20)).toBe(true);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should preserve reverse geocoding and roll back imported data when GeoPulse import fails later', async ({ page, isolatedUsers, dbManager, testIdentity}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            const fixturePath = path.join(
                ensureDownloadsPath(),
                `failed-geopulse-import-${testIdentity.uniqueToken}.zip`
            );
            const fixture = buildFailingGeoPulseZip(fixturePath, testIdentity, 'rollback-imported-data');

            try {
                await loginPage.navigate();
                await loginPage.login(testUser.email, testUser.password);
                await TestHelpers.waitForNavigation(page, '**/app/timeline');

                await exportImportPage.navigate();
                await exportImportPage.waitForPageLoad();
                await exportImportPage.switchToTab('import');

                await exportImportPage.selectImportFormat('geopulse');
                await exportImportPage.uploadFile(fixture.filePath);

                await exportImportPage.clickStartImport();
                await exportImportPage.waitForSuccessToast();
                await exportImportPage.waitForImportJobStatus('Failed', 120000);

                const reverseGeocodingCount = await DataExportImportPage.getReverseGeocodingLocationCount(
                    dbManager,
                    userId,
                    fixture.reverseDisplayName
                );
                const gpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
                const favoritesCount = await DataExportImportPage.getFavoritesCount(dbManager, userId);

                expect(reverseGeocodingCount).toBe(1);
                expect(gpsCount).toBe(0);
                expect(favoritesCount).toBe(0);
            } finally {
                if (fs.existsSync(fixture.filePath)) {
                    fs.unlinkSync(fixture.filePath);
                }
            }
        });

        test('should keep existing GPS data when clear-before-import GeoPulse import fails later', async ({ page, isolatedUsers, dbManager, testIdentity}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);
            const userId = (await dbManager.getUserByEmail(testUser.email)).id;

            const fixturePath = path.join(
                ensureDownloadsPath(),
                `failed-clear-geopulse-import-${testIdentity.uniqueToken}.zip`
            );
            const fixture = buildFailingGeoPulseZip(fixturePath, testIdentity, 'rollback-clear');

            await DataExportImportPage.insertSampleGpsData(dbManager, userId, 5, {
                startDate: new Date(fixture.startDate),
                endDate: new Date(fixture.endDate)
            });
            const originalGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);

            try {
                await loginPage.navigate();
                await loginPage.login(testUser.email, testUser.password);
                await TestHelpers.waitForNavigation(page, '**/app/timeline');

                await exportImportPage.navigate();
                await exportImportPage.waitForPageLoad();
                await exportImportPage.switchToTab('import');

                await exportImportPage.selectImportFormat('geopulse');
                await exportImportPage.uploadFile(fixture.filePath);
                await exportImportPage.enableClearDataBeforeImport();

                await exportImportPage.clickStartImport();
                await exportImportPage.waitForSuccessToast();
                await exportImportPage.waitForImportJobStatus('Failed', 120000);

                const finalGpsCount = await DataExportImportPage.getRawGpsPointsCount(dbManager, userId);
                const reverseGeocodingCount = await DataExportImportPage.getReverseGeocodingLocationCount(
                    dbManager,
                    userId,
                    fixture.reverseDisplayName
                );
                const favoritesCount = await DataExportImportPage.getFavoritesCount(dbManager, userId);

                expect(originalGpsCount).toBe(5);
                expect(finalGpsCount).toBe(originalGpsCount);
                expect(reverseGeocodingCount).toBe(1);
                expect(favoritesCount).toBe(0);
            } finally {
                if (fs.existsSync(fixture.filePath)) {
                    fs.unlinkSync(fixture.filePath);
                }
            }
        });
    });

    test.describe('Import - Other Formats', () => {
        test('should import OwnTracks JSON format', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user
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

        test('should import GPX format', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user
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

        test('should import Google Timeline legacy format', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user
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

        test('should import Google Timeline format', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user
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
        test('should maintain data integrity through export-import cycle', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user with known data
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

            const importUser = await isolatedUsers.create(page);
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
            await expect.poll(
                () => DataExportImportPage.getRawGpsPointsCount(dbManager, importUserId),
                { timeout: 30000 }
            ).toBe(originalGpsCount);
            await expect.poll(
                () => DataExportImportPage.getFavoritesCount(dbManager, importUserId),
                { timeout: 30000 }
            ).toBe(originalFavoritesCount);

            // Cleanup
            if (fs.existsSync(exportFilePath)) {
                fs.unlinkSync(exportFilePath);
            }
        });

        test('should maintain data integrity through OwnTracks export-import cycle', async ({ page, isolatedUsers, dbManager}) => {
            const loginPage = new LoginPage(page);
            const exportImportPage = new DataExportImportPage(page);
            const testUser = await isolatedUsers.create(page);

            // Create user with GPS data
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

            const importUser = await isolatedUsers.create(page);
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
            await expect.poll(
                () => DataExportImportPage.getRawGpsPointsCount(dbManager, importUserId),
                { timeout: 30000 }
            ).toBe(originalGpsCount);

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
