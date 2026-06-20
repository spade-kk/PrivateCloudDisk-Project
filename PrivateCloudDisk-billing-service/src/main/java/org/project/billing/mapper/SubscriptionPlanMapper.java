package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.SubscriptionPlanEntity;

import java.util.List;

@Mapper
public interface SubscriptionPlanMapper {

    SubscriptionPlanEntity findByPlanCode(@Param("planCode") String planCode);

    SubscriptionPlanEntity findById(@Param("id") Long id);

    List<SubscriptionPlanEntity> findAllActive();

    List<SubscriptionPlanEntity> findAll();
}