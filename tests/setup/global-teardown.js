import { DatabaseManager } from './database-manager.js';

export default async function globalTeardown() {
  console.log('🧹 Starting global test teardown...');

  try {
    // Clean up database
    const dbManager = new DatabaseManager();
    await dbManager.connect();
    await dbManager.resetDatabase();
    await dbManager.disconnect();

    console.log('✅ Global test teardown completed successfully');
  } catch (error) {
    console.error('❌ Global test teardown failed:', error);
    // Don't fail the build on teardown errors
  }
}