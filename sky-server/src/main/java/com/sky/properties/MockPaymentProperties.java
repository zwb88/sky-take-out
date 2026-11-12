package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模拟支付配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "mock.payment")
public class MockPaymentProperties {

    /**
     * 是否启用模拟支付
     */
    private boolean enabled = true;

    /**
     * 最小延迟（毫秒）
     */
    private long minDelayMs = 1000;

    /**
     * 最大延迟（毫秒）
     */
    private long maxDelayMs = 3000;

    /**
     * 支付成功概率（0-100）
     */
    private int successRate = 80;

    /**
     * 支付失败概率（0-100）
     */
    private int failRate = 10;

    /**
     * 支付取消概率（0-100）
     */
    private int cancelRate = 10;
}
