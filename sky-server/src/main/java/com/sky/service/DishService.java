package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface DishService {
     /**
      * 新增菜品
      * @param dishDTO
      */
     void saveWithFlavor(DishDTO dishDTO);

     /**
      * 菜品分页查询
      * @param dishPageQueryDTO
      * @return
      */
     PageResult PageQueery(DishPageQueryDTO dishPageQueryDTO);

     /**
      * 菜品删除
      * @param ids
      */
     void delectBatch(List<Long> ids);

     /**
      * 根据id查菜品
      * @param id
      * @return
      */
     DishVO getByIdWithFlavor(Long id);

     /**
      * 修改菜品
      * @param dishDTO
      */
     void updateWithFlavor(DishDTO dishDTO);

     /**
      * 根据分类id查菜品
      * @param id
      * @return
      */
     List<Dish> categoryById(Long categoryId);

     /**
      * 菜品起售与停售
      * @param status
      * @param id
      */
    void startOrstop(Integer status, Long id);

     /**
      * 条件查询菜品和口味
      * @param dish
      * @return
      */
     List<DishVO> listWithFlavor(Dish dish);
}
