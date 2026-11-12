package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 支付结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商户订单号 */
    private String orderNumber;

    /** 支付流水号 */
    private String transactionId;

    /** 支付状态：pending-待支付 processing-支付中 success-成功 failed-失败 cancelled-已取消 refunded-已退款 */
    private String status;

    /** 状态提示文案 */
    private String message;

    /** 支付方式 */
    private Integer payMethod;

    /** 是否已结束（成功/失败/取消均为true） */
    private boolean finished;
}
