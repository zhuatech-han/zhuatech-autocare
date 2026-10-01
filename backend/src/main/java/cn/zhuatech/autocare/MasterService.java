// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 部门档案管理；库存数不接受客户端直接改写。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MasterService {
  final Store db;
  final AccessService access;

  public MasterService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 业务档案白名单输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String code,
      String name,
      String contact,
      String notes,
      String plate,
      String vin,
      String model,
      Long customerId,
      Long departmentId,
      Long odometer,
      String unit,
      BigDecimal price,
      BigDecimal reorderLevel,
      Boolean enabled) {}

  /** 分部门读取档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<?> list(String type) {
    access.require("master.read");
    return db.all(type(type)).stream().filter(x -> access.visible(department(x))).toList();
  }

  /** 新建、修改并保护部门归属和已引用车辆车主。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    access.require("master.write");
    db.lock(Department.class, 1L);
    Long dept = v.departmentId == null ? access.current().departmentId : v.departmentId;
    access.department(dept);
    db.get(Department.class, dept);
    Object item = id == null ? null : db.get(type(type), id);
    if (item != null) {
      access.department(department(item));
      if (!department(item).equals(dept)) throw new Problem(409, "IMMUTABLE_FIELD");
    }
    Object out;
    switch (type) {
      case "customers" -> {
        var a = id == null ? new Customer() : (Customer) item;
        a.code = AdminService.text(v.code, 60);
        a.name = AdminService.text(v.name, 120);
        a.contact = optional(v.contact, 200);
        a.notes = optional(v.notes, 1000);
        a.departmentId = dept;
        a.enabled = Boolean.TRUE.equals(v.enabled);
        out = id == null ? db.save(a) : a;
      }
      case "vehicles" -> {
        var a = id == null ? new Vehicle() : (Vehicle) item;
        var c = db.get(Customer.class, v.customerId);
        if (!c.departmentId.equals(dept) || !c.enabled) throw new Problem(400, "INVALID_MASTER");
        if (id != null
            && !a.customerId.equals(c.id)
            && !db.query(RepairJob.class, "from RepairJob where vehicleId=?1", id).isEmpty())
          throw new Problem(409, "IMMUTABLE_FIELD");
        a.plate = AdminService.text(v.plate, 60).toUpperCase(Locale.ROOT);
        a.vin = optional(v.vin, 60).toUpperCase(Locale.ROOT);
        a.model = AdminService.text(v.model, 120);
        a.customerId = c.id;
        a.departmentId = dept;
        a.enabled = Boolean.TRUE.equals(v.enabled);
        long odometer = mileage(v.odometer);
        if (id != null && odometer < a.odometer) throw new Problem(409, "ODOMETER_BACKWARD");
        a.odometer = odometer;
        out = id == null ? db.save(a) : a;
      }
      case "parts" -> {
        var a = id == null ? new Part() : (Part) item;
        String unit = AdminService.text(v.unit, 20);
        if (id != null && !a.unit.equals(unit)) throw new Problem(409, "IMMUTABLE_FIELD");
        a.code = AdminService.text(v.code, 60);
        a.name = AdminService.text(v.name, 120);
        a.unit = unit;
        a.price = WorkshopPolicy.money(v.price);
        a.reorderLevel = WorkshopPolicy.quantity(v.reorderLevel, false);
        a.departmentId = dept;
        a.enabled = Boolean.TRUE.equals(v.enabled);
        out = id == null ? db.save(a) : a;
      }
      case "labor" -> {
        var a = id == null ? new LaborItem() : (LaborItem) item;
        a.code = AdminService.text(v.code, 60);
        a.name = AdminService.text(v.name, 120);
        a.unit = AdminService.text(v.unit, 20);
        a.price = WorkshopPolicy.money(v.price);
        a.departmentId = dept;
        a.enabled = Boolean.TRUE.equals(v.enabled);
        out = id == null ? db.save(a) : a;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("MASTER_SAVE_" + type, id == null ? "NEW" : id, dept);
    return out;
  }

  /** 外键保护档案删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id) {
    access.require("master.write");
    db.lock(Department.class, 1L);
    var a = db.get(type(type), id);
    access.department(department(a));
    if (a instanceof Part p && p.quantity.signum() != 0) throw new Problem(409, "CONFLICT");
    if (a instanceof LaborItem || a instanceof Part) {
      String kind = a instanceof Part ? "PART" : "LABOR";
      if (!db.query(RepairLine.class, "from RepairLine where itemId=?1 and kind=?2", id, kind)
          .isEmpty()) throw new Problem(409, "CONFLICT");
    }
    access.audit("MASTER_DELETE_" + type, id, department(a));
    db.delete(a);
  }

  /** 只允许已实现的档案类别。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Class<?> type(String type) {
    return switch (type) {
      case "customers" -> Customer.class;
      case "vehicles" -> Vehicle.class;
      case "parts" -> Part.class;
      case "labor" -> LaborItem.class;
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 获取主档案部门。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static Long department(Object o) {
    if (o instanceof Customer a) return a.departmentId;
    if (o instanceof Vehicle a) return a.departmentId;
    if (o instanceof Part a) return a.departmentId;
    if (o instanceof LaborItem a) return a.departmentId;
    throw new Problem(400, "INVALID_INPUT");
  }

  /** 有限可选文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(String s, int max) {
    return s == null || s.isBlank() ? "" : AdminService.text(s, max);
  }

  /** 里程为非负整公里。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static long mileage(Long v) {
    if (v == null || v < 0 || v > 10000000) throw new Problem(400, "INVALID_ODOMETER");
    return v;
  }
}
