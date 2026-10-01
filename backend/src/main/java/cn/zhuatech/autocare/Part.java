// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 部门配件及移动平均库存。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "part")
public class Part {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = true, length = 60)
  public String code = "";

  @Column(name = "name", nullable = true, length = 120)
  public String name = "";

  @Column(name = "unit", nullable = true, length = 20)
  public String unit = "";

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "price", nullable = true, precision = 18, scale = 2)
  public BigDecimal price = BigDecimal.ZERO;

  @Column(name = "quantity", nullable = true, precision = 18, scale = 3)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "inventory_value", nullable = true, precision = 18, scale = 2)
  public BigDecimal inventoryValue = BigDecimal.ZERO;

  @Column(name = "reorder_level", nullable = true, precision = 18, scale = 3)
  public BigDecimal reorderLevel = BigDecimal.ZERO;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;
}
