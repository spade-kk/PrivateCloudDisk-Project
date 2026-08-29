// ============================================================
// cloudflow.ts — CloudFlow DSL 语言注册（Monaco Monarch）
// ============================================================
// 设计：
//   - 语法高亮规则不在此处硬编码，而是引用生成自 GRAMMAR.pest + AST.rs
//     的统一规范产物 cloudflow.monarch.json（唯一事实来源）。
//   - DSL 语法变更时，只需重新运行 runtime 的生成器并把新产物复制到
//     src/languages/cloudflow.monarch.json，无需改动本文件或任何编辑器组件。
//
// 更新流程（详见 docs/CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md）：
//   cd PrivateCloudDisk-cloudflow-runtime
//   python3 syntax-highlight/generator/build_spec.py --force
//   python3 syntax-highlight/generator/convert.py --format monarch --verbose
//   cp syntax-highlight/build/cloudflow.monarch.json ../PrivateCloudDisk-web/src/languages/cloudflow.monarch.json
// ============================================================

// 从统一规范生成的高亮规则（tokenizer/scope 完整映射）。
// eslint-disable-next-line @typescript-eslint/no-explicit-any
import cloudflowMonarch from '@/languages/cloudflow.monarch.json'

/**
 * 注册 CloudFlow 语言（幂等）。
 * @param monaco 已加载的 Monaco 实例
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function registerCloudFlowLanguage(monaco: any): void {
  if (monaco.languages.getLanguages().some((language: { id: string }) => language.id === 'cloudflow')) {
    return
  }
  monaco.languages.register({ id: 'cloudflow' })
  // Monarch 只负责视觉词法高亮；语法/语义结论仍以 Rust CloudFlow Compiler 为唯一真源。
  monaco.languages.setMonarchTokensProvider('cloudflow', {
    tokenPostfix: '.cloudflow',
    ...(cloudflowMonarch as Record<string, unknown>),
  })
}
