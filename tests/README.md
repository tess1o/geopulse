# GeoPulse E2E Tests

This directory contains end-to-end tests for the GeoPulse application using Playwright.

## Quick Start

```bash
# From the project root directory
npm run test:e2e
```

## What's Included

### Authentication Tests (`e2e/auth.spec.js`)
- User registration flow
- User login flow  
- Session management
- Cookie-based JWT authentication
- Form validation
- Error handling

### Test Coverage
- ✅ Register new user → redirect to location sources
- ✅ Login existing user → redirect to timeline
- ✅ Handle invalid credentials
- ✅ Form validation errors
- ✅ Session persistence across reloads
- ✅ Logout functionality
- ✅ Complete register → login → logout flow

## Architecture

The tests run against a complete isolated environment:

```
Playwright → Vue.js Frontend → Quarkus Backend → PostgreSQL Test DB
```

All services run in Docker containers with test-specific configuration.

## Key Features

### Cookie-Based Authentication
Tests properly handle GeoPulse's HttpOnly JWT cookies:
- Access tokens stored in secure cookies
- Refresh tokens for session management
- User profile data in localStorage
- Automatic token refresh testing

### Database Isolation
- Separate test database (`geopulse_test`)
- Pre-seeded with test users
- Clean state for each test run
- Database utilities for test data management

### Page Object Model
- `LoginPage` - Login form interactions
- `RegisterPage` - Registration form interactions  
- `DashboardPage` - Authenticated app pages
- `TestHelpers` - Common utilities

### Test Data
- Pre-defined test users with known credentials
- Dynamic user generation for registration tests
- Database seeding scripts
- Configurable test data fixtures

## Test Users

```javascript
// Existing users for login tests
{
  email: 'testuser@example.com',
  password: 'password123'
}

{  
  email: 'existinguser@example.com',
  password: 'password123'
}
```

## Running Tests Manually

### Prerequisites
1. **Docker containers must be running:**
   - PostgreSQL database
   - Backend API (default: http://localhost:8081)
   - Frontend UI (default: http://localhost:5556)

2. **Setup:**
   ```bash
   cd tests
   npm install
   npx playwright install
   ```

### Basic Commands

```bash
# Run all tests
npm run test:e2e

# Run tests in UI mode (interactive)
npm run test:e2e:ui

# Run tests in headed mode (see browser)
npm run test:e2e:headed

# Debug tests (step through)
npm run test:e2e:debug

# View test report
npm run test:e2e:report
```

### Advanced Commands

```bash
# Run specific test file
npx playwright test user-registration.spec.js

# Run specific test by name
npx playwright test -g "should successfully register a new user"

# Run with different browser
npx playwright test --project=chromium

# Run with verbose output
npx playwright test --reporter=list
```

### Environment Variables

```bash
# Custom URLs
export BASE_URL=http://localhost:3000        # Frontend
export API_BASE_URL=http://localhost:8080    # Backend API

# Database connection (for test database)
export DATABASE_HOST=localhost
export DATABASE_PORT=5433
export DATABASE_NAME=geopulse_test
export DATABASE_USER=geopulse_test
export DATABASE_PASSWORD=testpassword
```

### Container Environments

GeoPulse has **two separate Docker environments** with different ports and container names:

#### **Development Environment** (`docker-compose.yml`)
- **Backend:** `geopulse-backend` → http://localhost:8080  
- **Frontend:** `geopulse-ui` → http://localhost:5555
- **Database:** `geopulse-postgres` → localhost:5432
- **Uses:** Pre-built Docker images (`tess1o/geopulse-backend:1.0.0-rc.2`)

#### **E2E Testing Environment** (`tests/docker-compose.e2e.yml`)  
- **Backend:** `geopulse-backend-e2e` → http://localhost:8081
- **Frontend:** `geopulse-ui-e2e` → http://localhost:5556  
- **Database:** `geopulse-postgres-e2e` → localhost:5433
- **Uses:** Build from source code (live builds)

### Bug Fix & Rebuild Procedure

When you fix a bug and need to rebuild containers:

#### **For Development Environment:**
```bash
# Fix backend bug
./dev-rebuild.sh backend

# Fix frontend bug  
./dev-rebuild.sh frontend

# Fix both
./dev-rebuild.sh both
```

#### **For E2E Testing Environment:**
```bash
# Fix backend bug for E2E
./dev-rebuild.sh backend --env=e2e

# Fix frontend bug for E2E
./dev-rebuild.sh frontend --env=e2e

# Fix both for E2E
./dev-rebuild.sh both --env=e2e
```

### Troubleshooting

1. **Connection issues:** Verify Docker containers are running on correct ports
   - Dev: Backend :8080, Frontend :5555
   - E2E: Backend :8081, Frontend :5556
2. **Database errors:** Check test database exists and is accessible
   - Dev DB: localhost:5432 
   - E2E DB: localhost:5433
3. **Browser issues:** Run `npx playwright install` to install browsers
4. **Port conflicts:** E2E containers use different ports to avoid conflicts with dev containers

### Adding New Tests

1. Create test file in `e2e/` directory
2. Use Page Object Model pattern
3. Handle authentication state properly
4. Clean up test data
5. Add meaningful test descriptions

Example:
```javascript
import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/LoginPage.js';

test.describe('New Feature', () => {
  test('should perform expected behavior', async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.navigate();
    // Test implementation
  });
});
```

For detailed documentation, see [E2E_TESTING.md](../docs/E2E_TESTING.md).