// Builds the curl `x-codeSamples` entry for an OpenAPI operation. Values come from
// parameter and schema examples; when a value has no example, one is derived from its type, format, or name.
// Rendered by src/theme/ApiExplorer/CodeSnippets.

const JSON_TYPE = 'application/json';
const MULTIPART_TYPE = 'multipart/form-data';
const MAX_DEPTH = 6;
// SmallRye gives Java date and time types a fixed example; ignore it so samples use consistent, recent values.
const GENERIC_EXAMPLE_SCHEMAS = new Set(['Instant', 'LocalDate', 'LocalDateTime', 'LocalTime', 'OffsetDateTime',
    'ZonedDateTime', 'Duration', 'UUID']);

const DOWNLOAD_FILE_NAMES = [
    ['application/zip', 'export.zip'],
    ['application/gpx+xml', 'track.gpx'],
    ['text/csv', 'export.csv'],
    ['application/pdf', 'digest.pdf'],
    ['image/', 'image.jpg'],
    ['application/octet-stream', 'backup.gpb'],
];

export function buildCodeSamples(spec, path, method, operation) {
    const request = describeRequest(spec, path, method, operation);
    return [{lang: 'cURL', label: 'curl', source: curlSample(request)}];
}

// ---------------------------------------------------------------------------------------------------------------
// Request model

function describeRequest(spec, path, method, operation) {
    const parameters = operation.parameters ?? [];
    const auth = describeAuth(operation.security ?? []);

    let resolvedPath = path;
    for (const param of parameters.filter((p) => p.in === 'path')) {
        resolvedPath = resolvedPath.replace(`{${param.name}}`, String(parameterValue(spec, param)));
    }

    const query = parameters
        .filter((p) => p.in === 'query' && p.name !== auth.queryKey && (p.required || p.example !== undefined))
        .map((p) => [p.name, parameterValue(spec, p)]);
    if (auth.queryKey) {
        query.push([auth.queryKey, {env: auth.queryEnv}]);
    }

    const headers = parameters
        .filter((p) => p.in === 'header' && (p.required || p.example !== undefined))
        .map((p) => [p.name, String(parameterValue(spec, p))]);

    return {
        method: method.toUpperCase(),
        path: resolvedPath,
        query,
        headers,
        auth,
        body: describeBody(spec, operation.requestBody),
        response: describeResponse(operation.responses ?? {}),
    };
}

function describeAuth(security) {
    const schemes = new Set(security.flatMap((requirement) => Object.keys(requirement)));
    if (schemes.has('apiKey') || schemes.has('bearerAuth')) {
        return {type: 'header', header: 'X-API-Key', env: 'GEOPULSE_API_TOKEN'};
    }
    if (schemes.has('gpsSourceBasic')) {
        return {type: 'basic', userEnv: 'GPS_SOURCE_USERNAME', passwordEnv: 'GPS_SOURCE_PASSWORD'};
    }
    if (schemes.has('gpsSourceToken')) {
        return {type: 'bearer', env: 'GPS_SOURCE_TOKEN'};
    }
    if (schemes.has('gpsSourceQueryKey')) {
        return {type: 'query', queryKey: 'api_key', queryEnv: 'GPS_SOURCE_TOKEN'};
    }
    if (schemes.has('shareLinkToken')) {
        return {type: 'bearer', env: 'SHARE_LINK_ACCESS_TOKEN'};
    }
    return {type: 'none'};
}

function describeBody(spec, requestBody) {
    const content = requestBody?.content;
    if (!content) {
        return null;
    }
    if (content[JSON_TYPE]) {
        return {type: 'json', value: mediaExample(spec, content[JSON_TYPE])};
    }
    if (content[MULTIPART_TYPE]) {
        const media = content[MULTIPART_TYPE];
        const schema = resolve(spec, media.schema ?? {});
        const fields = Object.entries(schema.properties ?? {}).map(([name, propertySchema]) => {
            const resolved = resolve(spec, propertySchema);
            if (resolved.format === 'binary') {
                return {name, kind: 'file', path: '/path/to/file'};
            }
            const value = sampleValue(spec, propertySchema, name, 0);
            if (media.encoding?.[name]?.contentType === JSON_TYPE || typeof value === 'object') {
                return {name, kind: 'json', value};
            }
            return {name, kind: 'text', value: String(value)};
        });
        return {type: 'multipart', fields};
    }
    const [contentType, media] = Object.entries(content)[0];
    return {type: 'raw', contentType, value: String(mediaExample(spec, media) ?? '')};
}

