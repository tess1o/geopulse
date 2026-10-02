import { expect } from '@playwright/test';

export const DateFormatValues = {
  DMY: 'DMY',
  MDY: 'MDY',
  YMD: 'YMD'
};

export const KnownDateStrings = {
  sep21_2025: {
    DMY: '21/09/2025',
    MDY: '09/21/2025',
    YMD: '2025-09-21'
  }
};

export class DateFormatTestHelper {
  static async setUserDateFormatByEmail(dbManager, email, dateFormat = DateFormatValues.DMY) {
    const user = await dbManager.getUserByEmail(email);
    if (!user) {
      throw new Error(`User not found for email ${email}`);
    }
    await this.setUserDateFormatById(dbManager, user.id, dateFormat);
    return user;
  }

  static async setUserDateFormatById(dbManager, userId, dateFormat = DateFormatValues.DMY) {
    await dbManager.client.query(
      "UPDATE users SET ui_preferences = jsonb_set(ui_preferences, '{dateFormat}', to_jsonb($1::text)), updated_at = NOW() WHERE id = $2",
      [dateFormat, userId]
    );
  }

  static async applyDateFormatIfProvided(dbManager, user) {
    if (!dbManager || !user?.email || !user?.dateFormat) return;
    await this.setUserDateFormatByEmail(dbManager, user.email, user.dateFormat);
  }

  static normalizeText(text) {
    return (text || '').replace(/\s+/g, ' ').trim();
  }

  static expectContainsDate(text, expectedDate, unexpectedDate = null) {
    const normalized = this.normalizeText(text);
    expect(normalized).toContain(expectedDate);
    if (unexpectedDate) {
      expect(normalized).not.toContain(unexpectedDate);
    }
  }

  static async expectLocatorContainsDate(locator, expectedDate, unexpectedDate = null) {
    const text = await locator.textContent();
    this.expectContainsDate(text, expectedDate, unexpectedDate);
  }
}
