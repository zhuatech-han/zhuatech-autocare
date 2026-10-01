// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 人工核验收款与原凭证冲销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "payment_entry")
public class PaymentEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = true)
  public Long jobId;

  @Column(name = "reversal_of", nullable = false)
  public Long reversalOf;

  @Column(name = "amount", nullable = true, precision = 18, scale = 2)
  public BigDecimal amount = BigDecimal.ZERO;

  @Column(name = "reference", nullable = true, length = 120)
  public String reference = "";

  @Column(name = "note", nullable = true, length = 500)
  public String note = "";

  @Column(name = "created_at", nullable = true)
  public Instant createdAt;

  @Column(name = "created_by", nullable = true, length = 60)
  public String createdBy = "";
}
