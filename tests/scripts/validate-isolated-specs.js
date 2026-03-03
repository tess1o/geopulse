import fs from 'fs';
import path from 'path';

const files = process.argv.slice(2);

if (files.length === 0) {
  console.error('Usage: node scripts/validate-isolated-specs.js <spec-file> [<spec-file>...]');
  process.exit(1);
}

const checks = [
  {
    name: 'Shared TestData user template',
    regex: /TestData\.users\.(existing|another)/,
    message: 'Use isolated user factory, not shared TestData user templates.',
  },
  {
    name: 'Direct UserFactory user creation',
    regex: /UserFactory\.createUser\(/,
    message: 'Use isolatedUsers.create(...) so created users are tracked and cleaned up.',
  },
  {
    name: 'Hardcoded test email literal',
    regex: /['"`][^'"`\n]*@[a-zA-Z0-9.-]+\.(com|org|net|io|dev|test)['"`]/,
    message: 'Avoid hardcoded emails; generate users via isolatedUsers.build/create.',
  },
  {
    name: 'Mutable user object assignment',
    regex: /testUser\.[a-zA-Z0-9_]+\s*=/,
    message: 'Do not mutate user objects after creation; pass overrides to isolatedUsers.create/build.',
  },
];

let hasFailures = false;

for (const file of files) {
  const absolutePath = path.resolve(file);
  const source = fs.readFileSync(absolutePath, 'utf8');
  const localFailures = [];

  for (const check of checks) {
    if (check.regex.test(source)) {
      localFailures.push(check);
    }
  }

  if (localFailures.length > 0) {
    hasFailures = true;
    console.error(`\n${file}`);
    for (const failure of localFailures) {
      console.error(`  - ${failure.name}: ${failure.message}`);
    }
  } else {
    console.log(`${file}: ok`);
  }
}

if (hasFailures) {
  process.exit(1);
}
