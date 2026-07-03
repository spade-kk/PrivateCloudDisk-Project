<!--
  ============================================================
  VideoPlayerCore.vue - 企业级视频流媒体播放器核心组件
  ============================================================
  对标 Bilibili / YouTube / 腾讯视频 / 抖音 桌面端播放体验

  核心功能:
    1.  进度条悬停缩略图预览 (Sprite Image 雪碧图方案，极致流畅)
    2.  进度条 hover 放大 + 时间气泡 + 缩略图联动
    3.  多段缓冲区间可视化
    4.  自定义控制栏 (播放/暂停/快进/快退/音量/倍速/画质/全屏/PiP)
    5.  多分辨率无缝切换 (HLS ABR + 手动切换)
    6.  AB 循环播放 (设置 A/B 点)
    7.  视频截图 (Canvas 导出当前帧)
    8.  镜像翻转
    9.  触屏手势 (左右滑动进度 / 左侧上下亮度 / 右侧上下音量)
    10. 键盘快捷键完整覆盖 (Space/←→/↑↓/F/M/[ ]/N/P/S/L/?/1-9)
    11. 快捷键帮助面板 (? 键呼出)
    12. 设置面板 (画质/倍速/循环/AB循环/镜像/截图)
    13. 自动隐藏控制栏 (3s 无操作)
    14. 断点续播
    15. 中央大播放按钮 + 缓冲动画
    16. 右键上下文菜单
    17. 音量/亮度手势指示器
    18. 响应式布局 (桌面/平板/手机)
  ============================================================
