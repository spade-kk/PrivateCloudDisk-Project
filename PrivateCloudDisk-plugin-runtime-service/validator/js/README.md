# vendored acorn（JavaScript AST 解析器）

`acorn.mjs` 是 [acorn](https://github.com/acornjs/acorn) 8.15.0 的官方 ESM 构建产物
（`dist/acorn.mjs`），由 Runtime 内 `validator/validate_js.mjs` 加载用于本地插件源码的
AST 静态校验。**本文件是生成产物，禁止手工修改**。

- 用途：需求五（5.1）要求的“使用 acorn/esprima 解析插件源码并生成 AST”。
- 来源：`acorn` npm 包 `dist/acorn.mjs`，版本 8.15.0。
- 许可：MIT（见同目录 `LICENSE`）。
- 升级方式：替换 `acorn.mjs` 与 `LICENSE`，并运行 `node validator/test_validate_js.mjs`
  执行完整规则回归；依赖上线前做依赖漏洞扫描（9.16/10.20）。
- 为什么 vendor：插件运行时所在节点通常无法访问 npm registry；将解析器作为固定资产随
  运行时镜像发布，保证校验结果可复现、不随依赖漂移。
