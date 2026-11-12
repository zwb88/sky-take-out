package com.sky.gateway;

import com.sky.entity.PaymentRecord;

import java.math.BigDecimal;

/**
 * 支付网关抽象接口
 * 后续可无缝替换为微信/支付宝等真实支付实现
 */
public interface PaymentGateway {

    /**
     * 发起支付
     *
     * @param outTradeNo  商户订单号
     * @param amount      支付金额
     * @param payMethod   支付方式
     * @return 预支付结果（包含流水号/签名等）
     */
    PaymentResult prepay(String outTradeNo, BigDecimal amount, Integer payMethod);

    /**
     * 查询支付状态
     *
     * @param outTradeNo   商户订单号
     * @param transactionId 支付流水号
     * @return 支付结果
     */
    PaymentResult query(String outTradeNo, String transactionId);

    /**
     * 关闭支付
     *
     * @param outTradeNo 商户订单号
     * @return 是否关闭成功
     */
    boolean close(String outTradeNo);

    /**
     * 退款
     *
     * @param outTradeNo    商户订单号
     * @param transactionId 支付流水号
     * @param refundAmount  退款金额
     * @return 退款结果
     */
    PaymentResult refund(String outTradeNo, String transactionId, BigDecimal refundAmount);

    /**
     * 支付结果封装
     */
    class PaymentResult {
        /** 状态：processing-支付中 success-成功 failed-失败 cancelled-已取消 refunded-已退款 */
        private String status;
        /** 支付流水号 */
        private String transactionId;
        /** 提示信息 */
        private String message;
        /** 原始响应（可选） */
        private Object rawResponse;

        public static PaymentResult processing(String transactionId, String message) {
            PaymentResult r = new PaymentResult();
            r.status = "processing";
            r.transactionId = transactionId;
            r.message = message;
            return r;
        }

        public static PaymentResult success(String transactionId, String message) {
            PaymentResult r = new PaymentResult();
            r.status = "success";
            r.transactionId = transactionId;
            r.message = message;
            return r;
        }

        public static PaymentResult failed(String transactionId, String message) {
            PaymentResult r = new PaymentResult();
            r.status = "failed";
            r.transactionId = transactionId;
            r.message = message;
            return r;
        }

        public static PaymentResult cancelled(String transactionId, String message) {
            PaymentResult r = new PaymentResult();
            r.status = "cancelled";
            r.transactionId = transactionId;
            r.message = message;
            return r;
        }

        public static PaymentResult refunded(String transactionId, String message) {
            PaymentResult r = new PaymentResult();
            r.status = "refunded";
            r.transactionId = transactionId;
            r.message = message;
            return r;
        }

        public String getStatus() { return status; }
        public String getTransactionId() { return transactionId; }
        public String getMessage() { return message; }
        public Object getRawResponse() { return rawResponse; }
        public void setRawResponse(Object rawResponse) { this.rawResponse = rawResponse; }
    }
}