-->
<template>
  <div
    class="vpc-root"
    :class="{ 'vpc--embedded': embedded }"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
    @touchstart="onTouchStart"
    @touchmove="onTouchMove"
    @touchend="onTouchEnd"
    @contextmenu.prevent="onContextMenu"
  >
    <!-- ==================== 视频容器 ==================== -->
    <div
      ref="containerRef"
      class="vpc-container"
      :class="{ 'vpc--fullscreen': isFullscreen }"
      @dblclick="toggleFullscreen"
    >
      <!-- 视频元素 -->
      <video
        ref="videoRef"
        class="vpc-video"
        :class="{ 'vpc-video--mirrored': isMirrored }"
        :style="{ filter: brightness !== 1 ? `brightness(${brightness})` : undefined }"
        :src="isHls ? undefined : videoSourceUrl"
        :volume="muted ? 0 : volume"
        :muted="muted"
        :playbackRate="playbackRate"
        crossorigin="anonymous"
        playsinline
        webkit-playsinline
        preload="auto"
        @loadedmetadata="onLoadedMetadata"
        @timeupdate="onTimeUpdate"
        @waiting="buffering = true"
        @canplay="buffering = false"
        @playing="onPlaying"
        @pause="onPause"
        @ended="onEnded"
        @error="onError"
        @click="togglePlay"
        @volumechange="onVolumeChange"
      />
      <!-- 字幕 -->
      <track v-for="sub in activeSubtitlesList" :key="sub.id" :src="sub.url" :srclang="sub.id" :label="sub.label" kind="subtitles" :default="sub.id === activeSubtitle" />

      <!-- ==================== 中央大播放按钮 ==================== -->
      <div v-if="!playing && !buffering && !error && videoReady" class="vpc-center-play" @click.stop="togglePlay">
        <svg viewBox="0 0 84 84" class="vpc-play-icon">
          <circle cx="42" cy="42" r="40" fill="none" stroke="rgba(255,255,255,0.9)" stroke-width="2" />
          <polygon points="34,26 34,58 62,42" fill="rgba(255,255,255,0.9)" />
        </svg>
      </div>

      <!-- ==================== 缓冲指示器 ==================== -->
      <div v-if="buffering && playing" class="vpc-buffering">
        <svg class="vpc-spinner" viewBox="0 0 50 50">
          <circle cx="25" cy="25" r="20" fill="none" stroke="rgba(255,255,255,0.3)" stroke-width="3" />
          <circle cx="25" cy="25" r="20" fill="none" stroke="#1677ff" stroke-width="3" stroke-linecap="round" stroke-dasharray="100" stroke-dashoffset="60" />
        </svg>
      </div>

      <!-- ==================== 手势音量指示器 ==================== -->
      <div class="vpc-gesture-indicator" :class="{ 'vpc-gesture-indicator--visible': showVolumeIndicator }">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2">
          <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" v-if="volume > 0" />
          <line x1="23" y1="9" x2="17" y2="15" v-if="muted" />
          <line x1="17" y1="9" x2="23" y2="15" v-if="muted" />
          <path d="M15.54 8.46a5 5 0 0 1 0 7.07" v-if="!muted && volume > 0" />
          <path d="M19.07 4.93a10 10 0 0 1 0 14.14" v-if="!muted && volume > 0.5" />
        </svg>
        <div class="vpc-gesture-bar">
          <div class="vpc-gesture-fill" :style="{ width: (muted ? 0 : Math.round(volume * 100)) + '%' }" />
        </div>
        <span class="vpc-gesture-value">{{ muted ? 0 : Math.round(volume * 100) }}</span>
      </div>

      <!-- ==================== 手势亮度指示器 ==================== -->
      <div class="vpc-gesture-indicator" :class="{ 'vpc-gesture-indicator--visible': showBrightnessIndicator }">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="5" />
          <line x1="12" y1="1" x2="12" y2="3" />
          <line x1="12" y1="21" x2="12" y2="23" />
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
          <line x1="1" y1="12" x2="3" y2="12" />
          <line x1="21" y1="12" x2="23" y2="12" />
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
        </svg>
        <div class="vpc-gesture-bar">
          <div class="vpc-gesture-fill" :style="{ width: Math.round(brightness * 100) + '%' }" />
        </div>
        <span class="vpc-gesture-value">{{ Math.round(brightness * 100) }}%</span>
      </div>

      <!-- ==================== 错误遮罩 ==================== -->
      <div v-if="error" class="vpc-error-overlay">
        <div class="vpc-error-content">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ff4d4f" stroke-width="1.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="15" y1="9" x2="9" y2="15" />
            <line x1="9" y1="9" x2="15" y2="15" />
          </svg>
          <p class="vpc-error-text">{{ error }}</p>
          <div class="vpc-error-btns">
            <button class="vpc-error-btn vpc-error-btn--primary" @click.stop="retry">重试</button>
            <button v-if="resolutions.length > 1" class="vpc-error-btn" @click.stop="switchResolutionFallback">切换分辨率</button>
          </div>
        </div>
      </div>

      <!-- ==================== 底部控制栏 ==================== -->
      <div
        class="vpc-controls"
        :class="{ 'vpc-controls--hidden': !controlsVisible && playing, 'vpc-controls--dragging': isDragging }"
        @mousemove.stop
      >
        <!-- ===== 进度条 ===== -->
        <div ref="progressRef" class="vpc-progress" @mousedown="onProgressMouseDown" @mousemove="onProgressMouseMove" @mouseleave="onProgressMouseLeave">
          <!-- 悬停预览缩略图 -->
          <div v-if="hoverPreview.visible && spriteInfo" class="vpc-progress__preview" :style="{ left: hoverPreview.percent + '%' }">
            <div v-if="thumbnailStyle" class="vpc-progress__thumbnail" :style="thumbnailStyle" />
            <div class="vpc-progress__time-bubble">{{ formatTime(hoverPreview.time) }}</div>
          </div>
          <!-- 缓冲区间 -->
          <div v-for="(range, i) in bufferedRanges" :key="i" class="vpc-progress__buffered" :style="{ left: (range.start / duration * 100) + '%', width: ((range.end - range.start) / duration * 100) + '%' }" />
          <!-- 已播放 -->
          <div class="vpc-progress__played" :style="{ width: playedPercent + '%' }" />
          <!-- AB 循环区域 -->
          <div v-if="abLoop.a !== null && abLoop.b !== null && abLoop.enabled" class="vpc-progress__ab-zone" :style="{ left: (abLoop.a / duration * 100) + '%', width: ((abLoop.b - abLoop.a) / duration * 100) + '%' }" />
          <div v-if="abLoop.a !== null" class="vpc-progress__ab-mark" :style="{ left: (abLoop.a / duration * 100) + '%' }" />
          <div v-if="abLoop.b !== null" class="vpc-progress__ab-mark vpc-progress__ab-mark--b" :style="{ left: (abLoop.b / duration * 100) + '%' }" />
          <!-- 滑块 -->
          <div class="vpc-progress__thumb" :style="{ left: playedPercent + '%' }" :class="{ 'vpc-progress__thumb--visible': isDragging || hoverProgress }" />
          <!-- 隐藏 input 用于原生拖拽 -->
          <input type="range" class="vpc-progress__input" min="0" :max="duration || 100" :value="currentTime" step="0.1" @input="onProgressInput" @change="onProgressChange" />
        </div>

        <!-- ===== 控制按钮行 ===== -->
        <div class="vpc-controls-row">
          <div class="vpc-controls-left">
            <!-- 播放/暂停 -->
            <button class="vpc-btn" :title="playing ? '暂停 (Space)' : '播放 (Space)'" @click.stop="togglePlay">
              <svg v-if="playing" viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1" /><rect x="14" y="4" width="4" height="16" rx="1" /></svg>
              <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><polygon points="7,3 7,21 21,12" /></svg>
            </button>

            <!-- 快退 10s -->
            <button class="vpc-btn vpc-btn--sm" title="快退 10 秒 (← / J)" @click.stop="seekRelative(-10)">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-10h2v6h-2v-6z" /><text x="7" y="17" font-size="8" fill="currentColor" font-weight="bold">10</text></svg>
            </button>

            <!-- 快进 10s -->
            <button class="vpc-btn vpc-btn--sm" title="快进 10 秒 (→ / L)" @click.stop="seekRelative(10)">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M11 18h2v-2h-2v2zm1-16C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-10h2v6h-2v-6z" /><text x="7" y="17" font-size="8" fill="currentColor" font-weight="bold">10</text></svg>
            </button>

            <!-- 音量 -->
            <div class="vpc-volume" @mouseenter="volumeHover = true" @mouseleave="volumeHover = false">
              <button class="vpc-btn" :title="muted ? '取消静音 (M)' : '静音 (M)'" @click.stop="toggleMute">
                <svg v-if="muted || volume === 0" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" /><line x1="23" y1="9" x2="17" y2="15" /><line x1="17" y1="9" x2="23" y2="15" /></svg>
                <svg v-else-if="volume < 0.5" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" /><path d="M15.54 8.46a5 5 0 0 1 0 7.07" /></svg>
                <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" /><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07" /></svg>
              </button>
              <div class="vpc-volume__slider" :class="{ 'vpc-volume__slider--visible': volumeHover }" @click.stop>
                <input type="range" class="vpc-range" min="0" max="100" :value="muted ? 0 : Math.round(volume * 100)" @input="onVolumeInput" />
              </div>
            </div>

            <!-- 时间 -->
            <span class="vpc-time">
              <span class="vpc-time__cur">{{ formatTime(currentTime) }}</span>
              <span class="vpc-time__sep">/</span>
              <span class="vpc-time__dur">{{ duration > 0 ? formatTime(duration) : '--:--' }}</span>
            </span>
          </div>

          <div class="vpc-controls-right">
            <!-- AB 循环 -->
            <button class="vpc-btn" :class="{ 'vpc-btn--active': abLoop.enabled }" :title="abLoop.enabled ? '取消 AB 循环' : 'AB 循环'" @click.stop="toggleABLoop">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="6" width="4" height="12" rx="1" /><rect x="16" y="6" width="4" height="12" rx="1" /><path d="M8 12h8" /></svg>
            </button>

            <!-- 循环 -->
            <button class="vpc-btn" :class="{ 'vpc-btn--active': loop }" :title="loop ? '关闭循环播放 (L)' : '循环播放 (L)'" @click.stop="toggleLoop">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 1 21 5 17 9" /><path d="M3 11V9a4 4 0 0 1 4-4h14" /><polyline points="7 23 3 19 7 15" /><path d="M21 13v2a4 4 0 0 1-4 4H3" /></svg>
            </button>

            <!-- 倍速 -->
            <div class="vpc-dropdown-wrapper" ref="speedDropdownRef">
              <button class="vpc-btn vpc-btn--text" @click.stop="toggleSpeedMenu">{{ playbackRate }}x</button>
              <div v-if="showSpeedMenu" class="vpc-dropdown">
                <button v-for="r in [0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3]" :key="r" class="vpc-dropdown__item" :class="{ 'vpc-dropdown__item--selected': playbackRate === r }" @click.stop="selectSpeed(r)">{{ r }}x</button>
              </div>
            </div>

            <!-- 画质 -->
            <div v-if="resolutions.length > 1" class="vpc-dropdown-wrapper" ref="resolutionDropdownRef">
              <button class="vpc-btn vpc-btn--text" @click.stop="toggleResolutionMenu">{{ currentResolutionLabel }}</button>
              <div v-if="showResolutionMenu" class="vpc-dropdown vpc-dropdown--right">
                <button v-for="res in resolutions" :key="res.value" class="vpc-dropdown__item" :class="{ 'vpc-dropdown__item--selected': currentResolution === res.value }" @click.stop="selectResolution(res.value)">
                  {{ res.label }}
                  <span v-if="res.bitrate" class="vpc-dropdown__sub">{{ formatBitrate(res.bitrate) }}</span>
                </button>
              </div>
            </div>

            <!-- 设置 -->
            <div class="vpc-dropdown-wrapper" ref="settingsDropdownRef">
              <button class="vpc-btn" title="设置" @click.stop="toggleSettingsMenu">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>
              </button>
              <div v-if="showSettingsMenu" class="vpc-dropdown vpc-dropdown--right">
                <div class="vpc-dropdown__section">画质</div>
                <button v-for="res in resolutions" :key="'s-'+res.value" class="vpc-dropdown__item" :class="{ 'vpc-dropdown__item--selected': currentResolution === res.value }" @click.stop="selectResolution(res.value)">{{ res.label }}</button>
                <div class="vpc-dropdown__divider" />
                <div class="vpc-dropdown__section">播放速度</div>
                <button v-for="r in [0.5,0.75,1,1.25,1.5,2]" :key="'ss-'+r" class="vpc-dropdown__item" :class="{ 'vpc-dropdown__item--selected': playbackRate === r }" @click.stop="selectSpeed(r)">{{ r }}x</button>
                <div class="vpc-dropdown__divider" />
                <button class="vpc-dropdown__item" @click.stop="toggleLoop">{{ loop ? '✓ ' : '' }}循环播放 (L)</button>
                <button class="vpc-dropdown__item" @click.stop="toggleABLoop">{{ abLoop.enabled ? '✓ ' : '' }}AB 循环</button>
                <button class="vpc-dropdown__item" @click.stop="toggleMirror">{{ isMirrored ? '✓ ' : '' }}镜像翻转</button>
                <button class="vpc-dropdown__item" @click.stop="captureScreenshot">视频截图 (S)</button>
                <div class="vpc-dropdown__divider" />
                <button class="vpc-dropdown__item" @click.stop="showShortcuts = true">键盘快捷键 (?)</button>
              </div>
            </div>

            <!-- 画中画 -->
            <button class="vpc-btn" title="画中画" @click.stop="togglePiP">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" /><rect x="12" y="11" width="8" height="6" rx="1" /></svg>
            </button>

            <!-- 全屏 -->
            <button class="vpc-btn" :title="isFullscreen ? '退出全屏 (F / Esc)' : '全屏 (F)'" @click.stop="toggleFullscreen">
              <svg v-if="isFullscreen" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="4 8 4 4 8 4" /><polyline points="20 8 20 4 16 4" /><polyline points="4 16 4 20 8 20" /><polyline points="20 16 20 20 16 20" /></svg>
              <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="8 4 4 4 4 8" /><polyline points="16 4 20 4 20 8" /><polyline points="4 16 4 20 8 20" /><polyline points="20 16 20 20 16 20" /></svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 快捷键帮助面板 ==================== -->
    <Teleport to="body">
      <div v-if="showShortcuts" class="vpc-shortcuts-overlay" @click="showShortcuts = false">
        <div class="vpc-shortcuts-panel" @click.stop>
          <div class="vpc-shortcuts__header">
            <h3>键盘快捷键</h3>
            <button class="vpc-btn" @click="showShortcuts = false">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
            </button>
          </div>
          <div class="vpc-shortcuts__grid">
            <div v-for="(shortcut, i) in shortcutsList" :key="i" class="vpc-shortcuts__item">
              <kbd class="vpc-kbd">{{ shortcut.key }}</kbd>
              <span>{{ shortcut.desc }}</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ==================== 右键菜单 ==================== -->
    <Teleport to="body">
      <div v-if="contextMenu.visible" class="vpc-context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
        <button class="vpc-context-menu__item" @click="togglePlay; closeContextMenu()">
          {{ playing ? '暂停' : '播放' }}
        </button>
        <button class="vpc-context-menu__item" @click="toggleFullscreen; closeContextMenu()">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
        <button class="vpc-context-menu__item" @click="togglePiP; closeContextMenu()">画中画</button>
        <div class="vpc-context-menu__divider" />
        <button class="vpc-context-menu__item" @click="captureScreenshot; closeContextMenu()">视频截图</button>
        <button class="vpc-context-menu__item" @click="toggleMirror; closeContextMenu()">
          {{ isMirrored ? '取消镜像' : '镜像翻转' }}
        </button>
        <button class="vpc-context-menu__item" @click="toggleLoop; closeContextMenu()">
          {{ loop ? '关闭循环播放' : '循环播放' }}
        </button>
        <div class="vpc-context-menu__divider" />
        <button class="vpc-context-menu__item" @click="showShortcuts = true; closeContextMenu()">键盘快捷键</button>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
