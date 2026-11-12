package com.sky.controller.user;

import com.sky.constant.PaymentConstant;
import com.sky.dto.MockPaymentDTO;
import com.sky.result.Result;
import com.sky.service.PaymentService;
import com.sky.vo.PaymentResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 模拟支付相关接口
 */
@RestController
@RequestMapping("/user/payment")
@Slf4j
@Api(tags = "用户端-模拟支付接口")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /**
     * 发起模拟支付
     */
    @PostMapping("/prepay")
    @ApiOperation("发起模拟支付")
    public Result<PaymentResultVO> prepay(@RequestBody MockPaymentDTO dto) {
        log.info("[Payment] 发起模拟支付: dto={}", dto);
        dto.setPayMethod(dto.getPayMethod() == null ? PaymentConstant.PAY_METHOD_MOCK : dto.getPayMethod());
        PaymentResultVO vo = paymentService.prepay(dto);
        return Result.success(vo);
    }

    /**
     * 查询支付结果（会触发模拟延迟并返回最终状态）
     */
    @GetMapping("/query")
    @ApiOperation("查询支付结果（触发模拟延迟）")
    public Result<PaymentResultVO> query(@RequestParam String orderNumber) {
        log.info("[Payment] 查询支付结果: orderNumber={}", orderNumber);
        PaymentResultVO vo = paymentService.queryAndFinalize(orderNumber);
        return Result.success(vo);
    }

    /**
     * 快速查询（不触发延迟，直接返回当前记录状态）
     */
    @GetMapping("/status")
    @ApiOperation("快速查询支付状态")
    public Result<PaymentResultVO> status(@RequestParam String orderNumber) {
        PaymentResultVO vo = paymentService.getStatus(orderNumber);
        return Result.success(vo);
    }

    /**
     * 关闭/取消支付
     */
    @PostMapping("/close")
    @ApiOperation("关闭/取消支付")
    public Result<PaymentResultVO> close(@RequestParam String orderNumber) {
        log.info("[Payment] 关闭支付: orderNumber={}", orderNumber);
        PaymentResultVO vo = paymentService.close(orderNumber);
        return Result.success(vo);
    }

    /**
     * 模拟退款
     */
    @PostMapping("/refund")
    @ApiOperation("模拟退款")
    public Result<PaymentResultVO> refund(@RequestParam String orderNumber,
                                          @RequestParam(required = false) BigDecimal amount) {
        log.info("[Payment] 模拟退款: orderNumber={}, amount={}", orderNumber, amount);
        PaymentResultVO vo = paymentService.refund(orderNumber, amount);
        return Result.success(vo);
    }
}
