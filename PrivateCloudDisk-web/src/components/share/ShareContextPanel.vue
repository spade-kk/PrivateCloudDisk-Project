<template>
  <aside class="context-stack" aria-label="分享补充信息">
    <section class="context-card description-card" aria-labelledby="share-description-title">
      <div class="card-heading">
        <div>
          <p class="card-kicker">来自分享者</p>
          <h2 id="share-description-title">分享说明</h2>
        </div>
        <span class="heading-icon" aria-hidden="true"><i class="fa fa-align-left"></i></span>
      </div>

      <div v-if="safeDescription" v-safe-html="safeDescription" class="rich-description"></div>
      <div v-else class="description-empty">
        <i class="fa fa-file-text-o" aria-hidden="true"></i>
        <div>
          <strong>分享者暂未填写说明</strong>
          <p>可直接浏览下方文件；重要的使用说明会显示在这里。</p>
        </div>
      </div>
    </section>

    <ShareQRCodeCard :url="qrUrl" :title="shareTitle" />

    <section class="context-card comments-card" aria-labelledby="share-comments-title">
      <div class="card-heading">
        <div>
          <p class="card-kicker">协作区域</p>
          <h2 id="share-comments-title">评论</h2>
        </div>
        <span class="status-pill">功能预留</span>
      </div>

      <label class="comment-label" for="share-comment-placeholder">写下评论</label>
      <div class="comment-composer">
        <textarea
          id="share-comment-placeholder"
          disabled
          rows="3"
          placeholder="评论功能开放后，可在这里交流分享内容"
        ></textarea>
        <div class="composer-footer">
          <span><i class="fa fa-lock" aria-hidden="true"></i> 当前为只读预览</span>
          <button type="button" disabled>发表评论</button>
        </div>
      </div>

      <div class="comment-empty" aria-live="polite">
        <span class="comment-avatar"><i class="fa fa-comments-o" aria-hidden="true"></i></span>
        <div>
          <strong>评论区已完成布局预留</strong>
          <p>后续接入接口后，评论列表将在此按时间展示。</p>
        </div>
      </div>

      <nav class="comment-pagination" aria-label="评论分页预留">
        <button type="button" disabled aria-label="上一页"><i class="fa fa-angle-left"></i></button>
        <span aria-current="page">1</span>
        <button type="button" disabled aria-label="下一页"><i class="fa fa-angle-right"></i></button>
      </nav>
    </section>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { sanitize } from '@/utils/sanitize'
import ShareQRCodeCard from './ShareQRCodeCard.vue'

const props = defineProps<{
  description?: string | null
  qrUrl: string
  shareTitle?: string
}>()

/** rich text is rendered only after a strict allow-list pass. */
const safeDescription = computed(() => sanitize(props.description || '', {
  ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'ul', 'ol', 'li', 'a', 'code', 'blockquote', 'h2', 'h3'],
  ALLOWED_ATTR: ['href', 'title', 'target', 'rel'],
  ALLOW_DATA_ATTR: false,
}))
</script>

<style scoped>
.context-stack { display: flex; min-width: 0; flex-direction: column; gap: 18px; }
.context-card { padding: 21px; border: 1px solid rgba(226, 232, 240, 0.92); border-radius: 20px; background: rgba(255,255,255,.96); box-shadow: 0 14px 40px rgba(15,23,42,.065); }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 17px; }
.card-kicker { margin: 0 0 4px; color: #8b99ad; font-size: 11px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.card-heading h2 { margin: 0; color: #172033; font-size: 17px; font-weight: 760; }
.heading-icon { display: inline-flex; width: 40px; height: 40px; align-items: center; justify-content: center; border-radius: 13px; background: #eff5ff; color: #165dff; }

.rich-description { color: #475569; font-size: 14px; line-height: 1.82; overflow-wrap: anywhere; }
.rich-description :deep(p) { margin: 0 0 12px; }
.rich-description :deep(p:last-child) { margin-bottom: 0; }
.rich-description :deep(ul),
.rich-description :deep(ol) { margin: 10px 0; padding-left: 22px; }
.rich-description :deep(a) { color: #165dff; text-decoration: underline; text-underline-offset: 3px; }
.rich-description :deep(blockquote) { margin: 12px 0; padding: 10px 13px; border-left: 3px solid #7fa7ff; border-radius: 0 10px 10px 0; background: #f6f9ff; color: #53647f; }
.rich-description :deep(code) { padding: 2px 5px; border-radius: 5px; background: #eef2f7; color: #b42318; font-size: .9em; }

.description-empty { display: flex; align-items: flex-start; gap: 12px; padding: 15px; border: 1px dashed #dce3ee; border-radius: 15px; background: #fafbfd; color: #7b889c; }
.description-empty > i { margin-top: 2px; color: #9cabc0; font-size: 20px; }
.description-empty strong { color: #506079; font-size: 13px; }
.description-empty p { margin: 5px 0 0; font-size: 12px; line-height: 1.6; }

.status-pill { padding: 5px 9px; border-radius: 999px; background: #f1f4f8; color: #718096; font-size: 11px; font-weight: 700; }
.comment-label { display: block; margin-bottom: 7px; color: #58677d; font-size: 12px; font-weight: 700; }
.comment-composer { overflow: hidden; border: 1px solid #e2e8f0; border-radius: 15px; background: #f8fafc; }
.comment-composer textarea { display: block; width: 100%; resize: none; border: 0; background: transparent; padding: 13px 14px; color: #7b8798; font-size: 13px; outline: 0; }
.comment-composer textarea::placeholder { color: #a3afbf; opacity: 1; }
.composer-footer { display: flex; min-height: 46px; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 10px 8px 14px; border-top: 1px solid #e8edf4; color: #8c98a8; font-size: 11px; }
.composer-footer button { min-height: 32px; border: 0; border-radius: 9px; background: #dbe2ec; padding: 0 12px; color: #8c98aa; font-size: 12px; font-weight: 700; }
.comment-empty { display: flex; align-items: center; gap: 12px; margin-top: 16px; padding: 13px; border-radius: 14px; background: #fafbfc; }
.comment-avatar { display: inline-flex; width: 38px; height: 38px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 50%; background: #edf2f8; color: #8090a7; }
.comment-empty strong { color: #526078; font-size: 12px; }
.comment-empty p { margin: 3px 0 0; color: #929eae; font-size: 11px; line-height: 1.5; }
.comment-pagination { display: flex; justify-content: center; gap: 7px; margin-top: 15px; }
.comment-pagination button,
.comment-pagination span { display: inline-flex; width: 32px; height: 32px; align-items: center; justify-content: center; border: 1px solid #e4e9f1; border-radius: 9px; background: #fff; color: #9aa6b6; font-size: 12px; }
.comment-pagination span { border-color: #cbdcff; background: #eff5ff; color: #165dff; font-weight: 700; }

@media (max-width: 430px) {
  .context-card { padding: 17px; border-radius: 17px; }
  .composer-footer { align-items: flex-start; flex-direction: column; }
  .composer-footer button { width: 100%; min-height: 40px; }
}
</style>

