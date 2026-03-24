import {defineConfig, devices} from '@playwright/test';

/**
 * @see https://playwright.dev/docs/test-configuration
 */
const isolatedSpecs = [
  '**/authentication.spec.js',
  '**/dashboard.spec.js',
  '**/gps-data.spec.js',
  '**/data-export-import.spec.js',
  '**/timeline.spec.js',
  '**/user-registration.spec.js',
  '**/user-validation.spec.js',
  '**/geocoding-management-visibility.spec.js',
  '**/journey-insights.spec.js',
  '**/time-digest.spec.js',
  '**/location-sources.spec.js',
  '**/location-analytics.spec.js',
  '**/place-details.spec.js',
  '**/share-links.spec.js',
  '**/share-links-access.spec.js',
  '**/timeline-labels-management.spec.js',
  '**/trips-management.spec.js',
  '**/trip-workspace.spec.js',
  '**/friends.spec.js',
  '**/favorites-management.spec.js',
  '**/geofences.spec.js',
  '**/timeline-map-interactions.spec.js',
  '**/timeline-reports.spec.js',
  '**/user-profile.spec.js',
  '**/geocoding-management.spec.js',
  '**/health.spec.js',
  '**/error-handling.spec.js',
  '**/notifications.spec.js',
];

const parallelWorkers = Number.parseInt(
  process.env.PLAYWRIGHT_WORKERS || (process.env.CI ? '4' : '6'),
  10
);

export default defineConfig({
  testDir: './e2e',
  testMatch: isolatedSpecs,
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  retries: 1,
  /* Worker count for parallel-safe specs. */
  workers: Number.isNaN(parallelWorkers) ? 4 : parallelWorkers,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['junit', { outputFile: 'test-results/results.xml' }],
    ['list'],
  ],
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: process.env.BASE_URL || 'http://localhost:5556',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
    
    /* Take screenshot on failure */
    screenshot: 'only-on-failure',
    
    /* Record video on failure */
    video: 'retain-on-failure',

    /* Accept all cookies and enable JavaScript */
    acceptDownloads: true,
    javaScriptEnabled: true,
    
    /* Ignore HTTPS errors in test environment */
    ignoreHTTPSErrors: true,

    /* Set viewport size */
    viewport: { width: 1280, height: 720 },

    /* Default timeout for actions */
    actionTimeout: 15000,
    navigationTimeout: 30000,
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    // Uncomment to test on Firefox and Safari
    // {
    //   name: 'firefox',
    //   use: { ...devices['Desktop Firefox'] },
    // },

    // {
    //   name: 'webkit',
    //   use: { ...devices['Desktop Safari'] },
    // },

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: { ...devices['Desktop Edge'], channel: 'msedge' },
    // },
    // {
    //   name: 'Google Chrome',
    //   use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    // },
  ],

  /* Global setup and teardown */
  globalSetup: './setup/global-setup.js',
  globalTeardown: './setup/global-teardown.js',

  /* Run your local dev server before starting the tests */
  // webServer: {
  //   command: 'npm run start',
  //   url: 'http://127.0.0.1:3000',
  //   reuseExistingServer: !process.env.CI,
  // },

  /* Test timeout */
  timeout: 60000,
  expect: {
    timeout: 10000,
  },

  /* Output directories */
  outputDir: 'test-results/',
});
