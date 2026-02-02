/**
 * Common test date constants to avoid duplication across test files
 */
export const TestDates = {
  // Period tag test dates
  PERIOD_TAG: {
    START_DATE: new Date('2025-09-20T00:00:00Z'),
    MIDDLE_DATE: new Date('2025-09-21T00:00:00Z'),
    END_DATE: new Date('2025-09-22T00:00:00Z'),
    OUTSIDE_RANGE: new Date('2025-08-01T00:00:00Z'),
  },

  // Timeline test dates
  TIMELINE: {
    SINGLE_DAY: new Date('2025-09-21'),
    START_DATE: new Date('2025-09-20'),
    END_DATE: new Date('2025-09-22'),
  },

  // Helper to get date range
  getDateRange(startDate, endDate) {
    return { startDate, endDate };
  },

  // Helper to get a date N days ago from now
  daysAgo(days) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date;
  },

  // Helper to get today's date
  today() {
    return new Date();
  }
};
