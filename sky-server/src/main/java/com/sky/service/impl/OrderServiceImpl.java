package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;

import com.sky.dto.*;
import com.sky.constant.PaymentConstant;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.properties.MockPaymentProperties;
import com.sky.result.PageResult;
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
    // 归属校验
       if (!userId.equals(addressBook.getUserId())) {
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

    /**
     * 查询历史订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 只能查当前登录用户的订单，防止小程序传入其他 userId 越权查询
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null || !currentUserId.equals(ordersPageQueryDTO.getUserId())) {
            throw new OrderBusinessException("只能查询当前用户的历史订单");
        }
        ordersPageQueryDTO.setUserId(currentUserId);

        // 开启分页，紧跟着的查询会被自动加上 limit
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 逐个订单查询订单明细，封装成 OrderVO
        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.getResult() != null) {
            for (Orders orders : page.getResult()) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }

        // total 用分页插件统计的总记录数，records 换成带明细的 VO
        return new PageResult(page == null ? 0 : page.getTotal(), orderVOList);
    }

    /**
     * 查询订单详细
     * @param orderId
     * @return
     */
    public OrderVO getByOrderId(Long orderId) {
        //查询订单
        Orders orders = orderMapper.getById(orderId);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null || !currentUserId.equals(orders.getUserId())) {
            throw new OrderBusinessException("无权查看该订单");
        }
        OrderVO orderVO = new OrderVO();
        //查询订单详细
        List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);
        //封装返回结果
        BeanUtils.copyProperties(orders,orderVO);
        orderVO.setOrderDetailList(orderDetails);
        return orderVO;
    }

    /**
     * 取消订单
     * @param id
     */
    @Override
    @Transactional
    public void cancelOrder(Long id) {
        Orders order = orderMapper.getById(id);
        //订单不能为空
        if (order == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //订单状态不符
        if (order.getStatus() >2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //只能取消当前登录用户的订单，防止越权
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId == null || !currentUserId.equals(order.getUserId())) {
            throw new OrderBusinessException("无权取消该订单");
        }

        // 已支付的订单（待接单）取消时需要退款：本项目是模拟支付，没有真实商户，走模拟支付网关退款接口
        if (Orders.PAID.equals(order.getPayStatus())) {
            // 金额传 null 表示按支付记录全额退款，refund 内部会同步更新支付记录与订单支付状态
            PaymentResultVO refundResult = paymentService.refund(order.getNumber(), null);
            // 模拟网关存在退款失败概率，失败时不取消订单，靠事务整体回滚
            if (refundResult == null || !"refunded".equals(refundResult.getStatus())) {
                throw new OrderBusinessException(refundResult == null || refundResult.getMessage() == null
                        ? "退款失败，取消订单未生效" : refundResult.getMessage());
            }
        }

        Orders orders = new Orders();
        // 更新订单状态、取消原因、取消时间（必须带 id，否则 where id = null 更新不到任何行）
        orders.setId(id);
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        if (Orders.PAID.equals(order.getPayStatus())) {
            orders.setPayStatus(Orders.REFUND);
        }
        orderMapper.update(orders);

    }

    /**
     * 再来一单
     * @param id
     */
   @Transactional
    public void repetitionOrder(Long id) {
        // 1. 校验订单存在 + 归属
        Orders order = orderMapper.getById(id);
        if( order == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Long currentId = BaseContext.getCurrentId();
        if (currentId == null || !currentId.equals(order.getUserId())){
            throw new OrderBusinessException("无权操作该订单");
        }
        //查询订单明细
        List<OrderDetail>  orderDetailList = orderDetailMapper.getByOrderId(id);
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //逐条传换成购物车对象
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail: orderDetailList
             ) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail,shoppingCart,"id");
            shoppingCart.setUserId(currentId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartList.add(shoppingCart);
        }

        //批量插入购物车
        shoppingCartMapper.insertBatch(shoppingCartList);



    }


}
