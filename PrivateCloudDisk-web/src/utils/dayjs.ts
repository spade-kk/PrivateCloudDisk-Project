// ============================================================
// dayjs.ts — 全局日期时间工具
// ============================================================
// 基于 dayjs 的企业级日期处理模块，提供统一的中文日期格式化、
// 相对时间计算、时间差比较等功能，消除项目中分散的 Date 原生操作。
//
// 依赖：dayjs (v1.11+)
// 插件：relativeTime（相对时间）、localeData（中文数据）
// ============================================================

import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

// 注册插件
dayjs.extend(relativeTime)

// 全局设置中文语言
dayjs.locale('zh-cn')

// ============================================================
// 类型定义
// ============================================================

/** 可接受的日期输入类型 */
export type DateInput = string | number | Date | null | undefined

// ============================================================
// 核心格式化函数
// ============================================================

/**
 * 格式化完整日期时间
 *
 * @param date - 日期对象、时间戳或 ISO 字符串
 * @param format - 格式模板，默认 "YYYY-MM-DD HH:mm:ss"
 * @returns 格式化后的字符串，无效日期返回 "--"
 *
 * @example
 * formatDateTime(new Date())           // => "2025-06-01 14:30:25"
 * formatDateTime(1717230625000)        // => "2025-06-01 14:30:25"
 * formatDateTime(null)                 // => "--"
 */
export function formatDateTime(
  date: DateInput,
  format: string = 'YYYY-MM-DD HH:mm:ss',
): string {
  if (!date) return '--'
  const d = dayjs(date)
  return d.isValid() ? d.format(format) : '--'
}

/**
 * 格式化日期（仅年月日）
 *
 * @param date - 日期对象、时间戳或 ISO 字符串
 * @returns 格式化后的日期字符串，如 "2025-06-01"
 */
export function formatDate(date: DateInput): string {
  if (!date) return '--'
  const d = dayjs(date)
  return d.isValid() ? d.format('YYYY-MM-DD') : '--'
}

/**
 * 格式化时间（仅时分秒）
 *
 * @param date - 日期对象、时间戳或 ISO 字符串
 * @returns 格式化后的时间字符串，如 "14:30:25"
 */
export function formatTime(date: DateInput): string {
  if (!date) return '--'
  const d = dayjs(date)
  return d.isValid() ? d.format('HH:mm:ss') : '--'
}

/**
 * 格式化友好的日期时间（中文）
 *
 * @param date - 日期对象、时间戳或 ISO 字符串
 * @returns 中文格式日期时间，如 "2025年6月1日 14:30"
 */
export function formatChinese(date: DateInput): string {
  if (!date) return '--'
  const d = dayjs(date)
  return d.isValid() ? d.format('YYYY年M月D日 HH:mm') : '--'
}

// ============================================================
// 相对时间
// ============================================================

/**
 * 相对时间（人性化时间差）
 *
 * 使用 dayjs relativeTime 插件，自动处理 "刚刚"、"5 分钟前"、
 * "3 小时前"、"2 天前" 等中文描述。超过 30 天降级显示完整日期。
 *
 * @param date - 日期对象、时间戳或 ISO 字符串
 * @returns 人性化相对时间描述
 *
 * @example
 * timeAgo(Date.now() - 5000)       // => "几秒前"
 * timeAgo(Date.now() - 300000)     // => "5 分钟前"
 * timeAgo(Date.now() - 7200000)    // => "2 小时前"
 * timeAgo("2025-01-01")            // => "2025-01-01"
 */
export function timeAgo(date: DateInput): string {
  if (!date) return '--'
  const d = dayjs(date)
  if (!d.isValid()) return '--'

  const now = dayjs()
  const diffDays = now.diff(d, 'day')

  // 超过 30 天，显示完整日期
  if (diffDays > 30) {
    return d.format('YYYY-MM-DD')
  }

  // 使用 dayjs 内置的相对时间（中文）
  return d.fromNow()
}

// ============================================================
// 时间差计算
// ============================================================

/**
 * 计算两个日期之间的天数差
 *
 * @param from - 起始日期
 * @param to - 结束日期，默认当前时间
 * @returns 天数差（绝对值）
 */
export function diffDays(from: DateInput, to?: DateInput): number {
  const d1 = dayjs(from)
  const d2 = to ? dayjs(to) : dayjs()
  return Math.abs(d2.diff(d1, 'day'))
}

/**
 * 计算两个日期之间的小时差
 *
 * @param from - 起始日期
 * @param to - 结束日期，默认当前时间
 * @returns 小时差（绝对值）
 */
export function diffHours(from: DateInput, to?: DateInput): number {
  const d1 = dayjs(from)
  const d2 = to ? dayjs(to) : dayjs()
  return Math.abs(d2.diff(d1, 'hour'))
}

// ============================================================
// 判断函数
// ============================================================

/**
 * 判断是否为今天
 */
export function isToday(date: DateInput): boolean {
  return dayjs(date).isSame(dayjs(), 'day')
}

/**
 * 判断是否为本周
 */
export function isThisWeek(date: DateInput): boolean {
  return dayjs(date).isSame(dayjs(), 'week')
}

/**
 * 判断时间是否过期
 *
 * @param date - 目标日期
 * @returns true 表示已过期
 */
export function isExpired(date: DateInput): boolean {
  return dayjs(date).isBefore(dayjs())
}

// ============================================================
// dayjs 实例导出
// ============================================================

/** 直接导出 dayjs 实例，供需要自定义格式的场景使用 */
export { dayjs }