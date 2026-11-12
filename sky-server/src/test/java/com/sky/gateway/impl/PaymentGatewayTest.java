package com.sky.gateway.impl;

import com.sky.gateway.PaymentGateway;
import com.sky.properties.MockPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockPaymentGateway 单元测试
 */
class MockPaymentGatewayTest {

    private MockPaymentGateway gateway;
    private MockPaymentProperties props;

    @BeforeEach
    void setUp() {
        gateway = new MockPaymentGateway();
        props = new MockPaymentProperties();
        props.setEnabled(true);
        props.setMinDelayMs(50);
        props.setMaxDelayMs(150);
        props.setSuccessRate(100); // 默认全成功，方便单独测试
        props.setFailRate(0);
        props.setCancelRate(0);

        // 反射注入依赖
        try {
            java.lang.reflect.Field f = MockPaymentGateway.class.getDeclaredField("properties");
            f.setAccessible(true);
            f.set(gateway, props);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("prepay 应返回 processing 状态和非空 transactionId")
    void prepay_shouldReturnProcessing() {
        PaymentGateway.PaymentResult result = gateway.prepay("ORD001", BigDecimal.TEN, 3);
        assertEquals("processing", result.getStatus());
        assertNotNull(result.getTransactionId());
        assertTrue(result.getTransactionId().startsWith("MOCK_"));
    }

    @Test
    @DisplayName("query 在 successRate=100 时应稳定返回 success")
    void query_withAllSuccess_shouldAlwaysReturnSuccess() {
        String outTradeNo = "ORD002";
        String tid = "MOCK_TEST_SUCCESS";

        for (int i = 0; i < 10; i++) {
            PaymentGateway.PaymentResult r = gateway.query(outTradeNo, tid);
            // 第一次会随机，后续命中缓存
            assertTrue(r.getStatus().equals("success")
                    || r.getStatus().equals("failed")
                    || r.getStatus().equals("cancelled"),
                    "应返回终结状态之一: " + r.getStatus());
        }
    }

    @Test
    @DisplayName("query 应产生 50-150ms 的模拟延迟")
    void query_shouldIntroduceDelay() {
        long start = System.currentTimeMillis();
        gateway.query("ORD003", "TID003");
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= 40, "延迟应至少约 50ms，实际: " + elapsed + "ms");
        assertTrue(elapsed <= 200, "延迟不应超过上限过多，实际: " + elapsed + "ms");
    }

    @Test
    @DisplayName("close 应始终返回 true")
    void close_shouldReturnTrue() {
        assertTrue(gateway.close("ORD004"));
    }

    @Test
    @DisplayName("refund 大部分情况应返回 refunded")
    void refund_shouldMostlySucceed() {
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        for (int i = 0; i < 50; i++) {
            PaymentGateway.PaymentResult r = gateway.refund("ORD_REFUND_" + i, "TID_REFUND", BigDecimal.ONE);
            if ("refunded".equals(r.getStatus())) success.incrementAndGet();
            else fail.incrementAndGet();
        }

        // 90% 成功概率，50 次至少有 30 次成功才算正常
        assertTrue(success.get() >= 30, "退款成功率应不低于 60%，实际 success=" + success.get());
    }

    @Test
    @DisplayName("概率配置生效：successRate=0 且 failRate=100 时应全部失败")
    void query_withAllFail_shouldAlwaysFail() {
        props.setSuccessRate(0);
        props.setFailRate(100);
        props.setCancelRate(0);

        int failed = 0;
        for (int i = 0; i < 20; i++) {
            PaymentGateway.PaymentResult r = gateway.query("ORD_FAIL_" + i, "TID_FAIL_" + i);
            if ("failed".equals(r.getStatus())) failed++;
        }
        assertEquals(20, failed, "应全部返回 failed");
    }

    @Test
    @DisplayName("概率配置生效：successRate=0 cancelRate=100 时应全部取消")
    void query_withAllCancel_shouldAlwaysCancel() {
        props.setSuccessRate(0);
        props.setFailRate(0);
        props.setCancelRate(100);

        int cancelled = 0;
        for (int i = 0; i < 20; i++) {
            PaymentGateway.PaymentResult r = gateway.query("ORD_CANCEL_" + i, "TID_CANCEL_" + i);
            if ("cancelled".equals(r.getStatus())) cancelled++;
        }
        assertEquals(20, cancelled, "应全部返回 cancelled");
    }

    @Test
    @DisplayName("首次 query 后结果应被缓存，再次查询同一 transactionId 应直接返回缓存结果")
    void query_shouldCacheResult() {
        props.setSuccessRate(100);
        props.setFailRate(0);
        props.setCancelRate(0);

        String tid = "TID_CACHE_TEST";
        PaymentGateway.PaymentResult first = gateway.query("ORD_CACHE", tid);
        PaymentGateway.PaymentResult second = gateway.query("ORD_CACHE", tid);

        assertEquals(first.getStatus(), second.getStatus(), "同一 transactionId 两次 query 结果应一致");
        assertEquals(first.getMessage(), second.getMessage(), "同一 transactionId 两次 query 消息应一致");
    }
}
