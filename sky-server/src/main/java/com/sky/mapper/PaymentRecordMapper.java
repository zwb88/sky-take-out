package com.sky.mapper;

import com.sky.entity.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaymentRecordMapper {

    /**
     * 插入支付记录
     */
    void insert(PaymentRecord record);

    /**
     * 根据商户订单号查询
     */
    @Select("select * from payment_record where out_trade_no = #{outTradeNo}")
    PaymentRecord getByOutTradeNo(String outTradeNo);

    /**
     * 根据主键查询
     */
    @Select("select * from payment_record where id = #{id}")
    PaymentRecord getById(Long id);

    /**
     * 根据支付流水号查询
     */
    @Select("select * from payment_record where transaction_id = #{transactionId}")
    PaymentRecord getByTransactionId(String transactionId);

    /**
     * 更新支付记录
     */
    void update(PaymentRecord record);
}
