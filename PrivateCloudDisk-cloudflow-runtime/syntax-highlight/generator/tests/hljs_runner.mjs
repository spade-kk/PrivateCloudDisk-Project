// 用真实 highlight.js 引擎验证生成的 cloudflow.hljs.js（由 Python 测试通过 env 调用）。
// 用法：HLJS_PATH=... GEN_PATH=... SAMPLE_PATH=... node hljs_runner.mjs
// 输出 JSON：{ out: 高亮后 HTML, err: 异常消息|null }
import fs from 'node:fs';
import vm from 'node:vm';

const hl = fs.readFileSync(process.env.HLJS_PATH, 'utf8');
const cx = vm.createContext({});
vm.runInContext(hl, cx);
const hljs = cx.hljs;

const gen = fs.readFileSync(process.env.GEN_PATH, 'utf8');
const cx2 = vm.createContext({});
cx2.hljs = hljs;
vm.runInContext(gen, cx2);

const source = fs.readFileSync(process.env.SAMPLE_PATH, 'utf8');
let out = null, err = null;
try {
  out = hljs.highlight(source, { language: 'cloudflow', ignoreIllegals: true }).value;
} catch (e) {
  err = e.message;
}
console.log(JSON.stringify({ out, err }));
