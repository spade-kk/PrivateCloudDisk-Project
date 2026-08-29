package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/** 轻量分页响应，避免好友搜索和申请列表一次加载全部历史记录。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {
    private List<T> items;
    private int page;
    private int size;
    private long total;
    private boolean hasMore;

    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        return PageResult.<T>builder().items(items).page(page).size(size).total(total)
                .hasMore((long) page * size < total).build();
    }
}