// ============================================================
// 企业级视频播放器核心组件
// ============================================================
import { ref, computed, watch, onMounted, onUnmounted, nextTick, Teleport } from 'vue'
import Hls from 'hls.js'

// ============================================================
// Props
// ============================================================
const props = defineProps({
  /** 是否为嵌入模式 (嵌入时隐藏部分 UI) */
  embedded: { type: Boolean, default: false },
  /** 流媒体信息 */
  streamInfo: { type: Object, default: () => null },
  /** 雪碧图信息 */
  spriteInfo: { type: Object, default: () => null },
  /** 字幕列表 */
  subtitles: { type: Array, default: () => [] },
  /** 当前活动字幕 */
  activeSubtitle: { type: String, default: null },
  /** 流媒体 Token */
  streamToken: { type: String, default: '' },
  /** 当前分辨率 */
  initialResolution: { type: String, default: 'auto' },
  /** 初始播放速度 */
  initialPlaybackRate: { type: Number, default: 1 },
  /** 初始音量 */
  initialVolume: { type: Number, default: 1 },
  /** 已保存的播放进度 (秒) */
  savedProgress: { type: Number, default: 0 },
  /** 是否为 HLS 流 */
  isHls: { type: Boolean, default: false },
  /** HLS 播放 URL */
  hlsSourceUrl: { type: String, default: '' },
  /** MP4 播放 URL */
  videoSourceUrl: { type: String, default: '' },
  /** 文件 ID (用于进度上报) */
  fileId: { type: String, default: '' },
})

