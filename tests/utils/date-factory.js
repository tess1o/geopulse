/**
 * Factory for creating test dates to eliminate date creation duplication
 */
export class DateFactory {
  /**
   * Get a future date by adding days to current date
   * @param {number} days - Number of days to add
   * @returns {Date}
   */
  static futureDate(days) {
    const date = new Date();
    date.setDate(date.getDate() + days);
    return date;
  }

  /**
   * Get a past date by subtracting days from current date
   * @param {number} days - Number of days to subtract
   * @returns {Date}
   */
  static pastDate(days) {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date;
  }

  /**
   * Get current date/time
   * @returns {Date}
   */
  static now() {
    return new Date();
  }

  /**
   * Create a date range for timeline tests
   * @param {number} startDaysFromNow - Days from now for start (negative for past)
   * @param {number} endDaysFromNow - Days from now for end
   * @param {number} expiresDaysFromNow - Days from now for expiration
   * @returns {{startDate: Date, endDate: Date, expiresAt: Date}}
   */
  static timelineRange(startDaysFromNow, endDaysFromNow, expiresDaysFromNow = 30) {
    return {
      startDate: startDaysFromNow < 0 ? this.pastDate(Math.abs(startDaysFromNow)) : this.futureDate(startDaysFromNow),
      endDate: endDaysFromNow < 0 ? this.pastDate(Math.abs(endDaysFromNow)) : this.futureDate(endDaysFromNow),
      expiresAt: this.futureDate(expiresDaysFromNow)
    };
  }

  /**
   * Common date ranges for different timeline scenarios
   */
  static get ranges() {
    return {
      // Active timeline: started 7 days ago, ends in 7 days
      active: () => this.timelineRange(-7, 7, 30),

      // Upcoming timeline: starts in 7 days, ends in 14 days
      upcoming: () => this.timelineRange(7, 14, 30),

      // Completed timeline: started 30 days ago, ended 14 days ago, expired 7 days ago
      completed: () => this.timelineRange(-30, -14, -7),

      // Expired link (for live location or timeline)
      expired: () => this.pastDate(7)
    };
  }

  /**
   * Create specific date from string (useful for consistent test data)
   * @param {string} dateString - ISO date string
   * @returns {Date}
   */
  static fromString(dateString) {
    return new Date(dateString);
  }

  /**
   * Format date for timeline tests that need specific dates
   * @param {number} year
   * @param {number} month - 1-indexed (1 = January)
   * @param {number} day
   * @param {number} hour
   * @param {number} minute
   * @returns {Date}
   */
  static create(year, month, day, hour = 0, minute = 0) {
    return new Date(year, month - 1, day, hour, minute);
  }
}
