package org.project.model.vo;

import lombok.Data;
import java.util.List;

/**
 * 仪表盘数据 VO
 */
@Data
public class DashboardVO {
    private SystemOverview overview;
    private List<StorageTrendPoint> storageTrend;
    private List<UserGrowthPoint> userGrowth;
    private List<FileTypeDistribution> fileTypeDistribution;
    private List<RecentActivity> recentActivities;
    private List<TopUserInfo> topUsers;
    private List<AlertInfo> alerts;

    @Data
    public static class SystemOverview {
        private Long totalUsers;
        private Long activeUsers24h;
        private Long totalFiles;
        private Long totalStorageBytes;
        private Long totalDownloads;
        private Double cpuUsage;
        private Double memoryUsage;
        private Double diskUsage;
        private String uptime;
        private String version;
    }

    @Data
    public static class StorageTrendPoint {
        private String date;
        private Long bytes;
    }

    @Data
    public static class UserGrowthPoint {
        private String date;
        private Long count;
    }

    @Data
    public static class FileTypeDistribution {
        private String type;
        private Long count;
        private Long bytes;
    }

    @Data
    public static class RecentActivity {
        private String id;
        private String userId;
        private String userName;
        private String action;
        private String resource;
        private String resourceId;
        private String detail;
        private String ip;
        private String status;
        private LocalDateTime createdAt;
    }

    @Data
    public static class TopUserInfo {
        private String userId;
        private String userName;
        private Long usedBytes;
        private Long totalBytes;
    }

    @Data
    public static class AlertInfo {
        private String type;
        private String message;
        private String severity;
        private LocalDateTime createdAt;
    }
}