const emit = defineEmits([
  'back', 'retry', 'timeupdate', 'progress-report',
  'resolution-change', 'speed-change', 'volume-change',
  'fullscreen-change', 'error'
])

// ============================================================
// Refs
// ============================================================
const videoRef = ref(null)
const containerRef = ref(null)
const progressRef = ref(null)
const speedDropdownRef = ref(null)
const resolutionDropdownRef = ref(null)
const settingsDropdownRef = ref(null)
let hlsInstance = null
let controlsTimer = null
let progressReportTimer = null
let lastVolume = 1

// ============================================================
// 核心播放状态
// ============================================================
const playing = ref(false)
const buffering = ref(false)
const videoReady = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const buffered = ref(0)
const bufferedRanges = ref([])
const volume = ref(props.initialVolume)
const muted = ref(false)
const playbackRate = ref(props.initialPlaybackRate)
const currentResolution = ref(props.initialResolution)
const isFullscreen = ref(false)
const isPiP = ref(false)
const error = ref(null)
const loop = ref(false)
const abLoop = ref({ a: null, b: null, enabled: false })
const isMirrored = ref(false)
const brightness = ref(1)

// ============================================================
// UI 状态
// ============================================================
const controlsVisible = ref(true)
const showSpeedMenu = ref(false)
const showResolutionMenu = ref(false)
const showSettingsMenu = ref(false)
const showShortcuts = ref(false)
const isDragging = ref(false)
const hoverProgress = ref(false)
const volumeHover = ref(false)
const showVolumeIndicator = ref(false)
const showBrightnessIndicator = ref(false)
const contextMenu = ref({ visible: false, x: 0, y: 0 })
const hoverPreview = ref({ visible: false, time: 0, percent: 0, spriteIndex: 0 })
const touchStart = ref(null)
let volumeIndicatorTimer = null
let brightnessIndicatorTimer = null

