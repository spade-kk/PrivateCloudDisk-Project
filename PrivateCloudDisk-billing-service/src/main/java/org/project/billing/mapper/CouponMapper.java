package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.CouponEntity;

import java.util.List;

@Mapper
public interface CouponMapper {

    CouponEntity findByCouponCode(@Param("couponCode") String couponCode);

    CouponEntity findByCouponCodeForUpdate(@Param("couponCode") String couponCode);

    CouponEntity findById(@Param("id") Long id);

    int incrementUsedQuantity(@Param("couponCode") String couponCode);

    List<CouponEntity> findAllActive();
}