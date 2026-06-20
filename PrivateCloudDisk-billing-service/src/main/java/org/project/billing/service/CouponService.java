package org.project.billing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.common.BillingException;
import org.project.billing.mapper.CouponMapper;
import org.project.billing.mapper.UserCouponMapper;
import org.project.billing.mapper.BillingEventMapper;
import org.project.billing.model.entity.CouponEntity;
import org.project.billing.model.entity.UserCouponEntity;
import org.project.billing.model.entity.BillingEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 优惠券管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final BillingEventMapper billingEventMapper;

    /**
     * 领取优惠券
     */
    @Transactional(rollbackFor = Exception.class)
    public UserCouponEntity claimCoupon(String userId, String couponCode) {
        CouponEntity coupon = couponMapper.findByCouponCodeForUpdate(couponCode);
        if (coupon == null) {
            throw BillingException.notFound("优惠券不存在: " + couponCode);
        }
        if (!coupon.getIsActive()) {
            throw BillingException.badRequest("优惠券已失效: " + couponCode);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidTo())) {
            throw BillingException.badRequest("优惠券不在有效期内: " + couponCode);
        }

        if (coupon.getTotalQuantity() > 0 && coupon.getUsedQuantity() >= coupon.getTotalQuantity()) {
            throw BillingException.badRequest("优惠券已领完: " + couponCode);
        }

        // 检查用户领取次数
        int userCount = userCouponMapper.countByUserIdAndCouponId(userId, coupon.getId());
        if (userCount >= coupon.getPerUserLimit()) {
            throw BillingException.badRequest("已达到每人限领次数: " + couponCode);
        }

        // 增加已使用量
        couponMapper.incrementUsedQuantity(couponCode);

        // 创建用户优惠券记录
        UserCouponEntity userCoupon = new UserCouponEntity();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setCouponCode(couponCode);
        userCoupon.setStatus("UNUSED");
        userCouponMapper.insert(userCoupon);

        log.info("优惠券领取成功: userId={}, couponCode={}", userId, couponCode);
        return userCoupon;
    }

    /**
     * 获取用户优惠券列表
     */
    public List<UserCouponEntity> getUserCoupons(String userId) {
        return userCouponMapper.findByUserId(userId);
    }

    /**
     * 验证并计算优惠
     * @return 优惠金额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal validateAndCalculate(String userId, String couponCode, String planCode, BigDecimal orderAmount) {
        CouponEntity coupon = couponMapper.findByCouponCode(couponCode);
        if (coupon == null) {
            throw BillingException.notFound("优惠券不存在: " + couponCode);
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidTo())) {
            throw BillingException.badRequest("优惠券已过期: " + couponCode);
        }

        // 检查适用计划
        if (coupon.getApplicablePlans() != null && !coupon.getApplicablePlans().isEmpty()) {
            String[] plans = coupon.getApplicablePlans().split(",");
            boolean planMatch = false;
            for (String p : plans) {
                if (p.trim().equals(planCode)) {
                    planMatch = true;
                    break;
                }
            }
            if (!planMatch) {
                throw BillingException.badRequest("优惠券不适用于当前计划: " + couponCode);
            }
        }

        // 检查最低订单金额
        if (orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw BillingException.badRequest("订单金额不满足最低要求: " + coupon.getMinOrderAmount());
        }

        // 检查用户是否有未使用的此优惠券
        UserCouponEntity userCoupon = userCouponMapper.findByUserIdAndCouponId(userId, coupon.getId());
        if (userCoupon == null) {
            throw BillingException.badRequest("用户未领取该优惠券: " + couponCode);
        }

        // 计算优惠金额
        BigDecimal discountAmount = BigDecimal.ZERO;
        switch (coupon.getCouponType()) {
            case "DISCOUNT":
                // 折扣类型: discountPercent 是折扣后百分比，如 50 表示 5 折
                BigDecimal discountRatio = coupon.getDiscountPercent().divide(new BigDecimal("100"));
                discountAmount = orderAmount.multiply(BigDecimal.ONE.subtract(discountRatio));
                break;
            case "FIXED_AMOUNT":
                discountAmount = coupon.getFixedAmount();
                break;
            default:
                throw BillingException.badRequest("不支持的优惠券类型: " + coupon.getCouponType());
        }

        // 限制最大优惠金额
        if (coupon.getMaxDiscountAmount() != null
                && discountAmount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
            discountAmount = coupon.getMaxDiscountAmount();
        }

        // 优惠金额不能超过订单金额
        if (discountAmount.compareTo(orderAmount) > 0) {
            discountAmount = orderAmount;
        }

        return discountAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 使用优惠券
     */
    @Transactional(rollbackFor = Exception.class)
    public void useCoupon(String userId, String couponCode, Long orderId) {
        CouponEntity coupon = couponMapper.findByCouponCode(couponCode);
        UserCouponEntity userCoupon = userCouponMapper.findByUserIdAndCouponId(userId, coupon.getId());
        if (userCoupon == null) {
            throw BillingException.badRequest("用户未领取该优惠券");
        }
        userCouponMapper.useCoupon(userCoupon.getId(), orderId, LocalDateTime.now());

        log.info("优惠券已使用: userId={}, couponCode={}, orderId={}", userId, couponCode, orderId);
    }

    public List<CouponEntity> getAllActiveCoupons() {
        return couponMapper.findAllActive();
    }
}