// ============================================================
// 计算属性
// ============================================================
const activeSubtitlesList = computed(() => {
  if (!props.activeSubtitle) return []
  return props.subtitles.filter(s => s.id === props.activeSubtitle)
})

const playedPercent = computed(() => {
  if (!duration.value) return 0
  return Math.min(100, (currentTime.value / duration.value) * 100)
})

const resolutions = computed(() => {
  if (!props.streamInfo?.resolutions) return []
  return [
    { label: '自动', value: 'auto', width: 0, height: 0, bitrate: 0 },
    ...props.streamInfo.resolutions
  ]
})

const currentResolutionLabel = computed(() => {
  if (currentResolution.value === 'auto') return '自动'
  const res = resolutions.value.find(r => r.value === currentResolution.value)
  return res?.label || '自动'
})

const thumbnailStyle = computed(() => {
  if (!props.spriteInfo?.config || !hoverPreview.value.visible) return null
  const { config } = props.spriteInfo
  const cols = config.cols || 10
  const w = config.width || 160
  const h = config.height || 90
  const idx = hoverPreview.value.spriteIndex
  const col = idx % cols
  const row = Math.floor(idx / cols)
  return {
    backgroundImage: `url(${props.spriteInfo.sprite_image || props.spriteInfo.sprite_url})`,
    backgroundPosition: `-${col * w}px -${row * h}px`,
    backgroundSize: `${cols * w}px auto`,
    width: `${w}px`,
    height: `${h}px`,
  }
})

const shortcutsList = [
  { key: 'Space / K', desc: '播放 / 暂停' },
  { key: '← / →', desc: '快退 / 快进 5 秒' },
  { key: 'Shift + ← / →', desc: '快退 / 快进 1 秒' },
  { key: '↑ / ↓', desc: '音量增加 / 减少' },
  { key: 'F', desc: '全屏 / 退出全屏' },
  { key: 'M', desc: '静音 / 取消静音' },
  { key: '[' / ']', desc: '减速 / 加速' },
  { key: 'J / L', desc: '快退 / 快进 10 秒' },
  { key: 'S', desc: '视频截图' },
  { key: '0-9', desc: '跳转到视频 0%-90%' },
  { key: '?', desc: '显示 / 隐藏快捷键面板' },
  { key: 'Esc', desc: '退出全屏 / 关闭面板' },
]

