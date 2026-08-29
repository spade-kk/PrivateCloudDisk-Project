# multi_entry_pkg 多步骤入口链

- step_a：标题行大写 → 写回候选
- step_b：读 step_a 输出，首行前追加 `# PROCESSED` → 写回候选
- 验证：末入口修改生效并提交候选，`CompletedEntrypoints=2`
- 用例：需求二 2.17 / 一 1.7 / 八 8.27
