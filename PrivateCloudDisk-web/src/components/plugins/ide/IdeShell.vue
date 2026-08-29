<template>
  <section
    class="ide-shell"
    :class="{
      'ide-shell--fullscreen': fullscreen,
      'ide-shell--left-collapsed': leftCollapsed,
      'ide-shell--right-collapsed': rightCollapsed,
      'ide-shell--bottom-collapsed': bottomCollapsed,
      'ide-shell--mobile-panel-open': mobilePanelOpen,
    }"
    aria-label="插件开发工作区"
  >
    <!--
      Web IDE 需求 1/8：统一承载顶部工具栏、双侧栏、编辑区和底部面板。
      原页面的 PageHeader 仍可继续使用；新增工作区通过插槽承载业务控件，避免
      把插件开发状态耦合到控制台布局，也便于后续工作流 IDE 复用同一外壳。
    -->
    <header class="ide-shell__topbar">
      <div class="ide-shell__topbar-main">
        <button
          class="ide-shell__icon-button lg:hidden"
          type="button"
          aria-label="打开文件和功能面板"
          :aria-expanded="!leftCollapsed"
          @click="$emit('toggle-left')"
        >
          <i class="fa fa-bars" aria-hidden="true"></i>
        </button>
        <slot name="topbar">
          <strong class="ide-shell__fallback-title">{{ title }}</strong>
        </slot>
      </div>
      <div class="ide-shell__topbar-actions">
        <span v-if="dirty" class="ide-shell__dirty" role="status" aria-live="polite">未保存更改</span>
        <span v-else-if="saveState === 'saving'" class="ide-shell__saving" role="status" aria-live="polite">保存中…</span>
        <span v-else-if="saveState === 'saved'" class="ide-shell__saved" role="status" aria-live="polite">已保存</span>
        <button
          class="ide-shell__icon-button lg:hidden"
          type="button"
          aria-label="打开属性面板"
          :aria-expanded="!rightCollapsed"
          @click="$emit('toggle-right')"
        >
          <i class="fa fa-sliders" aria-hidden="true"></i>
        </button>
        <button
          class="ide-shell__icon-button"
          type="button"
          :aria-label="fullscreen ? '退出全屏编辑' : '全屏编辑'"
          :title="fullscreen ? '退出全屏编辑' : '全屏编辑'"
          @click="$emit('toggle-fullscreen')"
        >
          <i :class="fullscreen ? 'fa fa-compress' : 'fa fa-expand'" aria-hidden="true"></i>
        </button>
      </div>
    </header>

    <!--
      [IDE-RESP-2026-08 / 3.9、4.1、5.18] 小屏抽屉打开时由同一遮罩负责
      关闭；桌面端不渲染，避免修改原有多栏工作区交互。
    -->
    <button
      v-if="mobilePanelOpen"
      class="ide-shell__mobile-scrim"
      type="button"
      aria-label="关闭当前 IDE 面板"
      @click="$emit('close-mobile-panels')"
    ></button>

    <div class="ide-shell__body">
      <aside v-if="!leftCollapsed" class="ide-shell__left" aria-label="项目导航面板">
        <slot name="sidebar"></slot>
      </aside>
      <main class="ide-shell__main">
        <slot name="editor"></slot>
        <section v-if="!bottomCollapsed" class="ide-shell__bottom" aria-label="输出与问题面板">
          <slot name="bottom"></slot>
        </section>
      </main>
      <aside v-if="!rightCollapsed" class="ide-shell__right" aria-label="属性和配置面板">
        <slot name="right"></slot>
      </aside>
    </div>

    <button
      v-if="!fullscreen && bottomCollapsed"
      class="ide-shell__bottom-trigger"
      type="button"
      aria-label="展开输出和问题面板"
      @click="$emit('toggle-bottom')"
    >
      <i class="fa fa-terminal" aria-hidden="true"></i>
      <span>输出 / 问题</span>
    </button>
  </section>
</template>

<script setup lang="ts">
/**
 * IDE 外壳只负责布局与面板开关，不持有插件业务数据。
 * 这样云插件、本地插件、工作流页面可以共用外壳并保持各自 store 独立。
 */
