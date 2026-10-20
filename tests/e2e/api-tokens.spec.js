import {test, expect} from '../fixtures/isolated-fixture.js';
import {LoginPage} from '../pages/LoginPage.js';
import {UserProfilePage} from '../pages/UserProfilePage.js';
import {TestHelpers} from '../utils/test-helpers.js';
import {TestConfig} from '../config/test-config.js';

// Endpoints chosen from ApiTokenScopeFilter: safe methods need `read`, other methods need `write`,
// and /api/v1/api-tokens needs full access (`all`).
const CURRENT_USER_URL = `${TestConfig.API_BASE_URL}/api/v1/users/me`;
const API_TOKENS_URL = `${TestConfig.API_BASE_URL}/api/v1/api-tokens`;

const unwrap = (payload) => payload?.data ?? payload;

async function loginAndOpenApiTokens(page, user) {
  const loginPage = new LoginPage(page);
  const userProfilePage = new UserProfilePage(page);
  await loginPage.navigate();
  await loginPage.login(user.email, user.password);
  await TestHelpers.waitForNavigation(page, '**/app/timeline');
  await openApiTokens(page, userProfilePage);
  return userProfilePage;
}

async function openApiTokens(page, userProfilePage) {
  await page.goto('/app/profile?tab=security');
  await expect(page.locator(userProfilePage.selectors.apiTokens.heading)).toBeVisible();
}

async function getApiTokensFromDb(dbManager, userId) {
  const result = await dbManager.client.query(
    'SELECT * FROM user_api_tokens WHERE user_id = $1 ORDER BY created_at', [userId]);
  return result.rows;
}

async function getFullNameFromDb(dbManager, userId) {
  const result = await dbManager.client.query('SELECT full_name FROM users WHERE id = $1', [userId]);
  return result.rows[0].full_name;
}

/** A request context with no session cookies, so only the API token authenticates requests. */
async function withTokenClient(playwright, run) {
  const client = await playwright.request.newContext();
  try {
    return await run(client);
  } finally {
    await client.dispose();
  }
}

const bearer = (secret) => ({Authorization: `Bearer ${secret}`});

