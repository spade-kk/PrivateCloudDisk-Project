/**
 * Backwards-compatible entry point for safe Markdown rendering.
 *
 * AI task blocks now use AiMarkdownRenderer directly so they can attach copy
 * controls, table preview actions, formulas and Mermaid enhancement after the
 * sanitized HTML is mounted. Existing consumers can continue importing this
 * helper, but no caller receives an escape-only regular-expression renderer.
 */
export { escapeHtml, renderAiMarkdown as renderSafeMarkdown, sanitizeAiMarkdownHtml } from './aiMarkdown'
