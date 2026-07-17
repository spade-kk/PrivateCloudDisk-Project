<!--
  ============================================================
  PreviewLoading.vue — 预览加载进度组件
  ============================================================
  显示文档加载/转换进度，支持以下状态：
    - 加载中：显示旋转动画和文字提示
    - 转换中：显示进度条和百分比
    - 加载失败：显示错误信息和重试按钮
  
  用法：
    <PreviewLoading
      :loading="loading"
      :converting="isConverting"
      :progress="conversionProgress"
      :error="errorMessage"
      @retry="handleRetry"
      @cancel="handleCancel"
    />
  ============================================================
-->
<template>
  <div class="preview-loading-container">
    <!-- 加载中 -->
    <div v-if="loading && !converting" class="loading-state">
      <div class="loading-icon">
        <i class="fa fa-spinner fa-spin"></i>
      </div>
      <h3 class="loading-title">正在加载文档...</h3>
      <p class="loading-hint">请稍候，正在获取文件信息</p>
    </div>

    <!-- 转换中 -->
    <div v-else-if="converting" class="converting-state">
      <div class="converting-icon">
        <i class="fa fa-cog fa-spin"></i>
      </div>
      <h3 class="converting-title">文档转换中</h3>
      <p class="converting-hint">
        Office 文档正在转换为 PDF 格式进行预览，这可能需要一些时间
      </p>
      <div class="progress-wrapper">
        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: progress + '%' }"
          ></div>
        </div>
        <span class="progress-text">{{ progress }}%</span>
      </div>
      <p v-if="progress > 0 && progress < 100" class="progress-estimate">
        预计还需 {{ estimatedTime }} 秒
      </p>
      <button v-if="showCancel" @click="$emit('cancel')" class="cancel-btn">
        取消转换
      </button>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state">
      <div class="error-icon">
        <i class="fa fa-exclamation-triangle"></i>
      </div>
      <h3 class="error-title">加载失败</h3>
      <p class="error-message">{{ error }}</p>
      <div class="error-actions">
        <button @click="$emit('retry')" class="retry-btn">
          <i class="fa fa-refresh"></i>
          重新加载
        </button>
        <button @click="$emit('close')" class="close-btn">
          返回
        </button>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty-state">
      <div class="empty-icon">
        <i class="fa fa-file-o"></i>
      </div>
      <h3 class="empty-title">未选择文件</h3>
      <p class="empty-hint">请从文件列表中选择一个文件进行预览</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  /** 是否加载中 */
  loading: { type: Boolean, default: false },
  /** 是否转换中 */
  converting: { type: Boolean, default: false },
  /** 转换进度 0-100 */
  progress: { type: Number, default: 0 },
  /** 错误信息 */
  error: { type: String, default: '' },
  /** 是否显示取消按钮 */
  showCancel: { type: Boolean, default: true },
})

defineEmits(['retry', 'cancel', 'close'])

/**
 * 估算剩余时间（秒）
 * 基于当前进度估算，假设总转换时间约 30 秒
 */
const estimatedTime = computed(() => {
  if (props.progress <= 0) return 30
  const totalEstimate = 30
  const remaining = Math.ceil(totalEstimate * (1 - props.progress / 100))
  return Math.max(1, remaining)
})
</script>

<style scoped>
.preview-loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 300px;
  padding: 2rem;
  text-align: center;
}

/* ========== 加载状态 ========== */
.loading-state,
.converting-state,
.error-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 420px;
}

.loading-icon,
.converting-icon {
  font-size: 3rem;
  color: #4b6cb7;
  margin-bottom: 1.25rem;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.loading-title,
.converting-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 0.5rem;
}

.loading-hint,
.converting-hint {
  font-size: 0.9rem;
  color: #64748b;
  margin: 0 0 1.5rem;
  line-height: 1.5;
}

/* ========== 进度条 ========== */
.progress-wrapper {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  margin-bottom: 0.5rem;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4b6cb7, #6c8ef5);
  border-radius: 4px;
  transition: width 0.3s ease;
  min-width: 0;
}

.progress-text {
  font-size: 0.85rem;
  font-weight: 600;
  color: #4b6cb7;
  min-width: 36px;
  text-align: right;
}

.progress-estimate {
  font-size: 0.8rem;
  color: #94a3b8;
  margin: 0.25rem 0 1rem;
}

/* ========== 取消按钮 ========== */
.cancel-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1.25rem;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  border-radius: 0.5rem;
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.cancel-btn:hover {
  background: #fef2f2;
  border-color: #fecaca;
  color: #dc2626;
}

/* ========== 错误状态 ========== */
.error-icon {
  font-size: 3.5rem;
  color: #f59e0b;
  margin-bottom: 1.25rem;
}

.error-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 0.5rem;
}

.error-message {
  font-size: 0.9rem;
  color: #64748b;
  margin: 0 0 1.5rem;
  line-height: 1.5;
  word-break: break-word;
}

.error-actions {
  display: flex;
  gap: 0.75rem;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.5rem;
  background: linear-gradient(135deg, #4b6cb7, #6c8ef5);
  color: #fff;
  border: none;
  border-radius: 0.5rem;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.retry-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(75, 108, 183, 0.3);
}

.close-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.625rem 1.5rem;
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
  border-radius: 0.5rem;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.close-btn:hover {
  background: #e2e8f0;
}

/* ========== 空状态 ========== */
.empty-icon {
  font-size: 4rem;
  color: #cbd5e1;
  margin-bottom: 1rem;
}

.empty-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #94a3b8;
  margin: 0 0 0.5rem;
}

.empty-hint {
  font-size: 0.9rem;
  color: #cbd5e1;
  margin: 0;
}
</style>