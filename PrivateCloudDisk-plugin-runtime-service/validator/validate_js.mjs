#!/usr/bin/env node
// 云插件 JavaScript/Node 静态安全校验器（需求五 5.1-5.25，与 validator/validate_python.py 对偶）。
//
// 使用 vendored acorn（validator/js/acorn.mjs，MIT，见 validator/js/LICENSE）解析源码并生成 AST；
// 只做静态分析、绝不执行插件代码，AST 白名单是发布门禁、不替代容器沙箱。
// 与 Python 校验器共享同一个 ValidationResponse JSON 契约（model.ValidationResponse）。
// 错误信息只输出相对位置（5.16/5.17），不包含宿主绝对路径。
//
// 用法：printf '{...ValidationRequest}' | node validator/validate_js.mjs --ast-only

import { readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parse } from './js/acorn.mjs';

// ------------------------------------------------------------------ 白名单/黑名单

// 本地插件唯一允许导入的模块（5.3/5.9）。其余一律拒绝，覆盖 child_process/fs/net/http/https 等宿主模块（5.4）。
const ALLOWED_MODULES = new Set(['plugin-sdk']);
// 明确点名的宿主模块，用于更清晰的错误提示（无论是否列入 ALLOWED_MODULES 都禁止）。
const HOST_MODULES = new Set([
  'child_process', 'fs', 'fs/promises', 'net', 'http', 'https',
  'dns', 'os', 'path', 'tls', 'zlib', 'vm', 'worker_threads',
  'cluster', 'repl', 'readline', 'tty', 'perf_hooks', 'async_hooks',
]);

// 宿主/全局对象：在受控运行环境之外存在，直接引用即视为越界访问（5.4/5.5）。
const HOST_GLOBALS = new Set([
  'process', 'global', 'globalThis', 'Buffer',
  'require', 'module', 'exports', '__dirname', '__filename',
]);

// 危险函数调用与构造（5.2/5.10/5.11/5.12）。
const FORBIDDEN_CALLS = new Set(['eval', 'Function', 'AsyncFunction']);
const FORBIDDEN_CONSTRUCTORS = new Set(['Function', 'AsyncFunction']); // new Function(...)（5.11）
const STRING_TIMER_CALLS = new Set(['setTimeout', 'setInterval', 'setImmediate']); // 字符串代码定时执行（5.2）
const NETWORK_BYPASS_CALLS = new Set(['fetch', 'XMLHttpRequest', 'WebSocket']); // 网络必须经 SDK/Agent（8.8）

// 原型链 / 属性污染（5.6）。
const POLLUTION_PROPS = new Set(['__proto__', 'constructor']);
const DEFINER_PROPS = new Set([
  '__defineGetter__', '__defineSetter__', '__lookupGetter__', '__lookupSetter__',
]);

// ------------------------------------------------------------------ 资源类上限（5.7/5.8/5.14/5.23）
const MAX_SOURCE_BYTES = 1024 * 1024;
const MAX_LINES = 5000;
const MAX_NODES = 20000;
const MAX_STRING_BYTES = 256 * 1024;
const MAX_AST_DEPTH = 256;
const MAX_FUNCTION_DEPTH = 4;      // 5.8 函数嵌套深度
const MAX_LOOP_DEPTH = 4;          // 5.8 循环嵌套深度
const MAX_TEMPLATE_EXPRESSIONS = 12; // 5.14 模板字符串插值数量
const MAX_COMPLEXITY_FN = 40;      // 5.23 单函数圈复杂度
const MAX_TOTAL_COMPLEXITY = 260;  // 5.23 全脚本圈复杂度

// 可疑字符串：命令执行 / 敏感路径 / 内网探测 / 逃逸模式（5.13）。
const SUSPICIOUS_PATTERNS = [
  'sh -c', 'bash -c', 'cmd /c', 'powershell',
  '/etc/passwd', '/etc/shadow', '/proc/self',
  '/var/run/docker.sock', '/run/docker.sock',
  '--privileged', 'mknod', 'kubelet',
  'process.binding', 'child_process',
  'constructor.constructor', '[].constructor',
  'node:child_process', 'node:fs', 'node:net',
];