function describeResponse(responses) {
    const contentTypes = Object.entries(responses)
        .filter(([code]) => code.startsWith('2'))
        .flatMap(([, response]) => Object.keys(response.content ?? {}));
    const binary = contentTypes.find((type) => type !== JSON_TYPE && type !== 'application/problem+json');
    if (!binary) {
        return {type: 'none'};
    }
    const fileName = DOWNLOAD_FILE_NAMES.find(([prefix]) => binary.startsWith(prefix))?.[1] ?? 'download';
    return {type: 'download', fileName};
}

// ---------------------------------------------------------------------------------------------------------------
// Example values

function parameterValue(spec, param) {
    if (param.example !== undefined) {
        return param.example;
    }
    return sampleValue(spec, param.schema ?? {}, param.name, 0);
}

function mediaExample(spec, media) {
    if (media.example !== undefined) {
        return media.example;
    }
    const firstExample = Object.values(media.examples ?? {})[0];
    if (firstExample?.value !== undefined) {
        return firstExample.value;
    }
    return sampleValue(spec, media.schema ?? {}, '', 0);
}

function resolve(spec, schema) {
    let current = schema ?? {};
    const seen = new Set();
    while (current.$ref && !seen.has(current.$ref)) {
        seen.add(current.$ref);
        const name = current.$ref.split('/').pop();
        const component = spec.components?.schemas?.[name] ?? {};
        const referenced = GENERIC_EXAMPLE_SCHEMAS.has(name) ? withoutKeys(component, ['example']) : component;
        current = {...referenced, ...withoutRef(current)};
    }
    if (current.allOf) {
        const merged = current.allOf.map((part) => resolve(spec, part))
            .reduce((acc, part) => ({...acc, ...part, properties: {...acc.properties, ...part.properties}}), {});
        current = {...merged, ...withoutKeys(current, ['allOf'])};
    }
    if (!current.type && (current.oneOf || current.anyOf)) {
        current = {...resolve(spec, (current.oneOf ?? current.anyOf)[0]), ...withoutKeys(current, ['oneOf', 'anyOf'])};
    }
    return current;
}

function withoutRef(schema) {
    return withoutKeys(schema, ['$ref']);
}

function withoutKeys(object, keys) {
    return Object.fromEntries(Object.entries(object).filter(([key]) => !keys.includes(key)));
}

function sampleValue(spec, rawSchema, name, depth) {
    if (rawSchema.example !== undefined) {
        return rawSchema.example;
    }
    const schema = resolve(spec, rawSchema);
    if (schema.example !== undefined) {
        return schema.example;
    }
    if (schema.default !== undefined) {
        return schema.default;
    }
    if (schema.enum?.length) {
        return schema.enum[0];
    }
    if (depth > MAX_DEPTH) {
        return null;
    }
    switch (schema.type) {
        case 'object':
            return objectValue(spec, schema, depth);
        case 'array':
            return [sampleValue(spec, schema.items ?? {}, singular(name), depth + 1)];
        case 'integer':
            return integerValue(name);
        case 'number':
            return numberValue(name);
        case 'boolean':
            return true;
        case 'string':
            return stringValue(schema, name);
        default:
            return schema.properties ? objectValue(spec, schema, depth) : stringValue(schema, name);
    }
}

function objectValue(spec, schema, depth) {
    const result = {};
    for (const [property, propertySchema] of Object.entries(schema.properties ?? {})) {
        if (propertySchema.readOnly || resolve(spec, propertySchema).readOnly) {
            continue;
        }
        result[property] = sampleValue(spec, propertySchema, property, depth + 1);
    }
    return result;
}

function singular(name) {
    return name.endsWith('s') ? name.slice(0, -1) : name;
}

