package com.sky.service;


import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;


public interface OrderService {
    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */

    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 查询历史订单
     *
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 查询订单详细
     *
     * @param orderId
     * @return
     */
    OrderVO getByOrderId(Long orderId);

    /**
     * 取消订单
     *
     * @param id
     */
    void cancelOrder(Long id);

    /**
     * 再来一单
     *
     * @param id
     */
    void repetitionOrder(Long id);

    /**
     * 条件订单搜索
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult getByOrdersPageQueryDTO(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 派送订单
     * @param id
     */
    void deliveryOrder(Long id);

    /**
     * 取消订单
     * @param orders
     */
    void cancelOrderAdmin(Orders orders);

    /**
     * 根据id查看订单详细
     * @param id
     * @return
     */
    OrderVO detail(Long id);

    /**
     * 接受订单
     * @param ordersDTO
     */
    void confirmOrder(OrdersDTO ordersDTO);

    /**
     * 拒绝订单
     * @param orders
     */
    void rejectionOrder(Orders orders);

    /**
     * 完成订单
     * @param id
     */
    void completeOrder(Long id);

    /**
     * 各个订单状态的数量统计
     * @return
     */
    OrderStatisticsVO statisticsOrder();
}
