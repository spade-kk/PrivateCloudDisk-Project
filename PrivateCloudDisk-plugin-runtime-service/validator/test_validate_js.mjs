// 云插件 JavaScript AST 静态校验器单元测试（需求五 5.23）。
// 用法：node validator/test_validate_js.mjs
import assert from 'node:assert/strict';
import validate from './validate_js.mjs';

const cases = [
  // --- 允许场景 ---
  ['SDK 导入与调用', `import { invoke } from "plugin-sdk";\nexport const main = () => invoke("file.read", { id: 1 });`, true],
  ['普通函数与 try/catch', `function a(){ try { return 1; } catch (e) { throw e; } }\na();`, true],
  ['对象键名不误报', `const cfg = { process: 1, module: 2, global: 3 };\nexport { cfg };`, true],
  ['setTimeout 使用函数回调', `setTimeout(() => { work(); }, 100);`, true],

  // --- 动态执行（5.2/5.11） ---
  ['eval', `eval("1+1");`, false],
  ['new Function', `const f = new Function("return 1");`, false],
  ['new AsyncFunction', `const f = new AsyncFunction("return 1");`, false],
  ['Function 调用', `Function("return 1")();`, false],

  // --- 模块加载（5.3/5.9） ---
  ['require(fs)', `const fs = require("fs");`, false],
  ['import child_process', `import cp from "child_process";`, false],
  ['动态 import(net)', `const m = await import("net");`, false],
  ['导出重导出 fs', `export { read } from "fs";`, false],

  // --- 宿主全局（5.4/5.5） ---
  ['process.binding', `process.binding("fs");`, false],
  ['global.process', `global.process.env;`, false],
  ['globalThis', `const g = globalThis;`, false],
  ['Buffer.from', `Buffer.from("x");`, false],
  ['__dirname', `const p = __dirname;`, false],

  // --- 定时器字符串代码（5.2） ---
  ['setTimeout 字符串', `setTimeout("alert(1)", 100);`, false],
  ['setInterval 字符串模板', `setInterval(\`x\`, 100);`, false],

  // --- 原型链/属性污染（5.6/5.15） ---
  ['obj.__proto__', `const p = obj.__proto__;`, false],
  ['obj.constructor', `const c = obj.constructor;`, false],
  ['下标 __proto__', `const p = obj["__proto__"];`, false],
  ['constructor.constructor 调用', `const f = obj.constructor.constructor("return 1");`, false],
  ['实例调用内省方法', `obj.__defineGetter__("x", () => 1);`, false],

  // --- 平台/网络绕行（5.12） ---
  ['WebAssembly', `WebAssembly.compile(bytes);`, false],
  ['fetch 绕过 SDK', `fetch("https://evil.example");`, false],
  ['document.write', `document.write("<script>");`, false],

  // --- 可疑字符串（5.13） ---
  ['敏感路径字符串', `const p = "/etc/passwd";`, false],
  ['逃逸模式字符串', `const s = "constructor.constructor";`, false],

  // --- 资源限制（5.7/5.8） ---
  ['循环嵌套过深', `for(let i=0;i<1;i++){for(let j=0;j<1;j++){for(let k=0;k<1;k++){for(let m=0;m<1;m++){for(let n=0;n<1;n++){ void 0; }}}}}`, false],

  // --- 语法错误（5.16） ---
  ['语法错误', `function {`, false],
];

let failed = 0;
for (const [name, source, expected] of cases) {
  const response = validate(source, 'main.js');
  const ok = response.valid === expected;
  const detail = response.valid ? 'valid' : `${response.error_type}: ${response.message}`;
  if (!ok) {
    failed += 1;
    console.error(`FAIL  ${name} 期望 valid=${expected}，实际 ${detail}`);
  } else {
    console.log(`ok    ${name}  (${detail})`);
  }
}
if (failed > 0) {
  console.error(`\n${failed}/${cases.length} 个用例失败`);
  process.exit(1);
}
console.log(`\n全部 ${cases.length} 个用例通过`);
