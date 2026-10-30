# ttdemo · 彩票预测平台

Spring Boot 4 + MyBatis-Plus + MySQL + Vue 3 的前后端工程。在「主站 + 管理后台 + 手机号验证码登录 + JWT 双令牌 + 细粒度权限」骨架上，叠加了完整的彩票预测业务：**彩种期次与开奖同步、预测发布审核、定价与积分解锁、开奖结算分账、创作者准确度评分、站内权益兑换、随机预测（含玄学玩法）**。

- 后端 20 个业务模块、39 张表、146 个接口（28 个 Controller）
- 前端 32 个页面，Element Plus 已按需引入
- 数据库初始化**只需一个脚本、一条命令**（幂等，可重复执行）

---

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 4.1.2 · Java 17 · Spring Security 7（无状态 JWT） |
| 持久层 | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`）· MySQL 8 |
| 令牌 | jjwt 0.12.6（HS256，access + refresh 轮换，带令牌版本号） |
| 短信 | 阿里云短信 SDK 4.6.0 · 本地 mock 通道 |
| 支付 | 微信支付 v3 Native（HMAC mock 通道用于联调） |
| 前端 | Vue 3.5 · TypeScript · Vite 8 · Vue Router 4 · Pinia · Element Plus 2 |
| 前端按需 | `unplugin-vue-components` + `unplugin-auto-import`（自定义 resolver 逐组件引入） |
| 构建 | Maven（`mvnw`）· npm |

---

## 2. 目录结构

```
ttdemo/
├── pom.xml
├── src/main/java/com/ttdemo/
│   ├── TtdemoApplication.java
│   ├── common/                 # 通用能力，与业务无关
│   │   ├── api/                # R 统一响应、ResultCode、PageQuery、PageResult
│   │   ├── exception/          # BizException + GlobalExceptionHandler
│   │   ├── lock/               # LockService 抽象 + LocalLockService（可换 Redis）
│   │   ├── trace/              # traceId 生成与透传
│   │   ├── util/               # PhoneUtils、IdGen（单号）、AES 加解密
│   │   └── web/                # RequestUtils（IP / UA 提取）
│   ├── config/                 # 基础设施配置
│   │   ├── SecurityConfig      # 过滤链：匿名白名单 / 角色前缀 / 其余需登录
│   │   ├── MybatisPlusConfig   # 分页插件 + createdAt/updatedAt 自动填充
│   │   ├── EncoderConfig       # BCrypt
│   │   ├── JwtProperties / SmsProperties / CorsProperties
│   │   └── AdminAccountInitializer   # 首次启动创建管理员
│   ├── security/
│   │   ├── JwtTokenProvider / JwtAuthenticationFilter / SecurityUtils
│   │   └── perm/               # @RequirePerm / @RequireCreator + PermAspect + PermService
│   ├── job/ScheduledJobs       # 开奖同步、补期、锁期、结算、评分、榜单、积分过期
│   └── modules/                # 业务模块（按功能纵向切分，共 20 个）
│       ├── auth/               # 认证：验证码、注册、登录、刷新、重置密码
│       ├── user/               # 用户：资料、头像、改密、注销
│       ├── admin/              # 管理后台：管理员登录、用户管理、统计、日志
│       ├── notice/             # 通知公告（平台广播）
│       ├── feedback/           # 用户反馈：提交、我的反馈、后台回复与状态流转
│       ├── log/                # 登录日志
│       ├── lottery/            # 彩种字典、期次、开奖同步与双人复核
│       ├── creator/            # 实名认证（AES-256-GCM）、创作者申请/审核、关注
│       ├── prediction/         # 发布/编辑/提交/撤回/审核/下架 + PriceCalculator + HitJudge
│       ├── random/             # 随机预测：15 种方法 + MysticEngine 玄学引擎 + 每日配额
│       ├── point/              # 积分账户、流水（唯一账本）、批次 FIFO、过期
│       ├── order/              # 解锁订单、充值订单、支付回调
│       ├── settlement/         # 开奖结算、分账、结算单撤销、财务对账
│       ├── finance/            # 真实资金流水分页与统计
│       ├── reward/             # 积分兑换站内权益（合规：不兑换现金）
│       ├── score/              # 准确度评分（威尔逊下界 + 时间衰减）、榜单快照
│       ├── message/            # 站内信（个人业务事件）
│       ├── growth/             # 签到、邀请、新人礼包
│       ├── report/             # 内容举报与处置
│       ├── risk/               # 敏感词本地拦截
│       └── system/             # 配置中心（热更新）、审计日志
├── src/main/resources/
│   ├── application.yml         # 公共配置
│   ├── application-dev.yml     # 开发数据源
│   └── db/schema.sql           # 全库建表脚本（唯一，幂等）
├── src/test/java/.../JwtTokenProviderTest.java
└── web/                        # 前端（主站 + /admin 后台同一工程）
    └── src/
        ├── api/                # http（双令牌拦截器）+ 14 个业务接口模块
        ├── stores/             # auth.ts 登录态与权限、notice.ts 未读数
        ├── router/index.ts     # 路由 + 守卫（requiresAuth / requiresAdmin）
        ├── layouts/            # SiteLayout、AdminLayout（菜单按权限过滤）
        ├── utils/              # format、perms、lottery 常量
        └── views/
            ├── site/           # 19 个前台页面
            ├── admin/          # 12 个后台页面
            └── error/
