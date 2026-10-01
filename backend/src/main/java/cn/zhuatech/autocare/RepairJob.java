// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 接车工单及结账交车状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "repair_job")
public class RepairJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "number", nullable = true, length = 60)
  public String number = "";

  @Column(name = "vehicle_id", nullable = true)
  public Long vehicleId;

  @Column(name = "customer_id", nullable = true)
  public Long customerId;

  @Column(name = "plate", nullable = true, length = 60)
  public String plate = "";

  @Column(name = "model", nullable = true, length = 120)
  public String model = "";

  @Column(name = "customer_name", nullable = true, length = 120)
  public String customerName = "";

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "technician_id", nullable = false)
  public Long technicianId;

  @Column(name = "odometer", nullable = true)
  public Long odometer;

  @Column(name = "complaint", nullable = true, length = 1000)
  public String complaint = "";

  @Column(name = "intake_note", nullable = true, length = 1000)
  public String intakeNote = "";

  @Column(name = "status", nullable = true, length = 20)
  public String status = "";

  @Column(name = "currency", nullable = true, length = 3)
  public String currency = "";

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "created_by", nullable = true, length = 60)
  public String createdBy = "";

  @Column(name = "quality_note", nullable = true, length = 1000)
  public String qualityNote = "";

  @Column(name = "handover_reference", nullable = true, length = 200)
  public String handoverReference = "";

  @Column(name = "discount", nullable = true, precision = 18, scale = 2)
  public BigDecimal discount = BigDecimal.ZERO;

  @Column(name = "net_paid", nullable = true, precision = 18, scale = 2)
  public BigDecimal netPaid = BigDecimal.ZERO;
}
