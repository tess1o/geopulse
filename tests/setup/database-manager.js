import pg from 'pg';

export class DatabaseManager {
    constructor() {
        this.client = null;
        this.userForeignKeys = null;
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
        this.userForeignKeys = null;
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
            'TRUNCATE TABLE user_friend_permissions CASCADE',
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

    async deleteUsersByEmails(emails) {
        if (!this.client) {
            throw new Error('Database not connected');
        }

        if (!Array.isArray(emails) || emails.length === 0) {
            return;
        }

        const userIdResult = await this.client.query(
            'SELECT id FROM users WHERE email = ANY($1::text[])',
            [emails]
        );
        const userIds = userIdResult.rows.map((row) => row.id);

        if (userIds.length === 0) {
            return;
        }

        if (!this.userForeignKeys) {
            const foreignKeysResult = await this.client.query(`
                SELECT
                    tc.table_name,
                    kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                   AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name
                   AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                  AND ccu.table_name = 'users'
                  AND ccu.column_name = 'id'
            `);
            this.userForeignKeys = foreignKeysResult.rows;
        }

        // Remove timeline rows that point to favorites/geocoding owned by target users.
        // This protects cleanup from cross-user reference leaks produced by import/export flows.
        const runBridgeCleanup = async () => {
            const bridgeCleanupQueries = [
                `
                DELETE FROM timeline_stays
                WHERE favorite_id IN (
                    SELECT id FROM favorite_locations WHERE user_id = ANY($1::uuid[])
                )
                `,
                `
                DELETE FROM timeline_stays
                WHERE geocoding_id IN (
                    SELECT id FROM reverse_geocoding_location WHERE user_id = ANY($1::uuid[])
                )
                `,
            ];

            for (const query of bridgeCleanupQueries) {
                try {
                    await this.client.query(query, [userIds]);
                } catch (error) {
                    // Ignore missing table/column in case schema differs across environments.
                    if (error.code !== '42P01' && error.code !== '42703') {
                        throw error;
                    }
                }
            }
        };

        // Group FK columns per table (some tables reference users via multiple columns).
        const tableToColumns = new Map();
        for (const row of this.userForeignKeys) {
            if (row.table_name === 'users') {
                continue;
            }
            if (!tableToColumns.has(row.table_name)) {
                tableToColumns.set(row.table_name, new Set());
            }
            tableToColumns.get(row.table_name).add(row.column_name);
        }

        const tables = [...tableToColumns.keys()];
        if (tables.length > 0) {
            // Build dependency graph between user-linked tables.
            // Edge child -> parent means child must be deleted first.
            const dependencyResult = await this.client.query(`
                SELECT
                    tc.table_name AS child_table,
                    ccu.table_name AS parent_table
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                   AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name
                   AND ccu.table_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = 'public'
                  AND tc.table_name = ANY($1::text[])
                  AND ccu.table_name = ANY($1::text[])
            `, [tables]);

            const adjacency = new Map();
            const indegree = new Map();
            for (const table of tables) {
                adjacency.set(table, new Set());
                indegree.set(table, 0);
            }

            for (const row of dependencyResult.rows) {
                const child = row.child_table;
                const parent = row.parent_table;
                if (child === parent || !adjacency.has(child) || !adjacency.has(parent)) {
                    continue;
                }
                if (!adjacency.get(child).has(parent)) {
                    adjacency.get(child).add(parent);
                    indegree.set(parent, indegree.get(parent) + 1);
                }
            }

            const queue = [...tables].filter((table) => indegree.get(table) === 0).sort();
            const orderedTables = [];

            while (queue.length > 0) {
                const table = queue.shift();
                orderedTables.push(table);

                for (const parent of adjacency.get(table)) {
                    indegree.set(parent, indegree.get(parent) - 1);
                    if (indegree.get(parent) === 0) {
                        queue.push(parent);
                        queue.sort();
                    }
                }
            }

            // Fallback for cycles/unexpected metadata: keep deterministic completion.
            for (const table of [...tables].sort()) {
                if (!orderedTables.includes(table)) {
                    orderedTables.push(table);
                }
            }

            const deleteTableByUserColumns = async (table) => {
                const columns = [...tableToColumns.get(table)];
                const conditions = columns
                    .map((column, index) => `"${column}" = ANY($${index + 1}::uuid[])`)
                    .join(' OR ');
                const params = columns.map(() => userIds);
                const query = `DELETE FROM "${table}" WHERE ${conditions}`;

                await this.client.query(query, params);
            };

            let pendingTables = [...orderedTables];
            let rounds = 0;

            while (pendingTables.length > 0) {
                rounds += 1;
                let madeProgress = false;
                const deferredTables = [];

                await runBridgeCleanup();

                for (const table of pendingTables) {
                    try {
                        await deleteTableByUserColumns(table);
                        madeProgress = true;
                    } catch (error) {
                        // FK races can happen when background import tasks are still finalizing.
                        // Defer and retry after another bridge cleanup pass.
                        if (error.code === '23503') {
                            deferredTables.push(table);
                            continue;
                        }
                        throw error;
                    }
                }

                if (deferredTables.length === 0) {
                    break;
                }

                if (!madeProgress || rounds >= 5) {
                    throw new Error(
                        `Could not delete user-linked rows due FK dependencies in tables: ${deferredTables.join(', ')}`
                    );
                }

                pendingTables = deferredTables;
                await new Promise((resolve) => setTimeout(resolve, 100));
            }
        }

        for (let attempt = 1; attempt <= 3; attempt += 1) {
            try {
                await this.client.query(
                    'DELETE FROM users WHERE id = ANY($1::uuid[])',
                    [userIds]
                );
                return;
            } catch (error) {
                if (error.code !== '23503' || attempt === 3) {
                    throw error;
                }

                await runBridgeCleanup();

                const directCleanupQueries = [
                    'DELETE FROM timeline_stays WHERE user_id = ANY($1::uuid[])',
                    'DELETE FROM timeline_trips WHERE user_id = ANY($1::uuid[])',
                ];
                for (const query of directCleanupQueries) {
                    try {
                        await this.client.query(query, [userIds]);
                    } catch (cleanupError) {
                        if (cleanupError.code !== '42P01') {
                            throw cleanupError;
                        }
                    }
                }
                await new Promise((resolve) => setTimeout(resolve, 100));
            }
        }
    }
}
