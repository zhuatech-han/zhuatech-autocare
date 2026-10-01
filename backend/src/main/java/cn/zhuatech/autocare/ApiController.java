// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 业务与管理 HTTP 接口，所有读取写入均验证权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final Store db;
  final AccessService access;
  final MasterService masters;
  final AdminService admin;
  final WorkshopService work;

  public ApiController(
      Store db,
      AccessService access,
      MasterService masters,
      AdminService admin,
      WorkshopService work) {
    this.db = db;
    this.access = access;
    this.masters = masters;
    this.admin = admin;
    this.work = work;
  }

  /** 参考数据按部门返回，技师联系人只提供安全字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/catalog")
  @Transactional(readOnly = true)
  public Map<String, Object> catalog() {
    access.current();
    Map<String, Object> out = new HashMap<>();
    out.put("settings", db.all(SystemSetting.class));
    out.put("dictionaries", db.all(DictionaryEntry.class));
    out.put(
        "departments",
        db.all(Department.class).stream().filter(x -> access.visible(x.id)).toList());
    if (access.role().permissions.contains("master.read"))
      for (var type : List.of("customers", "vehicles", "parts", "labor"))
        out.put(type, masters.list(type));
    if (access.role().permissions.contains("job.read"))
      out.put(
          "technicians",
          db.all(Account.class).stream()
              .filter(
                  a ->
                      a.enabled
                          && access.visible(a.departmentId)
                          && db.get(AccessRole.class, a.roleId).permissions.contains("work"))
              .map(
                  a ->
                      Map.of(
                          "id", a.id, "displayName", a.displayName, "departmentId", a.departmentId))
              .toList());
    if (access.role().permissions.contains("admin")) {
      out.put("roles", db.all(AccessRole.class));
      out.put("permissions", db.all(Permission.class));
    }
    return out;
  }

  /** 搜索、状态过滤、排序和分页；超过 10,000 条明确拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/lists/{resource}")
  @Transactional(readOnly = true)
  public Map<String, Object> list(
      @PathVariable String resource,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "id") String sort,
      @RequestParam(defaultValue = "true") boolean desc,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0
        || size < 1
        || size > 100
        || search.length() > 200
        || !Set.of("id", "name", "code", "number", "plate", "createdAt").contains(sort))
      throw new Problem(400, "INVALID_PAGE");
    var q = search.toLowerCase(Locale.ROOT);
    var rows =
        rows(resource).stream()
            .filter(r -> searchText(r).toLowerCase(Locale.ROOT).contains(q))
            .filter(
                r ->
                    status.isBlank()
                        || status.equals(field(r, "status"))
                        || status.equals(field(r, "kind")))
            .sorted(
                (a, b) -> {
                  int n =
                      sort.equals("id")
                          ? Long.compare(
                              ((Number) field(a, "id")).longValue(),
                              ((Number) field(b, "id")).longValue())
                          : String.valueOf(field(a, sort))
                              .compareTo(String.valueOf(field(b, sort)));
                  return desc ? -n : n;
                })
            .toList();
    return Map.of(
        "items",
        rows.stream().skip((long) page * size).limit(size).toList(),
        "total",
        rows.size(),
        "page",
        page,
        "size",
        size);
  }

  /** 主档案新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/{type}")
  public Object createMaster(@PathVariable String type, @RequestBody MasterService.Input v) {
    return masters.save(type, null, v);
  }

  /** 主档案修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/master/{type}/{id}")
  public Object updateMaster(
      @PathVariable String type, @PathVariable Long id, @RequestBody MasterService.Input v) {
    return masters.save(type, id, v);
  }

  /** 档案引用保护删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/master/{type}/{id}")
  public Object deleteMaster(@PathVariable String type, @PathVariable Long id) {
    masters.delete(type, id);
    return Map.of("ok", true);
  }

  /** 原子 JSON 配件导入，任一错误回滚整个批次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/master/parts/import")
  @Transactional
  public Object importParts(@RequestBody List<MasterService.Input> v) {
    access.require("master.write");
    if (v == null || v.isEmpty() || v.size() > 500) throw new Problem(400, "INVALID_INPUT");
    for (var input : v) masters.save("parts", null, input);
    return Map.of("created", v.size());
  }

  /** 管理资源新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object createAdmin(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 管理资源编辑。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object updateAdmin(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 管理资源受保护删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object deleteAdmin(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }

  /** 接车新工单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs")
  public Object intake(@RequestBody WorkshopService.Intake v) {
    return work.intake(v);
  }

  /** 报价前接车记录修改。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/jobs/{id}")
  public Object intakeEdit(@PathVariable Long id, @RequestBody WorkshopService.Intake v) {
    return work.editIntake(id, v);
  }

  /** 工单详情含快照、流水与金额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/jobs/{id}")
  public Object detail(@PathVariable Long id) {
    return work.detail(id);
  }

  /** 维修生命周期及款项操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs/{id}/{action}")
  public Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    return work.act(id, action, v);
  }

  /** 工单建立独立报价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/jobs/{id}/estimates")
  public Object quote(@PathVariable Long id, @RequestBody WorkshopService.QuoteInput v) {
    return work.quote(id, null, v);
  }

  /** 未提交报价修改，不改变已接受报价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/estimates/{id}")
  @Transactional
  public Object editQuote(@PathVariable Long id, @RequestBody WorkshopService.QuoteInput v) {
    var e = db.get(Estimate.class, id);
    return work.quote(e.jobId, id, v);
  }

  /** 提交、人工确认、拒绝或删除未提交报价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/estimates/{id}/{action}")
  public Object estimateAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    return work.estimateAction(id, action, v);
  }

  /** 工单配件领退和维修项完成。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/lines/{id}/{action}")
  public Object lineAction(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    return work.lineAction(id, action, v);
  }

  /** 库存收货和盘点。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/parts/{id}/{action}")
  public Object stock(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> v) {
    return work.stock(id, action, v);
  }

  /** 车辆维修历史同样过滤可见工单，不向技师泄露其他派工。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/vehicles/{id}/history")
  @Transactional(readOnly = true)
  public Object history(@PathVariable Long id) {
    access.require("job.read");
    var v = db.get(Vehicle.class, id);
    access.department(v.departmentId);
    return db
        .query(RepairJob.class, "from RepairJob where vehicleId=?1 order by id desc", id)
        .stream()
        .filter(work::visible)
        .toList();
  }

  /** 工作台实际状态数量，非虚构营销数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  @Transactional(readOnly = true)
  public Object dashboard() {
    access.require("dashboard");
    var jobs = access.role().permissions.contains("job.read") ? jobs() : List.<RepairJob>of();
    var counts = jobs.stream().collect(Collectors.groupingBy(j -> j.status, Collectors.counting()));
    var recent =
        jobs.stream()
            .sorted(Comparator.comparing((RepairJob j) -> j.id).reversed())
            .limit(8)
            .toList();
    long low =
        access.role().permissions.contains("stock.read")
            ? db.all(Part.class).stream()
                .filter(
                    p ->
                        access.visible(p.departmentId)
                            && p.enabled
                            && p.quantity.compareTo(p.reorderLevel) <= 0)
                .count()
            : 0;
    return Map.of("counts", counts, "recent", recent, "lowStock", low);
  }

  /** 日期范围只统计已质检工单，净金额、实收和配件成本分别报告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/reports")
  @Transactional(readOnly = true)
  public Map<String, Object> reports(
      @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to) {
    access.require("report");
    java.time.LocalDate start =
        from.isBlank() ? java.time.LocalDate.of(1970, 1, 1) : java.time.LocalDate.parse(from);
    java.time.LocalDate end =
        to.isBlank() ? java.time.LocalDate.of(9999, 12, 31) : java.time.LocalDate.parse(to);
    if (end.isBefore(start)) throw new Problem(400, "INVALID_INPUT");
    var zone = java.time.ZoneId.of(work.setting("timezone"));
    var jobs =
        jobs().stream()
            .filter(j -> Set.of("READY", "DELIVERED").contains(j.status))
            .filter(
                j -> {
                  var day = j.createdAt.atZone(zone).toLocalDate();
                  return !day.isBefore(start) && !day.isAfter(end);
                })
            .toList();
    var items =
        jobs.stream()
            .map(
                j ->
                    Map.<String, Object>of(
                        "id",
                        j.id,
                        "number",
                        j.number,
                        "plate",
                        j.plate,
                        "customerName",
                        j.customerName,
                        "status",
                        j.status,
                        "amount",
                        work.gross(j.id).subtract(j.discount),
                        "paid",
                        j.netPaid,
                        "due",
                        work.due(j),
                        "partsCost",
                        work.partsCost(j.id)))
            .toList();
    BigDecimal amount = sum(items, "amount"),
        paid = sum(items, "paid"),
        cost = sum(items, "partsCost");
    return Map.of(
        "items",
        items,
        "amount",
        amount,
        "paid",
        paid,
        "due",
        amount.subtract(paid),
        "partsCost",
        cost,
        "contribution",
        amount.subtract(cost),
        "from",
        from,
        "to",
        to);
  }

  /** 报告 CSV 防公式注入，不含推广文案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping(value = "/reports.csv", produces = "text/csv")
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> csv(
      @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to) {
    var report = reports(from, to);
    var b = new StringBuilder("\uFEFFnumber,plate,customer,status,amount,paid,due,parts_cost\r\n");
    for (Object row : (List<?>) report.get("items")) {
      var m = (Map<?, ?>) row;
      b.append(
              List.of(
                      "number",
                      "plate",
                      "customerName",
                      "status",
                      "amount",
                      "paid",
                      "due",
                      "partsCost")
                  .stream()
                  .map(k -> WorkshopPolicy.csv(m.get(k)))
                  .collect(Collectors.joining(",")))
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=workshop.csv")
        .body(b.toString().getBytes(StandardCharsets.UTF_8));
  }

  private List<RepairJob> jobs() {
    return db.all(RepairJob.class).stream().filter(work::visible).toList();
  }

  private BigDecimal sum(List<Map<String, Object>> rows, String key) {
    return rows.stream().map(m -> (BigDecimal) m.get(key)).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private List<?> rows(String r) {
    if (r.equals("jobs")) {
      access.require("job.read");
      return jobs();
    }
    if (r.equals("parts")) {
      access.require("stock.read");
      return db.all(Part.class).stream().filter(p -> access.visible(p.departmentId)).toList();
    }
    if (r.equals("movements")) {
      access.require("stock.read");
      var allowed =
          access.role().permissions.contains("job.read")
              ? new HashSet<>(jobs().stream().map(j -> j.id).toList())
              : Set.<Long>of();
      return db.all(PartMovement.class).stream()
          .filter(
              m -> access.visible(m.departmentId) && (m.jobId == null || allowed.contains(m.jobId)))
          .toList();
    }
    if (r.equals("audit")) {
      access.require("audit");
      return db.all(AuditEvent.class).stream().filter(a -> access.visible(a.departmentId)).toList();
    }
    if (Set.of("customers", "vehicles", "labor").contains(r)) return masters.list(r);
    return admin.list(r);
  }

  private Object field(Object o, String f) {
    try {
      return o.getClass().getField(f).get(o);
    } catch (ReflectiveOperationException e) {
      return "";
    }
  }

  private String searchText(Object o) {
    return List.of(
            "id",
            "number",
            "name",
            "code",
            "plate",
            "model",
            "customerName",
            "contact",
            "username",
            "displayName",
            "actor",
            "action",
            "objectId",
            "reference",
            "note",
            "createdAt",
            "nameEn")
        .stream()
        .map(f -> String.valueOf(field(o, f)))
        .collect(Collectors.joining(" "));
  }
}