```

分层约定：`controller`（只做参数校验与响应包装）→ `service`（业务）→ `mapper`（SQL）。跨模块禁止直接访问对方 mapper，统一走对方 `service`。

---

## 3. 快速开始

### 3.1 建库

```bash
mysql --default-character-set=utf8mb4 -uroot -p < src/main/resources/db/schema.sql
```

**全脚本幂等**：建表 `CREATE TABLE IF NOT EXISTS`、加列先查 `information_schema`、种子数据 `ON DUPLICATE KEY UPDATE`。对已有库重跑不会报错，也不会覆盖已调整过的配置值。

### 3.2 启动后端（默认 dev profile）

```bash
# Windows
mvnw.cmd spring-boot:run
# macOS / Linux
./mvnw spring-boot:run
```

数据源默认 `localhost:3306/ttdemo`、`root/123456`，可用环境变量覆盖：`DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD`。

首次启动会自动创建管理员：**13800000000 / admin123456**（见 `AdminAccountInitializer`，请立即修改）。

### 3.3 启动前端

```bash
cd web
npm install
npm run dev      # http://localhost:5173 ，/api 由 vite 代理到 8080
```

| 入口 | 地址 |
|---|---|
| 主站 | http://localhost:5173/ |
| 前台登录 / 注册 | http://localhost:5173/login |
| 管理后台 | http://localhost:5173/admin |

> 后台登录入口已从页面移除：用管理员账号在前台 `/login` 登录后，会自动进入 `/admin`；非后台角色的访问会被守卫拦截。

---

## 4. 配置项

### 4.1 环境变量（`application.yml`）

| 配置 | 说明 | 默认 |
|---|---|---|
| `app.jwt.secret` | HS256 密钥，≥32 字节 | 开发默认值，生产必须设 `APP_JWT_SECRET` |
| `app.jwt.access-token-ttl` | 访问令牌有效期 | `30m` |
| `app.jwt.refresh-token-ttl` | 刷新令牌有效期 | `7d` |
| `app.crypto.key` | 姓名/身份证/手机号 AES-256-GCM 密钥 | 生产必须设 `APP_CRYPTO_KEY` |
| `app.sms.provider` | `mock` / `aliyun` | `mock` |
| `app.sms.expire` / `resend-interval` / `daily-limit` / `max-attempts` | 验证码有效期 / 重发间隔 / 每日上限 / 最大错误次数 | `5m` / `60s` / `10` / `5` |
| `app.sms.aliyun.*` | AccessKey、签名、模板 | 走 `ALIYUN_SMS_*` 环境变量 |
| `app.pay.provider` | `mock` / `wechat` | `mock` |
| `app.pay.mock-secret` | mock 通道回调验签密钥 | **生产必须改** `APP_PAY_SECRET` |
| `app.pay.wechat.*` | 商户号、证书序列号、私钥、APIv3 密钥、平台公钥、回调地址 | 走 `WX_PAY_*` 环境变量 |
| `app.draw.proxy` | 官方开奖同步代理，留空读 `HTTPS_PROXY` | 空 |
| `app.cors.allowed-origins` | 允许跨域的前端地址 | `localhost:5173` |
| `app.upload.dir` / `max-size` | 头像本地上传目录与大小上限 | `./data/uploads` / 2 MB |
| `app.init.admin-phone/password` | 初始管理员 | `13800000000` / `admin123456` |

切到阿里云短信：

```bash
set APP_SMS_PROVIDER=aliyun
set ALIYUN_SMS_ACCESS_KEY_ID=xxx
set ALIYUN_SMS_ACCESS_KEY_SECRET=xxx
set ALIYUN_SMS_SIGN_NAME=xxx
set ALIYUN_SMS_TEMPLATE_CODE=SMS_xxxxxx   # 模板变量名必须为 ${code}
```

切到微信支付：配齐 `WX_PAY_*` 后设 `APP_PAY_PROVIDER=wechat`，代码无需改动。

### 4.2 运营配置（`sys_config` 表，后台 `/admin/finance` 可改，热生效）

| Key | 说明 |
|---|---|
| `feature.draw.sync` / `feature.unlock.enabled` / `feature.recharge.enabled` / `feature.settle.enabled` | 功能总开关：开奖同步 / 解锁 / 充值 / 结算 |
| `auth.login.fail.limit` / `auth.login.lock.minutes` | 密码登录失败上限（默认 5）与锁定时长（默认 15 分钟） |
| `realname.min.age` | 实名认证最小年龄（默认 18） |
| `random.vip.enabled` / `random.vip.daily.free` | 随机预测会员分级开关 / 非会员每日免费试用次数（默认 3） |
| `price.base.<玩法>` / `price.coef.slope` / `price.coef.intercept` / `price.sample.full` / `price.max.points` | 定价：各玩法基础价、样本量系数斜率和截距、样本饱和值、积分上限 |
| `platform.fee.rate` / `platform.fee.newbie` | 平台分成比例、新人优惠期分成 |
| `point.recharge.rate` / `point.recharge.min.fen` / `max.fen` / `daily.fen` | 充值汇率（分→积分）与单笔/每日限额 |
| `point.newbie.bonus` / `point.newbie.expire.days` / `point.expire.months` | 新人礼包积分、有效期、积分过期月数 |
| `point.sign.in.points` / `point.sign.streak.bonus` / `point.invite.reward` / `point.invite.recharge.reward` | 签到、连签奖励、邀请注册、被邀首充奖励 |
| `promote.points.per.day` | 预测置顶推广单价（积分/天） |
| `reward.exchange.enabled` / `min.points` / `daily.limit` | 权益兑换开关、起兑积分、每日次数上限 |
| `report.auto.offline.threshold` | 举报累计达此值自动下架预测 |
| `score.window.days` / `score.window.max` / `score.cold.start` / `score.cold.cap` | 评分窗口天数与最大样本、冷启动分与封顶 |
| `hit.threshold` | 命中判定阈值 |
| `risk.max.prediction.per.issue` | 单期次单创作者发布上限 |
| `agreement.required` / `agreement.version` | 注册是否强制勾选协议、协议版本 |

`SysConfigService` 热读取，改完 `PriceCalculator` / `HitJudge` 等立即生效，无需重启。布尔值兼容 `true/1/on/yes`。

---

## 5. 接口清单

统一响应：`{"code":0,"message":"ok","data":...,"traceId":"..."}`，**`code=0` 为成功**（v1.1 起由 200 改为 0）；HTTP 状态码与语义对齐（401/403/409/429 等），业务错误 400 + `code` 1xxx；分页统一返回 `PageResult`（`list/total/pageNum/pageSize/hasNext`）。

### 5.1 认证（匿名）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/sms-code` | 获取验证码，`{phone, scene: REGISTER\|LOGIN\|RESET_PASSWORD}` |
| POST | `/api/auth/register` | 验证码注册（`agreed` 必传，记录协议版本与同意时间） |
| POST | `/api/auth/login/sms` | 验证码登录（未注册自动建档） |
| POST | `/api/auth/login/password` | 手机号 + 密码登录（失败超限锁定） |
| POST | `/api/auth/refresh` | 用 refreshToken 换新双令牌（校验令牌版本） |
| POST | `/api/auth/logout` | 退出（令牌版本号自增，旧令牌立即失效） |
| POST | `/api/auth/password/reset` | 验证码重置密码 |

