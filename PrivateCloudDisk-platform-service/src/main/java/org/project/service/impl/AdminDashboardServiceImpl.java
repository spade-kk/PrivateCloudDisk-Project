package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import org.project.mapper.AdminAuditLogMapper;
import org.project.mapper.UserMapper;
import org.project.model.vo.DashboardVO;
import org.project.service.AdminDashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final UserMapper userMapper;
    private final AdminAuditLogMapper adminAuditLogMapper;

    @Override
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();

        DashboardVO.SystemOverview overview = new DashboardVO.SystemOverview();
        overview.setVersion("1.0.0");
        vo.setOverview(overview);

        vo.setStorageTrend(new ArrayList<>());
        vo.setUserGrowth(new ArrayList<>());
        vo.setFileTypeDistribution(new ArrayList<>());
        vo.setRecentActivities(new ArrayList<>());
        vo.setTopUsers(new ArrayList<>());
        vo.setAlerts(new ArrayList<>());

        return vo;
    }
}