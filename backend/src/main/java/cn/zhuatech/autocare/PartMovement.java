// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 配件收货领用退回不可变流水。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "part_movement")
public class PartMovement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "part_id", nullable = true)
  public Long partId;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "line_id", nullable = false)
  public Long lineId;

  @Column(name = "source_id", nullable = false)
  public Long sourceId;

  @Column(name = "kind", nullable = true, length = 20)
  public String kind = "";

  @Column(name = "quantity", nullable = true, precision = 18, scale = 3)
  public BigDecimal quantity = BigDecimal.ZERO;

  @Column(name = "inventory_value", nullable = true, precision = 18, scale = 2)
  public BigDecimal inventoryValue = BigDecimal.ZERO;

  @Column(name = "reference", nullable = true, length = 120)
  public String reference = "";

  @Column(name = "note", nullable = true, length = 500)
  public String note = "";

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "created_by", nullable = true, length = 60)
  public String createdBy = "";
}
