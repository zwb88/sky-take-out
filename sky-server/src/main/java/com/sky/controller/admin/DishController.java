package com.sky.controller.admin;


import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@Api(tags = "菜品接口")
@Slf4j
@RequestMapping("/admin/dish")
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save( @RequestBody DishDTO dishDTO){

        log.info("新增菜品：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);

        String key = "dish_" + dishDTO.getCategoryId();
        cleanCache(key);

      return Result.success();

    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.PageQueery(dishPageQueryDTO);
        return Result.success(pageResult);

    }

    /**
     * 菜品删除
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("菜品删除")
    public Result delect( @RequestParam List<Long> ids){

        log.info("菜品删除：{}",ids);
        dishService.delectBatch(ids);

        cleanCache("dish_*");

        return Result.success();

    }

    /**
     * 根据id查菜品
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查菜品")
    public Result<DishVO> dishgetByTd( @PathVariable Long id){
        log.info("根据id查菜品：{}",id);
       DishVO dishVO = dishService.getByIdWithFlavor(id);
       return Result.success(dishVO);


    }

    /**
     * 修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("修改菜品：{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);
        cleanCache("dish_*");
        return Result.success();


    }

    /**
     * 根据分类id查菜品
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查菜品")
    public Result<List<Dish>> categoryById(@RequestParam Long categoryId){
        log.info("根据分类id查菜品：{}",categoryId);
        List<Dish> dish = dishService.categoryById(categoryId);
        return Result.success(dish);

    }

    /**
     * 菜品起售与停售
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售与停售")
    public Result<String> startOrStop(@PathVariable Integer status , Long id){
        log.info("菜品起售与停售：{}{}",status,id);
        dishService.startOrstop(status,id);
        cleanCache("dish_*");
        return Result.success();
    }

    /**
     * 清理缓存数据
     * @param pattern
     */
    // keys 命令 O(N) 阻塞单线程，生产应改 scan
    private void cleanCache(String pattern) {

        // 1. 准备一个集合，用来收集扫到的 key
        Set<String> keys = new HashSet<>();

        // 2. 构造扫描参数：匹配什么模式、每批取多少
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)     // "dish_*"
                .count(100)         // 每批 100 个
                .build();

        // 3. try-with-resources：cursor 用完自动关闭
        try (Cursor<String> cursor = redisTemplate.scan(options)) {

            // 4. 游标循环：还有下一个吗？
            while (cursor.hasNext()) {
                keys.add(cursor.next());   // 有，取出来放进集合
            }

        }
        // 5. 删除收集到的所有 key
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理缓存 {} 条，pattern={}", keys.size(), pattern);
        }
    }

    }





