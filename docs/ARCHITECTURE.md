# 架构、状态与接口

知华科技 · 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

## 持久化与权限

19 张业务/管理表：department、access_role、permission、role_permission、nav_menu、account、audit_event、system_setting、dictionary_entry、customer、vehicle、part、labor_item、repair_job、estimate、repair_line、part_movement、payment_entry、mutation_stamp。另有 Flyway 历史表。主档案外键、唯一键、配件/维修数量检查和索引位于 V1__workshop_schema.sql。JPA ddl-auto=validate，不自动建表。

权限目录是注册功能，不允许凭空新增权限或页面。菜单仅调整名称、启停、顺序及注册权限；直接接口仍独立校验。部门范围 ALL / DEPARTMENT；只有全范围角色可拥有 admin。仅维修执行类技师只能读派给自己的工单，工单详情、车辆历史、首页与流水同步过滤。参考客户/车辆/配件档案按部门可见，不承诺技师只读一个客户的档案。

所有写事务锁定基础部门行序列化，库存、报价、资金和幂等标识一起提交；失败全部回滚。仅面向小型门店，尚未压力测试。所有全量资源读取上限10,000条，超限拒绝；在范围过滤前发现超限也拒绝，扩容需数据库分页/聚合及更细锁设计。

## 状态与计价

CHECKED_IN → QUOTED → AUTHORIZED → IN_PROGRESS → QUALITY → READY → DELIVERED。QUOTE_ACCEPT 也可以将 CHECKED_IN/QUOTED 转为 AUTHORIZED。开工前可作废到 CANCELLED；开工后没有自动撤销实物和款项的按钮。质检不通过回 IN_PROGRESS。

报价独立 DRAFT → PRESENTED → ACCEPTED / DECLINED。草稿可改/删；提交冻结。追加报价不会覆盖原已接受报价；未接受行不能执行或计费，DRAFT/PRESENTED 会阻止质检。车主确认是人工外部证据登记，不是电子签约。

工时完成按授权数量乘快照单价，配件完成按净领用数量乘快照单价，每行金额 HALF_UP 两位舍入。数量最多三位小数。配件行净领用不能超授权，领料可以分次；每次领料记录移动平均成本，最后全部出库带走剩余价值，避免零数量残值。退回必须引用同维修行的原 ISSUE，按原领料尚未退回的成本恢复，最后一份退回带回尾差。完成冻结净领用，不再允许领退；实际耗用须由技师核对。

待收 = 已完成工时/配件金额 − 优惠 − 净收款；收款不能超待收，优惠不能使净结算低于已收。冲销引用同工单未冲销的原收款，新增反向记录而不删除原凭据。交车仅 READY 且待收零。交车后没有退款或重开接口。

报表包含 READY/DELIVERED，按公司时区的接车日期筛选。收款不是交易平台支付确认；配件成本不包括工资/税费。零价项目允许，不能因此跳过授权/完成/质检/交车检查。

## HTTP API

同源 `/api`，JSON。登录前 GET `/auth/csrf` 得到 header/token；写请求使用该请求头并保留会话 Cookie。会话 HttpOnly/SameSite Strict，30分钟空闲过期；账号禁用、改密、撤权限实时校验。

| 接口 | 主要输入 / 返回 |
| --- | --- |
| POST /auth/login；GET /auth/me；POST /auth/logout | username/password；安全账号、菜单、权限、scope |
| POST /auth/password | oldPassword/newPassword；成功后会话失效 |
| GET /catalog | 按部门的档案与注册参考数据，技师名单不含密码 |
| GET /lists/{resource} | search/status/sort/desc/page/size；items/total/page/size |
| POST /master/{type}；PUT/DELETE /master/{type}/{id} | customers/vehicles/parts/labor；白名单档案字段 |
| POST /master/parts/import | 1–500 条配件 JSON，原子创建，不接受库存量 |
| POST /admin/{type}；PUT/DELETE /admin/{type}/{id} | users/roles/departments/menus/permissions/dictionaries/settings |
| POST /jobs；PUT /jobs/{id} | vehicleId/odometer/complaint/intakeNote；更新仅无报价的接车记录 |
| GET /jobs/{id} | job/estimates/lines/movements/payments/gross/authorized/due/quoteTotals |
| POST /jobs/{id}/estimates；PUT /estimates/{id} | note、lines[{kind:PART或LABOR,itemId,quantity,price}] |
| POST /estimates/{id}/submit、accept、decline、delete | accept/decline 必须 reference；delete 只允许草稿 |
| POST /jobs/{id}/assign、start、quality、review | technicianId；空对象；空对象；passed布尔值及note |
| POST /lines/{id}/issue、return、complete | quantity/reference/requestKey；另加sourceId；完成须note |
| POST /parts/{id}/receive、count | quantity/price/reference/requestKey；盘点加expected及expectedValue |
| POST /jobs/{id}/pay、reverse、discount、deliver、cancel | amount/reference/requestKey；sourceId/reference/note/requestKey；amount；reference；note |
| GET /vehicles/{id}/history | 当前权限可见的历史工单 |
| GET /dashboard | 状态计数、最近工单、配件预警 |
| GET /reports；GET /reports.csv | from/to 为 YYYY-MM-DD，可空；报表/下载 |

权限：job.write接车/报价/派工/作废；work开工/完成/送质检；stock.write收货/领退/盘点；quality质检；finance收款/优惠/交车；report报表与导出；master.write档案/导入；admin后台；audit审计。相应读取要求 job.read/master.read/stock.read。技师还需当前派工匹配。

API 数量和金额可传十进制字符串，避免浮点运算。领退料、收货盘点、收款冲销提供唯一 requestKey。事务仍允许该动作时同 key/同内容重试不重复过账；同 key 不同内容返回 KEY_REUSED。状态已经改变的请求会拒绝，客户端先查台账，不换标识重复登记。

错误 code：401 UNAUTHENTICATED/LOGIN_FAILED；403 FORBIDDEN/OUT_OF_SCOPE；400 INVALID_INPUT/INVALID_QUANTITY/INVALID_MONEY；409 INVALID_STATE/PENDING_AUTHORIZATION/INSUFFICIENT_STOCK/STALE_STOCK/EXCEEDS_QUANTITY/EXCEEDS_BALANCE/UNPAID/VEHICLE_BUSY/CONFLICT；413 RESOURCE_LIMIT。错误不返回 SQL、密码或内部堆栈。

不暴露密码哈希、不输出广告到业务 JSON/CSV/日志，不提供任意文件读写、URL 抓取或脚本执行接口。
