import {test, expect} from '../fixtures/database-fixture.js';
import {TestSetupHelper} from '../utils/test-setup-helper.js';
import {TestData} from '../fixtures/test-data.js';

test.describe('Period Tags Management Page', () => {

  test.describe('Page Load and Initial State', () => {
    test('should display period tags management page correctly', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Verify we're on the period tags page
      expect(await periodTagsPage.isOnPeriodTagsPage()).toBe(true);

      // Verify create button is visible
      const createButton = page.locator(periodTagsPage.selectors.createButton);
      expect(await createButton.isVisible()).toBe(true);

      // Verify table is empty initially
      expect(await periodTagsPage.isTableEmpty()).toBe(true);
    });

    test('should display existing period tags on page load', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create test period tags
      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 3, 'manual');

      // Reload page to see tags
      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Verify tags appear in table
      const rowCount = await periodTagsPage.getTableRowCount();
      expect(rowCount).toBe(3);
    });

    test('should display active tag banner when active tag exists', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create an active tag
      await TestSetupHelper.createActivePeriodTag(dbManager, user.id, {
        tagName: 'My Active Trip',
        source: 'owntracks'
      });

      // Reload page
      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Wait for data to load
      await page.waitForTimeout(2000);

      // Verify active tag banner is visible
      expect(await periodTagsPage.isActiveTagBannerVisible()).toBe(true);

      // Verify active tag name is displayed
      const activeTagName = await periodTagsPage.getActiveTagName();
      expect(activeTagName).toContain('My Active Trip');
    });
  });

  test.describe('Create Period Tag', () => {
    test('should create a new period tag with start and end date', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const initialCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);

      const startDate = new Date('2024-06-01');
      const endDate = new Date('2024-06-07');

      // Create period tag
      await periodTagsPage.createPeriodTagWorkflow('Summer Vacation', startDate, endDate);

      // Verify database
      const finalCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);
      expect(finalCount).toBe(initialCount + 1);

      // Verify tag appears in table
      const rowCount = await periodTagsPage.getTableRowCount();
      expect(rowCount).toBe(1);

      // Verify tag data
      const rowData = await periodTagsPage.getTableRowData(0);
      expect(rowData.name).toContain('Summer Vacation');
      expect(rowData.source).toContain('Manual');
    });

    test('should validate that date range is required', async ({page, dbManager}) => {
      const {periodTagsPage} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      await periodTagsPage.clickCreateButton();
      await periodTagsPage.waitForCreateDialog();

      // Fill only tag name, no dates
      const nameInput = page.locator(periodTagsPage.selectors.tagNameInput).first();
      await nameInput.fill('Incomplete Tag');

      // Try to submit without dates
      await periodTagsPage.submitCreateDialog();

      await page.waitForTimeout(500);

      // Dialog should still be visible (validation failed)
      const dialog = page.locator(periodTagsPage.selectors.createDialog);
      expect(await dialog.isVisible()).toBe(true);
    });

    test('should cancel create period tag dialog', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const initialCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);

      await periodTagsPage.clickCreateButton();
      await periodTagsPage.waitForCreateDialog();
      await periodTagsPage.cancelDialog();

      await page.waitForTimeout(500);

      // Verify no tag was created
      const finalCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);
      expect(finalCount).toBe(initialCount);
    });

    test('should validate tag name is required', async ({page, dbManager}) => {
      const {periodTagsPage} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      await periodTagsPage.clickCreateButton();
      await periodTagsPage.waitForCreateDialog();

      // Fill dates but not tag name
      const startDate = new Date('2024-06-01');
      const endDate = new Date('2024-06-07');
      await periodTagsPage.fillDateRange(startDate, endDate);

      // Try to submit without tag name
      await periodTagsPage.submitCreateDialog();

      await page.waitForTimeout(500);

      // Dialog should still be visible (validation failed)
      const dialog = page.locator(periodTagsPage.selectors.createDialog);
      expect(await dialog.isVisible()).toBe(true);
    });
  });

  test.describe('Edit Period Tag', () => {
    test('should edit manual period tag name', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create a manual tag
      const tagId = await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Original Name',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Edit the tag
      await periodTagsPage.editPeriodTagWorkflow(0, 'Updated Name');

      // Verify database
      const tag = await TestSetupHelper.getPeriodTagById(dbManager, tagId);
      expect(tag.tag_name).toBe('Updated Name');

      // Verify UI
      const rowData = await periodTagsPage.getTableRowData(0);
      expect(rowData.name).toContain('Updated Name');
    });

    test('should edit period tag dates', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const tagId = await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Test Tag',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      const newStartDate = new Date('2024-06-10');
      const newEndDate = new Date('2024-06-17');

      await periodTagsPage.clickEditInTable(0);
      await periodTagsPage.fillEditDialog('Test Tag', newStartDate, newEndDate);
      await periodTagsPage.submitEditDialog();
      await periodTagsPage.waitForSuccessToast();

      await page.waitForTimeout(1000);

      // Verify database
      const tag = await TestSetupHelper.getPeriodTagById(dbManager, tagId);
      const startDate = new Date(tag.start_time);
      const endDate = new Date(tag.end_time);

      expect(startDate.getDate()).toBe(10);
      expect(endDate.getDate()).toBe(17);
    });

    test('should not allow editing active OwnTracks tag', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create an active OwnTracks tag
      await TestSetupHelper.createActivePeriodTag(dbManager, user.id, {
        tagName: 'Active OwnTracks Tag',
        source: 'owntracks'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Verify edit button is disabled
      const isDisabled = await periodTagsPage.isEditButtonDisabled(0);
      expect(isDisabled).toBe(true);
    });

    test('should allow editing completed OwnTracks tag', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create a completed OwnTracks tag
      const tagId = await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Completed OwnTracks Tag',
        startTime: new Date('2024-05-01'),
        endTime: new Date('2024-05-07'),
        source: 'owntracks'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Edit should be allowed
      await periodTagsPage.editPeriodTagWorkflow(0, 'Updated OwnTracks Tag');

      // Verify database
      const tag = await TestSetupHelper.getPeriodTagById(dbManager, tagId);
      expect(tag.tag_name).toBe('Updated OwnTracks Tag');
    });
  });

  test.describe('Delete Period Tag', () => {
    test('should delete manual period tag', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const tagId = await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Tag to Delete',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);

      // Delete the tag
      await periodTagsPage.deletePeriodTagWorkflow(0);

      // Verify database
      const finalCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);
      expect(finalCount).toBe(initialCount - 1);

      const tag = await TestSetupHelper.getPeriodTagById(dbManager, tagId);
      expect(tag).toBeNull();

      // Verify table is empty
      expect(await periodTagsPage.isTableEmpty()).toBe(true);
    });

    test('should cancel delete confirmation', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Do Not Delete',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);

      // Try to delete but cancel
      await periodTagsPage.clickDeleteInTable(0);
      await periodTagsPage.rejectDialog();

      // Verify not deleted
      const finalCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);
      expect(finalCount).toBe(initialCount);
    });

    test('should not allow deleting active OwnTracks tag', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create an active OwnTracks tag
      await TestSetupHelper.createActivePeriodTag(dbManager, user.id, {
        tagName: 'Active OwnTracks Tag',
        source: 'owntracks'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Verify delete button is disabled
      const isDisabled = await periodTagsPage.isDeleteButtonDisabled(0);
      expect(isDisabled).toBe(true);
    });
  });

  test.describe('Bulk Delete', () => {
    test('should bulk delete multiple manual tags', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create multiple manual tags
      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 3, 'manual');

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);

      // Select first two tags
      await periodTagsPage.selectTableRow(0);
      await periodTagsPage.selectTableRow(1);

      // Verify bulk delete button appears with correct count
      expect(await periodTagsPage.isBulkDeleteButtonVisible()).toBe(true);
      expect(await periodTagsPage.getBulkDeleteCount()).toBe(2);

      // Bulk delete
      await periodTagsPage.clickBulkDelete();
      await periodTagsPage.confirmDialog();
      await periodTagsPage.waitForSuccessToast();

      await page.waitForTimeout(1000);

      // Verify database
      const finalCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);
      expect(finalCount).toBe(initialCount - 2);

      // Verify only 1 tag remains in table
      const rowCount = await periodTagsPage.getTableRowCount();
      expect(rowCount).toBe(1);
    });

    test('should prevent bulk delete when active OwnTracks tag is selected', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create a manual tag and an active OwnTracks tag
      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Manual Tag',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await TestSetupHelper.createActivePeriodTag(dbManager, user.id, {
        tagName: 'Active OwnTracks',
        source: 'owntracks'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      const initialCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);

      // Try to select both
      await periodTagsPage.selectTableRow(0);
      await periodTagsPage.selectTableRow(1);

      // Click bulk delete
      await periodTagsPage.clickBulkDelete();

      // Should show warning toast
      await periodTagsPage.waitForWarnToast();

      await page.waitForTimeout(1000);

      // Verify nothing was deleted
      const finalCount = await TestSetupHelper.countPeriodTags(dbManager, user.id);
      expect(finalCount).toBe(initialCount);
    });
  });

  test.describe('Search and Filters', () => {
    test('should filter by tag name search', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create tags with different names
      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Summer Vacation',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Winter Trip',
        startTime: new Date('2024-12-01'),
        endTime: new Date('2024-12-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Search for "Summer"
      await periodTagsPage.fillSearchInput('Summer');

      // Verify only matching tag is shown
      const rowCount = await periodTagsPage.getTableRowCount();
      expect(rowCount).toBe(1);

      const rowData = await periodTagsPage.getTableRowData(0);
      expect(rowData.name).toContain('Summer');
    });

    test('should filter by source', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create tags with different sources
      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 2, 'manual');
      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 1, 'owntracks');

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Filter by manual
      await periodTagsPage.selectSourceFilter('manual');

      const manualCount = await periodTagsPage.getTableRowCount();
      expect(manualCount).toBe(2);

      // Filter by OwnTracks
      await periodTagsPage.selectSourceFilter('owntracks');

      const ownTracksCount = await periodTagsPage.getTableRowCount();
      expect(ownTracksCount).toBe(1);

      // Show all
      await periodTagsPage.selectSourceFilter(null);

      const allCount = await periodTagsPage.getTableRowCount();
      expect(allCount).toBe(3);
    });

    test('should combine search and source filters', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create tags
      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Manual Summer Trip',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'OwnTracks Summer Adventure',
        startTime: new Date('2024-06-10'),
        endTime: new Date('2024-06-17'),
        source: 'owntracks'
      });

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Manual Winter Trip',
        startTime: new Date('2024-12-01'),
        endTime: new Date('2024-12-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Apply both filters
      await periodTagsPage.fillSearchInput('Summer');
      await periodTagsPage.selectSourceFilter('manual');

      // Should only show Manual Summer Trip
      const rowCount = await periodTagsPage.getTableRowCount();
      expect(rowCount).toBe(1);

      const rowData = await periodTagsPage.getTableRowData(0);
      expect(rowData.name).toContain('Manual Summer Trip');
    });

    test('should clear search filter', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 3, 'manual');

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Apply search
      await periodTagsPage.fillSearchInput('Test Tag 1');
      expect(await periodTagsPage.getTableRowCount()).toBe(1);

      // Clear search
      await periodTagsPage.fillSearchInput('');
      await page.waitForTimeout(500);

      // All tags should be visible again
      expect(await periodTagsPage.getTableRowCount()).toBe(3);
    });
  });

  test.describe('View Timeline', () => {
    test('should navigate to timeline with period tag dates', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const startDate = new Date('2024-06-01');
      const endDate = new Date('2024-06-07');

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Test Tag',
        startTime: startDate,
        endTime: endDate,
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Click view timeline
      await periodTagsPage.clickViewTimeline(0);

      // Verify navigation to timeline with correct date range
      await page.waitForURL('**/app/timeline**', { timeout: 5000 });
      const url = page.url();

      expect(url).toContain('start=06/01/2024');
      expect(url).toContain('end=06/07/2024');
    });

    test('should navigate to timeline for active tag with current date as end', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const startDate = new Date('2024-06-01');

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Active Tag',
        startTime: startDate,
        endTime: null,
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Click view timeline
      await periodTagsPage.clickViewTimeline(0);

      // Verify navigation to timeline
      await page.waitForURL('**/app/timeline**', { timeout: 5000 });
      const url = page.url();

      expect(url).toContain('start=06/01/2024');
      // End date should be current date (not checking exact value)
      expect(url).toContain('end=');
    });
  });

  test.describe('Responsive Behavior', () => {
    test('should display mobile cards on small viewport', async ({page, dbManager}) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });

      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 2, 'manual');

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Verify mobile cards are displayed
      const cardCount = await periodTagsPage.getMobileCardCount();
      expect(cardCount).toBe(2);

      // Verify desktop table is hidden
      const desktopTable = page.locator(periodTagsPage.selectors.dataTable);
      expect(await desktopTable.isVisible()).toBe(false);
    });

    test('should support edit and delete from mobile cards', async ({page, dbManager}) => {
      await page.setViewportSize({ width: 375, height: 667 });

      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      const tagId = await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Mobile Tag',
        startTime: new Date('2024-06-01'),
        endTime: new Date('2024-06-07'),
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Edit via mobile card
      await periodTagsPage.clickEditInMobileCard(0);
      await periodTagsPage.fillEditDialog('Updated Mobile Tag');
      await periodTagsPage.submitEditDialog();
      await periodTagsPage.waitForSuccessToast();

      await page.waitForTimeout(1000);

      // Verify database
      const tag = await TestSetupHelper.getPeriodTagById(dbManager, tagId);
      expect(tag.tag_name).toBe('Updated Mobile Tag');
    });
  });

  test.describe('Empty State', () => {

    test('should show empty state when filters return no results', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create only manual tags
      await TestSetupHelper.createMultiplePeriodTags(dbManager, user.id, 2, 'manual');

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Filter by OwnTracks (should return no results)
      await periodTagsPage.selectSourceFilter('owntracks');

      // Verify empty state
      expect(await periodTagsPage.isTableEmpty()).toBe(true);
    });
  });

  test.describe('Data Validation', () => {
    test('should show error when end date is before start date', async ({page, dbManager}) => {
      const {periodTagsPage} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      await periodTagsPage.clickCreateButton();
      await periodTagsPage.waitForCreateDialog();

      const startDate = new Date('2024-06-07');
      const endDate = new Date('2024-06-01'); // Before start date

      await periodTagsPage.fillCreateDialog('Invalid Tag', startDate, endDate);
      await periodTagsPage.submitCreateDialog();

      await page.waitForTimeout(500);

      // Should show error or dialog should remain open
      const dialog = page.locator(periodTagsPage.selectors.createDialog);
      const isVisible = await dialog.isVisible();
      expect(isVisible).toBe(true); // Dialog still open due to validation error
    });
  });

  test.describe('Page Subtitle', () => {
    test('should display correct subtitle with tag count and days', async ({page, dbManager}) => {
      const {periodTagsPage, user} = await TestSetupHelper.loginAndNavigateToPeriodTagsPage(page, dbManager);

      // Create tags with known durations
      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Tag 1',
        startTime: new Date('2024-06-01T00:00:00Z'),
        endTime: new Date('2024-06-08T00:00:00Z'), // 7 days
        source: 'manual'
      });

      await TestSetupHelper.createPeriodTag(dbManager, user.id, {
        tagName: 'Tag 2',
        startTime: new Date('2024-07-01T00:00:00Z'),
        endTime: new Date('2024-07-04T00:00:00Z'), // 3 days
        source: 'manual'
      });

      await page.reload();
      await periodTagsPage.waitForPageLoad();

      // Check subtitle
      const subtitle = page.locator('.page-container').locator('p').first();
      const text = await subtitle.textContent();

      expect(text).toContain('2 periods');
      expect(text).toContain('days tagged');
    });
  });
});
