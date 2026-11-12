package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟支付请求 DTO
 */
@Data
public class MockPaymentDTO implements Serializable {
    /**
     * 商户订单号（必填）
     */
    private String orderNumber;

    /**
     * 支付方式 1微信 2支付宝 3模拟支付（默认3）
     */
    private Integer payMethod;
}