test.describe('API Tokens', () => {

  test('shows a new token secret once and lists read-only and full-access tokens', async ({page, isolatedUsers, dbManager}) => {
    const testUser = await isolatedUsers.create(page);
    const userProfilePage = await loginAndOpenApiTokens(page, testUser);
    const sel = userProfilePage.selectors.apiTokens;

    await expect(page.locator(sel.rows)).toContainText('No API tokens created.');

    const readSecret = await userProfilePage.createApiToken('E2E read only', 'readOnly');
    const fullSecret = await userProfilePage.createApiToken('E2E full access', 'full');

    expect(readSecret).toMatch(/^gp_sa_/);
    expect(fullSecret).toMatch(/^gp_sa_/);
    expect(fullSecret).not.toBe(readSecret);

    const readRow = page.locator(sel.rows, {hasText: 'E2E read only'});
    const fullRow = page.locator(sel.rows, {hasText: 'E2E full access'});
    await expect(readRow).toContainText('Read only');
    await expect(readRow).toContainText('Active');
    await expect(fullRow).toContainText('Full access');
    await expect(fullRow).toContainText('Active');

    // After the dialog is closed, and again after a reload, only the short preview is shown
    const secretShown = (secrets) => page.evaluate((values) =>
      values.some(value => document.body.innerText.includes(value)
        || Array.from(document.querySelectorAll('input')).some(input => input.value === value)), secrets);
    expect(await secretShown([readSecret, fullSecret])).toBe(false);

    await page.reload();
    await expect(page.locator(sel.heading)).toBeVisible();
    await expect(page.locator(sel.rows)).toHaveCount(2);
    await expect(page.locator(sel.createdDialog)).toHaveCount(0);
    expect(await secretShown([readSecret, fullSecret])).toBe(false);

    // Stored with the chosen scopes; the secret itself is never stored
    const user = await dbManager.getUserByEmail(testUser.email);
    const tokens = await getApiTokensFromDb(dbManager, user.id);
    expect(tokens.map(token => [token.name, token.scopes])).toEqual([
      ['E2E read only', 'read'],
      ['E2E full access', 'all'],
    ]);
    for (const token of tokens) {
      expect(token.revoked_at).toBeNull();
      expect(token.token_hash).not.toContain(readSecret);
      expect(token.token_hash).not.toContain(fullSecret);
    }
  });

  test('allows reads with a read-only token and writes only with a full-access token', async ({page, playwright, isolatedUsers, dbManager}) => {
    const testUser = await isolatedUsers.create(page);
    const userProfilePage = await loginAndOpenApiTokens(page, testUser);
    const readSecret = await userProfilePage.createApiToken('E2E scope read', 'readOnly');
    const fullSecret = await userProfilePage.createApiToken('E2E scope full', 'full');
    const user = await dbManager.getUserByEmail(testUser.email);
    const originalName = await getFullNameFromDb(dbManager, user.id);

    await withTokenClient(playwright, async (client) => {
      // Read-only token: GET works and acts as the token's owner
      const readResponse = await client.get(CURRENT_USER_URL, {headers: bearer(readSecret)});
      expect(readResponse.status()).toBe(200);
      expect(unwrap(await readResponse.json()).email).toBe(testUser.email);

      // ...but cannot change data
      const deniedWrite = await client.patch(CURRENT_USER_URL, {
        headers: bearer(readSecret),
        data: {fullName: 'Changed by read-only token'}
      });
      expect(deniedWrite.status()).toBe(403);
      expect(await getFullNameFromDb(dbManager, user.id)).toBe(originalName);

      // ...and cannot manage API tokens, which needs full access even for reading
      const deniedTokenList = await client.get(API_TOKENS_URL, {headers: bearer(readSecret)});
      expect(deniedTokenList.status()).toBe(403);

      // Full-access token: the same write succeeds
      const allowedWrite = await client.patch(CURRENT_USER_URL, {
        headers: bearer(fullSecret),
        data: {fullName: 'Changed by full token'}
      });
      expect(allowedWrite.status()).toBe(200);
      expect(unwrap(await allowedWrite.json()).fullName).toBe('Changed by full token');
      expect(await getFullNameFromDb(dbManager, user.id)).toBe('Changed by full token');

      const tokenList = await client.get(API_TOKENS_URL, {headers: bearer(fullSecret)});
      expect(tokenList.status()).toBe(200);
      expect(unwrap(await tokenList.json()).map(token => token.name).sort())
        .toEqual(['E2E scope full', 'E2E scope read']);
    });
  });

  test('rejects a token as soon as it is revoked in the UI', async ({page, playwright, isolatedUsers, dbManager}) => {
    const testUser = await isolatedUsers.create(page);
    const userProfilePage = await loginAndOpenApiTokens(page, testUser);
    const sel = userProfilePage.selectors.apiTokens;
    const secret = await userProfilePage.createApiToken('E2E to revoke', 'full');

    await withTokenClient(playwright, async (client) => {
      const before = await client.get(CURRENT_USER_URL, {headers: bearer(secret)});
      expect(before.status()).toBe(200);

      await userProfilePage.revokeApiToken('E2E to revoke');
      const row = page.locator(sel.rows, {hasText: 'E2E to revoke'});
      await expect(row).toContainText('Revoked');
      await expect(row.locator(sel.revokeButton)).toBeDisabled();

      const user = await dbManager.getUserByEmail(testUser.email);
      const [token] = await getApiTokensFromDb(dbManager, user.id);
      expect(token.revoked_at).not.toBeNull();

      const afterRead = await client.get(CURRENT_USER_URL, {headers: bearer(secret)});
      expect(afterRead.status()).toBe(401);
      const afterWrite = await client.patch(CURRENT_USER_URL, {
        headers: bearer(secret),
        data: {fullName: 'Changed by revoked token'}
      });
      expect(afterWrite.status()).toBe(401);
    });

    // The revoked token stays listed as revoked after a reload
    await openApiTokens(page, userProfilePage);
    await expect(page.locator(sel.rows, {hasText: 'E2E to revoke'})).toContainText('Revoked');
  });

  test("does not let another user's token reach the first user's data", async ({page, playwright, isolatedUsers, dbManager}) => {
    const owner = await isolatedUsers.create(page);
    const otherUser = await isolatedUsers.create(page);

    const userProfilePage = await loginAndOpenApiTokens(page, owner);
    const ownerSecret = await userProfilePage.createApiToken('E2E owner token', 'full');
    const ownerDbUser = await dbManager.getUserByEmail(owner.email);
    const [ownerToken] = await getApiTokensFromDb(dbManager, ownerDbUser.id);
    const ownerName = await getFullNameFromDb(dbManager, ownerDbUser.id);

    // The other user signs in on their own and creates a full-access token through the API
    const otherSecret = await withTokenClient(playwright, async (client) => {
      const login = await client.post(TestConfig.API_ENDPOINTS.login, {
        data: {email: otherUser.email, password: otherUser.password}
      });
      expect(login.ok()).toBe(true);
      const created = await client.post(API_TOKENS_URL, {data: {name: 'E2E other token', scopes: ['all']}});
      expect(created.status()).toBe(201);
      return unwrap(await created.json()).token;
    });
    expect(otherSecret).toMatch(/^gp_sa_/);

    await withTokenClient(playwright, async (client) => {
      // The other user's token resolves to the other user, never to the owner
      const me = await client.get(CURRENT_USER_URL, {headers: bearer(otherSecret)});
      expect(me.status()).toBe(200);
      const meBody = unwrap(await me.json());
      expect(meBody.email).toBe(otherUser.email);
      expect(meBody.userId).not.toBe(ownerDbUser.id);

      // Its writes change the other user's profile only
      const write = await client.patch(CURRENT_USER_URL, {
        headers: bearer(otherSecret),
        data: {fullName: 'Changed by other user'}
      });
      expect(write.status()).toBe(200);
      expect(await getFullNameFromDb(dbManager, ownerDbUser.id)).toBe(ownerName);

      // The owner's tokens are not listed for it and cannot be revoked with it
      const list = await client.get(API_TOKENS_URL, {headers: bearer(otherSecret)});
      expect(list.status()).toBe(200);
      const listedIds = unwrap(await list.json()).map(token => token.id);
      expect(listedIds).not.toContain(ownerToken.id);

      const revoke = await client.delete(`${API_TOKENS_URL}/${ownerToken.id}`, {headers: bearer(otherSecret)});
      expect(revoke.ok()).toBe(false);
      const [ownerTokenAfter] = await getApiTokensFromDb(dbManager, ownerDbUser.id);
      expect(ownerTokenAfter.revoked_at).toBeNull();

      // The owner's token still works for the owner
      const ownerMe = await client.get(CURRENT_USER_URL, {headers: bearer(ownerSecret)});
      expect(ownerMe.status()).toBe(200);
      expect(unwrap(await ownerMe.json()).email).toBe(owner.email);
    });
  });
});
