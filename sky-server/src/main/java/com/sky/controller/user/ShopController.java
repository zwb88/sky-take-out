package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺状态相关")
@Slf4j
public class ShopController {

    public final static String KEY = "SHOP_STATUS";
    private static final int STATUS_OPEN = 1;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取店铺营业状态
     * @param status
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("用户获取店铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        if (status == null) {
            status = STATUS_OPEN;         //默认营业，与管理端保持一致
            redisTemplate.opsForValue().set(KEY, status);
        }
        log.info("获取店铺营业状态：{}", status == 1 ? "营业中":"打烊中");
        return Result.success(status);
    }



}
