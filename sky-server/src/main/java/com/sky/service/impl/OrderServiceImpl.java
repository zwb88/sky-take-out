package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersDTO;
import com.sky.constant.PaymentConstant;
import com.sky.dto.MockPaymentDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.MockPaymentProperties;
import com.sky.service.OrderService;
import com.sky.service.PaymentService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.vo.PaymentResultVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MockPaymentProperties mockPaymentProperties;
    @Lazy
    @Autowired
    private PaymentService paymentService;
    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
@Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {

    Long userId = BaseContext.getCurrentId();
        //处理各种业务异常(地址薄、购物车为空)

        //地址薄为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
       if (!userId.equals(addressBook.getUserId())) {      // 归属校验
        throw new AddressBookBusinessException("地址簿不属于当前用户");
    }

        //购物车为空
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(shoppingCart);

        if (shoppingCarts == null || shoppingCarts.size() == 0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        //构造订单数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setUserId(userId);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setOrderTime(LocalDateTime.now());
        BigDecimal amount = shoppingCarts.stream()                          // 服务端算钱
            .map(c -> c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setAmount(amount);
        //向订单表插入1条数据
        orderMapper.insert(order);

        //向订单明细表插入n条数据

        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart: shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);
        //清空购物
        shoppingCartMapper.delectById(userId);
        //封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderTime(order.getOrderTime())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .build();


        return orderSubmitVO;
    }



    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //启用模拟支付时，走模拟支付网关，不依赖真实微信支付商户证书
        if (mockPaymentProperties.isEnabled()) {
            return mockPayment(ordersPaymentDTO);
        }

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 模拟支付：发起支付 -> 等待模拟延迟 -> 根据随机结果决定是否更新订单状态
     *
     * @param ordersPaymentDTO 支付请求
     * @return 支付凭证（前端收到即视为支付完成）
     */
    private OrderPaymentVO mockPayment(OrdersPaymentDTO ordersPaymentDTO) {
        MockPaymentDTO mockPaymentDTO = new MockPaymentDTO();
        mockPaymentDTO.setOrderNumber(ordersPaymentDTO.getOrderNumber());
        mockPaymentDTO.setPayMethod(PaymentConstant.PAY_METHOD_MOCK);

        // 发起支付（内含重复支付拦截、订单状态校验）
        paymentService.prepay(mockPaymentDTO);

        // 等待模拟延迟并获取最终结果，成功时会联动更新订单状态
        PaymentResultVO result = paymentService.queryAndFinalize(ordersPaymentDTO.getOrderNumber());

        // 失败/取消时抛出业务异常，由全局异常处理器返回提示文案
        if (!"success".equals(result.getStatus())) {
            throw new OrderBusinessException(result.getMessage());
        }

        return OrderPaymentVO.builder()
                .nonceStr(UUID.randomUUID().toString().replace("-", ""))
                .paySign("MOCK_SUCCESS")
                .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                .signType("MOCK")
                .packageStr("prepay_id=" + result.getTransactionId())
                .build();
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }
}