### 5.2 用户中心（需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET / PUT | `/api/user/me` | 当前用户信息 / 修改昵称 |
| POST | `/api/user/avatar` | 上传头像 |
| PUT | `/api/user/password` | 修改密码（需原密码） |
| PUT | `/api/user/password/init` | 首次设置密码（手机号自动注册的场景） |
| GET / POST | `/api/user/deactivate` | 注销申请：查询剩余积分与应退金额 / 提交 |

### 5.3 通知公告与反馈（需登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/notices` | 我的公告分页，支持 `unreadOnly`、`type` |
| GET | `/api/notices/unread-count` | 未读公告数（顶栏红点） |
| GET | `/api/notices/{id}` | 公告详情，**打开即自动标记已读** |
| POST | `/api/notices/read-all` | 全部标记已读 |
| POST | `/api/feedback` | 提交反馈，`{type, title, content, contact?}` |
| GET | `/api/feedback/my` | 我的反馈分页（含管理员回复） |

### 5.4 彩票与期次

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/lottery/types` | 彩种列表 |
| GET | `/api/v1/lottery/issues` | 期次列表（`typeCode`、`limit` ≤ 100） |
| GET | `/api/v1/lottery/issues/current` | 各彩种当前期（首页「即将开奖」） |
| GET | `/api/v1/lottery/issues/{id}` | 期次详情 |

### 5.5 预测

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/predictions` | 预测广场（分页 + 筛选 + 置顶优先） |
| POST | `/api/v1/predictions` | 创建草稿（结构化录入） |
| GET / PUT / DELETE | `/api/v1/predictions/{id}` | 详情 / 编辑 / 删除草稿 |
| GET | `/api/v1/predictions/{id}/content` | 付费内容（未解锁不可见） |
| GET | `/api/v1/predictions/{id}/hit-detail` | 命中明细（**仅购买者可见**，其余脱敏） |
| POST | `/api/v1/predictions/{id}/submit` | 提交审核 |
| POST | `/api/v1/predictions/{id}/withdraw` | 撤回 |
| POST | `/api/v1/predictions/{id}/promote` | 置顶推广（50 积分/天） |
| GET | `/api/v1/predictions/price-quote` | 定价试算 |
| GET | `/api/v1/predictions/mine` / `purchased` | 我发布的 / 我解锁的 |