// ============================================================
// 工具函数
// ============================================================
function formatTime(seconds) {
  if (!seconds || !isFinite(seconds) || seconds < 0) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const pad = n => String(n).padStart(2, '0')
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`
  return `${pad(m)}:${pad(s)}`
}

function formatBitrate(bps) {
  if (bps >= 1000000) return `${(bps / 1000000).toFixed(1)}Mbps`
  if (bps >= 1000) return `${(bps / 1000).toFixed(0)}Kbps`
  return `${bps}bps`
}

function clamp(v, min, max) {
  return Math.max(min, Math.min(max, v))
}

// ============================================================
// 视频初始化
// ============================================================
function initVideo() {
  const video = videoRef.value
  if (!video) return

  if (props.isHls && props.hlsSourceUrl) {
    initHls(video, props.hlsSourceUrl)
  }
}

function initHls(video, url) {
  if (hlsInstance) hlsInstance.destroy()

  if (!Hls.isSupported()) {
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url
    }
    return
  }

  hlsInstance = new Hls({
    enableWorker: true,
    lowLatencyMode: false,
    backBufferLength: 90,
    maxBufferLength: 30,
    maxMaxBufferLength: 600,
    maxBufferSize: 60 * 1000 * 1000,
    maxBufferHole: 0.5,
    abrEwmaDefaultEstimate: 500000,
    abrBandWidthFactor: 0.95,
    abrBandWidthUpFactor: 0.7,
    startLevel: -1,
    manifestLoadingMaxRetry: 4,
    levelLoadingMaxRetry: 4,
    fragLoadingMaxRetry: 6,
    debug: false,
  })

  hlsInstance.loadSource(url)
  hlsInstance.attachMedia(video)

  hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
    videoReady.value = true
    if (props.savedProgress > 0 && props.savedProgress < video.duration - 10) {
      video.currentTime = props.savedProgress
    }
  })

  hlsInstance.on(Hls.Events.ERROR, (event, data) => {
    if (data.fatal) {
      switch (data.type) {
        case Hls.ErrorTypes.NETWORK_ERROR:
          hlsInstance.startLoad()
          break
        case Hls.ErrorTypes.MEDIA_ERROR:
          hlsInstance.recoverMediaError()
          break
        default:
          error.value = '流媒体加载失败，请尝试切换分辨率'
          break
      }
    }
  })
}

// ============================================================
// 视频事件处理
// ============================================================
function onLoadedMetadata(e) {
  const video = e.target
  duration.value = video.duration || 0
  videoReady.value = true
  if (props.savedProgress > 0 && props.savedProgress < video.duration - 10) {
    video.currentTime = props.savedProgress
  }
}

function onTimeUpdate(e) {
  const video = e.target
  currentTime.value = video.currentTime
  buffered.value = video.buffered.length > 0
    ? video.buffered.end(video.buffered.length - 1) : 0
  const ranges = []
  for (let i = 0; i < video.buffered.length; i++) {
    ranges.push({ start: video.buffered.start(i), end: video.buffered.end(i) })
  }
  bufferedRanges.value = ranges

  // AB 循环检测
  if (abLoop.value.enabled && abLoop.value.b !== null && video.currentTime >= abLoop.value.b) {
    video.currentTime = abLoop.value.a || 0
  }

  emit('timeupdate', { currentTime: video.currentTime, duration: video.duration })
}

function onPlaying() {
  playing.value = true
  buffering.value = false
  startProgressReport()
}

function onPause() {
  playing.value = false
  stopProgressReport()
}

function onEnded() {
  playing.value = false
  stopProgressReport()
  if (loop.value && !abLoop.value.enabled) {
    const video = videoRef.value
    if (video) {
      video.currentTime = 0
      video.play().catch(() => {})
    }
  }
}

function onError(e) {
  const video = e.target
  const codes = { 1: '视频加载被中止', 2: '网络错误导致加载失败', 3: '视频解码失败', 4: '视频格式不支持或文件损坏' }
  const msg = video?.error ? (codes[video.error.code] || '播放错误') : '播放错误'
  error.value = msg
  emit('error', msg)
}

function onVolumeChange() {
  const video = videoRef.value
  if (video) {
    volume.value = video.volume
    muted.value = video.muted
  }
}

// ============================================================
// 进度上报
// ============================================================
function startProgressReport() {
  stopProgressReport()
  progressReportTimer = setInterval(() => {
    const video = videoRef.value
    if (video && video.currentTime > 0 && video.duration > 0) {
      emit('progress-report', { currentTime: video.currentTime, duration: video.duration })
    }
  }, 5000)
}

function stopProgressReport() {
  if (progressReportTimer) {
    clearInterval(progressReportTimer)
    progressReportTimer = null
    const video = videoRef.value
    if (video && video.currentTime > 0) {
      emit('progress-report', { currentTime: video.currentTime, duration: video.duration || 0 })
    }
  }
}

// ============================================================
// 播放控制
// ============================================================
function togglePlay() {
  const video = videoRef.value
  if (!video) return
  if (video.paused || video.ended) {
    if (video.ended) video.currentTime = 0
    video.play().catch(() => {})
  } else {
    video.pause()
  }
}

function seek(time) {
  const video = videoRef.value
  if (!video) return
  const target = clamp(time, 0, duration.value || video.duration || 0)
  video.currentTime = target
  currentTime.value = target
}

function seekRelative(offset) {
  seek(currentTime.value + offset)
}

function setVolume(val) {
  const video = videoRef.value
  if (!video) return
  const v = clamp(val, 0, 1)
  video.volume = v
  video.muted = v === 0
  if (v > 0) lastVolume = v
  emit('volume-change', v)
}

function toggleMute() {
  const video = videoRef.value
  if (!video) return
  if (video.muted) {
    video.muted = false
    video.volume = lastVolume || 1
  } else {
    lastVolume = video.volume
    video.muted = true
  }
}

function setPlaybackRate(rate) {
  const video = videoRef.value
  if (!video) return
  video.playbackRate = rate
  playbackRate.value = rate
  emit('speed-change', rate)
}

function setResolution(resolution) {
  currentResolution.value = resolution
  emit('resolution-change', resolution)
}

async function toggleFullscreen() {
  const el = containerRef.value
  if (!el) return
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
      isFullscreen.value = false
    } else {
      await el.requestFullscreen()
      isFullscreen.value = true
    }
    emit('fullscreen-change', isFullscreen.value)
  } catch { /* 忽略 */ }
}

async function togglePiP() {
  const video = videoRef.value
  if (!video) return
  try {
    if (document.pictureInPictureElement) {
      await document.exitPictureInPicture()
      isPiP.value = false
    } else {
      await video.requestPictureInPicture()
      isPiP.value = true
    }
  } catch { /* 忽略 */ }
}

function toggleLoop() {
  const video = videoRef.value
  if (!video) return
  video.loop = !video.loop
  loop.value = video.loop
}

function toggleABLoop() {
  const video = videoRef.value
  if (!video || !video.duration) return

  if (abLoop.value.enabled) {
    abLoop.value = { a: null, b: null, enabled: false }
  } else if (abLoop.value.a === null) {
    abLoop.value = { a: video.currentTime, b: null, enabled: false }
  } else if (abLoop.value.b === null) {
    const b = Math.max(video.currentTime, abLoop.value.a + 1)
    abLoop.value = { a: abLoop.value.a, b, enabled: true }
    video.currentTime = abLoop.value.a
    video.play().catch(() => {})
  }
}

function toggleMirror() {
  isMirrored.value = !isMirrored.value
}

function captureScreenshot() {
  const video = videoRef.value
  if (!video || video.readyState < 2) return
  try {
    const canvas = document.createElement('canvas')
    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    const ctx = canvas.getContext('2d')
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    const link = document.createElement('a')
    link.download = `screenshot_${formatTime(video.currentTime).replace(/:/g, '-')}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
  } catch { /* 忽略 */ }
}

