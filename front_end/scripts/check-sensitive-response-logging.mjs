import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { analyzeFullPayloadLogs, analyzeSensitiveRequestLogs } from './sensitive-log-analyzer.mjs';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const loginPath = resolve(scriptDirectory, '../src/views/user/Login.vue');
const sourceRoot = resolve(scriptDirectory, '../src');
const vueSource = readFileSync(loginPath, 'utf8');
const scriptMatch = vueSource.match(/<script\b[^>]*>([\s\S]*?)<\/script>/i);

if (!scriptMatch) {
  console.error('Sensitive logging check could not find the Login.vue script block.');
  process.exit(1);
}

const scriptOffset = vueSource.slice(0, scriptMatch.index).split(/\r?\n/).length;
const findings = analyzeSensitiveRequestLogs(scriptMatch[1], 'Login.vue.ts', scriptOffset - 1);

if (findings.length > 0) {
  for (const finding of findings) {
    console.error(`Login.vue:${finding.line} sends a login/account response to console.${finding.method}.`);
  }
  console.error('Sensitive login or account responses must not be written to the browser console.');
  process.exit(1);
}

const payloadFindings = [];

function inspectSourceTree(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      inspectSourceTree(path);
      continue;
    }
    if (!entry.name.endsWith('.ts') && !entry.name.endsWith('.vue')) continue;

    const source = readFileSync(path, 'utf8');
    const vueScript = entry.name.endsWith('.vue')
      ? source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/i)
      : undefined;
    if (entry.name.endsWith('.vue') && !vueScript) continue;
    const analyzedSource = vueScript ? vueScript[1] : source;
    const lineOffset = vueScript
      ? source.slice(0, vueScript.index).split(/\r?\n/).length - 1
      : 0;
    for (const finding of analyzeFullPayloadLogs(analyzedSource, entry.name, lineOffset)) {
      payloadFindings.push({ path, ...finding });
    }
  }
}

inspectSourceTree(sourceRoot);
if (payloadFindings.length > 0) {
  for (const finding of payloadFindings) {
    console.error(`${finding.path}:${finding.line} sends a complete request or response payload to console.${finding.method}.`);
  }
  console.error('Complete request and response payloads must not be written to the browser console.');
  process.exit(1);
}

console.log('Sensitive response logging check passed.');
