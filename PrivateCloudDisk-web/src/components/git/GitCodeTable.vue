<template>
  <div class="git-code-table" :class="{ 'is-wrapped': wrap }">
    <div v-for="(line, index) in lines" :key="index" class="git-code-table__row" :class="{ 'is-selected': selectedLines.includes(index + 1) }">
      <button type="button" class="git-code-table__line" :aria-label="`选择第 ${index + 1} 行`" @click="$emit('select-line', index + 1, $event)">{{ index + 1 }}</button>
      <span v-if="blameLines[index]" class="git-code-table__blame" :title="blameLines[index].hash"><b>{{ blameLines[index].author || '未知作者' }}</b><small>{{ blameLines[index].hash.slice(0, 8) }}</small></span>
      <code class="git-code-table__content" v-html="highlightedLines[index] || '&nbsp;'"></code>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { GitBlameLine } from '@/utils/gitRepositoryPresentation'

defineProps<{ lines: string[]; highlightedLines: string[]; selectedLines: number[]; blameLines: GitBlameLine[]; wrap?: boolean }>()
defineEmits<{ 'select-line': [line: number, event: MouseEvent] }>()
</script>
