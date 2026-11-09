package com.sky.controller.user;


import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.result.Result;
import com.sky.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/shoppingCart")
@Api(tags = "C端购物车")
@Slf4j
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    @ApiOperation("添加购物车")
    @PostMapping("/add")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO){

        log.info("添加购物车信息：{}",shoppingCartDTO);
        shoppingCartService.addShopCart(shoppingCartDTO);
        return Result.success();


    }
    @GetMapping("/list")
    @ApiOperation("查看购物车")
    public Result<List<ShoppingCart>> list(){
        log.info("查看购物车...");
        List<ShoppingCart> shoppingCartList = shoppingCartService.showShoppingCart();
       return Result.success(shoppingCartList);


    }


    @DeleteMapping("/clean")
    @ApiOperation("删除购物车")
    public Result clean(){

        log.info("删除购物车");
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }

}
