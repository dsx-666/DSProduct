# 衡账经营数据工作台前端

原生 HTML、CSS、JavaScript（ES6+）实现，不依赖 Vue、React 或 axios。接口通过 `fetch` 调用；平面图表由本地 ECharts 5.6.0 渲染，3D 品类视图由本地 Three.js 0.186.1 渲染。

## 功能范围

| 编号 | 实现 | 交互与数据口径 |
| --- | --- | --- |
| F1 | 四项 KPI | 总销售额、去重订单数、平均客单价、热销商品数；读取 `/api/orders/kpi` |
| F2 | 品类多选 | 读取 `/api/categories`，集合内为 OR，与日期、品牌为 AND；关闭品类或品牌选项面板后自动提交筛选 |
| F3 | 时间筛选 | 起止日期和近 7/30/90 天；固定业务日为 `2025-11-14`；每次进入页面恢复近 30 天、全部品类和全部授权品牌 |
| F4 | 销售趋势 | 销售额、订单量、双指标切换；日/周/月粒度 |
| F5 | 全量品类构成 | 读取 `/api/product/category-stats`，展示当前筛选范围全部品类的销售额、销量、订单数和占比 |
| F6 | 商品 TOP10 | 完整显示最多 10 个商品；`isHot` 仅标记整个筛选范围销量前 `ceil(N×30%)` 的商品，不隐藏非热销商品 |
| F7 | 用户与支付构成 | 读取 `/api/user/customer-stats` 与 `/api/orders/payment-stats` 的全量筛选统计 |
| F8 | 订单明细 | 商品名称搜索、状态、每页条数、分页与当前列表行摘要抽屉 |
| B8 | 品牌新老客分层 | `/api/brand/customer-segments` 全量历史统计；SUPER_ADMIN 可选单品牌，ADMIN 固定会话品牌；不携带经营日期 |
| B9 | 品牌商品经营 | `/api/brand/products` 支持当前筛选/全量历史、商品 ID 或名称查询、品牌范围、排序和分页；点选商品在居中的全局窗口读取经营详情 |
| B10 | 商品评价 | 商品详情内按日期、评分和每页条数分页读取 `/api/brand/products/{productId}/reviews` |
| B11 | 商品购买用户 | 商品详情内按用户 ID 精确查询去重购买用户，并沿用商品经营范围和日期 |
| P1 | 人员查询 | 超级管理员按姓名、邮箱、用户 ID、角色、状态分页查询 `/api/admin/users` |
| P2 | 新增管理员 | 提交邮箱、初始密码、姓名、单一品牌和状态到 `POST /api/admin/users` |
| P3 | 编辑人员 | 使用服务端版本号最小化 PATCH 姓名、品牌、状态；保护本人和超级管理员账号；当前数据库存在 `chk_role_user_type` 时暂不从界面提交角色转换 |
| R1 | 实时更新 | 登录后以一次性短效票据建立 `/ws/updates`；MQ 更新事件触发相关视图自动重新读取，支持心跳与断线重连 |

所有区域包含加载、空结果、失败和重试状态。快速切换筛选时会取消旧请求，并以请求版本阻止旧响应覆盖新数据。401 会清除会话并返回登录页；403、404、409 与 500 分别显示对应的恢复提示。

品类图表可切换平面与真实 WebGL 3D 视图：柱高表示销售额、柱体深度表示销量，柱旁显示品类名称，悬停显示两项完整数值。浏览器不支持 WebGL 时保留平面图表；Three.js 随前端本地提供，不依赖 CDN，许可见 `vendor/THREE-LICENSE.txt`。

## 本地运行

先启动 RabbitMQ 和现有 Spring Boot 服务（默认 `http://127.0.0.1:8080`），再在仓库根目录运行：

```powershell
node frontend/serve.mjs
```

打开 `http://127.0.0.1:4173`。`serve.mjs` 负责静态文件，并把 `/api/*` 与 `/ws/*` 原样转发到后端。

可用环境变量：

```powershell
$env:FRONTEND_PORT = "4173"
$env:API_ORIGIN = "http://127.0.0.1:8080"
node frontend/serve.mjs
```

生产部署时，将 `frontend/` 中的静态资源交给 Web 服务器，并把同源 `/api` 反向代理到 Spring Boot。不要把数据库连接信息或应用登录密码写入前端。

## 接口约定

