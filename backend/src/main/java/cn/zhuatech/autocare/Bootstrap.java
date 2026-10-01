// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import java.math.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 首次空库初始化岗位和可选虚构学习档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;
  final boolean demo;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${autocare.admin-username}") String username,
      @Value("${autocare.admin-password}") String password,
      @Value("${autocare.seed-demo}") boolean demo) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
    this.demo = demo;
  }

  /** 管理员必须使用独立强密码，重启不覆盖。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_USERNAME");
    var d = new Department();
    d.name = "主门店 / Main workshop";
    db.save(d);
    String[][] ps = {
      {"dashboard", "工作台 / Overview"},
      {"master.read", "查看档案 / View masters"},
      {"master.write", "维护档案 / Edit masters"},
      {"job.read", "查看工单 / View jobs"},
      {"job.write", "接车报价与派工 / Service desk"},
      {"work", "维修执行 / Repair work"},
      {"stock.read", "库存查询 / Stock view"},
      {"stock.write", "收货领退与盘点 / Stock operations"},
      {"quality", "质量验收 / Quality review"},
      {"finance", "收款与交车 / Payments and handover"},
      {"report", "报表导出 / Reports"},
      {"audit", "操作审计 / Audit"},
      {"admin", "系统管理 / Administration"}
    };
    Set<String> all = new HashSet<>();
    for (var p : ps) {
      var e = new Permission();
      e.code = p[0];
      e.name = p[1];
      db.save(e);
      all.add(p[0]);
    }
    var admin = role("管理员 / Administrator", all, "ALL");
    role(
        "服务顾问 / Service advisor",
        Set.of("dashboard", "master.read", "master.write", "job.read", "job.write", "stock.read"),
        "DEPARTMENT");
    role(
        "技师 / Technician",
        Set.of("dashboard", "master.read", "job.read", "work", "stock.read"),
        "DEPARTMENT");
    role(
        "配件员 / Parts operator",
        Set.of("dashboard", "master.read", "job.read", "stock.read", "stock.write"),
        "DEPARTMENT");
    role(
        "质检员 / Quality reviewer",
        Set.of("dashboard", "master.read", "job.read", "quality", "stock.read"),
        "DEPARTMENT");
    role(
        "收银 / Cashier",
        Set.of("dashboard", "master.read", "job.read", "finance", "report"),
        "DEPARTMENT");
    var a = new Account();
    a.username = username;
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "工作台", "Overview", "dashboard"},
      {"jobs", "维修工单", "Job cards", "job.read"},
      {"vehicles", "车辆", "Vehicles", "master.read"},
      {"customers", "客户", "Customers", "master.read"},
      {"parts", "配件库存", "Parts & stock", "stock.read"},
      {"labor", "工时项目", "Labor services", "master.read"},
      {"movements", "配件流水", "Parts ledger", "stock.read"},
      {"reports", "经营报表", "Reports", "report"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles & permissions", "admin"},
      {"departments", "门店部门", "Departments", "admin"},
      {"menus", "菜单管理", "Navigation", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"},
      {"audit", "操作审计", "Audit", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of(
                "companyName",
                "知华汽修 / ZhuaTech AutoCare",
                "currency",
                "CNY",
                "timezone",
                "Asia/Shanghai")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    for (var e : new String[][] {{"cash", "现金", "Cash"}, {"bank", "转账", "Bank transfer"}}) {
      var x = new DictionaryEntry();
      x.type = "payment_method";
      x.code = e[0];
      x.name = e[1];
      x.nameEn = e[2];
      db.save(x);
    }
    if (demo) {
      var c = new Customer();
      c.code = "DEMO-CUSTOMER";
      c.name = "示例车主 / Demo customer";
      c.contact = "demo@example.invalid";
      c.notes = "虚构学习数据 / Fictional learning data";
      c.departmentId = d.id;
      c.enabled = true;
      db.save(c);
      var v = new Vehicle();
      v.plate = "DEMO-001";
      v.model = "示例轿车 / Demo sedan";
      v.customerId = c.id;
      v.departmentId = d.id;
      v.odometer = 48000L;
      v.enabled = true;
      db.save(v);
      for (var row :
          new String[][] {
            {"P-OIL", "机油 / Engine oil", "升 / L", "65"},
            {"P-FILTER", "机滤 / Oil filter", "件 / piece", "45"},
            {"P-BRAKE", "制动片 / Brake pad", "套 / set", "380"}
          }) {
        var p = new Part();
        p.code = row[0];
        p.name = row[1];
        p.unit = row[2];
        p.price = new BigDecimal(row[3]);
        p.reorderLevel = new BigDecimal("5");
        p.departmentId = d.id;
        p.enabled = true;
        db.save(p);
      }
      for (var row :
          new String[][] {
            {"L-SERVICE", "常规保养 / Routine service", "次 / job", "120"},
            {"L-BRAKE", "制动维修 / Brake service", "小时 / hour", "100"}
          }) {
        var l = new LaborItem();
        l.code = row[0];
        l.name = row[1];
        l.unit = row[2];
        l.price = new BigDecimal(row[3]);
        l.departmentId = d.id;
        l.enabled = true;
        db.save(l);
      }
    }
  }

  private AccessRole role(String name, Set<String> permissions, String scope) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(permissions);
    return db.save(r);
  }
}
