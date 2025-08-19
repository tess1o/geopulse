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

## Development

### Running Tests Locally

```bash
# Setup test dependencies
npm run test:e2e:setup

# Run tests in UI mode
npm run test:e2e:ui

# Debug tests
npm run test:e2e:debug

# View test report
npm run test:e2e:report
```

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