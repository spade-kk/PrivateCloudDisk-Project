<template>
  <view class="search-page">
    <!-- 搜索框 -->
    <view class="search-input-wrap">
      <u-search
        v-model="keyword"
        placeholder="搜索文件名、内容..."
        :showAction="true"
        actionText="搜索"
        @search="doSearch"
        @clear="clearSearch"
        :focus="true"
      />
    </view>

    <!-- 过滤器 -->
    <view class="filter-row">
      <u-tabs
        :list="fileTypeTabs"
        :current="currentTab"
        @change="onTabChange"
        lineColor="#1a73e8"
        :activeStyle="{ color: '#1a73e8', fontWeight: 'bold' }"
        :inactiveStyle="{ color: '#5f6368' }"
      />
    </view>

    <!-- 排序切换 -->
    <view class="sort-row flex-between">
      <text class="result-count">共 {{ totalHits }} 个结果</text>
      <view class="sort-btns">
        <text
          :class="['sort-item', { active: sortField === '_score' }]"
          @click="toggleSort('_score')"
        >相关性</text>
        <text
          :class="['sort-item', { active: sortField === 'uploaded_time' }]"
          @click="toggleSort('uploaded_time')"
        >时间</text>
        <text
          :class="['sort-item', { active: sortField === 'size' }]"
          @click="toggleSort('size')"
        >大小</text>
      </view>
    </view>

    <!-- 搜索结果 -->
    <view class="result-list">
      <view
        class="result-item"
        v-for="item in results"
        :key="item._id"
        @click="handleClick(item)"
      >
        <u-icon
          :name="getFileIcon(item['name'] || item['_source.name'])"
          size="44"
          :color="getFileIconColor(item['name'] || item['_source.name'])"
        />
        <view class="result-info">
          <text class="result-name ellipsis" v-html="highlightName(item)"></text>
          <text class="result-meta">{{ formatFileSize(getField(item, 'size')) }}</text>
        </view>
        <u-icon name="arrow-right" size="28" color="#c4c7cc" />
      </view>

      <u-empty
        v-if="!loading && searched && results.length === 0"
        text="未找到相关文件"
        icon="file-text"
      />

      <view class="load-more" v-if="hasMore">
        <text class="load-more-text" @click="loadMore">加载更多</text>
      </view>
    </view>
  </view>
</template>

<script>
import { advancedSearch } from '@/api/file'
import { formatFileSize, getFileIcon, getFileIconColor } from '@/utils/helper'

const FILE_TYPE_TABS = [
  { name: '全部' },
  { name: '图片' },
  { name: '视频' },
  { name: '文档' },
  { name: '压缩包' },
  { name: '其他' }
]

export default {
  data() {
    return {
      keyword: '',
      fileTypeTabs: FILE_TYPE_TABS,
      currentTab: 0,
      sortField: '_score',
      sortAsc: false,

      loading: false,
      searched: false,
      results: [],
      totalHits: 0,
      page: 1,
      hasMore: false,
      searchAfter: null
    }
  },
  methods: {
    formatFileSize,
    getFileIcon,
    getFileIconColor,

    doSearch() {
      if (!this.keyword.trim()) return
      this.page = 1
      this.searchAfter = null
      this.results = []
      this.searchRequest()
    },

    async searchRequest() {
      this.loading = true
      this.searched = true
      try {
        const fileType = FILE_TYPE_TABS[this.currentTab].name
        const params = {
          keyword: this.keyword,
          page: this.page,
          size: 20,
          sortField: this.sortField,
          asc: this.sortAsc,
          highlightFields: ['name', 'content']
        }
        if (fileType !== '全部') {
          params.filters = { fileCategory: fileType }
        }
        if (this.searchAfter) {
          params.searchAfter = this.searchAfter
        }

        const res = await advancedSearch(params)
        const data = res.data

        if (this.page === 1) {
          this.results = data.hits || []
        } else {
          this.results.push(...(data.hits || []))
        }
        this.totalHits = data.total || 0
        this.searchAfter = data.searchAfter
        this.hasMore = !!data.searchAfter && (data.hits?.length || 0) >= 20
      } catch (e) {
        console.error('搜索失败:', e)
      } finally {
        this.loading = false
      }
    },

    loadMore() {
      if (this.hasMore && !this.loading) {
        this.page++
        this.searchRequest()
      }
    },

    onTabChange(index) {
      this.currentTab = index.index
      if (this.keyword.trim()) {
        this.doSearch()
      }
    },

    toggleSort(field) {
      if (this.sortField === field) {
        this.sortAsc = !this.sortAsc
      } else {
        this.sortField = field
        this.sortAsc = false
      }
      if (this.keyword.trim()) {
        this.doSearch()
      }
    },

    clearSearch() {
      this.results = []
      this.totalHits = 0
      this.searched = false
    },

    getField(item, field) {
      return item._source?.[field] || item[field]
    },

    highlightName(item) {
      const name = item['name'] || item._source?.name || ''
      if (item.highlight?.name) {
        return item.highlight.name[0]
      }
      return name
    },

    handleClick(item) {
      const id = item._source?.id || item.id
      const name = item._source?.name || item.name
      if (id) {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${id}&fileName=${name || ''}`
        })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.search-page {
  min-height: 100vh;
}

.search-input-wrap {
  padding: 16rpx 24rpx;
  background: $bg-white;
}

.filter-row {
  background: $bg-white;
  border-top: 1rpx solid $border-color;
}

.sort-row {
  padding: 16rpx 24rpx;

  .result-count {
    font-size: 24rpx;
    color: $text-secondary;
  }

  .sort-btns {
    display: flex;
    gap: 24rpx;

    .sort-item {
      font-size: 24rpx;
      color: $text-secondary;

      &.active {
        color: $primary-color;
        font-weight: 600;
      }
    }
  }
}

.result-list {
  background: $bg-white;
  margin: 0 24rpx;
  border-radius: $radius-md;
  overflow: hidden;
}

.result-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  border-bottom: 1rpx solid $border-color;

  &:last-child {
    border-bottom: none;
  }

  .result-info {
    flex: 1;
    margin-left: 20rpx;
    margin-right: 12rpx;

    .result-name {
      font-size: 28rpx;
    }

    .result-meta {
      font-size: 22rpx;
      color: $text-secondary;
      margin-top: 4rpx;
    }
  }
}

.load-more {
  padding: 24rpx;
  text-align: center;

  .load-more-text {
    color: $primary-color;
    font-size: 26rpx;
  }
}
</style>