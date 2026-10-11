import { test, expect } from '../fixtures/oidc-fixture.js';
import { LoginPage } from '../pages/LoginPage.js';
import { MOCK_OIDC_PUBLIC_URL, signInAtMockIdp, trackOidcLaunches } from '../utils/mock-oidc.js';

/**
 * OIDC auto-launch (issue #628): with password login off and a single provider, /login goes straight
 * to the provider, except when that would sign the user back in after a deliberate sign-out or retry
 * a sign-in that just failed.
 *
 * Runs in the chromium-oidc project only: these tests switch global auth settings.
 */

const MOCK_OIDC_HOST = new URL(MOCK_OIDC_PUBLIC_URL).hostname;

const continueButton = (page, oidc) =>
  page.getByRole('button', { name: `Continue with ${oidc.provider.displayName}` });

const waitForCallback = (page) => page.waitForResponse(
  (response) => response.url().includes('/auth/oidc/callbacks') && response.request().method() === 'POST'
);

/** Sign in through auto-launch from a guarded page and wait until the app is back on it. */
async function signInThroughAutoLaunch(page, oidc, user, path = '/app/timeline') {
  const callback = waitForCallback(page);
  // The app leaves this page for /login and the provider, possibly before "load".
  await page.goto(path, { waitUntil: 'commit' });
  await signInAtMockIdp(page, user.email);
  expect((await callback).status()).toBe(200);
  await page.waitForURL((url) => url.pathname === path);
}

/** The login page has loaded its options, shows the provider button and did not leave on its own. */
async function expectLoginPageWithoutLaunch(page, oidc, launches, expectedLaunches = 0) {
  await page.waitForURL((url) => url.pathname === '/login');
  await expect(continueButton(page, oidc)).toBeVisible();
  await page.waitForLoadState('networkidle');
  expect(launches.count).toBe(expectedLaunches);
  expect(new URL(page.url()).pathname).toBe('/login');
  await expect(page.getByText(/^Redirecting to /)).toHaveCount(0);
}

async function logoutFromUserMenu(page) {
  const logoutButton = page.locator('.gp-user-menu-logout');
  if (!(await logoutButton.isVisible().catch(() => false))) {
    await page.locator('.gp-user-menu-trigger:visible').first().click();
  }
  await logoutButton.click();
}

/**
 * Drop GeoPulse's cookies but keep the provider's, as when the GeoPulse session expires. Leaves the app
 * first: requests still in flight from the page would otherwise get a 401 and redirect it to /login on
 * their own, racing the test's next navigation.
 */
async function expireGeoPulseSession(page) {
  await page.goto('about:blank');
  const context = page.context();
  for (const cookie of await context.cookies()) {
    if (cookie.domain !== MOCK_OIDC_HOST) {
      await context.clearCookies({ name: cookie.name, domain: cookie.domain });
    }
  }
}

async function readStorageFlag(page, storage, key) {
  return page.evaluate(([area, name]) => window[area].getItem(name), [storage, key]);
}

