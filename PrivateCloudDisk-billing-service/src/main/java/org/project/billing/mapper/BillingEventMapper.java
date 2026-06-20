package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.BillingEventEntity;

@Mapper
public interface BillingEventMapper {

    int insert(BillingEventEntity event);
}