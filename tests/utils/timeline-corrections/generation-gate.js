import pg from 'pg';
import { expect } from '@playwright/test';

// Holds only this test user's derived rows. Generation's DELETE blocks after a real job starts;
// correction commits, previews, other users, schema and the backend process remain untouched.
export async function holdGeneration(db, user) {
  const client = new pg.Client(db.config);
  await client.connect();
  await client.query('BEGIN');
  const { rows: [{ pid }] } = await client.query('SELECT pg_backend_pid() AS pid');
  const locked = await client.query('SELECT id FROM timeline_stays WHERE user_id=$1 FOR UPDATE', [user]);
  expect(locked.rowCount, 'generation gate needs existing derived stays').toBeGreaterThan(0);
  let closed = false;
  return {
    async blockedPid() {
      let blocked;
      await expect.poll(async () => {
        const { rows } = await db.client.query(`SELECT pid FROM pg_stat_activity
          WHERE $1::int = ANY(pg_blocking_pids(pid)) AND datname=current_database()
            AND usename=current_user AND query ILIKE '%timeline_stays%'`, [pid]);
        blocked = rows[0]?.pid;
        return Boolean(blocked);
      }, { timeout: 20000, message: 'real generation waits on the isolated user row gate' }).toBe(true);
      return blocked;
    },
    async failBlockedJob() {
      const blocked = await this.blockedPid();
      const { rows: [result] } = await db.client.query('SELECT pg_cancel_backend($1) AS cancelled', [blocked]);
      expect(result.cancelled).toBe(true);
    },
    async release() {
      if (closed) return;
      closed = true;
      try { await client.query('ROLLBACK'); } finally { await client.end(); }
    },
  };
}
