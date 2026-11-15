package com.sky.controller.admin;

import com.sky.dto.OrdersDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminController")
@RequestMapping("/admin/order")
@Api(tags = "管理端订单相关接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;


    /**
     * 条件订单搜素
     * @param ordersPageQueryDTO
     * @return
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("条件订单搜索")
    public Result<PageResult> conditionSearchOrder( OrdersPageQueryDTO ordersPageQueryDTO){
        log.info("开始搜素订单: {}",ordersPageQueryDTO);
       PageResult pageResult= orderService.getByOrdersPageQueryDTO(ordersPageQueryDTO);
        return  Result.success(pageResult);

    }

    /**
     * 派送订单
     * @param id
     * @return
     */
    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result deliveryOrder(@PathVariable Long id){
        log.info("开始派送订单: {}", id);
        orderService.deliveryOrder(id);
        return Result.success();
    }


    /**
     * 取消订单
     * @param orders
     * @return
     */

    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result cancelOrder(@RequestBody Orders orders){
        log.info("开始取消订单: {}",orders);
        orderService.cancelOrderAdmin(orders);
        return Result.success();
    }

    /**
     * 根据id查看订单详细
     * @param id
     * @return
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查看订单详细")
    public Result<OrderVO> detailsOrder(@PathVariable Long id){
        log.info("开始查看订单详细: {}",id);
        OrderVO orderVO =  orderService.detail(id);
        return Result.success(orderVO);
    }

    /**
     * 接收订单
     * @param ordersDTO
     * @return
     */
    @PutMapping("/confirm")
    @ApiOperation("接收订单")
    public Result confirmOrder( @RequestBody OrdersDTO ordersDTO){
        log.info("开始接收订单: {}",ordersDTO);
        orderService.confirmOrder(ordersDTO);
        return Result.success();
    }

    /**
     * 拒绝订单
     * @param orders
     * @return
     */
    @PutMapping("/rejection")
    @ApiOperation("拒绝订单")
    public Result rejectionOrder(@RequestBody Orders orders){
        log.info("开始拒绝订单:{}",orders);
        orderService.rejectionOrder(orders);
        return Result.success();
    }

    /**
     * 完成订单
     * @param id
     * @return
     */
    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result completeOrder(@PathVariable Long id){
        log.info("开始完成订单: {}",id);
        orderService.completeOrder(id);
        return Result.success();
    }


    /**
     * 各个订单状态的数量统计
     * @return
     */
    @GetMapping("/statistics")
    @ApiOperation("各个订单状态的数量统计")
    public Result<OrderStatisticsVO> statisticsOrder(){
        log.info("开始各个订单状态的数量统计");
        OrderStatisticsVO orderStatisticsVO = orderService.statisticsOrder();
        return Result.success(orderStatisticsVO);
    }



}
