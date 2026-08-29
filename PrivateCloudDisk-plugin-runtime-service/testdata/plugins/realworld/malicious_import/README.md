# malicious_import 恶意模块导入尝试

- 样本内容：`import os / subprocess / sys`
- 防护：**发布门禁 AST 校验拒绝（SECURITY_VIOLATION）**，不进入执行阶段
- 验证：`validator/test_validate_python.py` 中对应拒绝用例；测试数据目录清单见
  `testdata/plugins/realworld/README.md`
- 用例：需求二 2.11 / 七 7.13 / 八 8.20
