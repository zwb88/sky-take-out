package com.sky.constant;

/**
 * 支付相关常量
 */
public class PaymentConstant {

    private PaymentConstant() {}

    /**
     * 支付状态 0待支付 1支付中 2成功 3失败 4已取消 5已退款
     */
    public static final int PAYMENT_PENDING = 0;
    public static final int PAYMENT_PROCESSING = 1;
    public static final int PAYMENT_SUCCESS = 2;
    public static final int PAYMENT_FAILED = 3;
    public static final int PAYMENT_CANCELLED = 4;
    public static final int PAYMENT_REFUNDED = 5;

    /**
     * 支付方式
     */
    public static final int PAY_METHOD_WECHAT = 1;
    public static final int PAY_METHOD_ALIPAY = 2;
    public static final int PAY_METHOD_MOCK = 3;

    /**
     * 支付提示文案
     */
    public static final String MSG_PAYMENT_LOADING = "正在支付中，请稍候...";
    public static final String MSG_PAYMENT_SUCCESS = "支付成功";
    public static final String MSG_PAYMENT_FAILED = "支付失败，请稍后重试";
    public static final String MSG_PAYMENT_CANCELLED = "支付已取消";
    public static final String MSG_PAYMENT_ALREADY_PAID = "该订单已支付，请勿重复操作";
    public static final String MSG_PAYMENT_TIMEOUT = "支付超时，请重新发起支付";
    public static final String MSG_ORDER_NOT_FOUND = "订单不存在";
    public static final String MSG_ORDER_STATUS_INVALID = "订单状态不允许支付";
    public static final String MSG_PAYMENT_RECORD_NOT_FOUND = "支付记录不存在";

    /**
     * 支付超时时间（分钟）
     */
    public static final int PAYMENT_TIMEOUT_MINUTES = 15;
}