### 5.6 订单、积分与充值

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/orders/unlock` | 解锁预测（积分扣款 + 幂等键） |
| GET | `/api/v1/orders/mine` / `{orderNo}` | 我的订单 / 订单详情 |
| GET | `/api/v1/points/account` / `transactions` | 积分账户 / 流水 |
| GET | `/api/v1/points/recharge-config` | 充值档位与赠送规则 |
| POST | `/api/v1/recharge/orders` | 创建充值单 |
| GET | `/api/v1/recharge/orders/{orderNo}` | 充值单状态 |
| POST | `/api/v1/recharge/orders/{orderNo}/mock-pay` | 模拟支付（**仅 mock 通道可用**） |
| POST | `/api/v1/recharge/notify/{channel}` | 支付回调（校验通道与签名） |
| POST | `/api/v1/recharge/notify/wechat` | 微信支付 v3 专用回调（验签 + 解密） |

### 5.7 随机预测、创作者、榜单、消息、权益、增长、举报

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/random/methods` | 15 种方法（含分组与是否会员专享） |
| POST | `/api/v1/random/generate` | 生成预测（扣配额） |
| GET | `/api/v1/random/quota` / `history` | 今日剩余免费次数 / 历史记录 |
| POST | `/api/v1/creator/apply` | 申请成为创作者 |
| GET / PUT | `/api/v1/creator/profile` | 创作者资料 |
| POST | `/api/v1/creator/realname` | 提交实名认证 |
| GET | `/api/v1/creator/dashboard` / `settlements` / `accuracy` | 看板 / 结算单 / 准确度 |
| GET | `/api/v1/creators/{id}` | 创作者主页 |
| POST / DELETE | `/api/v1/creators/{id}/follow` | 关注 / 取关 |
| GET | `/api/v1/rank/list` | 创作者榜单 |
| GET | `/api/v1/messages` / `unread-count` / `unread-stats` | 消息列表 / 未读数 / 分类未读 |
| POST | `/api/v1/messages/read` / `read-all` | 标记已读（`{ids:[]}`） |
| GET | `/api/v1/rewards/items` / `summary` | 权益商品 / 兑换概览 |
| POST | `/api/v1/rewards/orders` | 兑换（扣积分 + 扣库存） |
| GET / POST | `/api/v1/growth/sign-in` | 签到状态 / 签到 |
| GET | `/api/v1/growth/invite` | 我的邀请码与奖励 |
| POST / GET | `/api/v1/reports` / `mine` | 提交举报 / 我的举报 |

### 5.8 管理后台（需后台角色，具体权限由 `@RequirePerm` 校验）

| 分组 | 接口 |
|---|---|
| 认证 | `POST /api/admin/auth/login`、`POST logout`、`GET me` |
| 仪表盘 | `GET /api/admin/stats/overview`、`GET /api/admin/stats/platform`、`GET /api/admin/logs` |
| 用户 | `GET /api/admin/users`、`GET {id}`、`PUT {id}/status`、`PUT {id}/role`、`PUT {id}/password`、`DELETE {id}` |
| 通知 | `GET/POST /api/admin/notices`、`GET/PUT/DELETE {id}`、`POST {id}/publish` |
| 反馈 | `GET /api/admin/feedback`、`GET/DELETE {id}`、`PUT {id}/reply` |
| 预测审核 | `GET /api/admin/v1/predictions`、`POST {id}/audit`、`POST {id}/offline` |
| 期次开奖 | `GET/POST /api/admin/v1/issues`、`PUT {id}`、`POST {id}/draw-result`、`POST {id}/draw-result/prepare`、`POST sync`、`POST sync-all`、`POST ensure-upcoming`、`POST cleanup`、`POST {id}/settle` |
| 彩种 | `GET/POST /api/admin/v1/lottery/types`、`PUT {id}` |
| 创作者 | `GET /api/admin/v1/creators`、`POST {id}/audit`、`GET /api/admin/v1/realnames`、`GET realnames/stats`、`POST realnames/{id}/audit`、`POST realnames/{id}/reset` |
| 财务 | `GET /api/admin/v1/finance/money/overview`、`GET finance/money/flows`、`GET finance/reconciliation`、`GET points/transactions`、`POST users/{id}/points/adjust` |
| 结算 | `GET /api/admin/v1/settlements`、`POST {id}/cancel` |
| 配置 | `GET/PUT /api/admin/v1/configs`、`GET /api/admin/v1/audit-logs` |
| 榜单重建 | `POST /api/admin/v1/rank/rebuild`、`POST /api/admin/v1/score/rebuild` |
| 权益 | `GET/POST /api/admin/v1/rewards/items`、`PUT items/{id}`、`GET rewards/orders`、`POST orders/{id}/issue`、`POST orders/{id}/cancel`、`POST rewards/vip/grant` |
| 举报注销 | `GET /api/admin/v1/reports`、`GET reports/pending-count`、`POST reports/{id}/handle`、`GET /api/admin/v1/deactivations`、`POST deactivations/{id}/handle` |

