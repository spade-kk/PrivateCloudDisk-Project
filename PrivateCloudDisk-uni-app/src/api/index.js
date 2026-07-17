/**
 * api/index.js - API 模块统一导出入口
 *
 * 对标 Vue3 Web 应用的 API 模块化设计模式。
 * 所有 API 模块通过此文件统一导出，便于按需引入。
 *
 * 使用方式:
 *   import { nodeApi, userApi, trashApi } from '@/api'
 *   const res = await nodeApi.getRootNode()
 */
export * as nodeApi from './node'
export * as fileApi from './file'
export * as userApi from './user'
export * as trashApi from './trash'
export * as favoriteApi from './favorite'
export * as searchApi from './search'
export * as uploadApi from './upload'
export * as downloadApi from './download'
export * as quotaApi from './quota'
export * as taskApi from './task'
export * as starApi from './star'