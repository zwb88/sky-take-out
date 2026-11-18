# sky-take-out 外卖系统

> 基于课程项目完成的 Spring Boot 外卖系统，在跟做基础上做了 **缓存优化、并发安全、部署上线** 三方面改造。
>
> **在线接口文档：** http://8.166.133.47:8080/doc.html
> （管理端账号 `admin` / 密码 `123456`）

![Java](https://img.shields.io/badge/Java-17-blue) ![SpringBoot](https://img.shields.io/badge/SpringBoot-2.7.3-green) ![MySQL](https://img.shields.io/badge/MySQL-8.0-orange) ![Redis](https://img.shields.io/badge/Redis-7.x-red)

---

## 一句话说明

一个支持「管理端 + 用户端」的外卖点餐系统，包含菜品/套餐管理、购物车、下单、支付、订单状态流转等完整业务链路。

本仓库在课程代码的基础上，额外完成了三项工程实践：**Redis 缓存三项问题修复**、**接口越权校验补全**、**云服务器部署上线**，并用 JMeter 做了压测对比。

---

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端框架 | Spring Boot 2.7.3 |
| 持久层 | MyBatis（XML + 注解）、Druid 连接池、PageHelper 分页 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis（菜品数据缓存） |
| 认证 | JWT（jjwt），管理端 / 用户端双密钥 |
| 接口文档 | Knife4j |
| 文件存储 | 阿里云 OSS |
| 前端 | 微信小程序（课程提供） |
| 部署 | 阿里云 ECS + Ubuntu 22.04 + jar 常驻 |

---

## 功能清单

**管理端（`/admin/**`）**

- 员工管理：登录、新增、分页查询、启用禁用、编辑
- 分类管理：新增、分页查询、删除、修改、启用禁用
- 菜品管理：新增（含口味）、分页查询、删除、修改、起售停售
- 套餐管理：新增、分页查询、删除、修改、起售停售
- 订单管理：条件搜索、各状态统计、接单、拒单、派送、完成、取消
- 店铺状态：营业 / 打烊

**用户端（`/user/**`）**

- 购物车：添加（已存在则数量+1）、查看、清空
- 地址簿：增删改查、设置默认地址
- 订单：下单、历史订单分页、订单详情、取消、再来一单
- 支付：模拟支付网关（预支付、查询、关闭、退款）
- 商品浏览：分类列表、菜品列表（带缓存）、套餐列表

**接口总计：64 个**（管理端 36 个 / 用户端 28 个）

---

## 核心改造（本项目的重点）

### 一、Redis 缓存三项问题修复

课程实现的缓存逻辑存在三个直接影响生产的问题，我逐个修复并压测验证。

#### 问题 1：空结果不缓存，存在缓存穿透

**原代码：**

```java
List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
if (list != null && list.size() != 0) {
    return Result.success(list);
}
```

**问题**：查询不存在的分类时，数据库返回空列表，但空列表没有被写入缓存。这意味着每一次请求都会打到数据库，攻击者可以用 `categoryId=9999` 之类的无效 ID 反复请求，绕过缓存直接压垮数据库。

**修复**：只判断 `null`，空结果也缓存，但使用**短 TTL（30 秒）**。

```java
List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
if (list != null) {                    // 空列表也算命中
    return Result.success(list);
}

list = dishService.listWithFlavor(dish);

if (list == null || list.isEmpty()) {
    redisTemplate.opsForValue().set(key, list, 30, TimeUnit.SECONDS);  // 空值短 TTL
    return Result.success(list);
}
```

**为什么 TTL 只给 30 秒**：空值可能是临时状态。如果管理员刚上架菜品，缓存还认为是空的，用户会看到"暂无菜品"。30 秒是个折中——既能挡住短时间的高频攻击，又能在数据变化后快速恢复。

#### 问题 2：缓存没有 TTL，且未做随机化

**原代码：**

```java
redisTemplate.opsForValue().set(key, list);   // 无过期时间
```

**问题**：数据永久驻留缓存，数据库变更后无法自动刷新；即使加了 TTL，如果所有 key 的过期时间相同，会在同一时刻集体失效，大量请求瞬间打到数据库——**缓存雪崩**。

**修复**：基础 30 分钟 + 0~300 秒随机值。

```java
int random = ThreadLocalRandom.current().nextInt(300);
int ttl = 1800 + random;
redisTemplate.opsForValue().set(key, list, ttl, TimeUnit.SECONDS);
```

#### 问题 3：用 `keys` 命令清理缓存，会阻塞 Redis

**原代码：**

```java
private void cleanCache(String pattern){
    Set keys = redisTemplate.keys(pattern);   // O(N) 同步命令
    redisTemplate.delete(keys);
}
```

**问题**：`keys` 是 O(N) 的同步命令，Redis 单线程在执行期间无法处理任何其他请求。key 数量多时会造成明显阻塞。

**修复**：改用 `scan` 游标分批遍历，每批 100 个。

```java
private void cleanCache(String pattern) {
    Set<String> keys = new HashSet<>();

    ScanOptions options = ScanOptions.scanOptions()
            .match(pattern)
            .count(100)
            .build();

    try (Cursor<String> cursor = redisTemplate.scan(options)) {
        while (cursor.hasNext()) {
            keys.add(cursor.next());
        }
    }

    if (!keys.isEmpty()) {
        redisTemplate.delete(keys);
        log.info("清理缓存 {} 条，pattern={}", keys.size(), pattern);
    }
}
```

`scan` 的代价是不保证一次拿全（遍历期间数据变化可能漏掉），但对"清缓存"这个场景无所谓——漏掉的 key 下次访问会重建。

---

### 二、压测对比数据

**测试环境**：本地开发机，JMeter 5.6.3，50 并发 × 20 次循环 = 1000 请求
**测试接口**：`GET /user/dish/list?categoryId=18`（该分类下 4 个菜品）
**对照组**：注释掉缓存读写逻辑，强制每次查库

| 指标 | 无缓存（A 组） | 有缓存（B 组） | 差异 |
|---|---|---|---|
| 平均响应时间 | 33 ms | 3 ms | 快 **11 倍** |
| **P95 响应时间** | **74 ms** | **5 ms** | 快 **14.8 倍** |
| P99 响应时间 | 243 ms | 6 ms | 快 **40 倍** |
| 最大响应时间 | 1249 ms | 29 ms | 快 **43 倍** |
| 吞吐量 | 196.4 /s | 201.5 /s | 基本持平 |
| 错误率 | 0.00% | 0.00% | — |

**关于吞吐量为什么基本持平**：

本次为本地单机压测，50 并发下 **JMeter 自身开销和本机 CPU 成为瓶颈**，数据库尚未被打满。因此 QPS 差异不明显。

**但 P95 / P99 的差异是真实的**：单次无缓存请求需要执行 **1 次菜品查询 + 4 次口味查询 = 5 次 SQL**（N+1 问题），而缓存命中时数据库查询为 **0 次**。响应时间分布上的差距（74ms vs 5ms）直接反映了这部分开销。

> **如果要做吞吐量对比**，需要在独立压测机上发压，或提升并发数至数据库成为瓶颈为止。

---

### 三、接口越权校验补全

**问题**：模拟支付模块的 5 个接口（预支付、查询、状态、关闭、退款）都只按订单号查订单，**没有校验订单归属**。

订单号格式为 `System.currentTimeMillis()`，是 19 位递增数字，容易被猜出。任何登录用户拿到别人的订单号，即可：

- 查询他人订单的支付状态
- **触发他人订单变为"已支付"**
- 关闭他人订单
- **对他人订单发起退款**

**修复**：抽出统一的归属校验方法，在 5 个入口处调用。

```java
private Orders requireOwnOrder(String orderNumber) {
    Orders order = orderMapper.getByNumber(orderNumber);
    if (order == null) {
        throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
    }
    Long userId = BaseContext.getCurrentId();
    if (userId != null && !userId.equals(order.getUserId())) {
        throw new PaymentBusinessException("无权操作该订单");
    }
    return order;
}
```

**验证**（用他人订单号调用退款接口）：

```
POST /user/payment/refund?orderNumber=1794452077988
Header: authentication: <当前用户token>

响应：{"code":0,"msg":"无权操作该订单","data":null}
```

---

### 四、下单接口的两处安全修复

**问题 1：订单金额由客户端决定**

原始实现直接用 `BeanUtils.copyProperties` 把前端传来的 `OrdersSubmitDTO` 拷贝到订单实体，其中包含 `amount` 字段。抓包修改金额即可一分钱下单。

**修复**：服务端根据购物车内容重新计算金额，覆盖前端传入值。

```java
BigDecimal amount = shoppingCarts.stream()
        .map(c -> c.getAmount().multiply(BigDecimal.valueOf(c.getNumber())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
order.setAmount(amount);
```

**问题 2：地址簿未校验归属**

下单时只按 `addressBookId` 查询地址，没有校验该地址是否属于当前用户。用户 A 可以传入用户 B 的地址 ID，把订单送到别人家。

**修复**：

```java
Long userId = BaseContext.getCurrentId();
AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
if (!userId.equals(addressBook.getUserId())) {
    throw new AddressBookBusinessException("地址簿不属于当前用户");
}
```

---

## 部署

**环境**：阿里云 ECS，Ubuntu 22.04，2 核 2G，3M 带宽
**地址**：http://8.166.133.47:8080/doc.html

部署过程中实际遇到的问题及解决方案：

| 问题 | 原因 | 解决方案 |
|---|---|---|
| SQL 导入报 `ERROR 1064` | Windows 端 mysqldump 5.7 导出的文件为 UTF-16 编码，MySQL 8.0 无法解析其中的 `\0` 字节 | 使用课程提供的原始 UTF-8 SQL；若必须导出，需加 `--default-character-set=utf8mb4` |
| admin 账号密码错误 | 原始 SQL 中密码为明文 `123456`，但代码登录时做 MD5 校验 | `UPDATE employee SET password='e10adc3949ba59abbe56e057f20f883e'` |
| MySQL 连接被拒绝 | Ubuntu 安装的 MySQL 默认 `root` 使用 `auth_socket` 认证，Java 无法凭密码连接 | 改为 `mysql_native_password` |
| JVM 内存不足 | 2G 内存需同时运行 MySQL + Redis + Java | 开启 2G swap，jar 启动时加 `-Xmx512m` |

**启动方式**：jar 常驻，通过脚本封装启动参数（数据库密码、Redis 密码、模拟支付成功率）。

---

## 踩过的坑

> 以下均为开发 / 部署过程中真实遇到的问题。

**1. 数据库导出文件编码损坏**

本地执行 `mysqldump` 导出的 SQL 文件是 UTF-16 LE 编码，导入 Linux 上的 MySQL 8.0 时报 `ASCII '\0' appeared in the statement`。

尝试过 `iconv` 转换、`sed` 删除注释、`perl` 正则处理，**都无法修复**——因为中文在导出时就已经错了，转码只是把错误字节重新排列。

最终用课程提供的原始 SQL 解决。

**教训**：编码问题一旦发生，修复成本远高于重新获取源文件。

**2. 文件上传到了错误目录**

使用阿里云 Workbench 上传 SQL 文件后，`ls /root/app/` 显示为空。原因是上传按钮会把文件传到**当前所在目录**，而当时停留在 `/` 根目录。

用 `find / -maxdepth 2 -name "*.sql"` 定位后 `mv` 移回。

**3. `keys` 命令在生产环境禁用**

详见上文「缓存改造」部分。

**4. JWT 双密钥设计**

管理端使用 `itcast` 密钥、header 名为 `token`；用户端使用 `itheima` 密钥、header 名为 `authentication`。

**如果不区分密钥**：学生用自己登录获得的 token 就能通过管理端拦截器的验签，直接访问管理接口——典型的越权漏洞。

---

## 本地运行

**环境要求**：JDK 17、MySQL 8.0（或 5.7）、Redis、Maven

```bash
# 1. 建库并导入数据（sky.sql 自带 CREATE DATABASE）
mysql -u root -p < db/sky.sql

# 2. 导入支付记录表
mysql -u root -p sky_take_out < sql/payment_record.sql

# 3. 初始化管理员密码（原始 SQL 是明文，代码用 MD5 校验）
mysql -u root -p -e "USE sky_take_out; UPDATE employee SET password='e10adc3949ba59abbe56e057f20f883e' WHERE username='admin';"

# 4. 配置数据库连接
cp sky-server/src/main/resources/application-dev.yml.example \
   sky-server/src/main/resources/application-dev.yml
# 编辑 application-dev.yml，填写 MySQL、Redis、OSS 配置

# 5. 打包运行
mvn clean package -DskipTests
java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
```

访问 `http://localhost:8080/doc.html`，用 `admin / 123456` 登录管理端。

---

## 目录结构

```
sky-take-out
├── sky-common/         公共模块：常量、异常、工具类、JWT
├── sky-pojo/           实体类：Entity / DTO / VO
├── sky-server/         业务逻辑与接口
│   ├── controller/     控制层（admin 管理端 / user 用户端）
│   ├── service/        业务层
│   ├── mapper/         数据访问层
│   ├── interceptor/    JWT 拦截器（管理端 / 用户端各一个）
│   ├── gateway/        支付网关抽象 + 模拟实现
│   └── config/         配置类
├── db/sky.sql          数据库初始化脚本（课程原始 UTF-8 版本）
└── sql/
    └── payment_record.sql  支付流水表
```

---

## 说明

本项目为学习项目，基于某课程的外卖系统实现。**以下内容为在课程代码基础上的额外工作**：

- 缓存三项问题修复（空值穿透、TTL 随机化、`keys` → `scan`）
- 支付接口订单归属校验
- 下单金额服务端校验、地址簿归属校验
- JMeter 压测与数据对比
- 云服务器部署与问题排查

**未实现的功能**（课程包含，但本项目未做）：WebSocket 来单提醒、客户催单、ECharts 数据统计、POI 报表导出、Nginx 反向代理。

**微信登录**：代码保留课程实现，需真实小程序 appid 与 secret 才能跑通，本项目的 JWT 令牌通过工具类直接生成用于测试。
