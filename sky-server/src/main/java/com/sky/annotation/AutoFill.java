package com.sky.annotation;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})//这个注释标记在方法上
@Retention(RetentionPolicy.RUNTIME)//股固定写法，运行时保留，AOP 才能读到
public @interface AutoFill {

    OperationType value(); // INSERT 还是 UPDATE


}
