package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "店铺状态相关")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";
    private static final int STATUS_OPEN = 1;
    private static final int STATUS_CLOSED = 0;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 设置店铺营业状态
     * @param status
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺营业状态")
    public Result setStatus( @PathVariable Integer status){
        log.info("设置店铺营业状态：{}", status == 1 ? "营业中":"打烊中");
        redisTemplate.opsForValue().set(KEY,status);
        return Result.success();
    }

    /**
     * 获取店铺营业状态
     * @param status
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("管理员获取店铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        if (status == null){
            status = STATUS_OPEN;         //默认营业
            redisTemplate.opsForValue().set(KEY,status);
        }
        log.info("获取店铺营业状态：{}", status == 1 ? "营业中":"打烊中");
        return Result.success(status);
    }



}
