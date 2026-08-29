<template>
  <span
    class="file-type-icon"
    :class="[`file-type-icon--${descriptor.kind}`, iconModeClass, { 'file-type-icon--thumbnail': Boolean(thumbnailUrl) && !thumbnailFailed }]"
    :style="iconStyle"
    :role="ariaLabel ? 'img' : undefined"
    :aria-label="ariaLabel"
    :title="title || undefined"
  >
    <img
      v-if="thumbnailUrl && !thumbnailFailed"
      class="file-type-icon__thumbnail"
      :src="thumbnailUrl"
      :alt="ariaLabel"
      loading="lazy"
      decoding="async"
      @error="handleThumbnailError"
    />
    <img
      v-else-if="descriptor.dynamicSvg"
      class="file-type-icon__dynamic"
      :src="descriptor.dynamicSvg"
      :alt="ariaLabel"
      aria-hidden="true"
    />
    <!-- AUDIT FIX [2.1-2.8/6.1-6.10]：已识别类型统一使用本地离线 VS Code Icons；动态 SVG 仅处理未知类型。 -->
    <Icon
      v-else-if="descriptor.iconName"
      :icon="descriptor.iconName"
      class="file-type-icon__library"
      width="1em"
      height="1em"
      aria-hidden="true"
    />
    <!-- 识别结果异常时仍回退到成熟图标库，不回退到自绘首字母 SVG。 -->
    <Icon
      v-else
      icon="vscode-icons:default-file"
      class="file-type-icon__library"
      width="1em"
      height="1em"
      aria-hidden="true"
    />
  </span>
</template>

<script setup lang="ts">
import { computed, inject, ref, watch } from 'vue'
import { Icon } from '@iconify/vue/offline'
import { resolveFileTypeIcon } from '@/utils/fileTypeIcons'
import { registerVscodeFileIcons } from '@/utils/vscodeFileIconRegistry'

registerVscodeFileIcons()

/**
 * [REQ-GIT-ICON-20260818] 图标颜色填充模式。
 * - full-color：保留图标原始多彩（默认，行为与扩展前完全一致）。
 * - monochrome：单色，颜色取 customColor（若传）否则取 CSS 变量 --git-icon-color（缺省回退 currentColor）。
 * - monochrome-inverse：反色单色，暗色主题显示浅色、亮色主题显示深色，
 *   取 CSS 变量 --git-icon-color-inverse（缺省回退 --git-icon-color / currentColor）。
 * - github：GitHub 风格预设，等同 monochrome 并按主题自动取灰/白（--git-icon-color）。
 */
export type FileIconColorMode = 'full-color' | 'monochrome' | 'monochrome-inverse' | 'github'

const props = withDefaults(defineProps<{
  fileName: string
  path?: string
  isDirectory?: boolean
  mimeType?: string
  thumbnailUrl?: string
  alt?: string
  title?: string
  /** 颜色填充模式，默认 full-color 保持原彩色图标。 */
  colorMode?: FileIconColorMode
  /** 自定义单色值，仅在 colorMode 为 monochrome 时生效。 */
  customColor?: string
}>(), {
  path: '',
  isDirectory: false,
  mimeType: '',
  thumbnailUrl: '',
  alt: '',
  title: '',
  // 默认取页面级 provide（gitIconColorMode），未提供时回退 full-color 保持向后兼容。
  colorMode: undefined as FileIconColorMode | undefined,
  customColor: '',
})

const emit = defineEmits<{
  'thumbnail-error': [fileName: string]
}>()

const thumbnailFailed = ref(false)
const descriptor = computed(() => resolveFileTypeIcon({
  fileName: props.fileName,
  path: props.path || props.fileName,
  isDirectory: props.isDirectory,
  mimeType: props.mimeType,
}))
const ariaLabel = computed(() => props.alt || `${descriptor.value.label}：${props.fileName}`)

// —— 单色模式计算（[REQ-GIT-ICON-20260818]）——
/** 页面级默认颜色模式：Git 仓库页通过 provide 注入 monochrome（需求 3.16）。 */
const providedColorMode = inject<FileIconColorMode>('gitIconColorMode', 'full-color')

/** 生效的颜色模式 = 显式 prop（优先）→ 页面级 provide → full-color。 */
const effectiveColorMode = computed<FileIconColorMode>(() => props.colorMode ?? providedColorMode)
const isMono = computed(() => effectiveColorMode.value !== 'full-color')

/** 单色模式类名；纯计算，结果稳定，便于 Vue 复用而不重复创建。 */
const iconModeClass = computed<string>(() => {
  if (effectiveColorMode.value === 'full-color') return ''
  if (effectiveColorMode.value === 'monochrome-inverse') return 'file-type-icon--mono file-type-icon--inverse'
  // monochrome 与 github 预设走同一路径。
  return 'file-type-icon--mono'
})

/**
 * 单色时给根元素注入 color：
 * - customColor 优先级最高；
 * - monochrome-inverse 使用反色变量；
 * - 其余（monochrome / github）使用 --git-icon-color（主题灰），缺省回退 currentColor。
 * 返回对象在 full-color 时不附加 color，完全保持旧行为。
 */
