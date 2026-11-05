package com.sky.controller.user;


import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 条件查询菜品和口味
     * @param categoryId
     * @return
     */
   @GetMapping("/list")
   @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list( Long categoryId){
       log.info("查询菜品 categoryId={}，当前用户 id={}", categoryId, BaseContext.getCurrentId());

       //构造redis的KEY,规则 key == dish_category_id
       String key = "dish_" + categoryId;

       //查询缓存是否含有菜品，有直接返回
       List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
       if (list != null && list.size()!=0){
           return Result.success(list);
       }
// TODO 改造点①：list.size()!=0 → 空结果不缓存，会穿透
// TODO 改造点②：set(key,list) 没有 TTL，数据永久驻留
       //查询起售中的菜品
       Dish dish = new Dish();
       dish.setCategoryId(categoryId);
       dish.setStatus(StatusConstant.ENABLE);

       //没有缓存,查数据库并添加进缓存
       list = dishService.listWithFlavor(dish);
       redisTemplate.opsForValue().set(key,list);
       return Result.success(list);



   }

}
