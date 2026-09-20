import { rmSync } from 'node:fs';
import path from 'node:path';

export default async function globalSetup() {
  console.log('🚀 Starting global test setup...');

  try {
    rmSync(path.resolve('test-results/network'), { recursive: true, force: true });
    // For now, just log that setup is starting
    // The backend will create the schema via Flyway migrations
    // Individual tests will create their own test data as needed
    
    console.log('✅ Global test setup completed successfully');
  } catch (error) {
    console.error('❌ Global test setup failed:', error);
    process.exit(1);
  }
}
