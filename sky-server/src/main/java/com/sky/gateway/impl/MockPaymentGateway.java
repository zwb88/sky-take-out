package com.sky.gateway.impl;

import com.sky.gateway.PaymentGateway;
import com.sky.properties.MockPaymentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模拟支付网关实现
 * 通过 mock.payment.enabled=true 启用
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "mock.payment", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Autowired
    private MockPaymentProperties properties;

    /**
     * 模拟支付中的请求记录：key=transactionId, value=最终结果
     * 用于处理异步回调场景
     */
    private final ConcurrentMap<String, MockPaymentResult> pendingResults = new ConcurrentHashMap<>();

    @Override
    public PaymentResult prepay(String outTradeNo, BigDecimal amount, Integer payMethod) {
        String transactionId = "MOCK_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        log.info("[MockPayment] 发起模拟支付: outTradeNo={}, amount={}, transactionId={}", outTradeNo, amount, transactionId);
        return PaymentResult.processing(transactionId, "模拟支付已发起，正在处理中...");
    }

    @Override
    public PaymentResult query(String outTradeNo, String transactionId) {
        MockPaymentResult cached = pendingResults.get(transactionId);
        if (cached != null) {
            log.info("[MockPayment] 查询支付: transactionId={}, 命中缓存结果={}", transactionId, cached.status);
            return cached.toPaymentResult();
        }

        // 首次查询：模拟随机延迟 + 随机结果
        long delay = computeDelay();
        log.info("[MockPayment] 查询支付: transactionId={}, 模拟延迟={}ms", transactionId, delay);
        sleepQuietly(delay);

        MockPaymentResult result = simulateResult();
        pendingResults.put(transactionId, result);
        log.info("[MockPayment] 查询支付: transactionId={}, 随机结果={}", transactionId, result.status);
        return result.toPaymentResult();
    }

    @Override
    public boolean close(String outTradeNo) {
        log.info("[MockPayment] 关闭支付: outTradeNo={}", outTradeNo);
        return true;
    }

    @Override
    public PaymentResult refund(String outTradeNo, String transactionId, BigDecimal refundAmount) {
        log.info("[MockPayment] 模拟退款: outTradeNo={}, transactionId={}, amount={}", outTradeNo, transactionId, refundAmount);
        long delay = computeDelay();
        sleepQuietly(delay);

        // 模拟退款：90% 成功
        if (randomInt(100) < 90) {
            log.info("[MockPayment] 模拟退款成功");
            return PaymentResult.refunded("MOCK_REFUND_" + System.currentTimeMillis(), "退款成功");
        } else {
            log.info("[MockPayment] 模拟退款失败");
            return PaymentResult.failed(transactionId, "退款失败，余额不足");
        }
    }

    /**
     * 根据概率配置随机生成支付结果
     */
    private MockPaymentResult simulateResult() {
        int roll = randomInt(100);
        int successRate = properties.getSuccessRate();
        int failRate = properties.getFailRate();
        int cancelRate = properties.getCancelRate();

        // 归一化后再判断（容错）
        int total = Math.max(1, successRate + failRate + cancelRate);
        double successProb = successRate * 100.0 / total;
        double failProb = failRate * 100.0 / total;

        if (roll < successProb) {
            return new MockPaymentResult("success", "支付成功");
        } else if (roll < successProb + failProb) {
            return new MockPaymentResult("failed", "支付失败：模拟的失败场景");
        } else {
            return new MockPaymentResult("cancelled", "支付已取消：用户主动取消或超时");
        }
    }

    /**
     * 计算随机延迟
     */
    private long computeDelay() {
        long min = Math.max(0, properties.getMinDelayMs());
        long max = Math.max(min, properties.getMaxDelayMs());
        if (min == max) return min;
        return min + randomInt((int) (max - min));
    }

    private static int randomInt(int bound) {
        if (bound <= 0) return 0;
        return (int) (Math.random() * bound);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 单次模拟结果记录
     */
    static class MockPaymentResult {
        final String status;
        final String message;

        MockPaymentResult(String status, String message) {
            this.status = status;
            this.message = message;
        }

        PaymentResult toPaymentResult() {
            switch (status) {
                case "success":  return PaymentResult.success(null, message);
                case "failed":   return PaymentResult.failed(null, message);
                case "cancelled":return PaymentResult.cancelled(null, message);
                case "refunded": return PaymentResult.refunded(null, message);
                default:         return PaymentResult.processing(null, message);
            }
        }
    }
}