withDefaults(defineProps<{
  title?: string
  fullscreen?: boolean
  leftCollapsed?: boolean
  rightCollapsed?: boolean
  bottomCollapsed?: boolean
  mobilePanelOpen?: boolean
  dirty?: boolean
  saveState?: 'idle' | 'saving' | 'saved' | 'error'
}>(), {
  title: '插件开发工作区',
  fullscreen: false,
  leftCollapsed: false,
  rightCollapsed: false,
  bottomCollapsed: false,
  mobilePanelOpen: false,
  dirty: false,
  saveState: 'idle',
})

defineEmits<{
  'toggle-left': []
  'toggle-right': []
  'toggle-bottom': []
  'toggle-fullscreen': []
  'close-mobile-panels': []
}>()
</script>

<style scoped>
.ide-shell {
  --ide-border: #273244;
  --ide-panel: #172033;
  --ide-surface: #111827;
  --ide-text: #cbd5e1;
  position: relative;
  display: flex;
  height: min(820px, calc(100dvh - 32px));
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--ide-border);
  border-radius: 16px;
  background: var(--ide-surface);
  color: var(--ide-text);
  box-shadow: 0 16px 45px rgba(15, 23, 42, .18);
}
.ide-shell--fullscreen {
  position: fixed;
  inset: 0;
  z-index: 120;
  min-height: 100dvh;
  border-radius: 0;
}
.ide-shell__topbar {
  display: flex;
  min-height: 52px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--ide-border);
  background: var(--ide-panel);
}
.ide-shell__topbar-main,
.ide-shell__topbar-actions { display: flex; min-width: 0; align-items: center; gap: 8px; }
.ide-shell__fallback-title { overflow: hidden; color: #f8fafc; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.ide-shell__icon-button {
  display: inline-flex;
  min-width: 34px;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #94a3b8;
  transition: background-color .15s ease, color .15s ease;
}
.ide-shell__icon-button:hover,
.ide-shell__icon-button:focus-visible { background: #273244; color: #fff; outline: none; }
.ide-shell__dirty { color: #fbbf24; font-size: 11px; }
.ide-shell__saving { color: #93c5fd; font-size: 11px; }
.ide-shell__saved { color: #86efac; font-size: 11px; }
.ide-shell__body { display: flex; min-height: 0; flex: 1; overflow: hidden; }
.ide-shell__left,
.ide-shell__right { min-width: 0; overflow: auto; background: #f8fafc; color: #334155; }
.ide-shell__left { width: 260px; flex: 0 0 260px; border-right: 1px solid #dbe4f0; }
.ide-shell__right { width: 320px; flex: 0 0 320px; border-left: 1px solid #dbe4f0; }
.ide-shell__main { display: flex; min-width: 0; min-height: 0; height: 100%; flex: 1; flex-direction: column; overflow: hidden; background: #0f172a; }
.ide-shell__main > :first-child { min-height: 0; flex: 1; }
.ide-shell__bottom { min-height: 160px; max-height: 42%; overflow: auto; border-top: 1px solid var(--ide-border); background: #111827; }
.ide-shell__bottom-trigger { position: absolute; right: 14px; bottom: 10px; z-index: 2; display: inline-flex; min-height: 36px; align-items: center; gap: 7px; padding: 0 12px; border-radius: 9px; background: #273244; color: #cbd5e1; font-size: 12px; }
.ide-shell__bottom-trigger:hover { background: #334155; color: #fff; }
@media (max-width: 1279px) {
  .ide-shell__right { width: 290px; flex-basis: 290px; }
}
@media (max-width: 767px) {
  .ide-shell { height: calc(100dvh - 16px); min-height: 0; border-radius: 12px; }
  .ide-shell__left,
  /* [IDE-RESP-2026-08 / 遮罩层级修复] 原值 z-index: 5 会被 80 层蒙版覆盖。
     抽屉必须在同一 Shell 的遮罩之上，且不跨越全局弹窗层。 */
  .ide-shell__right { position: absolute; inset: 52px auto 0 0; z-index: var(--ide-z-drawer, 90); width: min(88vw, 320px); box-shadow: 12px 0 30px rgba(15, 23, 42, .22); }
  .ide-shell__right { right: 0; left: auto; box-shadow: -12px 0 30px rgba(15, 23, 42, .22); }
  .ide-shell__bottom { min-height: 130px; max-height: 40%; }
  .ide-shell__dirty,
  .ide-shell__saving,
  .ide-shell__saved { display: none; }
}
@media (prefers-reduced-motion: reduce) {
  .ide-shell__icon-button { transition: none; }
}
</style>