---

## 6. 鉴权与权限

### 6.1 JWT 双令牌流程

1. 登录成功 → `accessToken`（30 分钟）+ `refreshToken`（7 天），**两者都携带令牌版本号 `tv`**。
2. 前端 `Authorization: Bearer <accessToken>` 调用接口。
3. `JwtAuthenticationFilter` 校验签名 / 过期 / 类型 / **令牌版本**，按 `uid` 回查用户；禁用用户放行为匿名 → 401。
4. accessToken 过期时 axios 拦截器用 refreshToken 静默换新令牌并重放原请求（并发请求共用同一个刷新 Promise）。
5. 刷新失败 → 清理本地会话并跳转 `/login`。

**令牌版本（`sys_user.token_version`）** 是强踢下线的机制：改密、重置密码、首次设密、注销、管理员禁用都会自增版本号，使已发出的令牌（含 refresh）立即失效——refresh 时同样比对版本，避免用旧 refresh 换出新 access 绕过强踢。

### 6.2 细粒度权限

- 接口侧：`@RequirePerm("prd:prediction:audit")` + `@RequireCreator`，由 `PermAspect` AOP 校验，Controller 内无角色判断。
- 共 18 个权限点（`security/perm/Permissions.java`）：预测发布/审核/下架、创作者审核、期次管理与结算、积分查看与调账、结算确认、榜单重建、风控、配置、角色授予、用户查看/管理、公告管理、反馈管理、仪表盘查看。
- 角色 → 权限映射见 `PermService.ROLE_PERMS`：`ADMIN` 全量；`OPERATOR` 审核 + 期次；`AUDITOR` 审核 + 风控；`FINANCE` 积分 + 结算 + 财务；`CREATOR` 发布。
- 菜单侧：后台菜单项按 `auth.can(...)` 隐藏无权限入口，前端与后端同一套权限点（`utils/perms.ts`）。

---

## 7. 前端工程

### 7.1 Element Plus 按需引入

`vite.config.ts` 中 `unplugin-vue-components` + `unplugin-auto-import` 接管组件与 `ElMessage`/`ElMessageBox`/`ElNotification`/`ElLoading` 的导入，样式随组件自动注入。

**关键实现**：官方 `ElementPlusResolver` 会把所有组件解析到 `element-plus/es`（导出全部组件的 barrel），esbuild/rolldown 预构建无法 tree-shake，会打出约 2 MB 单文件 + 4.2 MB sourcemap，导致浏览器 DevTools 打开时解析卡顿。本项目**包装了官方 resolver**，把导入改写为 `element-plus/es/components/<dir>/index.mjs`：

- 组件名 → 入口的映射由扫描 `es/components/*/index.mjs` 的 `export {}` 生成（像 `ElFormItem`/`ElTableColumn`/`ElAside`/`ElOption` 这类无独立入口的子组件，会正确落到父入口）
- 查不到映射时回退官方行为，不会因优化导致解析失败
- `Components({ dts: false })` 是刻意保留的：生成精确组件类型会让 `el-table` 插槽的 `scope.row` 从 `any` 收紧为 `DefaultRow`，历史模板需改 27 处
- 中文 locale 由 `App.vue` 的 `<el-config-provider :locale="zhCn">` 兜底

效果：dev 态预构建 9.0 MB → 2.8 MB，最大单文件 map 4.21 MB → 0.42 MB；生产 `dist` JS 约 1.2 MB，最大单文件 124 KB。

### 7.2 页面

| 分区 | 页面 |
|---|---|
| 主站 | 首页、预测广场、预测详情、随机预测、榜单、创作者主页/认证/看板/收益、发布预测、我的积分、我的解锁、积分兑换、消息中心、通知中心、意见反馈、个人中心、协议条款、登录注册 |
| 后台 | 仪表盘、用户管理、登录日志、预测审核、期次管理、创作者审核、财务与配置、权益管理、举报与注销、通知公告、用户反馈、后台登录 |

