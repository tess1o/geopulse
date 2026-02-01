/**
 * Test constants to eliminate magic values
 */
export class TestConstants {
  /**
   * Common test passwords and their bcrypt hashes
   * These are pre-computed bcrypt hashes for consistent test passwords
   */
  static PASSWORDS = {
    testpass123: 'testpass123',
    timelinepass: 'timelinepass',
    secret123: 'secret123',
    newpass123: 'newpass123'
  };

  /**
   * Pre-computed bcrypt hashes for test passwords
   * Generated with: bcrypt.hash(password, 12)
   */
  static BCRYPT_HASHES = {
    testpass123: '$2a$12$Iuh13ihQQPT2Kr9u9KVygu5kS2FMxBnIWE154uXbtPnVfSEpNjOqC',
    timelinepass: '$2a$12$pe7F7dhlJC3OE7gp9lqiW.i2H/er3W0U7G357gqsj3pQJHrXifZCK'
  };

  /**
   * Common timeout values (in milliseconds)
   */
  static TIMEOUTS = {
    SHORT: 500,
    MEDIUM: 1000,
    LONG: 2000,
    VERY_LONG: 5000,
    PAGE_LOAD: 10000
  };

  /**
   * Test UUIDs for consistent test data
   */
  static TEST_UUIDS = {
    LINK_1: '11111111-1111-1111-1111-111111111111',
    LINK_2: '22222222-2222-2222-2222-222222222222',
    LINK_3: '33333333-3333-3333-3333-333333333333',
    LINK_4: '44444444-4444-4444-4444-444444444444',
    LINK_5: '55555555-5555-5555-5555-555555555555',
    LINK_6: '66666666-6666-6666-6666-666666666666',
    LINK_7: '77777777-7777-7777-7777-777777777777',
    LINK_8: '88888888-8888-8888-8888-888888888888',
    LINK_9: '99999999-9999-9999-9999-999999999999',
    LINK_A: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    LINK_B: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    LINK_C: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
    LINK_D: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    LINK_E: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    LINK_F: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
  };

  /**
   * Generate a test UUID (for dynamic cases)
   */
  static generateTestUUID() {
    return '00000000-0000-0000-0000-' + Date.now().toString().padStart(12, '0');
  }

  /**
   * Common GPS coordinates for testing
   */
  static GPS_LOCATIONS = {
    LONDON: { lat: 51.5074, lon: -0.1278, name: 'London, UK' },
    NEW_YORK: { lat: 40.7128, lon: -74.0060, name: 'New York, NY' },
    PARIS: { lat: 48.8566, lon: 2.3522, name: 'Paris, France' },
    TOKYO: { lat: 35.6762, lon: 139.6503, name: 'Tokyo, Japan' }
  };

  /**
   * Common test data counts
   */
  static DATA_COUNTS = {
    GPS_POINTS_SMALL: 5,
    GPS_POINTS_MEDIUM: 10,
    GPS_POINTS_LARGE: 20
  };

  /**
   * Common date offsets (in days)
   */
  static DATE_OFFSETS = {
    WEEK_FUTURE: 7,
    TWO_WEEKS_FUTURE: 14,
    MONTH_FUTURE: 30,
    WEEK_PAST: -7,
    TWO_WEEKS_PAST: -14,
    MONTH_PAST: -30
  };

  /**
   * Share link types
   */
  static SHARE_TYPES = {
    LIVE_LOCATION: 'LIVE_LOCATION',
    TIMELINE: 'TIMELINE'
  };

  /**
   * Test user email patterns
   */
  static TEST_EMAILS = {
    owner: (id = 1) => `owner${id}@test.com`,
    viewer: (id = 1) => `viewer${id}@test.com`,
    user: (id = 1) => `user${id}@test.com`
  };
}