const iconStyle = computed(() => {
  const style: Record<string, string> = { '--file-icon-color': descriptor.value.color }
  if (!isMono.value) return style
  if (effectiveColorMode.value === 'monochrome' && props.customColor) {
    style.color = props.customColor
  } else if (effectiveColorMode.value === 'monochrome-inverse') {
    style.color = 'var(--git-icon-color-inverse, var(--git-icon-color, currentColor))'
  } else {
    style.color = 'var(--git-icon-color, currentColor)'
  }
  return style
})

function handleThumbnailError() {
  thumbnailFailed.value = true
  emit('thumbnail-error', props.fileName)
}

watch(() => props.thumbnailUrl, () => {
  thumbnailFailed.value = false
})
</script>

<style scoped>
/* AUDIT FIX [8.4-8.9]：组件自身只占 1em，网格/列表继续由原有父容器控制尺寸。 */
.file-type-icon {
  --file-icon-color: #64748b;
  display: inline-flex;
  flex: 0 0 auto;
  width: 1em;
  height: 1em;
  min-width: 0;
  min-height: 0;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  /* 单色模式由 iconStyle 注入 color；彩色模式由 --file-icon-color 右侧回退保持现状。 */
  color: var(--file-icon-color);
  line-height: 1;
  vertical-align: middle;
}

.file-type-icon__dynamic,
.file-type-icon__thumbnail,
.file-type-icon__library {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
  max-height: 100%;
  box-sizing: border-box;
  flex: 0 0 100%;
}

/* AUDIT FIX [8.4-8.9]：Iconify 将真实 SVG 作为 Icon 组件根节点输出，显式锁定内层 SVG，
   使父级 font-size、width、height 以及 Tailwind text-* 尺寸均能控制最终图标，不再溢出。 */
.file-type-icon :deep(svg.file-type-icon__library) {
  display: block;
  width: 100% !important;
  height: 100% !important;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
  max-height: 100%;
  box-sizing: border-box;
}

.file-type-icon__thumbnail {
  border-radius: 4px;
  object-fit: cover;
}

.file-type-icon--thumbnail {
  overflow: hidden;
  border-radius: 4px;
}

/* ============================================================
 * [REQ-GIT-ICON-20260818] 单色（monochrome / github / monochrome-inverse）
 * ------------------------------------------------------------
 * 原理：VS Code Icons 是带内联 fill 的多彩 SVG。CSS 中「表现属性」优先级低于任意 CSS 规则，
 * 因此在 --mono 作用域内用 `fill: currentColor !important` 覆盖所有内部路径为当前
 * 文字颜色（由根元素 color 注入为 var(--git-icon-color)），即可得到与原文一致的
 * 单色形状，图标尺寸 / 对齐 / 间距均不变。此方案为需求 2.4/2.5 的首选实现；
 * mask 方案（2.6/2.7）与该实现结果等价，可作为备选。以下同时保留 mask 变量钩子
 * 与 filter 降级（2.8），便于按主题继续微调。
 * ============================================================ */
.file-type-icon--mono {
  /* 主题灰：由 .git-repository-panel 定义 --git-icon-color；缺省回退 currentColor。 */
  color: var(--git-icon-color, currentColor);
}

/* 内联库图标：覆盖内联 fill，转为纯单色形状（保留原有描边/形状）。 */
.file-type-icon--mono :deep(svg),
.file-type-icon--mono :deep(svg *),
.file-type-icon--mono :deep(svg path),
.file-type-icon--mono :deep(svg rect),
.file-type-icon--mono :deep(svg circle),
.file-type-icon--mono :deep(svg polygon),
.file-type-icon--mono :deep(svg polyline) {
  fill: currentColor !important;
  stroke: currentColor !important;
}

/* 反色单色：颜色来自 --git-icon-color-inverse（缺省回退主题灰/currentColor）。 */
.file-type-icon--inverse {
  color: var(--git-icon-color-inverse, var(--git-icon-color, currentColor));
}

/* 未知后缀动态 SVG 以 <img> 呈现，无法用 CSS 改内部 fill，改用主题感知的
   grayscale 滤镜降级（需求 2.8）：亮色取近黑，暗色经 invert 取近白。
   注意：库图标走 currentColor 覆盖，不套用 filter（brightness(0) 会把主题灰压成纯黑）。 */
.file-type-icon--mono .file-type-icon__dynamic {
  filter: var(--git-icon-filter, grayscale(1) brightness(0) contrast(1.2));
}

/* 兼容旧浏览器的 mask 方案钩子：如需 mask 渲染，可在消费侧设置
   --git-icon-mask 为图标 mask 地址，并用 background-color 着色。默认不使用。
   为了 -webkit- 前缀覆盖（需求 5.7），若启用请同时提供 -webkit-mask。 */
.file-type-icon--mono-mask {
  -webkit-mask-image: var(--git-icon-mask);
  mask-image: var(--git-icon-mask);
  -webkit-mask-size: contain;
  mask-size: contain;
  -webkit-mask-repeat: no-repeat;
  mask-repeat: no-repeat;
  -webkit-mask-position: center;
  mask-position: center;
  background-color: var(--git-icon-color, currentColor);
}
.file-type-icon--mono-mask svg,
.file-type-icon--mono-mask img {
  visibility: hidden;
}
</style>
