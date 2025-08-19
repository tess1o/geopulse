export class TestConfig {
  static get API_BASE_URL() {
    return process.env.API_BASE_URL || 'http://localhost:8081';
  }

  static get WEB_BASE_URL() {
    return process.env.WEB_BASE_URL || 'http://localhost:3000';
  }

  static get API_ENDPOINTS() {
    return {
      register: `${this.API_BASE_URL}/api/users/register`,
      login: `${this.API_BASE_URL}/api/auth/login`,
      logout: `${this.API_BASE_URL}/api/auth/logout`,
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