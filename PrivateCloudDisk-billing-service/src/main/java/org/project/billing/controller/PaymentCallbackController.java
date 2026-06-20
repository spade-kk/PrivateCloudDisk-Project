package org.project.billing.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.billing.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付回调接口
 * 接收支付宝、微信支付、Apple IAP 的异步通知
 */
@Slf4j
@RestController
@RequestMapping("/api/billing/callback")
@RequiredArgsConstructor
public class PaymentCallbackController {

    private final PaymentService paymentService;

    /**
     * 支付宝异步通知回调
     */
    @PostMapping("/alipay")
    public String alipayCallback(HttpServletRequest request) {
        try {
            String rawData = request.getReader().lines().collect(Collectors.joining("\n"));
            log.info("支付宝回调原始数据: {}", rawData);

            // 解析参数 (实际项目中需要验签)
            Map<String, String[]> paramMap = request.getParameterMap();
            String orderNo = request.getParameter("out_trade_no");
            String tradeNo = request.getParameter("trade_no");
            String totalAmount = request.getParameter("total_amount");
            String tradeStatus = request.getParameter("trade_status");

            if (orderNo == null || tradeNo == null) {
                log.error("支付宝回调参数缺失");
                return "fail";
            }

            if (!"TRADE_SUCCESS".equals(tradeStatus)) {
                log.info("支付宝回调非成功状态: {}", tradeStatus);
                return "success";
            }

            paymentService.handleAlipayCallback(orderNo, tradeNo,
                    new BigDecimal(totalAmount), rawData);

            return "success";
        } catch (Exception e) {
            log.error("支付宝回调处理异常", e);
            return "fail";
        }
    }

    /**
     * 微信支付异步通知回调
     */
    @PostMapping("/wechat")
    public String wechatCallback(HttpServletRequest request) {
        try {
            String rawData = request.getReader().lines().collect(Collectors.joining("\n"));
            log.info("微信支付回调原始数据: {}", rawData);

            // 实际项目中需要解密和验签
            // 这里简化处理，实际应从解密后的XML/JSON中提取
            String orderNo = request.getParameter("out_trade_no");
            String transactionId = request.getParameter("transaction_id");
            String totalAmount = request.getParameter("total_fee"); // 微信支付以分为单位

            if (orderNo == null || transactionId == null) {
                log.error("微信支付回调参数缺失");
                return "<xml><return_code><![CDATA[FAIL]]></return_code></xml>";
            }

            BigDecimal amount = new BigDecimal(totalAmount).divide(new BigDecimal("100"));
            paymentService.handleWechatCallback(orderNo, transactionId, amount, rawData);

            return "<xml><return_code><![CDATA[SUCCESS]]></return_code></xml>";
        } catch (Exception e) {
            log.error("微信支付回调处理异常", e);
            return "<xml><return_code><![CDATA[FAIL]]></return_code></xml>";
        }
    }

    /**
     * Apple IAP 收据验证回调
     */
    @PostMapping("/apple-iap")
    public String appleIAPCallback(@RequestBody Map<String, Object> requestBody) {
        try {
            String rawData = requestBody.toString();
            log.info("Apple IAP回调原始数据: {}", rawData);

            String orderNo = (String) requestBody.get("order_no");
            String transactionId = (String) requestBody.get("transaction_id");
            Object amountObj = requestBody.get("amount");

            if (orderNo == null || transactionId == null) {
                log.error("Apple IAP回调参数缺失");
                return "fail";
            }

            BigDecimal amount = amountObj != null ?
                    new BigDecimal(amountObj.toString()) : BigDecimal.ZERO;
            paymentService.handleAppleIAPCallback(orderNo, transactionId, amount, rawData);

            return "success";
        } catch (Exception e) {
            log.error("Apple IAP回调处理异常", e);
            return "fail";
        }
    }
}