function retry() {
  error.value = null
  const video = videoRef.value
  if (!video) return
  if (props.isHls && props.hlsSourceUrl) {
    initHls(video, props.hlsSourceUrl)
  } else {
    video.load()
  }
  emit('retry')
}

function switchResolutionFallback() {
  const idx = resolutions.value.findIndex(r => r.value === currentResolution.value)
  const next = resolutions.value[(idx + 1) % resolutions.value.length]
  if (next && next.value !== currentResolution.value) {
    setResolution(next.value)
  }
}

// ============================================================
// 进度条交互
// ============================================================
function onProgressMouseDown(e) {
  isDragging.value = true
  updateProgressFromEvent(e)
  document.addEventListener('mousemove', onProgressDrag)
  document.addEventListener('mouseup', onProgressDragEnd)
}

function onProgressDrag(e) {
  if (isDragging.value) updateProgressFromEvent(e)
}

function onProgressDragEnd() {
  if (isDragging.value) {
    seek(hoverPreview.value.time)
    isDragging.value = false
  }
  document.removeEventListener('mousemove', onProgressDrag)
  document.removeEventListener('mouseup', onProgressDragEnd)
}

function onProgressMouseMove(e) {
  hoverProgress.value = true
  updateProgressFromEvent(e)
  hoverPreview.value.visible = true
}

function onProgressMouseLeave() {
  hoverProgress.value = false
  if (!isDragging.value) {
    hoverPreview.value.visible = false
  }
}

function updateProgressFromEvent(e) {
  const rect = progressRef.value?.getBoundingClientRect()
  if (!rect || !duration.value) return
  const percent = clamp((e.clientX - rect.left) / rect.width, 0, 1)
  const time = percent * duration.value
  hoverPreview.value.percent = percent * 100
  hoverPreview.value.time = time

  // 计算雪碧图索引
  if (props.spriteInfo?.config) {
    const interval = props.spriteInfo.config.interval || 10
    hoverPreview.value.spriteIndex = Math.floor(time / interval)
  }
}

function onProgressInput(e) {
  seek(parseFloat(e.target.value))
}

function onProgressChange(e) {
  seek(parseFloat(e.target.value))
}

// ============================================================
// 音量
// ============================================================
function onVolumeInput(e) {
  setVolume(parseInt(e.target.value) / 100)
}

// ============================================================
// 下拉菜单
// ============================================================
function toggleSpeedMenu() {
  showSpeedMenu.value = !showSpeedMenu.value
  showResolutionMenu.value = false
  showSettingsMenu.value = false
}

function toggleResolutionMenu() {
  showResolutionMenu.value = !showResolutionMenu.value
  showSpeedMenu.value = false
  showSettingsMenu.value = false
}

function toggleSettingsMenu() {
  showSettingsMenu.value = !showSettingsMenu.value
  showSpeedMenu.value = false
  showResolutionMenu.value = false
}

function selectSpeed(rate) {
  setPlaybackRate(rate)
  showSpeedMenu.value = false
  showSettingsMenu.value = false
}

function selectResolution(resolution) {
  setResolution(resolution)
  showResolutionMenu.value = false
  showSettingsMenu.value = false
}

function closeAllDropdowns() {
  showSpeedMenu.value = false
  showResolutionMenu.value = false
  showSettingsMenu.value = false
}

// ============================================================
// 控制栏自动隐藏
// ============================================================
function showControls() {
  controlsVisible.value = true
  if (controlsTimer) clearTimeout(controlsTimer)
  if (playing.value) {
    controlsTimer = setTimeout(() => {
      controlsVisible.value = false
    }, 3000)
  }
}

function onMouseMove() {
  showControls()
}

function onMouseLeave() {
  if (controlsTimer) clearTimeout(controlsTimer)
  if (playing.value) controlsVisible.value = false
}

// ============================================================
// 右键菜单
// ============================================================
function onContextMenu(e) {
  contextMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY
  }
}

function closeContextMenu() {
  contextMenu.value.visible = false
}

// ============================================================
// 触屏手势
// ============================================================
function onTouchStart(e) {
  if (e.touches.length === 1) {
    touchStart.value = {
      x: e.touches[0].clientX,
      y: e.touches[0].clientY,
      time: currentTime.value,
      volume: volume.value,
      brightness: brightness.value,
    }
  }
}

