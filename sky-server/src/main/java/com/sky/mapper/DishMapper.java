package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper
public interface DishMapper {

 /**
  * 根据分类id查询菜品数量
  *
  * @param categoryId
  * @return
  */
 @Select("select count(id) from dish where category_id = #{categoryId}")
 Integer countByCategoryId(Long categoryId);


 /**
  * 新增菜品
  *
  * @param dish
  */
 @AutoFill(OperationType.INSERT)
 void insert(Dish dish);

 /**
  * 菜品分页查询
  *
  * @param dishPageQueryDTO
  * @return
  */
 Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

 /**
  * 根据主键查询菜品信息
  *
  * @param id
  * @return
  */
 @Select("select * from dish where id = #{id}")
 Dish getByTd(Long id);

 /**
  * 根据主键删除菜品信息
  *
  * @param id
  */
 @Delete("delete  from dish where id = #{id}")
 void delectById(Long id);

 /**
  * 批量删除菜品信息
  *
  * @param ids
  */
 void delectByIds(List<Long> ids);

 /**
  * 修改菜品信息
  *
  * @param dish
  */
 @AutoFill(OperationType.UPDATE)
 void update(Dish dish);

 /**
  * g根据分类id查菜品
  *
  * @param id
  * @return
  */
 @Select("select * from dish where category_id = #{categoryId} and status = 1")
 List<Dish> categoryById(Long categoryId);

 /**
  * 根据套餐id查询套餐内的所有菜品
  *
  * @param setmealId
  * @return
  */
 @Select("select d.* from dish d left join setmeal_dish sd on d.id = sd.dish_id where sd.setmeal_id = #{setmealId}")
 List<Dish> getBySetmealId(Long setmealId);

 /**
  * 条件查询菜品
  *
  * @param dish
  * @return
  */
 @Select("select * from dish where category_id = #{categoryId} and status = 1")
 List<Dish> list(Dish dish);


}



