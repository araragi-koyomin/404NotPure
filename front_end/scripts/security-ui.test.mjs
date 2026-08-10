import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import ts from 'typescript';
import { analyzeFullPayloadLogs, analyzeSensitiveRequestLogs } from './sensitive-log-analyzer.mjs';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));

function loadTypeScriptModule(relativePath) {
  const sourcePath = resolve(scriptDirectory, relativePath);
  const source = readFileSync(sourcePath, 'utf8');
  const transpiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
    },
    fileName: sourcePath,
  }).outputText;
  const module = { exports: {} };
  new Function('module', 'exports', transpiled)(module, module.exports);
  return module.exports;
}

test('safe request error description never retains request credentials or response data', () => {
  const { describeRequestError } = loadTypeScriptModule('../src/utils/safe-error.ts');
  const description = describeRequestError({
    name: 'AxiosError',
    message: 'Request failed with fake-message-token',
    code: 'ERR_BAD_RESPONSE',
    config: {
      headers: { token: 'fake-secret-token' },
      data: { password: 'fake-password' },
    },
    response: {
      status: 500,
      data: { token: 'fake-response-token' },
    },
  });

  assert.deepEqual(description, {
    name: 'AxiosError',
    code: 'ERR_BAD_RESPONSE',
    status: 500,
  });
  const serialized = JSON.stringify(description);
  assert.doesNotMatch(serialized, /fake-message-token|fake-secret-token|fake-password|fake-response-token/);
});

test('sensitive response analyzer covers promise, await and console.error without rejecting status-only logs', () => {
  const unsafePromise = analyzeSensitiveRequestLogs(`
    userLogin(credentials).then(response => console.error(response.data));
  `);
  const unsafeAwait = analyzeSensitiveRequestLogs(`
    const accountResponse = await userInfo(username);
    console.log(accountResponse);
  `);
  const safeStatus = analyzeSensitiveRequestLogs(`
    const loginResponse = await userLogin(credentials);
    console.warn(loginResponse.status);
  `);

  assert.equal(unsafePromise.length, 1);
  assert.equal(unsafePromise[0].method, 'error');
  assert.equal(unsafeAwait.length, 1);
  assert.deepEqual(safeStatus, []);
});

test('full payload analyzer rejects response bodies and serialized request objects', () => {
  assert.equal(analyzeFullPayloadLogs('console.log(response.data);').length, 1);
  assert.equal(analyzeFullPayloadLogs('console.error(err.response?.data || err);').length, 1);
  assert.equal(analyzeFullPayloadLogs('console.log(JSON.stringify(product));').length, 1);
  assert.deepEqual(analyzeFullPayloadLogs("console.info('product-created', product.id);"), []);
  assert.deepEqual(analyzeFullPayloadLogs('console.warn(response.status);'), []);
});

test('failed logout keeps local state and current page so the user can retry', async () => {
  const { performLogout } = loadTypeScriptModule('../src/utils/logout.ts');
  const events = [];

  const result = await performLogout({
    requestLogout: async () => { throw new Error('network unavailable'); },
    clearLocalState: () => events.push('clear'),
    navigateToLogin: async () => { events.push('navigate'); },
    notifyFailure: () => events.push('notify'),
  });

  assert.equal(result, false);
  assert.deepEqual(events, ['notify']);
});

test('successful logout clears local state only after the server clears its cookie', async () => {
  const { performLogout } = loadTypeScriptModule('../src/utils/logout.ts');
  const events = [];

  const result = await performLogout({
    requestLogout: async () => { events.push('server'); },
    clearLocalState: () => events.push('clear'),
    navigateToLogin: async () => { events.push('navigate'); },
    notifyFailure: () => events.push('notify'),
  });

  assert.equal(result, true);
  assert.deepEqual(events, ['server', 'clear', 'navigate']);
});
