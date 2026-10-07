// Writes the OpenAPI document used by the API reference: the backend specification without operations marked
// `x-internal` (endpoints that only exist for the GeoPulse web app). Tags and tag groups left without operations
// are removed too, and every operation gets generated code samples (see code-samples.mjs). Run through
// `npm run api:gen`.
import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {buildCodeSamples} from './code-samples.mjs';

const siteDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = resolve(siteDir, '../docs/openapi/openapi.json');
const targetPath = resolve(siteDir, '.generated/openapi.public.json');

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'];

const spec = JSON.parse(readFileSync(sourcePath, 'utf8'));
const usedTags = new Set();
let removed = 0;

for (const [path, pathItem] of Object.entries(spec.paths ?? {})) {
    for (const method of HTTP_METHODS) {
        const operation = pathItem[method];
        if (!operation) {
            continue;
        }
        if (operation['x-internal'] === true) {
            delete pathItem[method];
            removed++;
        } else {
            (operation.tags ?? []).forEach((tag) => usedTags.add(tag));
            operation['x-codeSamples'] = buildCodeSamples(spec, path, method, operation);
        }
    }
    if (!HTTP_METHODS.some((method) => pathItem[method])) {
        delete spec.paths[path];
    }
}

spec.tags = (spec.tags ?? []).filter((tag) => usedTags.has(tag.name));
spec['x-tagGroups'] = (spec['x-tagGroups'] ?? [])
    .map((group) => ({...group, tags: group.tags.filter((tag) => usedTags.has(tag))}))
    .filter((group) => group.tags.length > 0);

mkdirSync(dirname(targetPath), {recursive: true});
writeFileSync(targetPath, JSON.stringify(spec, null, 2));
console.log(`Public OpenAPI written to ${targetPath} (${removed} internal operations left out)`);