function onTouchMove(e) {
  if (!touchStart.value || e.touches.length !== 1) return
  const start = touchStart.value
  const dx = e.touches[0].clientX - start.x
  const dy = e.touches[0].clientY - start.y
  const containerWidth = containerRef.value?.clientWidth || 1

  if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 10) {
    // 水平滑动 - 进度
    const ratio = dx / containerWidth
    seek(start.time + ratio * (duration.value || 0))
  } else if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 10) {
    if (start.x < containerWidth / 2) {
      // 左侧 - 亮度
      const newBri = clamp(start.brightness - dy / 300, 0.3, 1.5)
      brightness.value = newBri
      showVolumeIndicator.value = false
      showBrightnessIndicator.value = true
      if (brightnessIndicatorTimer) clearTimeout(brightnessIndicatorTimer)
      brightnessIndicatorTimer = setTimeout(() => { showBrightnessIndicator.value = false }, 1000)
    } else {
      // 右侧 - 音量
      const newVol = clamp(start.volume - dy / 300, 0, 1)
      setVolume(newVol)
      showBrightnessIndicator.value = false
      showVolumeIndicator.value = true
      if (volumeIndicatorTimer) clearTimeout(volumeIndicatorTimer)
      volumeIndicatorTimer = setTimeout(() => { showVolumeIndicator.value = false }, 1000)
    }
  }
}

function onTouchEnd() {
  touchStart.value = null
}

// ============================================================
// 键盘快捷键
// ============================================================
function handleKeydown(e) {
  if (showShortcuts.value) {
    if (e.key === 'Escape') showShortcuts.value = false
    return
  }

  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return

  const video = videoRef.value
  if (!video) return

  switch (e.key) {
    case ' ':
    case 'k':
      e.preventDefault()
      togglePlay()
      showControls()
      break
    case 'ArrowLeft':
      e.preventDefault()
      seekRelative(e.shiftKey ? -1 : -5)
      showControls()
      break
    case 'ArrowRight':
      e.preventDefault()
      seekRelative(e.shiftKey ? 1 : 5)
      showControls()
      break
    case 'ArrowUp':
      e.preventDefault()
      setVolume(Math.min(1, volume.value + 0.05))
      showControls()
      break
    case 'ArrowDown':
      e.preventDefault()
      setVolume(Math.max(0, volume.value - 0.05))
      showControls()
      break
    case 'f':
      e.preventDefault()
      toggleFullscreen()
      break
    case 'm':
      e.preventDefault()
      toggleMute()
      showControls()
      break
    case '[':
      e.preventDefault()
      setPlaybackRate(Math.max(0.25, playbackRate.value - 0.25))
      break
    case ']':
      e.preventDefault()
      setPlaybackRate(Math.min(3, playbackRate.value + 0.25))
      break
    case 'j':
      e.preventDefault()
      seekRelative(-10)
      showControls()
      break
    case 'l':
      e.preventDefault()
      seekRelative(10)
      showControls()
      break
    case 's':
      e.preventDefault()
      captureScreenshot()
      break
    case '?':
      e.preventDefault()
      showShortcuts.value = !showShortcuts.value
      break
    case 'Escape':
      if (document.fullscreenElement) {
        document.exitFullscreen().catch(() => {})
      }
      break
    case '0': case '1': case '2': case '3': case '4':
    case '5': case '6': case '7': case '8': case '9':
      e.preventDefault()
      seek((parseInt(e.key) / 10) * duration.value)
      break
  }
}

// ============================================================
// 全屏变化监听
// ============================================================
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
}

// ============================================================
// 点击外部关闭
// ============================================================
function onDocumentClick(e) {
  if (!e.target.closest('.vpc-dropdown-wrapper')) {
    closeAllDropdowns()
  }
  if (!e.target.closest('.vpc-context-menu')) {
    closeContextMenu()
  }
}

// ============================================================
// 暴露方法给父组件
// ============================================================
defineExpose({
  videoRef,
  containerRef,
  play: () => videoRef.value?.play(),
  pause: () => videoRef.value?.pause(),
  togglePlay,
  seek,
  seekRelative,
  setVolume,
  toggleMute,
  setPlaybackRate,
  setResolution,
  toggleFullscreen,
  togglePiP,
  captureScreenshot,
  get currentTime() { return currentTime.value },
  get duration() { return duration.value },
  get playing() { return playing.value },
})

// ============================================================
// 生命周期
// ============================================================
onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)
  nextTick(() => initVideo())
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  if (hlsInstance) {
    hlsInstance.destroy()
    hlsInstance = null
  }
  stopProgressReport()
  if (controlsTimer) clearTimeout(controlsTimer)
  if (volumeIndicatorTimer) clearTimeout(volumeIndicatorTimer)
  if (brightnessIndicatorTimer) clearTimeout(brightnessIndicatorTimer)
})

// 监听分辨率变化，重新加载视频
watch(() => currentResolution.value, () => {
  const video = videoRef.value
  if (!video) return
  const currentTimeSnapshot = video.currentTime
  const wasPlaying = !video.paused

  nextTick(() => {
    if (videoRef.value) {
      videoRef.value.currentTime = currentTimeSnapshot
      if (wasPlaying) videoRef.value.play().catch(() => {})
    }
  })
})

// 监听 HLS URL 变化
watch(() => props.hlsSourceUrl, (newUrl) => {
  if (newUrl && videoRef.value && props.isHls) {
    initHls(videoRef.value, newUrl)
  }
})
</script>

<style src="./VideoPlayerCore.css"></style>