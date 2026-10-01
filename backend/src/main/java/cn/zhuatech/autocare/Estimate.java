// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;
import java.time.Instant;

/** 独立冻结的维修报价与追加授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "estimate")
public class Estimate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = true)
  public Long jobId;

  @Column(name = "number", nullable = true, length = 60)
  public String number = "";

  @Column(name = "status", nullable = true, length = 20)
  public String status = "";

  @Column(name = "approval_reference", nullable = true, length = 200)
  public String approvalReference = "";

  @Column(name = "note", nullable = true, length = 1000)
  public String note = "";

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "approved_at", nullable = false)
  public Instant approvedAt;
}