// ------------------------------------------------------------------ 工具

function nodeLocation(node) {
  if (node && node.loc && node.loc.start) {
    return { line: node.loc.start.line, column: node.loc.start.column + 1 };
  }
  return { line: 0, column: 0 };
}

function finding(kind, node, message) {
  const loc = nodeLocation(node);
  return { type: kind, line: loc.line, column: loc.column, message };
}

function result(valid, kind, message, findings, metrics) {
  return {
    valid,
    error_type: kind,
    line: 0,
    column: 0,
    message,
    suggestion: '',
    findings,
    metrics,
  };
}

// 提取子节点（数组展开，忽略 loc/start/end/type 等元数据）。
function childNodes(node) {
  const out = [];
  for (const key of Object.keys(node)) {
    if (key === 'loc' || key === 'start' || key === 'end' || key === 'type' || key === 'range') {
      continue;
    }
    const value = node[key];
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item && typeof item === 'object' && typeof item.type === 'string') {
          out.push(item);
        }
      }
    } else if (value && typeof value === 'object' && typeof value.type === 'string') {
      out.push(value);
    }
  }
  return out;
}

function suspiciousLabel(value) {
  for (const pattern of SUSPICIOUS_PATTERNS) {
    if (value.includes(pattern)) {
      return pattern;
    }
  }
  return null;
}

// child 是否为“键”位置（否则会被当作值引用而触发宿主全局检查）。
function isKeyPosition(node, child) {
  if (node.type === 'MemberExpression' && !node.computed) {
    return node.property === child;
  }
  if ((node.type === 'Property' || node.type === 'PropertyDefinition' ||
       node.type === 'MethodDefinition') && !node.computed) {
    return node.key === child && node.shorthand !== true;
  }
  if (node.type === 'LabeledStatement' || node.type === 'BreakStatement' ||
      node.type === 'ContinueStatement') {
    return node.label === child;
  }
  return false;
}

function isFunctionNode(node) {
  return node.type === 'FunctionDeclaration' || node.type === 'FunctionExpression' ||
    node.type === 'ArrowFunctionExpression';
}

function isLoopNode(node) {
  return node.type === 'ForStatement' || node.type === 'ForInStatement' ||
    node.type === 'ForOfStatement' || node.type === 'WhileStatement' ||
    node.type === 'DoWhileStatement';
}

// ------------------------------------------------------------------ 解析

function parseSource(source) {
  const options = {
    ecmaVersion: 'latest',
    sourceType: 'module',
    locations: true,
    allowHashBang: true,
    allowReturnOutsideFunction: false,
  };
  try {
    return parse(source, options);
  } catch (moduleError) {
    try {
      return parse(source, { ...options, sourceType: 'script' });
    } catch (_scriptError) {
      throw moduleError; // 保留模块解析的原始诊断
    }
  }
}

// ------------------------------------------------------------------ 主校验

