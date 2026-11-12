-- 模拟支付记录表
CREATE TABLE IF NOT EXISTS payment_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    out_trade_no     VARCHAR(64)  NOT NULL COMMENT '商户订单号',
    transaction_id   VARCHAR(64)  DEFAULT NULL COMMENT '支付流水号',
    amount           DECIMAL(10, 2) NOT NULL COMMENT '支付金额',
    pay_method       INT          NOT NULL COMMENT '支付方式 1微信 2支付宝 3模拟支付',
    pay_status       INT          NOT NULL DEFAULT 0 COMMENT '支付状态 0待支付 1支付中 2成功 3失败 4已取消 5已退款',
    fail_reason      VARCHAR(255) DEFAULT NULL COMMENT '失败/取消原因',
    create_time      DATETIME     NOT NULL COMMENT '发起时间',
    finish_time      DATETIME     DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_out_trade_no (out_trade_no),
    KEY idx_transaction_id (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水记录';