---

## 8. 关键业务规则

| 规则 | 实现位置 | 说明 |
|---|---|---|
| 定价公式 | `prediction/engine/PriceCalculator` | `round5(基础价 × 系数 × 样本因子)`，系数与基础价读 `sys_config`，**全项目唯一实现** |
| 命中判定 | `prediction/engine/HitJudge` | 11 种 item_type 全覆盖，`hit_detail` 存原始计算过程 |
| 状态机 | `PredictionServiceImpl` | 0草稿→1待审→3上架→4锁定→5结算；6撤回/7下架触发全额退款 |
| 下单扣款 | `OrderServiceImpl.unlock` | 事务内：扣积分 → 写订单（分账挂账）→ buyer_count+1；`Idempotent-Key` + `uk_user_pred` 双保险，幂等键按用户隔离 |
| 结算分账 | `SettlementServiceImpl` | 按 creator 分组生成结算单，`uk_creator_issue` + 流水 `uk_biz` 保证重跑不重复入账；状态写盘走独立事务（REQUIRES_NEW），失败可留痕可重试 |
| 评分 | `ScoreServiceImpl.calculateScore` | 威尔逊下界 0.7 + 时间衰减加权命中 0.3；n<5 取中性分 30，5-9 封顶 60 |
| 积分账本 | `PointServiceImpl` | 所有积分变动必须走 `PointService`（写流水 + 批次 FIFO），禁止直接 UPDATE available |
| 权益兑换 | `RewardServiceImpl` | 积分**只兑换站内权益**（会员天数/实物礼品），不兑换现金——合规红线；库存扣减为条件 UPDATE 防超卖 |
| 审计 | `AuditLogService` | 审核、下架、调账、结算撤销、开奖更正全部留痕 |

### 8.1 期号与开奖

- 官方期号判定统一为 `LENGTH(issue_no) IN (5,7)`（福彩 7 位、体彩 5 位），8 位为测试数据
- 期次列表排序 `draw_time DESC, id DESC`（不用字符串 issue_no 比较，否则 8 位测试期号会顶到最前）
- `lot_issue.settle_status`：`PENDING / RUNNING / DONE / FAILED`，`settlePending` 会一并捞取卡死超 30 分钟的 RUNNING 重试

**官方数据源**（均为官方公开接口，非第三方爬虫，拉取带 UA/Referer，连接 6s / 读 15s 超时）：

| 彩种 | 数据源 | 参数 |
|---|---|---|
| 双色球 SSQ | `www.cwl.gov.cn` 福彩官方查询接口 | `name=ssq` |
| 福彩 3D FC3D | 同上 | `name=3d` |
| 快乐 8 KL8 | 同上 | `name=kl8` |
| 大乐透 DLT | `webapi.sporttery.cn` 体彩官方接口 | `gameNo=85`，结果拆 5 前区 + 2 后区 |
| 排列 3 PL3 | 同上 | `gameNo=35` |

**同步策略**：

- 按 `(type_code, issue_no)` **upsert**，重复执行幂等（已存在且号码一致 → 跳过）；已结算期次不覆盖，避免破坏账目
- 校验复用 `LotteryService.validateNumbers`，失败落 `lot_sync_error`
- 统计口径只认官方期号，历史测试期次不参与热号/冷号计算
- `ensure-upcoming` 按最新真实期号 +1 并结合开奖日历（`draw_weekdays` / `draw_time_of_day`）生成下一期，跨年自动重置为 `001`
- 开关：`sys_config.feature.draw.sync`；代理：`app.draw.proxy`（留空则读环境变量 `HTTPS_PROXY`）
- 同步接口返回 `{fetched, inserted, updated, skipped, failed, message, errors[]}`

### 8.2 随机预测

15 种方法分三组（`modules/random/RandomMethod.java`）：

| 分组 | 方法 | 会员专享 |
|---|---|---|
| `BASIC` | 纯随机、均衡分布、黄金分割 | 否（永久免费） |
| `STATS` | 热号追踪、冷号回补、冷热均衡、极限遗漏、转移概率、和值锚定 | 是 |
| `MYSTIC` | 易经起卦、五行生克、八字、生肖、塔罗、黄历 | 是 |

玄学算法集中在 `MysticEngine`，结果带娱乐性声明。非会员每天有 `random.vip.daily.free`（默认 3）次免费试用，会员不限；配额表 `rnd_daily_usage`，消耗用条件 UPDATE，天然防并发超用。

**实现要点**：

