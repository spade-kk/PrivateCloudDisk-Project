package org.project.billing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.project.billing.model.entity.UserSubscriptionEntity;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserSubscriptionMapper {

    UserSubscriptionEntity findByUserId(@Param("userId") String userId);

    UserSubscriptionEntity findByUserIdForUpdate(@Param("userId") String userId);

    int insert(UserSubscriptionEntity subscription);

    int updateStatus(@Param("userId") String userId, @Param("status") String status);

    int updatePlan(@Param("userId") String userId,
                   @Param("planId") Long planId,
                   @Param("billingCycle") String billingCycle,
                   @Param("startDate") LocalDateTime startDate,
                   @Param("endDate") LocalDateTime endDate,
                   @Param("nextBillingDate") LocalDateTime nextBillingDate);

    int updateSubscription(UserSubscriptionEntity subscription);

    List<UserSubscriptionEntity> findExpiringSubscriptions(@Param("beforeDate") LocalDateTime beforeDate);

    List<UserSubscriptionEntity> findExpiredSubscriptions(@Param("now") LocalDateTime now);

    List<UserSubscriptionEntity> findAutoRenewSubscriptions(@Param("now") LocalDateTime now);
}