export default function validate(source, entrypoint) {
  const sourceBytes = Buffer.byteLength(source, 'utf8');
  if (sourceBytes > MAX_SOURCE_BYTES) {
    return result(false, 'RESOURCE_LIMIT', '脚本超过 1 MiB', [], {});
  }
  if (source.split('\n').length > MAX_LINES) {
    return result(false, 'RESOURCE_LIMIT', '脚本行数超过限制', [], {});
  }

  let tree;
  try {
    tree = parseSource(source);
  } catch (error) {
    const line = error && error.loc ? error.loc.line : 0;
    const column = error && error.loc ? error.loc.column + 1 : 0;
    return {
      valid: false,
      error_type: 'SYNTAX_ERROR',
      line,
      column,
      message: String((error && error.message) || '语法错误').replace(/\s*\(\d+:\d+\)$/, ''),
      suggestion: '请修复语法错误后重新校验',
      findings: [],
      metrics: { mode: 'ast-only' },
    };
  }

  const findings = [];
  let nodeCount = 0;
  let maxDepth = 0;
  let maxFnDepth = 0;
  let maxLoopDepth = 0;
  let totalComplexity = 1;
  const functions = [];
  const fnComplexity = new Map(); // 函数名 -> { count }

  // 显式栈遍历：{ node, depth, fnDepth, loopDepth, frame, asKey }
  // frame 是当前函数复杂度累积对象；顶层使用 shared 帧。
  const shared = { count: 1 };
  const stack = [{ node: tree, depth: 1, fnDepth: 0, loopDepth: 0, frame: shared, asKey: false }];

  while (stack.length > 0) {
    const entry = stack.pop();
    const node = entry.node;
    nodeCount += 1;
    if (entry.depth > maxDepth) maxDepth = entry.depth;
    if (entry.fnDepth > maxFnDepth) maxFnDepth = entry.fnDepth;
    if (entry.loopDepth > maxLoopDepth) maxLoopDepth = entry.loopDepth;

    const frame = entry.frame;
    const isDecision =
      node.type === 'IfStatement' || node.type === 'ForStatement' ||
      node.type === 'ForInStatement' || node.type === 'ForOfStatement' ||
      node.type === 'WhileStatement' || node.type === 'DoWhileStatement' ||
      node.type === 'CatchClause' || node.type === 'ConditionalExpression' ||
      node.type === 'SwitchCase' || node.type === 'LogicalExpression';
    if (isDecision) {
      frame.count += 1;
      totalComplexity += 1;
    }

    // ---------------------------------------------------------------- 静态规则
    if (node.type === 'ImportDeclaration') {
      const imported = node.source && node.source.value;
      if (!imported || !ALLOWED_MODULES.has(imported)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止导入模块：${imported || '(动态)'}`));
      }
    } else if (node.type === 'ImportExpression') {
      const imported = node.source && node.source.value;
      if (!imported || !ALLOWED_MODULES.has(imported)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止动态导入模块：${imported || '(动态)'}`));
      }
    } else if (node.type === 'ExportNamedDeclaration' || node.type === 'ExportAllDeclaration') {
      if (node.source) {
        const imported = node.source.value;
        if (!ALLOWED_MODULES.has(imported)) {
          findings.push(finding('SECURITY_VIOLATION', node, `禁止转出模块：${imported}`));
        }
      }
    } else if (node.type === 'Identifier') {
      if (!entry.asKey && HOST_GLOBALS.has(node.name) && isReferenceContext(node)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止访问宿主全局：${node.name}`));
      }
    } else if (node.type === 'Literal') {
      if (typeof node.value === 'string' && Buffer.byteLength(node.value, 'utf8') > MAX_STRING_BYTES) {
        findings.push(finding('RESOURCE_LIMIT', node, '字符串常量超过限制'));
      }
      if (typeof node.value === 'string') {
        const pattern = suspiciousLabel(node.value);
        if (pattern) {
          findings.push(finding('SUSPICIOUS_STRING', node, `检测到可疑字符串：${pattern}`));
        }
      }
    } else if (node.type === 'TemplateLiteral') {
      if (node.expressions.length > MAX_TEMPLATE_EXPRESSIONS) {
        findings.push(finding('RESOURCE_LIMIT', node, '模板字符串插值数量超过限制'));
      }
      const span = source.slice(node.start, node.end);
      if (span.length <= MAX_STRING_BYTES) {
        const pattern = suspiciousLabel(span);
        if (pattern) {
          findings.push(finding('SUSPICIOUS_STRING', node, `检测到可疑字符串：${pattern}`));
        }
      }
    } else if (node.type === 'CallExpression') {
      const callee = node.callee;
      if (callee.type === 'Identifier') {
        const name = callee.name;
        if (FORBIDDEN_CALLS.has(name)) {
          findings.push(finding('SECURITY_VIOLATION', node, `禁止动态执行调用：${name}`));
        }
        if (name === 'require') {
          const arg = node.arguments[0];
          const moduleName = arg && arg.type === 'Literal' ? String(arg.value) : null;
          if (!moduleName || !ALLOWED_MODULES.has(moduleName.replace(/^node:/, ''))) {
            findings.push(finding('SECURITY_VIOLATION', node,
              `禁止 require 加载非白名单模块：${moduleName || '(动态)'}`));
          }
        }
        if (STRING_TIMER_CALLS.has(name)) {
          const first = node.arguments[0];
          if (first && (first.type === 'Literal' || first.type === 'TemplateLiteral')) {
            findings.push(finding('SECURITY_VIOLATION', node, `禁止以字符串代码调用 ${name}`));
          }
        }
        if (NETWORK_BYPASS_CALLS.has(name)) {
          findings.push(finding('SECURITY_VIOLATION', node, `网络访问必须通过插件 SDK：${name}`));
        }
      } else if (callee.type === 'MemberExpression') {
        const propName = callee.property && callee.property.type === 'Identifier'
          ? callee.property.name : null;
        if (callee.object.type === 'Identifier' && callee.object.name === 'document' &&
            (propName === 'write' || propName === 'writeln')) {
          findings.push(finding('SECURITY_VIOLATION', node, '禁止直接向文档写入内容'));
        }
        // x.constructor.constructor('...') 原型链绕行逃逸（5.15）
        if (propName === 'constructor' &&
            callee.object.type === 'MemberExpression' &&
            callee.object.property && callee.object.property.type === 'Identifier' &&
            callee.object.property.name === 'constructor') {
          findings.push(finding('SECURITY_VIOLATION', node, '禁止原型链绕行调用'));
        }
        // WebAssembly.compile / WebAssembly.instantiate（5.12）
        if (callee.object.type === 'Identifier' && callee.object.name === 'WebAssembly') {
          findings.push(finding('SECURITY_VIOLATION', node, '禁止使用 WebAssembly'));
        }
        if (propName && HOST_GLOBALS.has(propName) && callee.object.type === 'Identifier' &&
            callee.object.name === 'globalThis') {
          findings.push(finding('SECURITY_VIOLATION', node, `禁止访问宿主全局：${propName}`));
        }
      }
      if (callee.type === 'Import') {
        const arg = node.arguments[0];
        const imported = arg && arg.type === 'Literal' ? String(arg.value) : null;
        if (!imported || !ALLOWED_MODULES.has(imported.replace(/^node:/, ''))) {
          findings.push(finding('SECURITY_VIOLATION', node,
            `禁止动态导入模块：${imported || '(动态)'}`));
        }
      }
    } else if (node.type === 'NewExpression') {
      const callee = node.callee;
      if (callee.type === 'Identifier' && FORBIDDEN_CONSTRUCTORS.has(callee.name)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止动态执行构造：new ${callee.name}`));
      } else if (callee.type === 'MemberExpression' &&
                 callee.property && callee.property.type === 'Identifier' &&
                 callee.property.name === 'constructor') {
        findings.push(finding('SECURITY_VIOLATION', node, '禁止原型链构造绕行'));
      }
    } else if (node.type === 'MemberExpression') {
      const propName = node.property && !node.computed && node.property.type === 'Identifier'
        ? node.property.name
        : (node.property && node.property.type === 'Literal' ? String(node.property.value) : null);
      if (node.property && node.property.type === 'Literal' && node.computed) {
        const value = String(node.property.value);
        if (POLLUTION_PROPS.has(value)) {
          findings.push(finding('SECURITY_VIOLATION', node, `禁止属性污染访问：${value}`));
        }
      }
      if (propName && POLLUTION_PROPS.has(propName)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止属性污染访问：${propName}`));
      }
      if (propName && DEFINER_PROPS.has(propName)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止对象内省/污染方法：${propName}`));
      }
      // Buffer.from / process.binding 等宿主成员访问
      if (node.object.type === 'Identifier' && HOST_GLOBALS.has(node.object.name)) {
        findings.push(finding('SECURITY_VIOLATION', node, `禁止访问宿主全局成员：${node.object.name}`));
      }
    } else if (node.type === 'FunctionDeclaration' || node.type === 'FunctionExpression' ||
               node.type === 'ArrowFunctionExpression') {
      if (node.id && node.id.type === 'Identifier') {
        functions.push(node.id.name);
      }
    }

    // ---------------------------------------------------------------- 子树入栈
    const children = childNodes(node);
    const isFn = isFunctionNode(node);
    const nextFrame = isFn ? { count: 1 } : frame;
    if (isFn) {
      fnComplexity.set((node.id && node.id.name) || '(anonymous)', nextFrame);
    }
    const nextFnDepth = entry.fnDepth + (isFn ? 1 : 0);
    const nextLoopDepth = entry.loopDepth + (isLoopNode(node) ? 1 : 0);
    for (let index = children.length - 1; index >= 0; index -= 1) {
      const child = children[index];
      stack.push({
        node: child,
        depth: entry.depth + 1,
        fnDepth: nextFnDepth,
        loopDepth: nextLoopDepth,
        frame: nextFrame,
        asKey: isKeyPosition(node, child),
      });
    }
  }

  // ---------------------------------------------------------------- 资源类汇总
  if (nodeCount > MAX_NODES) {
    return result(false, 'RESOURCE_LIMIT', 'AST 节点数量超过限制', [], { ast_nodes: nodeCount });
  }
  if (maxDepth > MAX_AST_DEPTH) {
    findings.push(finding('RESOURCE_LIMIT', tree, `AST 嵌套深度 ${maxDepth} 超过限制 ${MAX_AST_DEPTH}`));
  }
  if (maxFnDepth > MAX_FUNCTION_DEPTH) {
    findings.push(finding('RESOURCE_LIMIT', tree,
      `函数嵌套深度 ${maxFnDepth} 超过限制 ${MAX_FUNCTION_DEPTH}`));
  }
  if (maxLoopDepth > MAX_LOOP_DEPTH) {
    findings.push(finding('RESOURCE_LIMIT', tree,
      `循环嵌套深度 ${maxLoopDepth} 超过限制 ${MAX_LOOP_DEPTH}`));
  }
  if (totalComplexity > MAX_TOTAL_COMPLEXITY) {
    findings.push(finding('COMPLEXITY_LIMIT', tree,
      `脚本圈复杂度 ${totalComplexity} 超过限制 ${MAX_TOTAL_COMPLEXITY}`));
  }
  for (const [name, metricFrame] of fnComplexity) {
    if (metricFrame.count > MAX_COMPLEXITY_FN) {
      findings.push(finding('COMPLEXITY_LIMIT', tree,
        `函数 ${name} 圈复杂度超过限制 ${MAX_COMPLEXITY_FN}`));
    }
  }

  // 按类型+位置去重，避免同一节点多重命中时向 IDE 重复汇报（5.16）。
  const seen = new Set();
  const unique = [];
  for (const item of findings) {
    const key = `${item.type}:${item.line}:${item.column}`;
    if (!seen.has(key)) {
      seen.add(key);
      unique.push(item);
    }
  }
  findings.length = 0;
  for (const item of unique) {
    findings.push(item);
  }

  const valid = findings.length === 0;
  const first = findings[0] || {};
  return {
    valid,
    error_type: first.type || '',
    line: first.line || 0,
    column: first.column || 0,
    message: valid ? '校验通过' : first.message || '校验失败',
    suggestion: valid ? '' : '请移除危险能力或降低代码复杂度',
    findings,
    metrics: {
      mode: 'ast-only',
      source_bytes: sourceBytes,
      lines: source.split('\n').length,
      ast_nodes: nodeCount,
      functions: [...new Set(functions)].sort(),
      function_depth: maxFnDepth,
      loop_depth: maxLoopDepth,
      cyclomatic: totalComplexity,
    },
  };
}

// Identifier 是否为值引用上下文（避免对声明名/键名误报宿主全局）。
function isReferenceContext() {
  return true; // 键位置已在 isKeyPosition 处理，其余 Identifier 一律按引用对待
}

// ------------------------------------------------------------------ CLI 入口

if (process.argv[1] && pathToFileURL(process.argv[1]).href === new URL(import.meta.url).href) {
  const astOnly = process.argv.includes('--ast-only');
  let request = {};
  try {
    request = JSON.parse(readFileSync(0, 'utf8'));
  } catch (_error) {
    request = {};
  }
  const response = validate(String(request.source || ''), String(request.entrypoint || ''));
  response.metrics.ast_only = Boolean(astOnly);
  process.stdout.write(JSON.stringify(response));
}
