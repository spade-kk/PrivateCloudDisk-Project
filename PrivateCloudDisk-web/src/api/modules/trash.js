import { del, get, post } from '@/utils/request'

export function getTrashTargetsApi(params = {}) {
  return get('business/trash/', {
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 20,
  })
}

export function getTrashTargetApi(trashId) {
  return get(`business/trash/${trashId}`)
}

export function moveFileToTrashApi(fileId) {
  return post(`business/trash/files/${fileId}`)
}

export function moveFolderToTrashApi(nodeId) {
  return post(`business/trash/folders/${nodeId}`)
}

export function restoreTrashTargetApi(trashId) {
  return post(`business/trash/${trashId}/restore`)
}

export function deleteTrashTargetApi(trashId) {
  return del(`business/trash/${trashId}`)
}

export function emptyTrashApi() {
  return del('business/trash/')
}

export function countTrashTargetsApi() {
  return get('business/trash/count')
}
