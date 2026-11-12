package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 商户订单号（订单号）
     */
    private String outTradeNo;

    /**
     * 支付流水号（模拟/第三方支付返回）
     */
    private String transactionId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付方式 1微信 2支付宝 3模拟支付
     */
    private Integer payMethod;

    /**
     * 支付状态 0待支付 1支付中 2成功 3失败 4已取消 5已退款
     */
    private Integer payStatus;

    /**
     * 失败原因/取消原因
     */
    private String failReason;

    /**
     * 支付发起时间
     */
    private LocalDateTime createTime;

    /**
     * 支付完成时间（成功/失败/取消）
     */
    private LocalDateTime finishTime;
}
