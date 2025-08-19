# E2E Testing with Playwright

This document describes how to run end-to-end (E2E) tests for the GeoPulse application using Playwright.

## Overview

The E2E testing setup uses:
- **Playwright** for browser automation and testing
- **Docker Compose** for isolated test environment
- **PostgreSQL** with test database for data isolation
- **Page Object Model** for maintainable test structure
- **Cookie-based JWT authentication** testing

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│  Playwright     │───▶│  GeoPulse UI     │───▶│  GeoPulse Backend   │
│  Test Container │    │  (Vue.js)        │    │  (Quarkus)          │
│  Port: N/A      │    │  Port: 5556      │    │  Port: 8081         │
└─────────────────┘    └──────────────────┘    └─────────────────────┘
                                                           │
                                                           ▼
                                               ┌─────────────────────┐
                                               │  PostgreSQL Test DB │
                                               │  Port: 5433         │
                                               └─────────────────────┘
```

## Directory Structure

```
tests/
├── e2e/                    # Test specifications
│   └── auth.spec.js        # Authentication flow tests
├── pages/                  # Page Object Models
│   ├── LoginPage.js        # Login page interactions
│   ├── RegisterPage.js     # Register page interactions
│   └── DashboardPage.js    # Dashboard/app page interactions
├── setup/                  # Test setup and utilities
│   ├── global-setup.js     # Global test setup
│   ├── global-teardown.js  # Global test cleanup
│   └── database-manager.js # Database operations
├── utils/                  # Helper utilities
│   └── test-helpers.js     # Common test functions
├── fixtures/               # Test data
│   └── test-data.js        # Predefined test data
├── sql/                    # Database seeding
│   └── 01-test-data.sql    # Initial test data
├── package.json            # Test dependencies
└── playwright.config.js    # Playwright configuration
```

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- Node.js 18+ (for local development)
- Available ports: 5433, 5556, 8081

### Running Tests

1. **Run all E2E tests:**
   ```bash
   npm run test:e2e
   ```

2. **Clean up after tests:**
   ```bash
   npm run test:e2e:clean
   ```

3. **View test logs:**
   ```bash
   npm run test:e2e:logs
   ```

4. **View test report (after running tests):**
   ```bash
   npm run test:e2e:report
   ```

### Development Mode

For test development, you can run tests in UI mode:

1. **Setup test dependencies:**
   ```bash
   npm run test:e2e:setup
   ```

2. **Run tests in UI mode:**
   ```bash
   npm run test:e2e:ui
   ```

3. **Debug tests:**
   ```bash
   npm run test:e2e:debug
   ```

## Test Environment

### Services

The E2E environment spins up the following services:

1. **geopulse-postgres-e2e** (Port 5433)
   - PostgreSQL 17 with PostGIS extension
   - Test database: `geopulse_test`
   - Credentials: `geopulse_test` / `testpassword`

2. **geopulse-backend-e2e** (Port 8081)
   - Quarkus backend in test mode
   - Environment variable: `GEOPULSE_TEST_MODE=true`
   - Connected to test database

3. **geopulse-ui-e2e** (Port 5556)
   - Vue.js frontend
   - Configured to use test backend

4. **playwright-tests**
   - Playwright test runner
   - Runs tests against UI service

### Environment Variables

Key environment variables for testing:

- `GEOPULSE_TEST_MODE=true` - Enables test mode in backend
- `GEOPULSE_AUTH_SECURE_COOKIES=false` - Disables secure cookies for testing
- `BASE_URL=http://geopulse-ui-e2e:80` - Frontend URL for tests
- `API_BASE_URL=http://geopulse-backend-e2e:8080/api` - Backend API URL

## Authentication Testing

### Cookie-Based JWT Flow

The tests handle GeoPulse's cookie-based JWT authentication:

1. **Login Flow:**
   - Submit credentials to `/api/auth/login`
   - Backend sets HttpOnly cookies: `access_token`, `refresh_token`
   - Frontend stores user profile in localStorage
   - Test verifies cookies and redirection

2. **Registration Flow:**
   - Submit user data to `/api/users/register`
   - Automatic login after successful registration
   - Redirect to onboarding page (`/app/location-sources`)

3. **Session Persistence:**
   - Tests verify session maintains across page reloads
   - Token refresh functionality testing
   - Logout clears cookies and localStorage

### Test Users

Pre-seeded test users:

```javascript
// Existing user for login tests
{
  email: 'testuser@example.com',
  password: 'password123',
  fullName: 'Test User'
}

// Another existing user
{
  email: 'existinguser@example.com', 
  password: 'password123',
  fullName: 'Existing User'
}
```

## Test Categories

### 1. User Registration Tests
- ✅ Successfully register new user
- ✅ Show validation errors for invalid input
- ✅ Prevent registration with existing email
- ✅ Navigate between register and login pages

### 2. User Login Tests
- ✅ Successfully login with valid credentials
- ✅ Show error for invalid credentials
- ✅ Show validation errors for empty fields
- ✅ Navigate between login and register pages

### 3. Authentication State Tests
- ✅ Maintain session across page reloads
- ✅ Redirect unauthenticated users to login
- ✅ Handle logout correctly

### 4. Complete Flow Tests
- ✅ Full register → login → logout flow

## Database Management

### Test Data Seeding

The database is automatically seeded with:
- Test users with known credentials
- Basic GPS source configurations
- Clean state for each test run

### Database Operations

Tests can use the `DatabaseManager` class for:
- Creating test users
- Querying user data
- Cleaning up test data
- Resetting database state

Example:
```javascript
const dbManager = new DatabaseManager();
await dbManager.connect();
await dbManager.createUser({
  email: 'test@example.com',
  fullName: 'Test User',
  passwordHash: 'hashed_password'
});
await dbManager.disconnect();
```

## Page Object Model

### LoginPage
```javascript
const loginPage = new LoginPage(page);
await loginPage.navigate();
await loginPage.login('user@example.com', 'password');
const isAuthenticated = await TestHelpers.isAuthenticated(page);
```

### RegisterPage
```javascript
const registerPage = new RegisterPage(page);
await registerPage.navigate();
await registerPage.register('user@example.com', 'Full Name', 'password');
```

### DashboardPage
```javascript
const dashboardPage = new DashboardPage(page);
const isOnTimeline = await dashboardPage.isOnTimelinePage();
await dashboardPage.logout();
```

## Troubleshooting

### Common Issues

1. **Port conflicts:**
   - Stop existing GeoPulse services: `docker-compose down`
   - Check ports 5433, 5556, 8081 are available

2. **Database connection errors:**
   - Ensure PostgreSQL container is healthy
   - Check database logs: `docker-compose -f docker-compose.e2e.yml logs geopulse-postgres-e2e`

3. **Test timeouts:**
   - Services might need more time to start
   - Check service health in docker-compose logs

4. **Authentication failures:**
   - Verify test users exist in database
   - Check backend is in test mode (`GEOPULSE_TEST_MODE=true`)

### Debugging Tests

1. **Run single test:**
   ```bash
   cd tests
   npx playwright test auth.spec.js --headed
   ```

2. **Debug mode:**
   ```bash
   cd tests
   npx playwright test --debug
   ```

3. **View test artifacts:**
   - Screenshots: `tests/test-results/`
   - Videos: `tests/test-results/`
   - HTML report: `tests/playwright-report/`

### Cleanup

To completely clean up the test environment:

```bash
# Stop and remove containers, volumes, and networks
npm run test:e2e:clean

# Remove Docker images (optional)
docker rmi $(docker images | grep "geopulse.*e2e" | awk '{print $3}')
```

## Configuration

### Playwright Configuration

Key settings in `tests/playwright.config.js`:

- **Browser:** Chromium (default)
- **Workers:** 1 (to avoid database conflicts)
- **Retries:** 2 on CI, 0 locally
- **Timeout:** 60s per test
- **Base URL:** `http://localhost:5556`

### Docker Configuration

Test environment settings in `docker-compose.e2e.yml`:

- Isolated network for test services
- Health checks for all services
- Volume mounts for test results
- Environment variables for test mode

## Contributing

When adding new tests:

1. **Follow Page Object Model pattern**
2. **Use TestHelpers for common operations**
3. **Clean up test data in database**
4. **Add meaningful test descriptions**
5. **Handle async operations properly**
6. **Add appropriate timeouts and waits**

Example test structure:
```javascript
test.describe('Feature Name', () => {
  test.beforeEach(async ({ page }) => {
    // Setup for each test
  });

  test('should perform expected behavior', async ({ page }) => {
    // Arrange
    const pageObject = new PageObject(page);
    
    // Act
    await pageObject.performAction();
    
    // Assert
    expect(await pageObject.getResult()).toBe(expectedValue);
  });
});
```