- **一注号码个数**优先取历史开奖结构（SSQ 6+1、DLT 5+2、FC3D/PL3 3 位、KL8 20 个），无历史时用内置标准表——`play_config.pickMin` 是「可选取号码数范围」，不等于注数
- FC3D / PL3 按**位号**处理，允许重复号码（如 `7,0,0`），其余彩种号码不重复且升序展示
- 输出 `items` 与发布接口 `PredictionDtos.ItemRequest` 同构，前端「带入发布页」直接填充（`sessionStorage: ttdemo.randomDraft`）
- 历史数据不足**不禁用**方法，降级为纯随机并在方法卡上提示，避免整排灰掉
- 可选 `seed`，同 seed 结果可复现（`OMISSION` 完全确定）
- **参考期数联动**：改期数后 400ms 防抖自动重算；结果区显示「使用最近 N 期」，参数区显示「库中可用 M 期」
- **数据可溯源**：结果下方给出数据说明 + 来源构成（自动同步 / 人工录入），可展开查看每期期号、开奖时间、开奖号、来源
- 生成的期次数据来自 `lot_issue` 表 `draw_status=1` 的记录，`dataSource` 区分 1=自动同步、2=人工录入
- 接口免登录：`GET /api/v1/random/methods`、`GET /api/v1/random/history`、`POST /api/v1/random/generate`；页面：`/random`

### 8.3 定时任务（`job/ScheduledJobs`）

| 频率 | 任务 | 说明 |
|---|---|---|
| 21–23 点每 10 分钟 | `drawSyncJob` | 开奖同步 |
| 每小时第 5 分钟 | `ensureUpcomingJob` | 补生成未来期次 |
| 每分钟 | `lockJob` | 到点锁定期次 |
| 每 5 分钟 | `settleJob` | 结算待结算期次 |
| 每天 00:30 | `scoreJob` | 重算准确度评分 |
| 每天 01:00 | `rankJob` | 重建榜单快照 |
| 每天 03:00 | `pointExpireJob` | 积分批次过期 |

全部幂等，可安全重跑。

---

## 9. 数据表

39 张表，全部在 `src/main/resources/db/schema.sql`，按五段组织：

1. **账号体系**：`sys_user`、`sms_code`、`sys_login_log`、`sys_notice`、`sys_notice_receiver`、`sys_feedback`、`sys_config`、`sys_audit_log`
2. **彩票平台**：`lot_lottery_type`、`lot_issue`、`lot_sync_error`、`prd_prediction*`、`prd_audit_record`、`prd_view_log`、`ord_*`、`acc_point_*`、`acc_settlement`、`score_*`、`not_message`、`not_send_log`、`usr_realname`、`cre_creator`、`cre_follow`
3. **合规与增长**：`sys_report`、`usr_sign_in`、`usr_deactivation`、`ord_promotion`
4. **财务权益**：`acc_reward_item`、`acc_reward_order`
5. **随机会员**：`rnd_daily_usage`
6. **风控**：`risk_blacklist`、`risk_sensitive_word`

---

## 10. 生产部署要点

- 必须设置 `APP_JWT_SECRET`（≥32 字节随机串）、`APP_CRYPTO_KEY`、数据库密码，禁止使用仓库默认值。
- **必须修改 `APP_PAY_SECRET`**：mock 通道的回调验签使用默认密钥时可被伪造入账；上线应切 `APP_PAY_PROVIDER=wechat`（非 mock 通道下 `/recharge/notify/**` 与 mock-pay 均被拒绝）。
- 关闭 `app.sms.provider=mock`，否则验证码会写日志并随接口返回。
- 修改默认管理员密码，`app.init.admin-password` 只在账号不存在时生效。
- 前端 `npm run build` 产物 `web/dist/` 交给 Nginx，`/api` 反代到后端：

```nginx
location / {
    root /var/www/ttdemo;
    try_files $uri $uri/ /index.html;   # history 路由必须
}
location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Host $host;
}
```

- 跨域：同域部署时把 `app.cors.allowed-origins` 收紧到实际域名。
- 日志：`logging.level.com.ttdemo` 生产建议 `info`，关闭 MyBatis SQL 打印。

---

## 11. 已知限制 / 后续可加

| 项 | 现状 | 建议 |
|---|---|---|
| 锁与限流 | 本地锁 + DB 唯一索引 + 状态机 | 多实例部署需把 `LockService` 换成 Redis 实现（抽象已就绪） |
| 验证码存储 | MySQL `sms_code` 表 | 量大后迁 Redis（`SmsCodeService` 换实现即可） |
| 接口文档 | 无（Spring Boot 4.1 暂无稳定 springdoc） | 引入 springdoc 或改用其他文档方案 |
| 逻辑删除 + 唯一手机号 | 删除后手机号占位 | 需要复用手机号时改为 `deleted_at` 或加后缀 |
| 预测申诉改判 | 由客服线下处理 + 财务调账 | 增加线上申诉流程 |
| 注销退款 | 登记但未自动回退未结算创作收益 | 如需严格合规可补齐 |