function integerValue(name) {
    const lower = name.toLowerCase();
    if (lower.includes('page') && !lower.includes('size')) return 0;
    if (lower.includes('size') || lower.includes('limit')) return 50;
    if (lower.includes('year')) return 2025;
    if (lower.includes('month')) return 6;
    if (lower.includes('minutes')) return 60;
    if (lower.includes('seconds')) return 300;
    if (lower.includes('meters')) return 100;
    return 42;
}

function numberValue(name) {
    const lower = name.toLowerCase();
    if (lower.includes('lat')) return 50.4501;
    if (lower.includes('lon') || lower.includes('lng')) return 30.5234;
    if (lower.includes('accuracy')) return 10;
    if (lower.includes('speed') || lower.includes('velocity')) return 5.5;
    return 1.5;
}

function stringValue(schema, name) {
    const lower = name.toLowerCase();
    switch (schema.format) {
        case 'date-time':
            return '2025-06-01T08:00:00Z';
        case 'date':
            return '2025-06-01';
        case 'uuid':
            return '3fa85f64-5717-4562-b3fc-2c963f66afa6';
        case 'binary':
            return '/path/to/file';
        default:
            break;
    }
    if (lower.endsWith('time') || lower.endsWith('at') || lower.endsWith('date')) return '2025-06-01T08:00:00Z';
    if (lower.includes('email')) return 'user@example.com';
    if (lower.includes('password')) return 'change-me';
    if (lower.includes('url')) return 'https://example.com';
    if (lower.includes('color')) return '#3b82f6';
    if (lower.includes('timezone')) return 'Europe/Kyiv';
    if (lower.endsWith('id')) return '3fa85f64-5717-4562-b3fc-2c963f66afa6';
    if (lower.includes('name') || lower.includes('title')) return 'Example';
    return 'text';
}

// ---------------------------------------------------------------------------------------------------------------
// curl

function curlSample(request) {
    const lines = [`curl${request.method === 'GET' ? '' : ` -X ${request.method}`} "${curlUrl(request)}"`];
    const {auth} = request;
    if (auth.type === 'header') lines.push(`-H "${auth.header}: $${auth.env}"`);
    if (auth.type === 'bearer') lines.push(`-H "Authorization: Bearer $${auth.env}"`);
    if (auth.type === 'basic') lines.push(`-u "$${auth.userEnv}:$${auth.passwordEnv}"`);
    for (const [name, value] of request.headers) {
        lines.push(`-H ${shellQuote(`${name}: ${value}`)}`);
    }
    const {body} = request;
    if (body?.type === 'json') {
        lines.push(`-H "Content-Type: application/json"`);
        lines.push(`-d ${shellQuote(JSON.stringify(body.value, null, 2))}`);
    } else if (body?.type === 'multipart') {
        for (const field of body.fields) {
            if (field.kind === 'file') {
                lines.push(`-F "${field.name}=@${field.path}"`);
            } else if (field.kind === 'json') {
                lines.push(`-F ${shellQuote(`${field.name}=${JSON.stringify(field.value)};type=application/json`)}`);
            } else {
                lines.push(`-F ${shellQuote(`${field.name}=${field.value}`)}`);
            }
        }
    } else if (body?.type === 'raw') {
        lines.push(`-H ${shellQuote(`Content-Type: ${body.contentType}`)}`);
        lines.push(`-d ${shellQuote(body.value)}`);
    }
    if (request.response.type === 'download') {
        lines.push(`-o ${request.response.fileName}`);
    }
    return lines.join(' \\\n  ');
}

function curlUrl(request) {
    const query = request.query.map(([name, value]) => (
        `${name}=${typeof value === 'object' && value?.env ? `$${value.env}` : encodeQueryValue(value)}`
    ));
    return `$GEOPULSE_URL${request.path}${query.length ? `?${query.join('&')}` : ''}`;
}

function encodeQueryValue(value) {
    return encodeURIComponent(String(value)).replace(/%3A/g, ':').replace(/%2C/g, ',');
}

function shellQuote(text) {
    return `'${text.replace(/'/g, `'\\''`)}'`;
}
