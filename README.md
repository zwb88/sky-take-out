# sky-take-out

> 基于 SpringBoot + MyBatis-Plus 的外卖点餐系统，在教程基础上做了缓存、幂等、部署三项改造。
>
> **在线体验：** http://YOUR_SERVER_IP （账号 `admin` / 密码 `123456`）

![Java](https://img.shields.io/badge/Java-17-blue) ![SpringBoot](https://img.shields.io/badge/SpringBoot-3.x-green) ![MySQL](https://img.shields.io/badge/MySQL-8.0-orange)

---

## 技术栈

SpringBoot 3.x · MyBatis-Plus · MySQL 8 · Redis · JWT · 阿里云 OSS · Nginx · Maven

## 功能

**管理端**：员工管理、分类管理、菜品管理、套餐管理、订单管理、店铺营业状态
**用户端**：微信登录、浏览菜品、购物车、下单、支付、订单跟踪、来单提醒

## 架构图

![架构图](docs/architecture.png)

> 画一张就够：浏览器 → Nginx → SpringBoot → MySQL / Redis / OSS / 微信支付。

---

## 我做的三项改造（本项目的重点）

### 1. 菜品/分类查询接入 Redis 缓存

- 空值缓存防止缓存穿透；TTL 加随机抖动防止缓存雪崩。
- 更新策略：先更新库，再删除缓存。
- **压测对比（JMeter，100 并发）：**

| 指标 | 改造前 | 改造后 |
|---|---|---|
| QPS | FILL_ME | FILL_ME |
| P95 响应时间 | FILL_ME ms | FILL_ME ms |
| DB 查询次数（单次列表请求） | FILL_ME | FILL_ME |

### 2. 下单接口幂等

- 方案：下单前申请 token 存 Redis，提交时校验并删除，保证只有一个请求能创建订单。
- 为什么不用唯一索引：订单表没有天然唯一键（同一用户可下多单），且唯一索引只能挡住重复写入，挡不住重复扣库存。
- 结果：并发重复提交 FILL_ME 次，只生成 FILL_ME 个订单。

### 3. 部署上线

- 云服务器 + Nginx 反向代理 + 后端 jar 常驻。
- 遇到并解决：FILL_ME（比如跨域、静态资源 404、时区导致订单时间差 8 小时）。

---

## 踩过的坑

> 这一段是面试官最爱看的。写真实踩过的，不要编。

- **MySQL 8 连接报时区错误**：URL 必须带 `serverTimezone=Asia/Shanghai`，否则 `The server time zone value` 异常。
- **OSS 上传 403**：桶权限默认为私有，需要改公共读，或改用签名 URL。
- **yml 缩进导致配置读不到**：`alioss` 的层级缩进错了，启动不报错但对象为 null。
- **MyBatis-Plus 版本冲突**：与 SpringBoot 3.x 不兼容，降版本解决。
- FILL_ME

---

## 本地运行

```bash
# 1. 建库
mysql -u root -p -e "CREATE DATABASE sky_take_out DEFAULT CHARSET utf8mb4;"
mysql -u root -p sky_take_out < db/sky_take_out.sql

# 2. 配置
cp application-dev.yml.example sky-server/src/main/resources/application-dev.yml
# 编辑 application-dev.yml，填数据库、OSS、微信配置

# 3. 打包运行
mvn clean package -DskipTests
java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

## 目录结构

```
sky-take-out
├── sky-common      公共模块（常量、工具类、异常）
├── sky-pojo        实体类（DTO / VO / Entity）
├── sky-server      业务逻辑与接口
├── db              SQL 脚本
└── docs            架构图
```

## 说明

本项目为学习项目，参考了某课程的外卖项目，在此基础上完成了缓存、幂等、部署改造，并补充了测试与压测数据。
