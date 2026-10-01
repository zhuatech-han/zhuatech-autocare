// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** 已授权工时或配件价格快照及履约量。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "repair_line")
public class RepairLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "estimate_id", nullable = true)
  public Long estimateId;

  @Column(name = "job_id", nullable = true)
  public Long jobId;

  @Column(name = "kind", nullable = true, length = 20)
  public String kind = "";

  @Column(name = "item_id", nullable = true)
  public Long itemId;

  @Column(name = "name", nullable = true, length = 120)
  public String name = "";

  @Column(name = "unit", nullable = true, length = 20)
  public String unit = "";

  @Column(name = "quantity", nullable = true, precision = 18, scale = 3)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "price", nullable = true, precision = 18, scale = 2)
  public BigDecimal price = BigDecimal.ZERO;

  @Column(name = "issued", nullable = true, precision = 18, scale = 3)
  public BigDecimal issued = BigDecimal.ZERO;

  @Column(name = "returned", nullable = true, precision = 18, scale = 3)
  public BigDecimal returned = BigDecimal.ZERO;

  @Column(name = "completed", nullable = true)
  public boolean completed;

  @Column(name = "work_note", nullable = true, length = 500)
  public String workNote = "";
}
