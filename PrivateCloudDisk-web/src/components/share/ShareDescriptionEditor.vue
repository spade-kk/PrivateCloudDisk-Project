<template>
  <div class="rich-editor" :class="{ 'rich-editor--focused': focused }">
    <div class="editor-toolbar" role="toolbar" aria-label="分享说明格式">
      <button type="button" title="加粗" aria-label="加粗" @mousedown.prevent="runCommand('bold')">
        <i class="fa fa-bold" aria-hidden="true"></i>
      </button>
      <button type="button" title="斜体" aria-label="斜体" @mousedown.prevent="runCommand('italic')">
        <i class="fa fa-italic" aria-hidden="true"></i>
      </button>
      <button type="button" title="下划线" aria-label="下划线" @mousedown.prevent="runCommand('underline')">
        <i class="fa fa-underline" aria-hidden="true"></i>
      </button>
      <span aria-hidden="true"></span>
      <button type="button" title="无序列表" aria-label="无序列表" @mousedown.prevent="runCommand('insertUnorderedList')">
        <i class="fa fa-list-ul" aria-hidden="true"></i>
      </button>
      <button type="button" title="有序列表" aria-label="有序列表" @mousedown.prevent="runCommand('insertOrderedList')">
        <i class="fa fa-list-ol" aria-hidden="true"></i>
      </button>
      <button type="button" title="引用" aria-label="引用" @mousedown.prevent="runCommand('formatBlock', 'blockquote')">
        <i class="fa fa-quote-left" aria-hidden="true"></i>
      </button>
    </div>
    <div
      ref="editor"
      class="editor-content"
      contenteditable="true"
      role="textbox"
      aria-multiline="true"
      :aria-label="label"
      :data-placeholder="placeholder"
      @focus="focused = true"
      @blur="focused = false"
      @input="handleInput"
      @paste="handlePaste"
    ></div>
    <div class="editor-footer">
      <span>支持加粗、列表和引用，危险内容会在展示前过滤</span>
      <span>{{ textLength }}/{{ maxLength }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  maxLength?: number
}>(), {
  label: '分享说明',
  placeholder: '补充文件用途、使用方式或注意事项…',
  maxLength: 10000,
})

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const editor = ref<HTMLDivElement | null>(null)
const focused = ref(false)
const textLength = computed(() => editor.value?.innerText.length || 0)

function syncFromModel() {
  if (!editor.value || editor.value.innerHTML === props.modelValue) return
  editor.value.innerHTML = props.modelValue || ''
}

function emitValue() {
  if (!editor.value) return
  if (editor.value.innerText.length > props.maxLength) {
    editor.value.innerHTML = props.modelValue || ''
    return
  }
  emit('update:modelValue', editor.value.innerHTML)
}

function handleInput() {
  emitValue()
}

function runCommand(command: string, value?: string) {
  editor.value?.focus()
  document.execCommand(command, false, value)
  emitValue()
}

function handlePaste(event: ClipboardEvent) {
  const text = event.clipboardData?.getData('text/plain') || ''
  if (!text || textLength.value + text.length > props.maxLength) return
  document.execCommand('insertText', false, text)
  emitValue()
}

watch(() => props.modelValue, () => void nextTick(syncFromModel), { immediate: true })
</script>

<style scoped>
.rich-editor { overflow: hidden; border: 1px solid #e4e7ed; border-radius: 12px; background: #fff; transition: border-color .16s ease, box-shadow .16s ease; }
.rich-editor--focused { border-color: #7ba3ff; box-shadow: 0 0 0 3px rgba(22,93,255,.12); }
.editor-toolbar { display: flex; align-items: center; gap: 3px; min-height: 42px; padding: 5px 8px; border-bottom: 1px solid #edf0f4; background: #fafbfd; }
.editor-toolbar button { display: inline-flex; width: 34px; height: 32px; align-items: center; justify-content: center; border: 0; border-radius: 8px; background: transparent; color: #67758a; cursor: pointer; }
.editor-toolbar button:hover { background: #eaf1ff; color: #165dff; }
.editor-toolbar button:focus-visible { outline: 3px solid rgba(22,93,255,.2); outline-offset: 1px; }
.editor-toolbar > span { width: 1px; height: 20px; margin: 0 4px; background: #dfe5ed; }
.editor-content { min-height: 112px; max-height: 220px; overflow-y: auto; padding: 12px 14px; color: #475569; font-size: 13px; line-height: 1.7; outline: none; overflow-wrap: anywhere; }
.editor-content:empty::before { content: attr(data-placeholder); color: #a3adba; pointer-events: none; }
.editor-content :deep(p) { margin: 0 0 8px; }
.editor-content :deep(ul),
.editor-content :deep(ol) { padding-left: 22px; }
.editor-content :deep(blockquote) { margin: 8px 0; padding: 7px 10px; border-left: 3px solid #7ba3ff; background: #f5f8ff; }
.editor-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 36px; padding: 7px 12px; border-top: 1px solid #edf0f4; background: #fafbfd; color: #929dab; font-size: 10px; }
@media (max-width: 480px) { .editor-footer { align-items: flex-start; flex-direction: column; gap: 2px; } }
</style>
