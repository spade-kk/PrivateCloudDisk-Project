/**
 * 根据文件名获取 Font Awesome 图标类
 * @param {string} fileName
 * @returns {string}
 */
export function getFileIconClass(fileName) {
  const ext = fileName.split('.').pop()?.toLowerCase() || ''
  const map = {
    pdf: 'fa-file-pdf-o text-danger',
    doc: 'fa-file-word-o text-blue-600',
    docx: 'fa-file-word-o text-blue-600',
    xls: 'fa-file-excel-o text-green-600',
    xlsx: 'fa-file-excel-o text-green-600',
    ppt: 'fa-file-powerpoint-o text-orange-500',
    pptx: 'fa-file-powerpoint-o text-orange-500',
    jpg: 'fa-file-image-o text-purple-500',
    jpeg: 'fa-file-image-o text-purple-500',
    png: 'fa-file-image-o text-purple-500',
    gif: 'fa-file-image-o text-purple-500',
    svg: 'fa-file-image-o text-purple-500',
    mp4: 'fa-file-video-o text-red-500',
    avi: 'fa-file-video-o text-red-500',
    mov: 'fa-file-video-o text-red-500',
    mp3: 'fa-file-audio-o text-pink-500',
    wav: 'fa-file-audio-o text-pink-500',
    zip: 'fa-file-archive-o text-yellow-600',
    rar: 'fa-file-archive-o text-yellow-600',
    '7z': 'fa-file-archive-o text-yellow-600',
    js: 'fa-file-code-o text-neutral-700',
    html: 'fa-file-code-o text-neutral-700',
    css: 'fa-file-code-o text-neutral-700',
    json: 'fa-file-code-o text-neutral-700',
    txt: 'fa-file-text-o text-neutral-500',
  }
  return map[ext] || 'fa-file-o text-neutral-400'
}