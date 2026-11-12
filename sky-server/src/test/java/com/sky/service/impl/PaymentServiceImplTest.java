package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.PaymentConstant;
import com.sky.context.BaseContext;
import com.sky.dto.MockPaymentDTO;
import com.sky.entity.Orders;
import com.sky.entity.PaymentRecord;
import com.sky.exception.PaymentBusinessException;
import com.sky.gateway.PaymentGateway;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.PaymentRecordMapper;
import com.sky.service.OrderService;
import com.sky.vo.PaymentResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PaymentServiceImpl 单元测试（纯 Mockito，不启 Spring 上下文）
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderService orderService;

    private Orders sampleOrder;

    @BeforeEach
    void setUp() {
        sampleOrder = Orders.builder()
                .id(1L)
                .number("TEST_ORDER_001")
                .status(Orders.PENDING_PAYMENT)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("29.99"))
                .build();
    }

    // ==================== prepay 测试 ====================

    @Test
    @DisplayName("prepay: 正常发起支付应创建记录并返回 processing")
    void prepay_normal_shouldCreateRecord() {
        MockPaymentDTO dto = new MockPaymentDTO();
        dto.setOrderNumber("TEST_ORDER_001");
        dto.setPayMethod(PaymentConstant.PAY_METHOD_MOCK);

        when(orderMapper.getByNumber("TEST_ORDER_001")).thenReturn(sampleOrder);
        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_001")).thenReturn(null);
        when(paymentGateway.prepay(eq("TEST_ORDER_001"), any(), any()))
                .thenReturn(PaymentGateway.PaymentResult.processing("TXN_001", "处理中"));

        PaymentResultVO vo = paymentService.prepay(dto);

        assertEquals("processing", vo.getStatus());
        assertFalse(vo.isFinished());
        verify(paymentRecordMapper).insert(any(PaymentRecord.class));
        verify(paymentRecordMapper).update(any(PaymentRecord.class));
    }

    @Test
    @DisplayName("prepay: 订单不存在应抛 OrderBusinessException")
    void prepay_orderNotFound_shouldThrow() {
        MockPaymentDTO dto = new MockPaymentDTO();
        dto.setOrderNumber("NO_SUCH_ORDER");

        when(orderMapper.getByNumber("NO_SUCH_ORDER")).thenReturn(null);

        Exception ex = assertThrows(Exception.class, () -> paymentService.prepay(dto));
        assertTrue(ex.getMessage().contains(MessageConstant.ORDER_NOT_FOUND));
    }

    @Test
    @DisplayName("prepay: 已支付的订单应拦截重复支付")
    void prepay_alreadyPaid_shouldBlock() {
        sampleOrder.setPayStatus(Orders.PAID);
        MockPaymentDTO dto = new MockPaymentDTO();
        dto.setOrderNumber("TEST_ORDER_001");

        when(orderMapper.getByNumber("TEST_ORDER_001")).thenReturn(sampleOrder);

        Exception ex = assertThrows(Exception.class, () -> paymentService.prepay(dto));
        assertTrue(ex.getMessage().contains(PaymentConstant.MSG_PAYMENT_ALREADY_PAID));
    }

    @Test
    @DisplayName("prepay: 订单状态非待支付应抛异常")
    void prepay_statusNotPending_shouldThrow() {
        sampleOrder.setStatus(Orders.TO_BE_CONFIRMED);
        MockPaymentDTO dto = new MockPaymentDTO();
        dto.setOrderNumber("TEST_ORDER_001");

        when(orderMapper.getByNumber("TEST_ORDER_001")).thenReturn(sampleOrder);

        Exception ex = assertThrows(Exception.class, () -> paymentService.prepay(dto));
        assertTrue(ex.getMessage().contains(MessageConstant.ORDER_STATUS_ERROR));
    }

    @Test
    @DisplayName("prepay: 已有完成的支付记录应直接返回，不再重复发起")
    void prepay_hasFinishedRecord_shouldReturnCurrent() {
        PaymentRecord existing = PaymentRecord.builder()
                .id(99L)
                .outTradeNo("TEST_ORDER_001")
                .amount(new BigDecimal("29.99"))
                .payMethod(PaymentConstant.PAY_METHOD_MOCK)
                .payStatus(PaymentConstant.PAYMENT_SUCCESS)
                .build();

        MockPaymentDTO dto = new MockPaymentDTO();
        dto.setOrderNumber("TEST_ORDER_001");

        when(orderMapper.getByNumber("TEST_ORDER_001")).thenReturn(sampleOrder);
        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_001")).thenReturn(existing);

        PaymentResultVO vo = paymentService.prepay(dto);
        assertEquals("success", vo.getStatus());
        assertTrue(vo.isFinished());
        verify(paymentGateway, never()).prepay(any(), any(), any());
    }

    // ==================== queryAndFinalize 测试 ====================

    @Test
    @DisplayName("queryAndFinalize: 网关返回 success 应更新记录并触发订单支付成功")
    void query_success_shouldFinalizeAndNotifyOrder() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_001")
                .transactionId("TXN_001")
                .amount(new BigDecimal("29.99"))
                .payMethod(PaymentConstant.PAY_METHOD_MOCK)
                .payStatus(PaymentConstant.PAYMENT_PROCESSING)
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_001")).thenReturn(record);
        when(paymentGateway.query("TEST_ORDER_001", "TXN_001"))
                .thenReturn(PaymentGateway.PaymentResult.success("TXN_001", "支付成功"));
        when(paymentRecordMapper.getById(1L)).thenReturn(
                PaymentRecord.builder()
                        .id(1L)
                        .outTradeNo("TEST_ORDER_001")
                        .payStatus(PaymentConstant.PAYMENT_SUCCESS)
                        .build()
        );

        PaymentResultVO vo = paymentService.queryAndFinalize("TEST_ORDER_001");

        assertEquals("success", vo.getStatus());
        assertTrue(vo.isFinished());
        verify(orderService).paySuccess("TEST_ORDER_001");
    }

    @Test
    @DisplayName("queryAndFinalize: 网关返回 failed 应标记失败，不触发订单更新")
    void query_failed_shouldMarkFailed() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_002")
                .transactionId("TXN_002")
                .payStatus(PaymentConstant.PAYMENT_PROCESSING)
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_002")).thenReturn(record);
        when(paymentGateway.query("TEST_ORDER_002", "TXN_002"))
                .thenReturn(PaymentGateway.PaymentResult.failed("TXN_002", "余额不足"));
        when(paymentRecordMapper.getById(1L)).thenReturn(
                PaymentRecord.builder().id(1L).payStatus(PaymentConstant.PAYMENT_FAILED).failReason("余额不足").build()
        );

        PaymentResultVO vo = paymentService.queryAndFinalize("TEST_ORDER_002");

        assertEquals("failed", vo.getStatus());
        assertTrue(vo.isFinished());
        verify(orderService, never()).paySuccess(any());
    }

    @Test
    @DisplayName("queryAndFinalize: 网关返回 cancelled 应标记取消")
    void query_cancelled_shouldMarkCancelled() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_003")
                .transactionId("TXN_003")
                .payStatus(PaymentConstant.PAYMENT_PROCESSING)
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_003")).thenReturn(record);
        when(paymentGateway.query("TEST_ORDER_003", "TXN_003"))
                .thenReturn(PaymentGateway.PaymentResult.cancelled("TXN_003", "用户取消"));
        when(paymentRecordMapper.getById(1L)).thenReturn(
                PaymentRecord.builder().id(1L).payStatus(PaymentConstant.PAYMENT_CANCELLED).failReason("用户取消").build()
        );

        PaymentResultVO vo = paymentService.queryAndFinalize("TEST_ORDER_003");

        assertEquals("cancelled", vo.getStatus());
        assertTrue(vo.isFinished());
    }

    @Test
    @DisplayName("queryAndFinalize: 已终结的记录应直接返回，不再查询网关")
    void query_alreadyFinished_shouldSkipGateway() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_004")
                .payStatus(PaymentConstant.PAYMENT_SUCCESS)
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_004")).thenReturn(record);

        PaymentResultVO vo = paymentService.queryAndFinalize("TEST_ORDER_004");

        assertEquals("success", vo.getStatus());
        verify(paymentGateway, never()).query(any(), any());
    }

    @Test
    @DisplayName("queryAndFinalize: 支付记录不存在应抛异常")
    void query_recordNotFound_shouldThrow() {
        when(paymentRecordMapper.getByOutTradeNo("NO_RECORD")).thenReturn(null);
        assertThrows(PaymentBusinessException.class, () -> paymentService.queryAndFinalize("NO_RECORD"));
    }

    // ==================== close / refund 测试 ====================

    @Test
    @DisplayName("close: 支付中状态应标记为已取消")
    void close_processing_shouldCancel() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_005")
                .payStatus(PaymentConstant.PAYMENT_PROCESSING)
                .transactionId("TXN_005")
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_005")).thenReturn(record);

        PaymentResultVO vo = paymentService.close("TEST_ORDER_005");

        assertEquals("cancelled", vo.getStatus());
        assertTrue(vo.isFinished());
        verify(paymentGateway).close("TEST_ORDER_005");
    }

    @Test
    @DisplayName("refund: 仅已支付的订单允许退款，未支付应抛异常")
    void refund_notPaid_shouldThrow() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_006")
                .payStatus(PaymentConstant.PAYMENT_FAILED)
                .amount(new BigDecimal("10"))
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_006")).thenReturn(record);

        Exception ex = assertThrows(PaymentBusinessException.class,
                () -> paymentService.refund("TEST_ORDER_006", BigDecimal.TEN));
        assertTrue(ex.getMessage().contains("仅已支付的订单允许退款"));
    }

    @Test
    @DisplayName("refund: 退款金额超过支付金额应抛异常")
    void refund_exceedAmount_shouldThrow() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_007")
                .payStatus(PaymentConstant.PAYMENT_SUCCESS)
                .amount(new BigDecimal("10"))
                .transactionId("TXN_007")
                .build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_007")).thenReturn(record);

        Exception ex = assertThrows(PaymentBusinessException.class,
                () -> paymentService.refund("TEST_ORDER_007", new BigDecimal("20")));
        assertTrue(ex.getMessage().contains("退款金额不能超过支付金额"));
    }

    @Test
    @DisplayName("refund: 全额退款成功应更新支付记录和订单状态")
    void refund_success_shouldUpdateRecords() {
        PaymentRecord record = PaymentRecord.builder()
                .id(1L)
                .outTradeNo("TEST_ORDER_008")
                .payStatus(PaymentConstant.PAYMENT_SUCCESS)
                .amount(new BigDecimal("50"))
                .transactionId("TXN_008")
                .build();
        Orders order = Orders.builder().id(10L).number("TEST_ORDER_008").build();

        when(paymentRecordMapper.getByOutTradeNo("TEST_ORDER_008")).thenReturn(record);
        when(paymentGateway.refund("TEST_ORDER_008", "TXN_008", new BigDecimal("50")))
                .thenReturn(PaymentGateway.PaymentResult.refunded("REFUND_008", "退款成功"));
        when(orderMapper.getByNumber("TEST_ORDER_008")).thenReturn(order);

        PaymentResultVO vo = paymentService.refund("TEST_ORDER_008", null);

        assertEquals("refunded", vo.getStatus());
        assertTrue(vo.isFinished());
        verify(paymentRecordMapper).update(argThat(r -> PaymentConstant.PAYMENT_REFUNDED == r.getPayStatus()));
        verify(orderMapper).update(argThat(o -> Orders.REFUND == o.getPayStatus()));
    }
}
