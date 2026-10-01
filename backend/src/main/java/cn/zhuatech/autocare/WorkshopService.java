// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import java.math.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 车辆接车、独立授权、配件库存、质检、收款交车的原子事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class WorkshopService {
  final Store db;
  final AccessService access;
  final Clock clock;

  public WorkshopService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 接车输入不接受状态、报价或收款字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Intake(Long vehicleId, Long odometer, String complaint, String intakeNote) {}

  /** 报价行输入以档案 ID 为准，名称单位由服务端快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record LineInput(String kind, Long itemId, BigDecimal quantity, BigDecimal price) {}

  /** 新报价与追加报价采用相同的独立授权结构。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record QuoteInput(List<LineInput> lines, String note) {}

  /** 读取工单先验证部门；仅维修执行权限的技师仅能读派给自己的工单。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public RepairJob job(Long id) {
    access.require("job.read");
    var j = db.get(RepairJob.class, id);
    access.department(j.departmentId);
    if (!visible(j)) throw new Problem(403, "OUT_OF_SCOPE");
    return j;
  }

  /** 技师只能访问已派工记录，其他业务岗位按部门范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(RepairJob j) {
    var p = access.role().permissions;
    boolean onlyWork =
        p.contains("work")
            && !p.contains("job.write")
            && !p.contains("quality")
            && !p.contains("finance")
            && !p.contains("stock.write")
            && !p.contains("admin");
    return access.visible(j.departmentId)
        && (!onlyWork || Objects.equals(j.technicianId, access.current().id));
  }

  /** 新接车防重复在修、防里程倒退；保存车主和车辆快照。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public RepairJob intake(Intake v) {
    access.require("job.write");
    serial();
    var vehicle = db.get(Vehicle.class, v.vehicleId);
    var customer = db.get(Customer.class, vehicle.customerId);
    access.department(vehicle.departmentId);
    if (!vehicle.enabled || !customer.enabled) throw new Problem(400, "INVALID_MASTER");
    if (db.query(RepairJob.class, "from RepairJob where vehicleId=?1", vehicle.id).stream()
        .anyMatch(j -> !Set.of("DELIVERED", "CANCELLED").contains(j.status)))
      throw new Problem(409, "VEHICLE_BUSY");
    long mileage = MasterService.mileage(v.odometer);
    if (mileage < vehicle.odometer) throw new Problem(409, "ODOMETER_BACKWARD");
    vehicle.odometer = mileage;
    var j = new RepairJob();
    j.number = "JOB-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    j.vehicleId = vehicle.id;
    j.customerId = customer.id;
    j.plate = vehicle.plate;
    j.model = vehicle.model;
    j.customerName = customer.name;
    j.departmentId = vehicle.departmentId;
    j.odometer = mileage;
    j.complaint = AdminService.text(v.complaint, 1000);
    j.intakeNote = MasterService.optional(v.intakeNote, 1000);
    j.status = "CHECKED_IN";
    j.currency = setting("currency");
    j.createdAt = clock.instant();
    j.createdBy = access.current().username;
    db.save(j);
    access.audit("INTAKE", j.id, j.departmentId);
    return j;
  }

  /** 接车草稿可修改，不回写车辆里程较新的记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public RepairJob editIntake(Long id, Intake v) {
    access.require("job.write");
    serial();
    var j = job(id);
    state(j, "CHECKED_IN");
    if (!j.vehicleId.equals(v.vehicleId) || !estimates(j.id).isEmpty())
      throw new Problem(409, "IMMUTABLE_FIELD");
    long n = MasterService.mileage(v.odometer);
    var vehicle = db.get(Vehicle.class, j.vehicleId);
    if (n < j.odometer || n < vehicle.odometer) throw new Problem(409, "ODOMETER_BACKWARD");
    j.odometer = n;
    vehicle.odometer = n;
    j.complaint = AdminService.text(v.complaint, 1000);
    j.intakeNote = MasterService.optional(v.intakeNote, 1000);
    access.audit("INTAKE_EDIT", j.id, j.departmentId);
    return j;
  }

  /** 报价草稿保持追加独立，不改写已接受的价格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Estimate quote(Long jobId, Long estimateId, QuoteInput v) {
    access.require("job.write");
    serial();
    var j = job(jobId);
    state(j, "CHECKED_IN", "QUOTED", "AUTHORIZED", "IN_PROGRESS");
    var e = estimateId == null ? new Estimate() : db.get(Estimate.class, estimateId);
    if (estimateId != null) {
      if (!e.jobId.equals(j.id) || !e.status.equals("DRAFT"))
        throw new Problem(409, "INVALID_STATE");
      for (var line : lines(e.id)) db.delete(line);
    }
    if (v.lines == null || v.lines.isEmpty() || v.lines.size() > 100)
      throw new Problem(400, "INVALID_INPUT");
    if (estimateId == null) {
      e.jobId = j.id;
      e.number =
          j.number
              + "-Q"
              + (estimates(j.id).size() + 1)
              + "-"
              + UUID.randomUUID().toString().substring(0, 4);
      e.status = "DRAFT";
      e.createdAt = clock.instant();
      db.save(e);
    }
    e.note = MasterService.optional(v.note, 1000);
    var keys = new HashSet<String>();
    for (var input : v.lines) {
      if (input.kind == null
          || !Set.of("LABOR", "PART").contains(input.kind)
          || !keys.add(input.kind + ":" + input.itemId)) throw new Problem(400, "INVALID_INPUT");
      var l = new RepairLine();
      l.estimateId = e.id;
      l.jobId = j.id;
      l.kind = input.kind;
      l.itemId = input.itemId;
      l.quantity = WorkshopPolicy.quantity(input.quantity, true);
      l.price = WorkshopPolicy.money(input.price);
      if (input.kind.equals("PART")) {
        var p = db.get(Part.class, input.itemId);
        valid(p.departmentId, p.enabled, j);
        l.name = p.name;
        l.unit = p.unit;
      } else {
        var p = db.get(LaborItem.class, input.itemId);
        valid(p.departmentId, p.enabled, j);
        l.name = p.name;
        l.unit = p.unit;
      }
      db.save(l);
    }
    access.audit("QUOTE_DRAFT", e.id, j.departmentId);
    return e;
  }

  /** 提交后冻结报价，车主同意必须有外部确认凭据；拒绝不计费。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Boolean> estimateAction(Long id, String action, Map<String, Object> v) {
    access.require("job.write");
    serial();
    var e = db.get(Estimate.class, id);
    var j = job(e.jobId);
    state(j, "CHECKED_IN", "QUOTED", "AUTHORIZED", "IN_PROGRESS");
    switch (action) {
      case "submit" -> {
        if (!e.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
        e.status = "PRESENTED";
        if (j.status.equals("CHECKED_IN")) j.status = "QUOTED";
      }
      case "accept" -> {
        if (!e.status.equals("PRESENTED")) throw new Problem(409, "INVALID_STATE");
        e.approvalReference = text(v, "reference", 200);
        e.approvedAt = clock.instant();
        e.status = "ACCEPTED";
        if (Set.of("CHECKED_IN", "QUOTED").contains(j.status)) j.status = "AUTHORIZED";
      }
      case "decline" -> {
        if (!e.status.equals("PRESENTED")) throw new Problem(409, "INVALID_STATE");
        e.approvalReference = text(v, "reference", 200);
        e.status = "DECLINED";
      }
      case "delete" -> {
        if (!e.status.equals("DRAFT")) throw new Problem(409, "INVALID_STATE");
        for (var l : lines(e.id)) db.delete(l);
        db.delete(e);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("QUOTE_" + action, id, j.departmentId);
    return Map.of("ok", true);
  }

  /** 生命周期、人工质检与金融动作均再次验证状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> act(Long id, String action, Map<String, Object> v) {
    String permission =
        switch (action) {
          case "assign", "cancel" -> "job.write";
          case "start", "quality" -> "work";
          case "review" -> "quality";
          case "pay", "reverse", "discount", "deliver" -> "finance";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    access.require(permission);
    serial();
    var j = job(id);
    switch (action) {
      case "assign" -> {
        state(j, "AUTHORIZED", "IN_PROGRESS");
        var a = db.get(Account.class, number(v, "technicianId"));
        if (!a.enabled
            || !a.departmentId.equals(j.departmentId)
            || !db.get(AccessRole.class, a.roleId).permissions.contains("work"))
          throw new Problem(400, "INVALID_TECHNICIAN");
        j.technicianId = a.id;
      }
      case "start" -> {
        state(j, "AUTHORIZED");
        worker(j);
        j.status = "IN_PROGRESS";
      }
      case "quality" -> {
        state(j, "IN_PROGRESS");
        worker(j);
        if (estimates(j.id).stream().anyMatch(e -> Set.of("DRAFT", "PRESENTED").contains(e.status)))
          throw new Problem(409, "PENDING_AUTHORIZATION");
        var lines = approvedLines(j.id);
        if (lines.isEmpty() || lines.stream().anyMatch(l -> !l.completed))
          throw new Problem(409, "WORK_INCOMPLETE");
        j.status = "QUALITY";
      }
      case "review" -> {
        state(j, "QUALITY");
        if (!(v.get("passed") instanceof Boolean)) throw new Problem(400, "INVALID_INPUT");
        j.qualityNote = text(v, "note", 1000);
        if (Boolean.TRUE.equals(v.get("passed"))) j.status = "READY";
        else j.status = "IN_PROGRESS";
      }
      case "discount" -> {
        state(j, "READY");
        var discount = WorkshopPolicy.money(decimal(v, "amount"));
        if (discount.compareTo(gross(j.id)) > 0
            || gross(j.id).subtract(discount).compareTo(j.netPaid) < 0)
          throw new Problem(409, "EXCEEDS_BALANCE");
        j.discount = discount;
      }
      case "pay" -> {
        state(j, "READY");
        if (!stamp("pay:" + id, v)) break;
        var amount = WorkshopPolicy.money(decimal(v, "amount"));
        if (amount.signum() <= 0 || amount.compareTo(due(j)) > 0)
          throw new Problem(409, "EXCEEDS_BALANCE");
        var p = new PaymentEntry();
        p.jobId = j.id;
        p.amount = amount;
        p.reference = text(v, "reference", 120);
        p.note = optional(v, "note", 500);
        p.createdAt = clock.instant();
        p.createdBy = access.current().username;
        db.save(p);
        j.netPaid = j.netPaid.add(amount);
      }
      case "reverse" -> {
        state(j, "READY");
        if (!stamp("reverse:" + id, v)) break;
        var source = db.get(PaymentEntry.class, number(v, "sourceId"));
        if (!source.jobId.equals(j.id)
            || source.reversalOf != null
            || !db.query(PaymentEntry.class, "from PaymentEntry where reversalOf=?1", source.id)
                .isEmpty()) throw new Problem(409, "INVALID_SOURCE");
        var p = new PaymentEntry();
        p.jobId = j.id;
        p.reversalOf = source.id;
        p.amount = source.amount;
        p.reference = text(v, "reference", 120);
        p.note = text(v, "note", 500);
        p.createdAt = clock.instant();
        p.createdBy = access.current().username;
        db.save(p);
        j.netPaid = j.netPaid.subtract(source.amount);
      }
      case "deliver" -> {
        state(j, "READY");
        if (due(j).signum() != 0) throw new Problem(409, "UNPAID");
        j.handoverReference = text(v, "reference", 200);
        j.status = "DELIVERED";
      }
      case "cancel" -> {
        state(j, "CHECKED_IN", "QUOTED", "AUTHORIZED");
        j.intakeNote = j.intakeNote + "\n" + text(v, "note", 500);
        if (j.intakeNote.length() > 1000) throw new Problem(400, "INVALID_INPUT");
        j.status = "CANCELLED";
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("JOB_" + action, j.id, j.departmentId);
    return detail(j.id);
  }

  /** 领退限于已授权配件；退回引用原领料并恢复原成本，完成后不得改耗用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> lineAction(Long id, String action, Map<String, Object> v) {
    access.require(action.equals("complete") ? "work" : "stock.write");
    serial();
    var l = db.get(RepairLine.class, id);
    var j = job(l.jobId);
    state(j, "IN_PROGRESS");
    if (!db.get(Estimate.class, l.estimateId).status.equals("ACCEPTED") || l.completed)
      throw new Problem(409, "INVALID_STATE");
    switch (action) {
      case "complete" -> {
        worker(j);
        l.workNote = text(v, "note", 500);
        l.completed = true;
      }
      case "issue" -> {
        if (!l.kind.equals("PART")) throw new Problem(400, "INVALID_INPUT");
        if (!stamp("issue:" + id, v)) break;
        var qty = WorkshopPolicy.quantity(decimal(v, "quantity"), true);
        if (l.issued.subtract(l.returned).add(qty).compareTo(l.quantity) > 0)
          throw new Problem(409, "EXCEEDS_QUANTITY");
        var p = db.get(Part.class, l.itemId);
        var cost = WorkshopPolicy.cost(p.quantity, p.inventoryValue, qty);
        p.quantity = p.quantity.subtract(qty);
        p.inventoryValue = p.inventoryValue.subtract(cost);
        l.issued = l.issued.add(qty);
        movement(p, j, l, null, "ISSUE", qty, cost, v);
      }
      case "return" -> {
        if (!l.kind.equals("PART")) throw new Problem(400, "INVALID_INPUT");
        if (!stamp("return:" + id, v)) break;
        var source = db.get(PartMovement.class, number(v, "sourceId"));
        if (!Objects.equals(source.lineId, l.id) || !source.kind.equals("ISSUE"))
          throw new Problem(409, "INVALID_SOURCE");
        var returns =
            db.query(PartMovement.class, "from PartMovement where sourceId=?1", source.id);
        var done = returns.stream().map(m -> m.quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        var costDone =
            returns.stream().map(m -> m.inventoryValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var qty = WorkshopPolicy.quantity(decimal(v, "quantity"), true);
        var available = source.quantity.subtract(done);
        var cost = WorkshopPolicy.cost(available, source.inventoryValue.subtract(costDone), qty);
        var p = db.get(Part.class, l.itemId);
        p.quantity = p.quantity.add(qty);
        p.inventoryValue = p.inventoryValue.add(cost);
        l.returned = l.returned.add(qty);
        movement(p, j, l, source.id, "RETURN", qty, cost, v);
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("LINE_" + action, l.id, j.departmentId);
    return detail(j.id);
  }

  /** 收货和盘点显式凭证、移动平均成本及账面快照保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Part stock(Long id, String action, Map<String, Object> v) {
    access.require("stock.write");
    serial();
    var p = db.get(Part.class, id);
    access.department(p.departmentId);
    if (!Set.of("receive", "count").contains(action)) throw new Problem(404, "NOT_FOUND");
    if (!stamp("stock:" + action + ":" + id, v)) return p;
    BigDecimal qty, cost;
    if (action.equals("receive")) {
      if (!p.enabled) throw new Problem(400, "INVALID_MASTER");
      qty = WorkshopPolicy.quantity(decimal(v, "quantity"), true);
      cost =
          qty.multiply(WorkshopPolicy.money(decimal(v, "price"))).setScale(2, RoundingMode.HALF_UP);
      p.quantity = p.quantity.add(qty);
      p.inventoryValue = p.inventoryValue.add(cost);
    } else {
      var counted = WorkshopPolicy.quantity(decimal(v, "quantity"), false);
      var expected = WorkshopPolicy.quantity(decimal(v, "expected"), false);
      var expectedValue = WorkshopPolicy.money(decimal(v, "expectedValue"));
      if (p.quantity.compareTo(expected) != 0 || p.inventoryValue.compareTo(expectedValue) != 0)
        throw new Problem(409, "STALE_STOCK");
      qty = counted.subtract(p.quantity);
      if (qty.signum() == 0) throw new Problem(400, "NO_CHANGE");
      cost =
          qty.signum() < 0
              ? WorkshopPolicy.cost(p.quantity, p.inventoryValue, qty.negate()).negate()
              : qty.multiply(WorkshopPolicy.money(decimal(v, "price")))
                  .setScale(2, RoundingMode.HALF_UP);
      p.quantity = counted;
      p.inventoryValue = p.inventoryValue.add(cost);
    }
    if (p.quantity.compareTo(new BigDecimal("1000000000")) > 0)
      throw new Problem(409, "RESOURCE_LIMIT");
    movement(p, null, null, null, action.equals("receive") ? "RECEIVE" : "COUNT", qty, cost, v);
    access.audit("STOCK_" + action, p.id, p.departmentId);
    return p;
  }

  /** 详情包含冻结报价、不可变资金配件流水和实际待收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    var j = job(id);
    var quoteTotals = new HashMap<Long, BigDecimal>();
    for (var estimate : estimates(id))
      quoteTotals.put(
          estimate.id,
          lines(estimate.id).stream()
              .map(l -> charge(l, l.quantity))
              .reduce(BigDecimal.ZERO, BigDecimal::add));
    return Map.of(
        "job",
        j,
        "estimates",
        estimates(id),
        "lines",
        db.query(RepairLine.class, "from RepairLine where jobId=?1 order by id", id),
        "movements",
        db.query(PartMovement.class, "from PartMovement where jobId=?1 order by id", id),
        "payments",
        db.query(PaymentEntry.class, "from PaymentEntry where jobId=?1 order by id", id),
        "gross",
        gross(id),
        "authorized",
        authorized(id),
        "due",
        due(j),
        "quoteTotals",
        quoteTotals);
  }

  /** 报价中授权量为价格上限；工时完成按授权量、配件完成按净领用计费。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal gross(Long id) {
    return approvedLines(id).stream()
        .filter(l -> l.completed)
        .map(l -> charge(l, l.kind.equals("PART") ? l.issued.subtract(l.returned) : l.quantity))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 累计车主接受的报价上限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal authorized(Long id) {
    return approvedLines(id).stream()
        .map(l -> charge(l, l.quantity))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 待收金额不表示线上支付状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal due(RepairJob j) {
    return gross(j.id).subtract(j.discount).subtract(j.netPaid);
  }

  /** 已用配件原领料成本减退回成本；工时工资与税费不包含。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public BigDecimal partsCost(Long id) {
    return db.query(PartMovement.class, "from PartMovement where jobId=?1", id).stream()
        .map(m -> m.kind.equals("RETURN") ? m.inventoryValue.negate() : m.inventoryValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** 参数通过管理端保存，不接收任意脚本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }

  private List<Estimate> estimates(Long id) {
    return db.query(Estimate.class, "from Estimate where jobId=?1 order by id", id);
  }

  private List<RepairLine> lines(Long id) {
    return db.query(RepairLine.class, "from RepairLine where estimateId=?1 order by id", id);
  }

  private List<RepairLine> approvedLines(Long id) {
    var accepted =
        new HashSet<>(
            estimates(id).stream()
                .filter(e -> e.status.equals("ACCEPTED"))
                .map(e -> e.id)
                .toList());
    return db.query(RepairLine.class, "from RepairLine where jobId=?1", id).stream()
        .filter(l -> accepted.contains(l.estimateId))
        .toList();
  }

  private BigDecimal charge(RepairLine l, BigDecimal qty) {
    return qty.multiply(l.price).setScale(2, RoundingMode.HALF_UP);
  }

  private void serial() {
    db.lock(Department.class, 1L);
  }

  private void valid(Long dept, boolean enabled, RepairJob j) {
    if (!dept.equals(j.departmentId) || !enabled) throw new Problem(400, "INVALID_MASTER");
  }

  private void state(RepairJob j, String... allowed) {
    if (!Set.of(allowed).contains(j.status)) throw new Problem(409, "INVALID_STATE");
  }

  /** 核对启用技师和当前派工，管理员可代操作验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void worker(RepairJob j) {
    if (j.technicianId == null) throw new Problem(409, "UNASSIGNED");
    var a = db.get(Account.class, j.technicianId);
    if (!a.enabled
        || !db.get(AccessRole.class, a.roleId).permissions.contains("work")
        || !a.departmentId.equals(j.departmentId)) throw new Problem(409, "INVALID_TECHNICIAN");
    if (!access.role().permissions.contains("admin")
        && !Objects.equals(j.technicianId, access.current().id))
      throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 固化实物变动、原凭证和成本，不保存可改写客户载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void movement(
      Part p,
      RepairJob j,
      RepairLine l,
      Long source,
      String kind,
      BigDecimal qty,
      BigDecimal cost,
      Map<String, Object> v) {
    var m = new PartMovement();
    m.partId = p.id;
    m.departmentId = p.departmentId;
    m.jobId = j == null ? null : j.id;
    m.lineId = l == null ? null : l.id;
    m.sourceId = source;
    m.kind = kind;
    m.quantity = qty;
    m.inventoryValue = cost;
    m.reference = text(v, "reference", 120);
    m.note = optional(v, "note", 500);
    m.createdAt = clock.instant();
    m.createdBy = access.current().username;
    db.save(m);
  }

  private String text(Map<String, Object> v, String key, int max) {
    Object x = v.get(key);
    if (!(x instanceof String s)) throw new Problem(400, "INVALID_INPUT");
    return AdminService.text(s, max);
  }

  private String optional(Map<String, Object> v, String key, int max) {
    Object x = v.get(key);
    if (x == null) return "";
    if (!(x instanceof String s)) throw new Problem(400, "INVALID_INPUT");
    return MasterService.optional(s, max);
  }

  private BigDecimal decimal(Map<String, Object> v, String key) {
    Object x = v.get(key);
    if (x == null) throw new Problem(400, "INVALID_INPUT");
    return new BigDecimal(x.toString());
  }

  private Long number(Map<String, Object> v, String key) {
    Object x = v.get(key);
    if (x == null) throw new Problem(400, "INVALID_INPUT");
    return Long.valueOf(x.toString());
  }

  /** 幂等标识与业务同事务提交；重复内容不重复过账，内容不同则拒绝。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private boolean stamp(String resource, Map<String, Object> v) {
    var key = text(v, "requestKey", 80);
    String fingerprint;
    try {
      fingerprint =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          tools.jackson.databind.json.JsonMapper.builder()
                              .build()
                              .writeValueAsBytes(new TreeMap<>(v))));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    var previous =
        db.query(
            MutationStamp.class,
            "from MutationStamp where resource=?1 and requestKey=?2",
            resource,
            key);
    if (!previous.isEmpty()) {
      if (!previous.getFirst().fingerprint.equals(fingerprint))
        throw new Problem(409, "KEY_REUSED");
      return false;
    }
    var s = new MutationStamp();
    s.resource = resource;
    s.requestKey = key;
    s.fingerprint = fingerprint;
    db.save(s);
    return true;
  }
}