---

## 附录 A · 实名认证与审核口径

### A.1 提交校验

| 校验 | 规则 | 失败返回 |
|---|---|---|
| 格式 | `^\d{17}[\dX]$` | `1026` |
| 校验位 | GB 11643-1999 mod 11-2 | `1026` |
| 出生日期 | 真实存在且年份 1900–今年 | `1026` |
| 年龄 | ≥ `sys_config.realname.min.age`（默认 18） | `1039` |
| 重复提交 | 已通过不可再提交 | `1040` |
| 一证一号 | `id_card_hash` 唯一 | 409 状态冲突 |

> 之前只做正则，`110101111201011234`（1112 年出生）也能入库，后台显示 914 岁，审核时无法核对。

### A.2 脱敏口径

| 字段 | 展示 | 说明 |
|---|---|---|
| 姓名 | `张*三` / `欧**乙` | 保留首末字，之前只留首字导致同名无法区分 |
| 身份证 | `110101********1234` | 保留地区码 6 位 + 末 4 位 |
| 手机号 | `139****2443` | 中间 4 位打码 |

审核页同时返回 `userId / nickname`，即使脱敏也能定位到具体账号。

### A.3 状态流转

```
提交 → 待审核(0) ─┬─ 通过 → 已通过(1) ──管理员撤销──→ 已驳回(2) → 可重新提交
                  └─ 驳回 → 已驳回(2) ──重新提交───→ 待审核(0)
```

- 重新提交会**刷新 `created_at` 并清空上一轮审核痕迹**，避免后台仍显示首次提交时间、排在旧位置。
- 撤销仅对「已通过」开放，同时把 `sys_user.realname_status` 置 3，用户可重新提交；操作写审计日志。
- 年龄以身份证**现算**为准；证件不合法的历史记录标 `idCardInvalid=true`，前端显示「证件异常」并整行高亮。

---

## 附录 B · 通知与消息的职责划分

两个入口曾长期混用（通知中心有页面无内容、消息中心有表无写入）。现按职责分离：

| | 通知中心 `/notices` | 消息中心 `/messages` |
|---|---|---|
| 定位 | 平台公告 | 个人业务进度 |
| 数据表 | `sys_notice` + `sys_notice_receiver` | `not_message` |
| 写入方 | 管理员后台发布（全员 / 定向） | 各领域服务在状态变更后推送 |
| 面向 | 一对多广播 | 一对一事件 |
| 顶栏名称 | 公告 | 消息 |

### 消息类型与触发点

类型常量见 `modules/message/MessageType.java`，文案集中在 `MessageService` 的语义方法里，业务侧只传事实。

| 类型 | 触发点 | 调用位置 | 跳转 |
|---|---|---|---|
| `AUDIT` | 预测审核通过 / 驳回（含理由）/ 被下架 | `PredictionServiceImpl` | 预测详情 |
| `SETTLE` | 期次结算收益入账 | `SettlementServiceImpl.doSettle` | 我的收益 |
| `TRADE` | 解锁订单退款 | `OrderServiceImpl.refundByPrediction` | 我的解锁 |
| `TRADE` | 权益兑换发放（会员即时 / 礼品人工）、兑换取消退分 | `RewardServiceImpl` | 积分兑换 |
| `SYSTEM` | 举报处理结果 | `ReportService.handle` | 个人中心 |
| `SYSTEM` | 注销申请驳回 | `UserServiceImpl.handleDeactivation` | 个人中心 |

- 消息推送失败只记 `warn` 日志，不影响主业务事务
- 未读数按类型分组：`GET /api/v1/messages/unread-stats`（一次 `GROUP BY`，非 N 次 count）
- 注销申请**通过**时不发消息：账号已停用，消息无处可投

---

## 附录 C · 外部依赖降级说明

对应需求文档中的外部依赖，本工程按下列方式降级，接口形状保持一致：

| 文档要求 | 本工程实现 | 说明 |
|---|---|---|
| Redis（锁/限流/计数） | 本地锁 + DB 唯一索引 + 状态机 | 单机部署等效；多实例需换 Redis 实现 |
| Xxl-Job | Spring `@Scheduled` | `job/ScheduledJobs`，全部幂等可重跑 |
| 微信/支付宝 | Mock 支付 + 微信支付 v3 Native | `app.pay.provider=mock\|wechat` |
| OSS 私有读 | 预留接口 | 本期图片仅存 objectKey，头像走本地上传 |
| 云内容安全 | 本地敏感词库 | 命中即拦截 |

**仍需外部资源才能真跑**：微信支付（商户号、证书序列号、私钥、APIv3 密钥、平台公钥）、阿里云短信（AccessKey / 签名 / 模板）、法务审核（三份协议文本为自拟稿）。
