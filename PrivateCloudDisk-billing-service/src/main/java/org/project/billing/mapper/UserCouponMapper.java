package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.UserCouponEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserCouponMapper {

    int insert(UserCouponEntity userCoupon);

    int useCoupon(@Param("id") Long id, @Param("orderId") Long orderId, @Param("usedAt") LocalDateTime usedAt);

    List<UserCouponEntity> findByUserId(@Param("userId") String userId);

    UserCouponEntity findByUserIdAndCouponId(@Param("userId") String userId, @Param("couponId") Long couponId);

    int countByUserIdAndCouponId(@Param("userId") String userId, @Param("couponId") Long couponId);
}