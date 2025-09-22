Current Problems Analysis

1. Date picker conversion - Browser timezone dates incorrectly converted to user timezone
2. Timeline grouping - Items appearing on wrong dates due to timezone mismatches
3. Overnight detection - Complex, buggy custom logic with 24-hour offset errors
4. Duration calculations - "On this day" showing wrong start times
5. Code duplication - Timezone logic scattered across multiple files
6. Edge cases - DST transitions, leap years not handled properly

Day.js Architecture Plan

Phase 1: Foundation Setup

Install & Configure Day.js
npm install dayjs

Required plugins:
- timezone - Core timezone functionality
- utc - UTC date handling for API calls
- duration - Time duration calculations
- customParseFormat - Flexible date parsing
- isSameOrBefore/isSameOrAfter - Date comparisons

Phase 2: Centralized Date Helpers

Create comprehensive dateHelpers.js with Day.js:

Core Functions:

1. createDateInTimezone(dateStr, timezone) - Create calendar date in specific timezone
2. getStartOfDayInTimezone(date, timezone) - Midnight start in timezone
3. getEndOfDayInTimezone(date, timezone) - End of day in timezone
4. formatDateInTimezone(date, timezone, format) - Consistent date formatting
5. isSameDayInTimezone(date1, date2, timezone) - Same calendar day check

Date Picker Functions:

6. convertPickerDateToUserTimezone(pickerDate, userTimezone) - Fix browser→user timezone
7. createDateRangeInTimezone(startDate, endDate, timezone) - Proper range boundaries

Timeline Functions:

8. isOvernightInTimezone(startTime, duration, timezone) - Reliable overnight detection
9. shouldItemAppearOnDate(item, dateKey, timezone) - Timeline grouping logic
10. calculateDayDurationInTimezone(item, dateStr, timezone) - "On this day" calculations
11. formatOnThisDayDuration(item, dateStr, itemType, timezone) - Duration formatting

Utility Functions:

12. daysDifferenceInTimezone(date1, date2, timezone) - Days between dates
13. formatTimeInTimezone(date, timezone) - Time formatting
14. getCurrentDateInTimezone(timezone) - Current date in timezone

Phase 3: Component Updates

AppNavbarWithDatePicker.vue

- Replace convertCalendarDateToUserTimezone() with Day.js equivalent
- Use createDateRangeInTimezone() for proper UTC boundaries
- Eliminate complex manual timezone calculations

TimelineContainer.vue

- Use formatDateInTimezone() for consistent date keys
- Replace shouldItemAppearOnDate() with Day.js-based version
- Use isSameDayInTimezone() for date comparisons

overnightHelpers.js → DEPRECATED

- Move all functions to centralized dateHelpers.js
- Use isOvernightInTimezone() for overnight detection
- Use Day.js for all timezone boundary calculations

Overnight*Card.vue Components

- Remove duplicate formatContinuationText() functions
- Use centralized formatOnThisDayDuration()
- Import all date logic from dateHelpers.js

Phase 4: Implementation Strategy

Step 1: Core Infrastructure

1. Install Day.js with plugins
2. Configure timezone data loading
3. Create basic dateHelpers.js structure
4. Implement core timezone boundary functions

Step 2: Date Picker Fix

1. Implement convertPickerDateToUserTimezone()
2. Update AppNavbarWithDatePicker.vue
3. Test date selection across timezones
4. Verify API calls receive correct UTC ranges

Step 3: Timeline Logic Overhaul

1. Implement timeline-specific helper functions
2. Update TimelineContainer.vue grouping logic
3. Replace overnight detection across all components
4. Update duration calculation functions

Step 4: Component Cleanup

1. Remove old overnightHelpers.js functions
2. Update all component imports
3. Eliminate duplicated timezone logic
4. Centralize all date operations

Step 5: Comprehensive Testing

1. Test date picker with browser≠user timezone
2. Verify timeline grouping accuracy
3. Test overnight detection edge cases
4. Validate "on this day" duration calculations
5. Test DST transition handling

Phase 5: Benefits & Validation

Code Quality Improvements:

- Reduce complexity: ~200 lines of custom timezone logic → ~50 lines with Day.js
- Eliminate bugs: No more 24-hour offset errors or complex iterations
- Centralize logic: All date operations in one maintainable location
- Handle edge cases: DST, leap years, timezone changes automatically

Performance Impact:

- Bundle size: +4KB (Day.js core + timezone plugin)
- Runtime performance: Faster than custom implementations
- Memory usage: Efficient timezone data caching

Reliability Improvements:

- Battle-tested library: Used by millions of applications
- Consistent API: Predictable behavior across all date operations
- Proper timezone handling: Accurate timezone conversion and DST support
- Error handling: Graceful handling of invalid dates and edge cases

Phase 1: Setup (Day 1)
1. Install Day.js + plugins (timezone, utc, duration, customParseFormat)
2. Configure Day.js with timezone data
3. Create dateHelpers.js structure with Day.js imports

Phase 2: Core Date Functions (Day 1-2)
1. Implement timezone boundary functions (getStartOfDayInTimezone, getEndOfDayInTimezone)
2. Create date picker conversion functions (convertPickerDateToUserTimezone, createDateRangeInTimezone)
3. Build timeline helper functions (isOvernightInTimezone, shouldItemAppearOnDate, formatOnThisDayDuration)
4. Add utility functions (isSameDayInTimezone, formatDateInTimezone, daysDifferenceInTimezone)

Phase 3: Fix Date Picker (Day 2)
1. Update AppNavbarWithDatePicker.vue to use new helper functions
2. Replace manual timezone conversion with Day.js-based convertPickerDateToUserTimezone()
3. Test date selection: 08/18/2025 NY timezone → correct UTC API calls

Phase 4: Timeline System Overhaul (Day 2-3)
1. Update TimelineContainer.vue with Day.js-based grouping logic
2. Replace overnightHelpers.js functions with centralized dateHelpers.js equivalents
3. Update all Overnight*Card.vue components to use centralized functions
4. Remove duplicate timezone logic across components

Phase 5: Testing & Cleanup (Day 3)
1. Test date picker with browser≠user timezone scenarios
2. Verify timeline grouping accuracy (items on correct dates)
3. Test overnight detection (no false overnight trips)
4. Validate "on this day" duration calculations (correct start times)
5. Remove old overnightHelpers.js, clean up imports

Expected Results:
- ✅ Date picker: 08/18/2025 NY → correct UTC range for API
- ✅ Timeline: Items appear on correct dates
- ✅ Overnight detection: Only truly overnight items marked
- ✅ Duration calculations: "00:00 - 11:11" instead of "20:00 - 11:11"
- 📦 Bundle: +4KB for major reliability improvement
- 🧹 Code: ~200 lines custom logic → ~50 lines Day.js
