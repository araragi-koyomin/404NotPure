import ts from 'typescript';

const sensitiveRequestFunctions = new Set(['userLogin', 'userInfo']);
const consoleMethods = new Set(['log', 'info', 'debug', 'warn', 'error']);
const safeResponseProperties = new Set(['status', 'statusText']);

function calledSensitiveRequest(node) {
  const expression = ts.isAwaitExpression(node) ? node.expression : node;
  return ts.isCallExpression(expression)
    && ts.isIdentifier(expression.expression)
    && sensitiveRequestFunctions.has(expression.expression.text);
}

function consoleMethod(node) {
  if (!ts.isCallExpression(node) || !ts.isPropertyAccessExpression(node.expression)) return undefined;
  if (!ts.isIdentifier(node.expression.expression) || node.expression.expression.text !== 'console') return undefined;
  return consoleMethods.has(node.expression.name.text) ? node.expression.name.text : undefined;
}

function containsSensitiveValue(node, identifiers) {
  let found = false;
  function visit(child) {
    if (found) return;
    if (ts.isIdentifier(child) && identifiers.has(child.text)) {
      const parent = child.parent;
      if (ts.isPropertyAccessExpression(parent)
          && parent.expression === child
          && safeResponseProperties.has(parent.name.text)) {
        return;
      }
      found = true;
      return;
    }
    ts.forEachChild(child, visit);
  }
  visit(node);
  return found;
}

export function analyzeSensitiveRequestLogs(source, fileName = 'source.ts', lineOffset = 0) {
  const sourceFile = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const sensitiveIdentifiers = new Set();
  const findings = [];

  function collect(node) {
    if (ts.isVariableDeclaration(node)
        && ts.isIdentifier(node.name)
        && node.initializer
        && calledSensitiveRequest(node.initializer)) {
      sensitiveIdentifiers.add(node.name.text);
    }
    if (ts.isCallExpression(node)
        && ts.isPropertyAccessExpression(node.expression)
        && node.expression.name.text === 'then'
        && calledSensitiveRequest(node.expression.expression)
        && node.arguments.length > 0) {
      const callback = node.arguments[0];
      if ((ts.isArrowFunction(callback) || ts.isFunctionExpression(callback))
          && callback.parameters.length > 0
          && ts.isIdentifier(callback.parameters[0].name)) {
        sensitiveIdentifiers.add(callback.parameters[0].name.text);
      }
    }
    ts.forEachChild(node, collect);
  }
  collect(sourceFile);

  function inspect(node) {
    const method = consoleMethod(node);
    if (method && node.arguments.some(argument => containsSensitiveValue(argument, sensitiveIdentifiers))) {
      const location = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
      findings.push({ line: location.line + 1 + lineOffset, method });
    }
    ts.forEachChild(node, inspect);
  }
  inspect(sourceFile);
  return findings;
}

export function analyzeFullPayloadLogs(source, fileName = 'source.ts', lineOffset = 0) {
  const sourceFile = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const findings = [];
  const responseNames = new Set(['res', 'response', 'err', 'error']);

  function rootIdentifier(node) {
    let current = node;
    while (ts.isPropertyAccessExpression(current) || ts.isElementAccessExpression(current)) {
      current = current.expression;
    }
    return ts.isIdentifier(current) ? current.text : undefined;
  }

  function containsFullPayload(node) {
    let found = false;
    function visit(child) {
      if (found) return;
      if ((ts.isPropertyAccessExpression(child) || ts.isElementAccessExpression(child))
          && responseNames.has(rootIdentifier(child))) {
        if (ts.isPropertyAccessExpression(child)
            && (child.name.text === 'data' || child.name.text === 'response')) {
          found = true;
          return;
        }
      }
      if (ts.isCallExpression(child)
          && ts.isPropertyAccessExpression(child.expression)
          && ts.isIdentifier(child.expression.expression)
          && child.expression.expression.text === 'JSON'
          && child.expression.name.text === 'stringify'
          && child.arguments.length > 0
          && !ts.isStringLiteralLike(child.arguments[0])
          && !ts.isNumericLiteral(child.arguments[0])) {
        found = true;
        return;
      }
      ts.forEachChild(child, visit);
    }
    visit(node);
    return found;
  }

  function inspect(node) {
    const method = consoleMethod(node);
    if (method && node.arguments.some(containsFullPayload)) {
      const location = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
      findings.push({ line: location.line + 1 + lineOffset, method });
    }
    ts.forEachChild(node, inspect);
  }
  inspect(sourceFile);
  return findings;
}
