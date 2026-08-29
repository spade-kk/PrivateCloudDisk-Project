package org.project.plugin.model;

import java.util.List;

/** 基于稳定 sequence_no 的游标分页，避免大日志 Offset 分页越翻越慢。 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {
}
