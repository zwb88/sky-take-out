package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.PaymentConstant;
import com.sky.context.BaseContext;
import com.sky.dto.MockPaymentDTO;
import com.sky.entity.Orders;
import com.sky.entity.PaymentRecord;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.PaymentBusinessException;
import com.sky.gateway.PaymentGateway;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.PaymentRecordMapper;
import com.sky.service.PaymentService;
import com.sky.service.OrderService;
import com.sky.vo.PaymentResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付服务实现
 */
@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentRecordMapper paymentRecordMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired(required = false)
    private PaymentGateway paymentGateway;

    /** 委托给 OrderService 的回调方法：支付成功时更新订单状态 */
    @Autowired
    private OrderService orderService;

    @Override
    @Transactional
    public PaymentResultVO prepay(MockPaymentDTO dto) {
        String outTradeNo = dto.getOrderNumber();
        Integer payMethod = dto.getPayMethod() == null ? PaymentConstant.PAY_METHOD_MOCK : dto.getPayMethod();

        log.info("[Payment] 发起支付: orderNumber={}, payMethod={}", outTradeNo, payMethod);

        // 1. 校验订单存在性
        Orders order = requireOwnOrder(outTradeNo);

        // 2. 重复支付拦截：仅拦截已支付/已退款，失败或取消的订单允许重新发起
        PaymentRecord existRecord = paymentRecordMapper.getByOutTradeNo(outTradeNo);
        if (existRecord != null) {
            if (PaymentConstant.PAYMENT_SUCCESS == existRecord.getPayStatus()
                    || PaymentConstant.PAYMENT_REFUNDED == existRecord.getPayStatus()) {
                log.info("[Payment] 该订单已支付，拦截重复支付: status={}", existRecord.getPayStatus());
                return recordToVO(existRecord);
            }
            // 待支付/支付中/失败/已取消：重置记录后重新发起
            log.info("[Payment] 重置既有支付记录并重新发起: id={}, 原状态={}",
                    existRecord.getId(), existRecord.getPayStatus());
            existRecord.setPayStatus(PaymentConstant.PAYMENT_PENDING);
            existRecord.setFailReason(null);
            existRecord.setTransactionId(null);
            existRecord.setFinishTime(null);
            paymentRecordMapper.update(existRecord);
            // 使用重新查询的最新记录
            existRecord = paymentRecordMapper.getById(existRecord.getId());
        }

        // 3. 校验订单状态（只有待支付状态的订单才能发起支付）
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (Orders.PAID.equals(order.getPayStatus())) {
            throw new PaymentBusinessException(PaymentConstant.MSG_PAYMENT_ALREADY_PAID);
        }

        // 4. 创建/获取支付记录
        PaymentRecord record = existRecord;
        if (record == null) {
            record = PaymentRecord.builder()
                    .outTradeNo(outTradeNo)
                    .amount(order.getAmount())
                    .payMethod(payMethod)
                    .payStatus(PaymentConstant.PAYMENT_PENDING)
                    .createTime(LocalDateTime.now())
                    .build();
            paymentRecordMapper.insert(record);
            log.info("[Payment] 创建支付记录: id={}", record.getId());
        }

        // 5. 调用支付网关发起支付
        PaymentGateway.PaymentResult gatewayResult;
        if (paymentGateway == null) {
            log.warn("[Payment] 未配置 PaymentGateway，使用默认模拟");
            gatewayResult = PaymentGateway.PaymentResult.processing("DEFAULT_" + System.currentTimeMillis(), PaymentConstant.MSG_PAYMENT_LOADING);
        } else {
            gatewayResult = paymentGateway.prepay(outTradeNo, order.getAmount(), payMethod);
        }

        // 6. 更新支付记录为"支付中"
        record.setTransactionId(gatewayResult.getTransactionId());
        record.setPayStatus(PaymentConstant.PAYMENT_PROCESSING);
        paymentRecordMapper.update(record);

        log.info("[Payment] 支付已发起: orderNumber={}, transactionId={}", outTradeNo, record.getTransactionId());

        return PaymentResultVO.builder()
                .orderNumber(outTradeNo)
                .transactionId(record.getTransactionId())
                .status("processing")
                .message(PaymentConstant.MSG_PAYMENT_LOADING)
                .payMethod(payMethod)
                .finished(false)
                .build();
    }

    @Override
    @Transactional
    public PaymentResultVO queryAndFinalize(String orderNumber) {
        log.info("[Payment] 查询并终结支付: orderNumber={}", orderNumber);

        // 1. 查询支付记录
        PaymentRecord record = paymentRecordMapper.getByOutTradeNo(orderNumber);
        if (record == null) {
            throw new PaymentBusinessException(PaymentConstant.MSG_PAYMENT_RECORD_NOT_FOUND);
        }
        requireOwnOrder(orderNumber);

        // 2. 已经终结的记录，直接返回
        if (isFinishedStatus(record.getPayStatus())) {
            log.info("[Payment] 支付已终结，直接返回: status={}", record.getPayStatus());
            return recordToVO(record);
        }

        // 3. 查询网关最终结果
        PaymentGateway.PaymentResult gatewayResult;
        if (paymentGateway == null) {
            gatewayResult = PaymentGateway.PaymentResult.processing(null, PaymentConstant.MSG_PAYMENT_LOADING);
        } else {
            gatewayResult = paymentGateway.query(orderNumber, record.getTransactionId());
        }

        // 4. 根据网关结果更新支付记录状态
        String finalStatus = gatewayResult.getStatus();
        switch (finalStatus) {
            case "success":
                markSuccess(record, gatewayResult);
                triggerOrderPaid(orderNumber);
                break;
            case "failed":
                markFailed(record, gatewayResult);
                break;
            case "cancelled":
                markCancelled(record, gatewayResult);
                break;
            case "processing":
            default:
                // 继续等待
                record.setPayStatus(PaymentConstant.PAYMENT_PROCESSING);
                paymentRecordMapper.update(record);
                return PaymentResultVO.builder()
                        .orderNumber(orderNumber)
                        .transactionId(record.getTransactionId())
                        .status("processing")
                        .message(PaymentConstant.MSG_PAYMENT_LOADING)
                        .payMethod(record.getPayMethod())
                        .finished(false)
                        .build();
        }

        // 5. 返回终结后的记录
        PaymentRecord finalRecord = paymentRecordMapper.getById(record.getId());
        return recordToVO(finalRecord);
    }

    @Override
    public PaymentResultVO getStatus(String orderNumber) {
        PaymentRecord record = paymentRecordMapper.getByOutTradeNo(orderNumber);
        if (record == null) {
            throw new PaymentBusinessException(PaymentConstant.MSG_PAYMENT_RECORD_NOT_FOUND);
        }
        requireOwnOrder(orderNumber);
        return recordToVO(record);
    }

    @Override
    @Transactional
    public PaymentResultVO close(String orderNumber) {
        log.info("[Payment] 关闭支付: orderNumber={}", orderNumber);

        PaymentRecord record = paymentRecordMapper.getByOutTradeNo(orderNumber);
        if (record == null) {
            throw new PaymentBusinessException(PaymentConstant.MSG_PAYMENT_RECORD_NOT_FOUND);
        }
        requireOwnOrder(orderNumber);
        if (isFinishedStatus(record.getPayStatus())) {
            return recordToVO(record);
        }

        if (paymentGateway != null) {
            paymentGateway.close(orderNumber);
        }


        record.setPayStatus(PaymentConstant.PAYMENT_CANCELLED);
        record.setFailReason("用户主动关闭支付");
        record.setFinishTime(LocalDateTime.now());
        paymentRecordMapper.update(record);

        return PaymentResultVO.builder()
                .orderNumber(orderNumber)
                .transactionId(record.getTransactionId())
                .status("cancelled")
                .message(PaymentConstant.MSG_PAYMENT_CANCELLED)
                .payMethod(record.getPayMethod())
                .finished(true)
                .build();
    }

    @Override
    @Transactional
    public PaymentResultVO refund(String orderNumber, BigDecimal refundAmount) {
        log.info("[Payment] 发起退款: orderNumber={}, refundAmount={}", orderNumber, refundAmount);

        // 归属校验必须最先执行：订单号来自前端，不能让他人仅凭订单号探测/操作订单
        Orders order = requireOwnOrder(orderNumber);

        PaymentRecord record = paymentRecordMapper.getByOutTradeNo(orderNumber);
        if (record == null) {
            throw new PaymentBusinessException(PaymentConstant.MSG_PAYMENT_RECORD_NOT_FOUND);
        }
        if (PaymentConstant.PAYMENT_SUCCESS != record.getPayStatus()) {
            throw new PaymentBusinessException("仅已支付的订单允许退款");
        }

        BigDecimal amount = refundAmount == null ? record.getAmount() : refundAmount;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentBusinessException("退款金额必须大于 0");
        }
        if (amount.compareTo(record.getAmount()) > 0) {
            throw new PaymentBusinessException("退款金额不能超过支付金额");
        }

        PaymentGateway.PaymentResult refundResult;
        if (paymentGateway == null) {
            refundResult = PaymentGateway.PaymentResult.refunded("DEFAULT_REFUND_" + System.currentTimeMillis(), "退款成功");
        } else {
            refundResult = paymentGateway.refund(orderNumber, record.getTransactionId(), amount);
        }

        if ("refunded".equals(refundResult.getStatus())) {
            record.setPayStatus(PaymentConstant.PAYMENT_REFUNDED);
            record.setFinishTime(LocalDateTime.now());
            paymentRecordMapper.update(record);

            // 更新订单支付状态为"退款"
            Orders refundOrder = Orders.builder()
                    .id(order.getId())
                    .payStatus(Orders.REFUND)
                    .build();
            orderMapper.update(refundOrder);

            return PaymentResultVO.builder()
                    .orderNumber(orderNumber)
                    .transactionId(refundResult.getTransactionId())
                    .status("refunded")
                    .message(refundResult.getMessage() != null ? refundResult.getMessage() : "退款成功")
                    .payMethod(record.getPayMethod())
                    .finished(true)
                    .build();
        } else {
            return PaymentResultVO.builder()
                    .orderNumber(orderNumber)
                    .transactionId(refundResult.getTransactionId())
                    .status("failed")
                    .message(refundResult.getMessage() != null ? refundResult.getMessage() : "退款失败")
                    .payMethod(record.getPayMethod())
                    .finished(true)
                    .build();
        }
    }

    // --------- 私有工具方法 ---------

    /**
     * 标记支付成功
     */
    private void markSuccess(PaymentRecord record, PaymentGateway.PaymentResult gatewayResult) {
        record.setPayStatus(PaymentConstant.PAYMENT_SUCCESS);
        record.setFailReason(null);
        record.setFinishTime(LocalDateTime.now());
        if (gatewayResult.getTransactionId() != null) {
            record.setTransactionId(gatewayResult.getTransactionId());
        }
        paymentRecordMapper.update(record);
    }

    /**
     * 标记支付失败
     */
    private void markFailed(PaymentRecord record, PaymentGateway.PaymentResult gatewayResult) {
        record.setPayStatus(PaymentConstant.PAYMENT_FAILED);
        record.setFailReason(gatewayResult.getMessage());
        record.setFinishTime(LocalDateTime.now());
        paymentRecordMapper.update(record);
    }

    /**
     * 标记支付取消
     */
    private void markCancelled(PaymentRecord record, PaymentGateway.PaymentResult gatewayResult) {
        record.setPayStatus(PaymentConstant.PAYMENT_CANCELLED);
        record.setFailReason(gatewayResult.getMessage());
        record.setFinishTime(LocalDateTime.now());
        paymentRecordMapper.update(record);
    }

    /**
     * 触发订单支付成功状态变更（复用 OrderService#paySuccess）
     */
    private void triggerOrderPaid(String outTradeNo) {
        try {
            orderService.paySuccess(outTradeNo);
            log.info("[Payment] 已通知订单支付成功: outTradeNo={}", outTradeNo);
        } catch (Exception e) {
            // 订单可能已是已支付状态，幂等处理
            log.warn("[Payment] 订单状态更新异常（可能已处理过）: outTradeNo={}, msg={}", outTradeNo, e.getMessage());
        }
    }

    /**
     * 数据库状态码 -> 状态字符串
     */
    private PaymentResultVO recordToVO(PaymentRecord record) {
        String status;
        String message;
        boolean finished;
        switch (record.getPayStatus()) {
            case PaymentConstant.PAYMENT_PENDING:
                status = "pending";
                message = PaymentConstant.MSG_PAYMENT_LOADING;
                finished = false;
                break;
            case PaymentConstant.PAYMENT_PROCESSING:
                status = "processing";
                message = PaymentConstant.MSG_PAYMENT_LOADING;
                finished = false;
                break;
            case PaymentConstant.PAYMENT_SUCCESS:
                status = "success";
                message = PaymentConstant.MSG_PAYMENT_SUCCESS;
                finished = true;
                break;
            case PaymentConstant.PAYMENT_FAILED:
                status = "failed";
                message = record.getFailReason() != null ? record.getFailReason() : PaymentConstant.MSG_PAYMENT_FAILED;
                finished = true;
                break;
            case PaymentConstant.PAYMENT_CANCELLED:
                status = "cancelled";
                message = record.getFailReason() != null ? record.getFailReason() : PaymentConstant.MSG_PAYMENT_CANCELLED;
                finished = true;
                break;
            case PaymentConstant.PAYMENT_REFUNDED:
                status = "refunded";
                message = "已退款";
                finished = true;
                break;
            default:
                status = "unknown";
                message = MessageConstant.UNKNOWN_ERROR;
                finished = true;
        }

        return PaymentResultVO.builder()
                .orderNumber(record.getOutTradeNo())
                .transactionId(record.getTransactionId())
                .status(status)
                .message(message)
                .payMethod(record.getPayMethod())
                .finished(finished)
                .build();
    }

    /**
     * 是否终结状态
     */
    private static boolean isFinishedStatus(Integer payStatus) {
        return PaymentConstant.PAYMENT_SUCCESS == payStatus
                || PaymentConstant.PAYMENT_FAILED == payStatus
                || PaymentConstant.PAYMENT_CANCELLED == payStatus
                || PaymentConstant.PAYMENT_REFUNDED == payStatus;
    }

    /**
     * 查出订单并校验归属。orderNumber 来自前端，必须确认是当前用户的订单。
     */
    private Orders requireOwnOrder(String orderNumber) {
        Orders order = orderMapper.getByNumber(orderNumber);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Long userId = BaseContext.getCurrentId();
        // fail-closed：未登录或不是本人订单一律拒绝，避免 ThreadLocal 为空时跳过归属校验
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new PaymentBusinessException("无权操作该订单");
        }
        return order;
    }
    // TODO 11/28 前：去掉 try/catch，改为 paySuccess 内部状态判断实现幂等
}
