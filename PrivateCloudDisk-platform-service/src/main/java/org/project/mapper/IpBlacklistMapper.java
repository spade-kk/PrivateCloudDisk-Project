package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.model.entity.IpBlacklistEntity;

import java.util.List;

@Mapper
public interface IpBlacklistMapper {
    IpBlacklistEntity findByIpAndStatus(@Param("blacklist_ip") String ip, @Param("blacklist_status") String status);

    List<IpBlacklistEntity> findAllActive();

    int insertIpBlacklist(IpBlacklistEntity entity);

    int updateStatus(@Param("blacklist_ip") String ip, @Param("blacklist_status") String status);
}