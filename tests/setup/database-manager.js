import pg from 'pg';
import faker from 'faker';

export class DatabaseManager {
    constructor() {
        this.client = null;
        this.config = {
            host: process.env.DATABASE_HOST || 'localhost',
            port: process.env.DATABASE_PORT || 5433,
            database: process.env.DATABASE_NAME || 'geopulse_test',
            user: process.env.DATABASE_USER || 'geopulse_test',
            password: process.env.DATABASE_PASSWORD || 'testpassword',
        };
    }

    async connect() {
        this.client = new pg.Client(this.config);
        await this.client.connect();
    }

    async disconnect() {
        if (this.client) {
            await this.client.end();
            this.client = null;
        }
    }

    async resetDatabase() {
        if (!this.client) {
            throw new Error('Database not connected');
        }

        // Clear all data from tables (in reverse dependency order)
        const clearQueries = [
            'TRUNCATE TABLE timeline_trips CASCADE',
            'TRUNCATE TABLE timeline_stays CASCADE',
            'TRUNCATE TABLE timeline_data_gaps CASCADE',
            'TRUNCATE TABLE gps_points CASCADE',
            'TRUNCATE TABLE reverse_geocoding_location CASCADE',
            'TRUNCATE TABLE favorite_locations CASCADE',
            'TRUNCATE TABLE shared_link CASCADE',
            'TRUNCATE TABLE user_friends CASCADE',
            'TRUNCATE TABLE gps_source_config CASCADE',
            'TRUNCATE TABLE friend_invitations CASCADE',
            'TRUNCATE TABLE users CASCADE',
        ];

        for (const query of clearQueries) {
            try {
                await this.client.query(query);
            } catch (error) {
                // Some tables might not exist yet, that's okay
                console.warn(`Warning: Could not clear table: ${error.message}`);
            }
        }
        console.log('Truncated all tables successfully');
    }

    async getUserByEmail(email) {
        if (!this.client) {
            throw new Error('Database not connected');
        }

        const result = await this.client.query(
            'SELECT * FROM users WHERE email = $1',
            [email]
        );

        return result.rows[0] || null;
    }
}