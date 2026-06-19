package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.IpBlacklistMapper;
import org.project.mapper.SecurityEventMapper;
import org.project.mapper.SystemConfigMapper;
import org.project.model.entity.IpBlacklistEntity;
import org.project.model.entity.SecurityEventEntity;
import org.project.model.entity.SystemConfigEntity;
import org.project.service.AdminSecurityService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSecurityServiceImpl implements AdminSecurityService {

    private final SecurityEventMapper securityEventMapper;
    private final IpBlacklistMapper ipBlacklistMapper;
    private final SystemConfigMapper systemConfigMapper;

    @Override
    public List<SecurityEventEntity> getSecurityEvents(String type, String severity, Boolean handled,
                                                       LocalDateTime startDate, LocalDateTime endDate,
                                                       int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return securityEventMapper.findByFilters(type, severity, handled, startDate, endDate, offset, pageSize);
    }

    @Override
    public long getSecurityEventCount(String type, String severity, Boolean handled,
                                      LocalDateTime startDate, LocalDateTime endDate) {
        return securityEventMapper.countByFilters(type, severity, handled, startDate, endDate);
    }

    @Override
    public void handleSecurityEvent(Long eventId, UUID handledBy, String resolution) {
        securityEventMapper.updateHandled(eventId, handledBy, LocalDateTime.now(), resolution);
        log.info("安全事件已处理: eventId={}, handledBy={}", eventId, handledBy);
    }

    @Override
    public void recordSecurityEvent(SecurityEventEntity event) {
        event.setEventCreatedAt(LocalDateTime.now());
        securityEventMapper.insertSecurityEvent(event);
    }

    @Override
    public List<IpBlacklistEntity> getIpBlacklist() {
        return ipBlacklistMapper.findAllActive();
    }

    @Override
    public void addIpBlacklist(String ip, String reason, UUID addedBy, LocalDateTime expiresAt) {
        IpBlacklistEntity entity = new IpBlacklistEntity();
        entity.setBlacklistIp(ip);
        entity.setBlacklistReason(reason);
        entity.setBlacklistAddedBy(addedBy);
        entity.setBlacklistStatus("ACTIVE");
        entity.setBlacklistExpiresAt(expiresAt);
        entity.setBlacklistCreatedAt(LocalDateTime.now());
        ipBlacklistMapper.insertIpBlacklist(entity);
        log.info("IP 已加入黑名单: ip={}, reason={}, addedBy={}", ip, reason, addedBy);
    }

    @Override
    public void removeIpBlacklist(String ip) {
        ipBlacklistMapper.updateStatus(ip, "REMOVED");
        log.info("IP 已从黑名单移除: ip={}", ip);
    }

    @Override
    public boolean isIpBlocked(String ip) {
        IpBlacklistEntity entity = ipBlacklistMapper.findByIpAndStatus(ip, "ACTIVE");
        return entity != null;
    }

    @Override
    public List<SystemConfigEntity> getAllConfigs() {
        return systemConfigMapper.findAll();
    }

    @Override
    public void updateConfig(String configKey, String configValue) {
        SystemConfigEntity config = systemConfigMapper.findByConfigKey(configKey);
        if (config == null) {
            log.warn("配置项不存在: {}", configKey);
            return;
        }
        int newVersion = config.getConfigVersion() + 1;
        systemConfigMapper.updateConfigValue(configKey, configValue, newVersion);
        log.info("系统配置已更新: key={}, version={}", configKey, newVersion);
    }
}