- 统一响应：`{ code, message, data }`，成功 `code = 200`。
- 认证：`Authorization: Bearer <JWT>`；令牌只保存在 `sessionStorage`，关闭标签页后失效。
- 实时连接：先以 Bearer JWT 调用 `POST /api/realtime/tickets` 换取 30 秒内单次有效的票据，再连接 `GET /ws/updates?ticket=...`；长期 JWT 不放进 WebSocket URL。
- 公共筛选参数：`startTime`、`endTime`、重复键 `category`、重复键 `brand`。
- 订单列表额外参数：`keyWord`、重复键 `statuses`、`page`、`num`；当前界面只开放商品名称搜索。
- TOP10 使用 `metric=amount|quantity`；趋势使用 `granularity=day|week|month`。
- 人员写请求在响应提供 `csrfToken` 时附带 `X-CSRF-Token`，所有 fetch 携带 `credentials: include`，同时兼容当前 JWT 后端。
- B8 固定为 `LIFETIME`，只允许 SUPER_ADMIN 传单个 `brand`；B9 的 `range=ALL` 不传日期，`range=FILTERED` 沿用页面日期；ADMIN 调用 B8/B9 时都不发送客户端品牌参数。

前端会调用下列既定接口：

```text
POST /api/auth/login
GET  /api/auth/me
POST /api/realtime/tickets
GET  /ws/updates?ticket=...
GET  /api/categories
GET  /api/brands
GET  /api/orders/kpi
GET  /api/orders/stats
GET  /api/product/ranking
GET  /api/product/category-stats
GET  /api/user/customer-stats
GET  /api/orders/payment-stats
GET  /api/orders
GET  /api/admin/users
POST /api/admin/users
PATCH /api/admin/users/{userId}
GET  /api/brand/customer-segments
GET  /api/brand/products
GET  /api/brand/products/{productId}
GET  /api/brand/products/{productId}/reviews
GET  /api/brand/products/{productId}/buyers
```

经营首页只请求当前控制器实际暴露的接口，不使用演示数据伪装成功。超级管理员通过 A11 获得真实品牌多选；人员管理接口仅在用户管理区域按需请求，前端隐藏入口同样不替代后端鉴权。

### 实时更新链路

人员注册、新增管理员和编辑人员在数据库事务提交后发布应用事件；异步监听器将事件投递到 RabbitMQ topic exchange，每个应用实例使用独立临时队列消费并广播给本实例的 WebSocket 连接。消息只声明数据类型、动作和资源 ID，前端收到后仍通过原有鉴权接口读取数据。RabbitMQ 默认连接 `localhost:5672` 的 `guest/guest`，可用 `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD` 覆盖。

数据库若保留 `chk_role_user_type`，直接把已有普通用户与管理员角色互转可能由数据库拒绝；前端因此只提交真正变化的字段，并暂时锁定角色选择，避免把姓名、品牌或状态编辑误变成角色更新。

## 文件结构

```text
frontend/
├─ index.html              页面语义结构
├─ styles.css              全局视觉系统与通用布局
├─ styles/
│  ├─ product-operations.css 商品经营列表与分层布局
│  └─ product-dialog.css   商品详情全局窗口与响应式布局
├─ app.js                  状态、API 和页面交互
├─ product-charts.js       商品详情性别环图与新老客条形图
├─ category-3d.js          Three.js 三维品类可视化
├─ serve.mjs               无依赖静态服务与开发代理
├─ vendor/echarts.min.js   本地 ECharts 运行时
├─ vendor/three.module.js  本地 Three.js ES 模块
├─ vendor/THREE-LICENSE.txt Three.js MIT 许可
└─ README.md               运行、接口与验收说明
```

## 验收建议

1. 使用管理员账号登录，确认身份和品牌范围正确。
2. 切换近 7/30/90 天、品类和品牌，确认 KPI、四个图表与订单同步更新；品牌管理员应固定为所属品牌，超级管理员可多选 A11 返回的真实品牌。
3. 快速连续切换筛选，确认旧请求不会回写页面。
4. 切换趋势指标、聚合粒度、TOP10 指标和新老用户/支付方式维度；确认 TOP10 全部显示，只有后端 `isHot=true` 的商品使用朱砂标记。
5. 使用商品名称搜索订单，切换状态与页大小，翻页并打开当前订单摘要；输入纯数字应提示只支持商品名称。
6. 以超级管理员进入用户管理，验证 P1 查询、P2 新增、P3 编辑、版本冲突和受保护账号状态。
7. 断开后端或使用无权限账号，确认错误态、接口未实现与空状态不会混淆。
8. 在 1440px、1024px 和 390px 检查导航、筛选、图表、表格横向滚动、人员弹窗和键盘焦点。
9. 上下滚动经营概览、订单追溯和用户管理，确认侧栏当前导航自动跟随可视区域切换。
10. 打开商品经营区，检查 B8 生命周期分层；切换 B9 当前筛选/全量历史并打开居中商品详情窗口，确认性别环图、新老客条形图、评价筛选和购买用户 ID 查询。
11. 同时登录两个浏览器窗口，在一个窗口新增或编辑人员，确认另一个窗口显示“数据已自动同步”且人员列表更新；停止并恢复 RabbitMQ 或后端，确认 WebSocket 自动重连。

本次实现只新增 `frontend/` 静态前端和开发代理，没有修改 Spring Boot、数据库或后端配置。
