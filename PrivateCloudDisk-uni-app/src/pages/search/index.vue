<template>
  <view class="search-page">
    <!-- 搜索输入 -->
    <view class="search-input-wrap">
      <u-search
        v-model="keyword"
        placeholder="搜索文件名..."
        :showAction="false"
        @search="handleSearch"
        @clear="handleClear"
        shape="round"
        searchIconSize="36"
        bgColor="#f5f5f5"
      />
    </view>

    <!-- 搜索结果 -->
    <view class="file-list" v-if="keyword">
      <FileItem
        v-for="node in list"
        :key="node.node_id"
        :node="node"
        @click="handleItemClick"
      />
      <EmptyState
        v-if="!loading && list.length === 0 && searched"
        icon="search"
        :text="'未找到 ' + searchedKeyword + ' 的相关文件' "
      />
    </view>

    <!-- 初始状态 -->
    <view class="search-empty" v-else>
      <EmptyState icon="search" text="输入关键词搜索文件" />
    </view>

    <LoadingOverlay :visible="loading" text="搜索中..." />
  </view>
</template>

<script>
import { searchFiles } from '@/api/search'
import FileItem from '@/components/file/FileItem.vue'
import EmptyState from '@/components/file/EmptyState.vue'
import LoadingOverlay from '@/components/common/LoadingOverlay.vue'

export default {
  components: { FileItem, EmptyState, LoadingOverlay },
  data() {
    return { keyword: '', list: [], loading: false, searched: false, searchedKeyword: '' }
  },
  methods: {
    async handleSearch() {
      const kw = this.keyword.trim()
      if (!kw) return
      this.loading = true
      this.searched = false
      try {
        const res = await searchFiles({ keyword: kw, page: 1, pageSize: 50 })
        this.list = res.data?.items || []
        this.searched = true
        this.searchedKeyword = kw
      } catch (e) { /* 已处理 */ } finally {
        this.loading = false
      }
    },
    handleClear() {
      this.list = []
      this.searched = false
    },
    handleItemClick(node) {
      if (node.node_type === 'FOLDER') {
        uni.switchTab({ url: '/pages/index/index' })
      } else {
        uni.navigateTo({
          url: `/pages/file-detail/index?fileId=${node.node_id}&fileName=${encodeURIComponent(node.node_name)}`
        })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.search-page { min-height: 100vh; background: #f5f5f5; }
.search-input-wrap { padding: 16rpx 24rpx; background: #fff; }
.file-list { margin: 16rpx 24rpx; background: #fff; border-radius: 16rpx; overflow: hidden; box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.06); }
.search-empty { padding-top: 80rpx; }
</style>