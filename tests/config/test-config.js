export class TestConfig {
  static get API_BASE_URL() {
    return process.env.API_BASE_URL || 'http://localhost:8081';
  }

  static get WEB_BASE_URL() {
    return process.env.WEB_BASE_URL || 'http://localhost:3000';
  }

  static get API_ENDPOINTS() {
    return {
      register: `${this.API_BASE_URL}/api/v1/registrations`,
      login: `${this.API_BASE_URL}/api/v1/auth/sessions`,
      logout: `${this.API_BASE_URL}/api/v1/auth/sessions/current`,
    };
  }

  static get TIMEOUTS() {
    return {
      navigation: 15000,
      element: 10000,
      api: 5000,
    };
  }
}