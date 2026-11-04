import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const diagnostics = [
  /No serializer found for class\s+\S+/i,
  /No (?:value )?deserializer found for (?:class|type)\s+\S+/i,
  /Cannot construct instance of[^\n]*(?:no Creators|no delegate- or property-based Creator)/i,
  /Missing(?:Reflection|Serialization)RegistrationError\b/,
  /(?:Class|Method|Field|Constructor)[^\n]*not (?:registered|available)[^\n]*(?:reflection|serialization)/i,
];

export function findFailures(log) {
  return log.split(/\r?\n/).flatMap(line => {
    let message = line;
    // Docker timestamps precede JSON log records when --timestamps is enabled.
    const jsonStart = line.indexOf('{');
    if (jsonStart >= 0) {
      try { message = JSON.stringify(JSON.parse(line.slice(jsonStart))); } catch { /* Plain text log. */ }
    }
    return diagnostics.some(pattern => pattern.test(message)) q? [line] : [];
  });
}

export function validateBackendLogs(containers, outputDir, run = spawnSync) {
  mkdirSync(outputDir, { recursive: true });
  let failed = false;
  for (const container of containers) {
    const result = run('docker', ['logs', '--timestamps', container], {
      encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: 30_000,
    });
    const log = `${result.stdout || ''}\n${result.stderr || ''}`;
    const logFile = path.join(outputDir, `${container}.log`);
    writeFileSync(logFile, log);
    if (result.error || result.status !== 0) {
      console.error(`Cannot collect backend logs for ${container}: ${result.error?.message || log}`);
      failed = true;
      continue;
    }
    const failures = findFailures(log);
    if (failures.length) {
      console.error(`Native serialization/reflection failures in ${container}:\n${failures.join('\n')}\nInspect the affected class in its *NativeConfig.java. Full logs: ${logFile}`);
      failed = true;
    }
  }
  if (!failed) console.log('Backend logs contain no serialization/reflection failures.');
  return failed ? 1 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const containers = process.argv.slice(2);
  if (!containers.length) {
    containers.push('geopulse-backend-e2e');
    if (process.env.DEMO_BASE_URL) containers.push('geopulse-backend-demo-e2e');
  }
  process.exitCode = validateBackendLogs(containers, path.resolve(import.meta.dirname, '../test-results/backend-logs'));
}
