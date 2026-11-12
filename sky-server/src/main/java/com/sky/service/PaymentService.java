package com.sky.service;

import com.sky.dto.MockPaymentDTO;
import com.sky.vo.PaymentResultVO;

import java.math.BigDecimal;

/**
 * 支付服务接口
 */
public interface PaymentService {

    /**
     * 发起模拟支付（创建支付记录 + 调用网关）
     *
     * @param dto 支付请求
     * @return 支付中状态的结果
     */
    PaymentResultVO prepay(MockPaymentDTO dto);

    /**
     * 异步查询支付结果
     * 根据支付记录调用网关查询，处理最终状态（成功/失败/取消），并联动更新订单状态
     *
     * @param orderNumber 商户订单号
     * @return 最终支付结果
     */
    PaymentResultVO queryAndFinalize(String orderNumber);

    /**
     * 根据订单号查询支付记录当前状态
     *
     * @param orderNumber 商户订单号
     * @return 支付状态
     */
    PaymentResultVO getStatus(String orderNumber);

    /**
     * 关闭/取消支付
     *
     * @param orderNumber 商户订单号
     * @return 操作结果
     */
    PaymentResultVO close(String orderNumber);

    /**
     * 退款（模拟）
     *
     * @param orderNumber  商户订单号
     * @param refundAmount 退款金额，若为 null 则全额退款
     * @return 退款结果
     */
    PaymentResultVO refund(String orderNumber, BigDecimal refundAmount);
}
