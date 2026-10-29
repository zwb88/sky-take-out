package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 新增套餐
     *
     * @param setmealDTO
     */
    public void save(SetmealDTO setmealDTO) {

        //新增套餐基本信息

        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmealMapper.save(setmeal);

        //新增套餐菜品信息
        Long setmealDishId = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();

        if (setmealDishes != null && setmealDishes.size() > 0) {

            setmealDishes.forEach(setmealDish -> {
                setmealDish.setSetmealId(setmealDishId);
            });
        }
        setmealDishMapper.insertBatch(setmealDishes);


    }

    /**
     * 套餐分页查询
     *
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult page(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());


    }

    /**
     * 套餐删除
     *
     * @param ids
     */
    public void delectById(List<Long> ids) {
        //起售状态不能删除
        for (Long id:ids) {
           SetmealVO setmeal = setmealById(id);
           if (setmeal.getStatus()==1){
               throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
              }
        }
        //删除套餐
        setmealMapper.delectBatch(ids);
        //删除套餐里的菜品
        setmealDishMapper.delectBatch(ids);



    }

    /**
     * 根据id查套餐
     * @param id
     * @return
     */
    public SetmealVO setmealById(long id) {

        SetmealVO setmealVO = setmealMapper.setmealById(id);
        return setmealVO;

    }

    /**
     * 修改套餐
     * @param setmealVO
     */
    public void updete(SetmealVO setmealVO) {
        //修改套餐
       Setmeal setmeal = new Setmeal();
       BeanUtils.copyProperties(setmealVO,setmeal);
       setmealMapper.update(setmeal);
        //修改套餐菜品信息
        // 删除原有的菜品
        setmealDishMapper.delect(setmealVO.getId());
        List<SetmealDish> setmealDish = setmealVO.getSetmealDishes();
        //重新新增菜品信息
          if (setmealDish != null && setmealDish.size()>0){
          setmealDish.forEach(setmealDish1 -> {
              setmealDish1.setSetmealId(setmeal.getId());
          });
            setmealDishMapper.insertBatch(setmealDish);
        }

}


    /**
     * 套餐起售与停售
     * @param status
     * @param id
     */
    public void satrtOrstop(Integer status, Long id) {
        Setmeal setmeal = new Setmeal();
        setmeal.setId(id);
        setmeal.setStatus(status);
        setmealMapper.update(setmeal);


        //起售套餐时，判断套餐内是否有停售菜品，有停售菜品提示"套餐内包含未启售菜品，无法启售"
        if (status == StatusConstant.ENABLE) {
            //根据套餐id查询套餐内所有菜品
            List<Dish> dishList = dishMapper.getBySetmealId(id);
            if (dishList != null && dishList.size() > 0) {
                dishList.forEach(dish -> {
                    if (dish.getStatus() == StatusConstant.DISABLE) {
                        //套餐内有停售菜品，抛出异常，事务回滚，套餐起售失败
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                });
            }
        }
    }

}
