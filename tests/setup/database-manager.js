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
            'TRUNCATE TABLE timeline_regeneration_tasks CASCADE',
            'TRUNCATE TABLE gps_points CASCADE',
            'TRUNCATE TABLE reverse_geocoding_locations CASCADE',
            'TRUNCATE TABLE favorites CASCADE',
            'TRUNCATE TABLE shared_links CASCADE',
            'TRUNCATE TABLE user_friends CASCADE',
            'TRUNCATE TABLE gps_source_configs CASCADE',
            'TRUNCATE TABLE export_jobs CASCADE',
            'TRUNCATE TABLE import_jobs CASCADE',
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
    }

    async seedTestData() {
        if (!this.client) {
            throw new Error('Database not connected');
        }

        // Create test users
        await this.createTestUsers();
    }

    async createTestUsers() {
        const testUsers = [
            {
                id: '550e8400-e29b-41d4-a716-446655440000',
                email: 'testuser@example.com',
                fullName: 'Test User',
                password: '$2a$10$N9qo8uLOickgx2ZMRZoMye1PgXaZskmFVlPSmEsZJJOaECR.rUm8y', // 'password123' hashed with BCrypt
                avatar: 'avatar1.png',
                createdAt: new Date().toISOString(),
            },
            {
                id: '550e8400-e29b-41d4-a716-446655440001',
                email: 'newuser@example.com',
                fullName: 'New User',
                password: '$2a$10$N9qo8uLOickgx2ZMRZoMye1PgXaZskmFVlPSmEsZJJOaECR.rUm8y', // 'password123' hashed with BCrypt
                avatar: 'avatar2.png',
                createdAt: new Date().toISOString(),
            }
        ];

        for (const user of testUsers) {
            await this.client.query(`
                INSERT INTO users (id, email, full_name, password_hash, avatar, created_at, updated_at)
                VALUES ($1, $2, $3, $4, $5, $6, $6) ON CONFLICT (email) DO NOTHING
            `, [user.id, user.email, user.fullName, user.password, user.avatar, user.createdAt]);
        }

        console.log(`Created ${testUsers.length} test users`);
    }

    async createUser(userData) {
        if (!this.client) {
            throw new Error('Database not connected');
        }

        const id = faker.datatype.uuid();
        const createdAt = new Date().toISOString();

        await this.client.query(`
            INSERT INTO users (id, email, full_name, password_hash, avatar, created_at, updated_at, emailverified)
            VALUES ($1, $2, $3, $4, $5, $6, $6, $7)
        `, [
            id,
            userData.email,
            userData.fullName,
            userData.passwordHash,
            userData.avatar || 'avatar1.png',
            createdAt,
            false
        ]);

        return {id, ...userData, createdAt};
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

    async deleteUser(email) {
        if (!this.client) {
            throw new Error('Database not connected');
        }

        await this.client.query('DELETE FROM users WHERE email = $1', [email]);
    }
}