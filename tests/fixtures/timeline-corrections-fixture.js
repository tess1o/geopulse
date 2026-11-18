import { test as base, expect } from './isolated-fixture.js';
import { holdGeneration } from '../utils/timeline-corrections/generation-gate.js';
import * as H from '../utils/timeline-corrections/helpers.js';

export const test = base.extend({
  generationGate: async ({ dbManager }, use) => {
    const gates = [];
    try { await use(async user => { const gate = await holdGeneration(dbManager, user); gates.push(gate); return gate; }); }
    finally { for (const gate of gates) await gate.release(); }
  },
  timelineSeed: [{}, { option: true }],
  scenario: async ({ page, dbManager, isolatedUsers, timelineSeed }, use, testInfo) => {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/api/v1/trip-planning/suggestions?*', route => {
      const url = new URL(route.request().url());
      return route.fulfill({ json: { title: 'Selected location', latitude: Number(url.searchParams.get('latitude')), longitude: Number(url.searchParams.get('longitude')) } });
    });
    const context = await H.setup(page, dbManager, isolatedUsers, timelineSeed);
    try { await use(context); }
    finally {
      const current = await H.gpsFingerprint(dbManager, context.user);
      await testInfo.attach('timeline-correction-state', { contentType: 'application/json', body: JSON.stringify({
        fixture: { date: context.date, timezone: context.timezone, preferences: context.preferences },
        expectedBaseline: context.baseline, timeline: await H.rows(dbManager, context.user),
        corrections: await H.corrections(page), gpsBefore: context.gps, gpsAfter: current, errors
      }, null, 2) });
      expect(current, 'corrections must preserve every raw GPS field').toEqual(context.gps);
      expect(errors, 'unhandled browser errors').toEqual([]);
    }
  },
});
export { expect };
