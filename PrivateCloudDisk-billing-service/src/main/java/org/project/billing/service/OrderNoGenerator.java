package org.project.billing.service;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单号生成器
 * 格式: PCD + yyyyMMddHHmmss + 6位随机数
 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public String generateOrderNo() {
        String datePart = LocalDateTime.now().format(DATE_FORMATTER);
        String randomPart = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return "PCD" + datePart + randomPart;
    }

    public String generateInvoiceNo() {
        String datePart = LocalDateTime.now().format(DATE_FORMATTER);
        String randomPart = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        return "INV" + datePart + randomPart;
    }

    public String generateMessageId() {
        String datePart = LocalDateTime.now().format(DATE_FORMATTER);
        String randomPart = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        return "MSG" + datePart + randomPart;
    }
}