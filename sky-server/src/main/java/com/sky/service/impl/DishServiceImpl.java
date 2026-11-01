package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Service
public class DishServiceImpl implements DishService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private DishFlavorMapper dishFlavorMapper;

    /**
     *  新增菜品和对应的口味
     * @param dishDTO
     */
    public void saveWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);

        //向菜品中插入一条数据
        dishMapper.insert(dish);

        //获取insert语句生成的主键值
        Long dishId = dish.getId();


        //向菜品插入口味
       List<DishFlavor> flavors = dishDTO.getFlavors();
       if (flavors != null && flavors.size() > 0){

           flavors.forEach(dishFlavor -> {
               dishFlavor.setDishId(dishId);
           });

       }
       dishFlavorMapper.insertBatch(flavors);


    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult PageQueery(DishPageQueryDTO dishPageQueryDTO) {

        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
//        #### 第 1 行：`PageHelper.startPage(...)`
//
//        - **PageHelper** 是一个 MyBatis 的分页插件，不用自己写 `limit`。
//        - 作用：告诉它 "查第 page 页，每页 pageSize 条"。
//        - 比如前端传 `page=1, pageSize=10`，它就自动帮你在 SQL 后面加上 `limit 0, 10`。
//        - **关键点**：这行代码必须**紧跟在查询语句前面**，不然分页会失效。
//
//#### 第 2 行：`Page<DishVO> page = dishMapper.pageQuery(...)`
//
//        - 调用 Mapper 层执行 SQL 查询。
//        - `Page<DishVO>` 是 PageHelper 返回的特殊 Page 对象，里面除了查到的数据，还自动附带了**总记录数**（一共多少条）。
//        - `DishVO` 是返回给前端的视图对象（View Object），包含菜品名、分类名等前端要展示的字段。
//
//#### 第 3 行：`return new PageResult(page.getTotal(), page.getResult())`
//
//        - 把结果封装成 `PageResult` 对象，这是统一的分页返回格式：
//        - `page.getTotal()`：总共有多少条数据（前端算总页数用）
//        - `page.getResult()`：当前页的数据列表
//                - 前端拿到这两个数就能渲染分页器了。



    }

    /**
     * 菜品删除
     * @param ids
     */
    @Override
    public void delectBatch(List<Long> ids) {
        //判断菜品状态是否起售
        for (Long id: ids) {
            Dish dish = dishMapper.getByTd(id);
            if (dish.getStatus()==1){
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断菜品是否和套餐关联
        List<Long> stemealids = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if(stemealids != null && stemealids.size()>0){
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }

//        //删除菜品
//        for (Long id: ids
//             ) {
//            //删除菜品信息
//            dishMapper.delectById(id);
//            //删除菜品口味
//            dishFlavorMapper.delectByDishId(id);
//
//        }
//        根据id结合批量删除菜品数据
//        delect from dish where id in (?,?,?)
        dishMapper.delectByIds(ids);

//        根据id结合批量删除菜品口味
//         delect from setmeal_dish where dish_id in (?,?,?)
          dishFlavorMapper.delectByDishIds(ids);
    }

    /**
     * 根据id查菜品和根据菜品id查口味
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {

        //根据id查菜品
        Dish dish = dishMapper.getByTd(id);

        //根据菜品id查口味
      List<DishFlavor> dishFlavor = dishFlavorMapper.getByDishID(id);
       //封装返回信息
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish,dishVO);
        dishVO.setFlavors(dishFlavor);

        return dishVO;
    }

    /**
     * 修改菜品
     * @param dishDTO
     */
    public void updateWithFlavor(DishDTO dishDTO) {

        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        //修改菜品信息
        dishMapper.update(dish);
        //删除口味
        dishFlavorMapper.delectByDishId(dishDTO.getId());
        //新增口味
        List<DishFlavor> dishFlavors = dishDTO.getFlavors();
        if (dishFlavors != null && dishFlavors.size()>0){
            dishFlavors.forEach(dishFlavor ->{
                dishFlavor.setDishId(dishDTO.getId());
            } );

            dishFlavorMapper.insertBatch(dishFlavors);

        }
    }

    /**
     * 根据分类id查菜品
     * @param id
     * @return
     */
    public List<Dish> categoryById(Long categoryId) {

        List<Dish> dish = dishMapper.categoryById(categoryId);
        return dish;

    }

    /**
     * 菜品起售与停售
     * @param status
     * @param id
     */
    public void startOrstop(Integer status, Long id) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setStatus(status);
        dishMapper.update(dish);
    }}