test.describe('OIDC auto-launch', () => {
  test.describe('Signing in', () => {
    test('goes straight to the only provider and returns to the requested page', async ({ page, oidc }) => {
      const user = await oidc.createUser();
      const launches = trackOidcLaunches(page);

      await signInThroughAutoLaunch(page, oidc, user, '/app/timeline');

      expect(launches.count).toBe(1);
      const stats = await oidc.stats();
      expect(stats.authorize).toBe(1);
      expect(stats.interactiveLogins).toBe(1);
    });

    test('signs back in silently when the GeoPulse session expires', async ({ page, oidc }) => {
      const user = await oidc.createUser();
      await signInThroughAutoLaunch(page, oidc, user, '/app/timeline');

      await expireGeoPulseSession(page);
      const callback = waitForCallback(page);
      await page.goto('/app/timeline', { waitUntil: 'commit' });

      expect((await callback).status()).toBe(200);
      await page.waitForURL((url) => url.pathname === '/app/timeline');
      const stats = await oidc.stats();
      expect(stats.interactiveLogins).toBe(1);
      expect(stats.silentLogins).toBe(1);
    });
  });

  test.describe('After a deliberate sign-out', () => {
    test('provider with single logout: lands on the login page and asks before signing in again', async ({ page, oidc }) => {
      const user = await oidc.createUser();
      await signInThroughAutoLaunch(page, oidc, user);
      const launches = trackOidcLaunches(page);

      await logoutFromUserMenu(page);

      // GeoPulse sends the browser to the provider's end_session endpoint, which returns it to /login.
      await expectLoginPageWithoutLaunch(page, oidc, launches);
      expect((await oidc.stats()).endSessions).toBe(1);

      // The provider session ended, so continuing asks for credentials again.
      const callback = waitForCallback(page);
      await continueButton(page, oidc).click();
      await signInAtMockIdp(page, user.email);
      expect((await callback).status()).toBe(200);
      await page.waitForURL((url) => url.pathname.startsWith('/app/'));
      expect((await oidc.stats()).interactiveLogins).toBe(2);
      expect(await readStorageFlag(page, 'localStorage', 'oidcAutoLaunchSignedOut')).toBeNull();
    });

    test.describe('provider without single logout', () => {
      test.use({ oidcOptions: { endSession: false } });

      test('does not sign the user back in, and re-arms after the next sign-in', async ({ page, oidc }) => {
        await oidc.setSetting('auth.guest-root-redirect-to-login.enabled', 'true');
        const user = await oidc.createUser();
        await signInThroughAutoLaunch(page, oidc, user);
        const launches = trackOidcLaunches(page);

        // Logout goes to "/", which sends guests to /login. The provider session is still alive, so a
        // launch here would sign the user straight back in.
        await logoutFromUserMenu(page);
        await expectLoginPageWithoutLaunch(page, oidc, launches);
        expect((await oidc.stats()).authorize).toBe(1);

        const callback = waitForCallback(page);
        await continueButton(page, oidc).click();
        expect((await callback).status()).toBe(200);
        await page.waitForURL((url) => url.pathname.startsWith('/app/'));
        expect((await oidc.stats()).silentLogins).toBe(1);

        // Signed in again: an expired session goes back through the provider on its own.
        await expireGeoPulseSession(page);
        const silentCallback = waitForCallback(page);
        await page.goto('/app/timeline', { waitUntil: 'commit' });
        expect((await silentCallback).status()).toBe(200);
        await page.waitForURL((url) => url.pathname === '/app/timeline');
        expect((await oidc.stats()).silentLogins).toBe(2);
      });

      test('pressing "Sign in" on Home after a sign-out goes straight to the provider', async ({ page, oidc }) => {
        const user = await oidc.createUser();
        await signInThroughAutoLaunch(page, oidc, user);
        const launches = trackOidcLaunches(page);

        await logoutFromUserMenu(page);
        await page.waitForURL((url) => url.pathname === '/');

        // Asking to sign in is not an accidental launch: the provider session is alive, so it is silent.
        const callback = waitForCallback(page);
        await page.locator('.nav-signin').click();
        expect((await callback).status()).toBe(200);
        await page.waitForURL((url) => url.pathname.startsWith('/app/'));
        expect(launches.count).toBe(1);
        expect((await oidc.stats()).silentLogins).toBe(1);
      });

      test('a sign-out in another tab keeps this tab signed out', async ({ page, oidc }) => {
        const user = await oidc.createUser();
        await signInThroughAutoLaunch(page, oidc, user);

        const otherTab = await page.context().newPage();
        await otherTab.goto('/app/timeline');
        await otherTab.waitForURL((url) => url.pathname === '/app/timeline');
        const otherTabLaunches = trackOidcLaunches(otherTab);

        await logoutFromUserMenu(page);
        await page.waitForURL((url) => !url.pathname.startsWith('/app/'));

        await otherTab.reload();
        await expectLoginPageWithoutLaunch(otherTab, oidc, otherTabLaunches);
        expect((await oidc.stats()).authorize).toBe(1);
        await otherTab.close();
      });

      test('the sign-out after a backup restore does not sign the user back in', async ({ page, oidc }) => {
        const user = await oidc.createUser();
        await signInThroughAutoLaunch(page, oidc, user);
        const launches = trackOidcLaunches(page);

        // Report a completed restore, which signs every browser out.
        await page.route('**/system/maintenance', (route) => route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            state: 'COMPLETED',
            completedAt: `e2e-${Date.now()}`,
            blocked: false,
            warning: false,
            unavailable: false,
          }),
        }));
        await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));

        await page.waitForURL((url) => url.pathname === '/login', { timeout: 20000 });
        await page.unroute('**/system/maintenance');
        await expectLoginPageWithoutLaunch(page, oidc, launches);
        expect((await oidc.stats()).authorize).toBe(1);
      });
    });
  });

  test.describe('Failed sign-ins', () => {
    test('a provider error is shown once and "Return to Login" does not retry it', async ({ page, oidc }) => {
      await oidc.configureProvider({ authorizeError: 'access_denied' });
      const launches = trackOidcLaunches(page);

      await page.goto('/login', { waitUntil: 'commit' });

      await page.waitForURL((url) => url.pathname === '/oidc/callback');
      await expect(page.getByText('Authentication Failed')).toBeVisible();
      await expect(page.getByText('Mock IdP refused the sign-in (access_denied)')).toBeVisible();

      await page.getByRole('button', { name: 'Return to Login' }).click();
      await expect(page).toHaveURL(/\/login\?autoLaunch=0$/);
      await expectLoginPageWithoutLaunch(page, oidc, launches, 1);
      expect((await oidc.stats()).authorize).toBe(1);
    });

    test('a GeoPulse-side failure is shown once, and the user can retry by hand', async ({ page, oidc }) => {
      const user = await oidc.createUser();
      await oidc.configureProvider({ tokenError: true });
      const launches = trackOidcLaunches(page);

      const failedCallback = waitForCallback(page);
      await page.goto('/login', { waitUntil: 'commit' });
      await signInAtMockIdp(page, user.email);
      expect((await failedCallback).ok()).toBe(false);
      await expect(page.getByText('Authentication Failed')).toBeVisible();

      await page.getByRole('button', { name: 'Return to Login' }).click();
      await expectLoginPageWithoutLaunch(page, oidc, launches, 1);

      // Once the provider works again, one click signs in (its session is still alive).
      await oidc.configureProvider({ tokenError: false });
      const callback = waitForCallback(page);
      await continueButton(page, oidc).click();
      expect((await callback).status()).toBe(200);
      await page.waitForURL((url) => url.pathname.startsWith('/app/'));
      expect(launches.count).toBe(2);
    });

    test('coming back from the provider without signing in shows the login page', async ({ page, oidc }) => {
      const user = await oidc.createUser();
      const launches = trackOidcLaunches(page);

      await page.goto('/login', { waitUntil: 'commit' });
      await expect(page.locator('#mock-oidc-login')).toBeVisible();
      await page.goBack();

      await expectLoginPageWithoutLaunch(page, oidc, launches, 1);
      expect((await oidc.stats()).authorize).toBe(1);

      const callback = waitForCallback(page);
      await continueButton(page, oidc).click();
      await signInAtMockIdp(page, user.email);
      expect((await callback).status()).toBe(200);
      await page.waitForURL((url) => url.pathname.startsWith('/app/'));
      expect(await readStorageFlag(page, 'sessionStorage', 'oidcAutoLaunchAttempted')).toBeNull();
    });

    test('a launch that cannot start leaves the login page usable', async ({ page, oidc }) => {
      await page.route('**/auth/oidc/login-authorizations/**', (route) => route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ status: 500, code: 'INTERNAL_ERROR', detail: 'Provider unavailable' }),
      }));
      const launches = trackOidcLaunches(page);

      await page.goto('/login');

      await expect(page.locator('.p-toast').getByText('Login Failed')).toBeVisible();
      await expectLoginPageWithoutLaunch(page, oidc, launches, 1);
      await expect(continueButton(page, oidc)).toBeEnabled();
      expect((await oidc.stats()).authorize).toBe(0);
    });
  });

  test.describe('Login page stays put', () => {
    test('with ?autoLaunch=0, so an administrator can sign in with a password', async ({ page, oidc }) => {
      const launches = trackOidcLaunches(page);

      await page.goto('/login?autoLaunch=0');
      await expectLoginPageWithoutLaunch(page, oidc, launches);

      await page.getByRole('button', { name: 'Administrator Access' }).click();
      await new LoginPage(page).login(oidc.admin.email, oidc.admin.password);
      await page.waitForURL((url) => url.pathname.startsWith('/app/'));
    });

    test('when it carries a notice for the user', async ({ page, oidc }) => {
      const launches = trackOidcLaunches(page);

      await page.goto('/login?reason=registration_disabled');

      await expect(page.locator('.p-toast').getByText('Registration Disabled')).toBeVisible();
      await expectLoginPageWithoutLaunch(page, oidc, launches);
    });

    test('when auto-launch is off', async ({ page, oidc }) => {
      await oidc.setSetting('auth.oidc.auto-launch.enabled', 'false');
      const launches = trackOidcLaunches(page);

      await page.goto('/login');

      await expectLoginPageWithoutLaunch(page, oidc, launches);
    });

    test('when more than one provider is enabled', async ({ page, oidc }) => {
      const second = await oidc.addProvider();
      const launches = trackOidcLaunches(page);

      await page.goto('/login');

      await expectLoginPageWithoutLaunch(page, oidc, launches);
      await expect(page.getByRole('button', { name: `Continue with ${second.displayName}` })).toBeVisible();
    });

    test('when password login is enabled', async ({ page, oidc }) => {
      await oidc.setSetting('auth.password-login.enabled', 'true');
      const launches = trackOidcLaunches(page);

      await page.goto('/login');

      await expectLoginPageWithoutLaunch(page, oidc, launches);
      await expect(page.locator('#email')).toBeVisible();
    });
  });

  test('administrators can switch auto-launch off in the admin settings', async ({ page, oidc, dbManager }) => {
    await page.goto('/login?autoLaunch=0');
    await page.getByRole('button', { name: 'Administrator Access' }).click();
    await new LoginPage(page).login(oidc.admin.email, oidc.admin.password);
    await page.waitForURL((url) => url.pathname.startsWith('/app/'));

    await page.goto('/app/admin/settings');
    const setting = page.locator('[data-setting-id="auth.oidc.auto-launch.enabled"]');
    await expect(setting).toContainText('Auto-Launch OIDC Login');
    await setting.getByRole('switch').click();

    await expect.poll(async () => {
      const result = await dbManager.client.query(
        "SELECT value FROM system_settings WHERE key = 'auth.oidc.auto-launch.enabled'"
      );
      return result.rows[0]?.value;
    }).toBe('false');
  });
});
