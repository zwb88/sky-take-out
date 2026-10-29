package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {

    /**
     * x新增套餐
     * @param setmealDTO
     */
    void save(SetmealDTO setmealDTO);

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */

    PageResult page(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 套餐删除
     * @param ids
     */
    void delectById(List<Long> ids);

    /**
     * 根据id查套餐
     * @param id
     * @return
     */
    SetmealVO setmealById(long id);
    /**
     * 修改套餐
     */

    void updete(SetmealVO setmealVO);

    /**
     * 套餐起售与停售
     * @param status
     * @param id
     */
    void satrtOrstop(Integer status, Long id);
}
