import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { findFailures, validateBackendLogs } from './validate-backend-logs.js';

const serializer = 'No serializer found for class org.github.tess1o.geopulse.export.dto.TimelineDataDto and no properties discovered to create BeanSerializer';

test('recognizes serializer, nested DTO, deserializer and native registration errors', () => {
  for (const message of [serializer, serializer.replace('TimelineDataDto', 'TimelineDataDto$StayDto'),
    'No deserializer found for class example.Payload',
    'Cannot construct instance of `example.Payload` (no Creators, like default constructor, exist)',
    'org.graalvm.nativeimage.MissingReflectionRegistrationError: example.Payload',
    'org.graalvm.nativeimage.MissingSerializationRegistrationError: example.Payload']) {
    assert.equal(findFailures(message).length, 1, message);
    assert.equal(findFailures(`2026-11-04T00:00:00Z ${JSON.stringify({ message, stackTrace: message })}`).length, 1);
  }
});

test('does not reject unrelated errors or empty logs', () => {
  assert.deepEqual(findFailures('ERROR Invalid credentials\nWARN Not found\n'), []);
  assert.deepEqual(findFailures(''), []);
});

test('collects both streams, saves artifacts, and fails on collection errors', t => {
  const errors = [];
  t.mock.method(console, 'error', message => errors.push(message));
  t.mock.method(console, 'log', () => {});
  const directory = mkdtempSync(path.join(tmpdir(), 'geopulse-logs-'));
  try {
    assert.equal(validateBackendLogs(['backend'], directory, () => ({ status: 0, stdout: 'started', stderr: serializer })), 1);
    assert.match(readFileSync(path.join(directory, 'backend.log'), 'utf8'), /TimelineDataDto/);
    assert.match(errors[0], /TimelineDataDto/);
    assert.match(errors[0], /NativeConfig/);
    assert.equal(validateBackendLogs(['backend'], directory, () => ({ status: 1, stderr: 'No such container' })), 1);
    assert.equal(validateBackendLogs(['backend'], directory, () => ({ status: null, error: new Error('timeout') })), 1);
    assert.equal(validateBackendLogs(['backend'], directory, () => ({ status: 0, stdout: 'healthy' })), 0);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});


test('local Make gate preserves either failure and checks logs after failed Playwright', () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'geopulse-e2e-gate-'));
  try {
    for (const [name, label, exitVariable] of [
      ['npx', 'playwright', 'PLAYWRIGHT_EXIT'], ['node', 'logs', 'LOG_EXIT'],
    ]) {
      writeFileSync(path.join(directory, name), `#!/bin/sh\nprintf '%s\\n' '${label}' >> "$GATE_TRACE"\nexit "$${exitVariable}"\n`, { mode: 0o755 });
    }
    for (const [playwright, logs] of [[0, 0], [7, 0], [0, 9], [7, 9]]) {
      const trace = path.join(directory, 'trace');
      writeFileSync(trace, '');
      const result = spawnSync('make', ['test-e2e'], {
        cwd: path.resolve(import.meta.dirname, '../..'), encoding: 'utf8',
        env: { ...process.env, PATH: `${directory}:${process.env.PATH}`, GATE_TRACE: trace,
          PLAYWRIGHT_EXIT: String(playwright), LOG_EXIT: String(logs) },
      });
      assert.equal(result.status === 0, playwright === 0 && logs === 0, result.stderr);
      assert.equal(readFileSync(trace, 'utf8'), 'playwright\nlogs\n');
    }
  } finally { rmSync(directory, { recursive: true, force: true }); }
});
