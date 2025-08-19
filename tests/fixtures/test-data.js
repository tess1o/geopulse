import faker from 'faker';

export const TestData = {
  
  // Pre-defined test users
  users: {
    existing: {
      passwordHash: '$2a$12$Tw/8dhICF4wBQsy5S/Lf1OhaWatjLSdGuKtO5VZw3wfBHMaOwFmP6',
      email: 'testuser@example.com',
      password: 'password123',
      fullName: 'Test User',
      id: '550e8400-e29b-41d4-a716-446655440000'
    },
    
    another: {
      passwordHash: '$2a$12$Tw/8dhICF4wBQsy5S/Lf1OhaWatjLSdGuKtO5VZw3wfBHMaOwFmP6',
      email: 'existinguser@example.com',
      password: 'password123',
      fullName: 'Existing User',
      id: '550e8400-e29b-41d4-a716-446655440001'
    }
  },

  // Generate new user data for registration tests
  generateNewUser() {
    return {
      email: faker.internet.email().toLowerCase(),
      fullName: faker.name.findName(),
      password: 'TestPassword123!',
    };
  },

  // Generate user with specific characteristics
  generateUserWithEmail(emailPrefix) {
    const timestamp = Date.now();
    return {
      email: `${emailPrefix}_${timestamp}@example.com`,
      fullName: faker.name.findName(),
      password: 'TestPassword123!',
    };
  },

  // Invalid data for validation testing
  invalid: {
    email: {
      empty: '',
      invalid: 'not-an-email',
      tooLong: 'a'.repeat(300) + '@example.com'
    },
    
    password: {
      empty: '',
      tooShort: '123',
      weak: 'password'
    },
    
    fullName: {
      empty: '',
      tooShort: 'A'
    }
  },

  // API endpoints
  api: {
    baseUrl: process.env.API_BASE_URL || 'http://localhost:8081/api',
    auth: {
      login: '/auth/login',
      register: '/users/register',
      logout: '/auth/logout',
      refresh: '/auth/refresh-cookie'
    }
  },

  // UI routes
  routes: {
    login: '/login',
    register: '/register',
    dashboard: '/app',
    timeline: '/app/timeline',
    locationSources: '/app/location-sources'
  },

  // Test timeouts
  timeouts: {
    short: 5000,
    medium: 10000,
    long: 30000
  }
};