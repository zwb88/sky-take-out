package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Api(tags = "套餐相关")
@Slf4j
@RequestMapping("/admin/setmeal")

 public class SetmealController {


 @Autowired
 private SetmealService setmealService;
 /**
  * 新增套餐
  * @param setmealDTO
  * @return
  */
 @PostMapping
 @ApiOperation("新增套餐")
 public Result save( @RequestBody SetmealDTO setmealDTO){

  log.info("新增套餐：{}",setmealDTO);
  setmealService.save(setmealDTO);
  return Result.success();
 }

 /**
  * 套餐分页查询
  * @param setmealPageQueryDTO
  * @return
  */
 @GetMapping("/page")
 @ApiOperation("套餐分页查询")
 public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO){
  log.info("套餐分页查询：{}",setmealPageQueryDTO);
  PageResult page = setmealService.page(setmealPageQueryDTO);
  return Result.success(page);
 }

 /**
  * 套餐删除
  * @param ids
  * @return
  */
 @DeleteMapping
 @ApiOperation("删除套餐")
 public Result delect(@RequestParam List<Long> ids){
  log.info("删除套餐：{}",ids);
  setmealService.delectById(ids);
  return Result.success();
 }

 /**
  * 根据id查询套餐信息
  * @param id
  * @return
  */
 @GetMapping("/{id}")
 @ApiOperation("根据id查询套餐")
 public Result<SetmealVO> setmealgetById(@PathVariable long id){
 log.info("根据id查套餐：{}",id);
  SetmealVO setmealVO = setmealService.setmealById(id);
  return Result.success(setmealVO);
 }

 /**
  * 修改套餐
  * @param setmealVO
  * @return
  */
 @PutMapping
 @ApiOperation("修改套餐")
 public Result<SetmealVO> update( @RequestBody SetmealVO setmealVO){
  log.info("修改套餐：{}",setmealVO);
  setmealService.updete(setmealVO);
  return Result.success();
 }

 @PostMapping("status/{status}")
@ApiOperation("套餐起售与停售")
 public Result<String> startOrstop(@PathVariable Integer status,Long id){
  log.info("套餐起售与停售：{}{}",status,id);
  setmealService.satrtOrstop(status,id);

  return Result.success();

 }

}
