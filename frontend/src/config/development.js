// Development configuration
export const DEV_CONFIG = {
  // Set to true to use stub data instead of real API calls
  USE_STUB_STORES: true,
  
  // Stub configuration
  STUB_CONFIG: {
    // Mock API response delays (in milliseconds)
    API_DELAY: 500,
    
    // Default passwords for testing
    DEMO_PASSWORD: 'demo123',
    
    // Mock data settings
    MAX_LINKS: 10,
    INITIAL_ACTIVE_LINKS: 2,
    
    // Test URLs for shared location
    TEST_URLS: {
      PASSWORD_PROTECTED: '/shared/password-test',
      PUBLIC_HISTORY: '/shared/public-test',
      CURRENT_ONLY: '/shared/current-test',
      EXPIRED: '/shared/expired-test'
    }
  }
}

// Helper function to check if we're in development mode
export const isDevelopment = () => {
  return import.meta.env.MODE === 'development'
}

// Helper function to check if we should use stub stores
export const useStubStores = () => {
  return isDevelopment() && DEV_CONFIG.USE_STUB_STORES
}