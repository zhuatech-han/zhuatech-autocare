// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;

/** 客户联系档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "customer")
public class Customer {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = true, length = 60)
  public String code = "";

  @Column(name = "name", nullable = true, length = 120)
  public String name = "";

  @Column(name = "contact", nullable = true, length = 200)
  public String contact = "";

  @Column(name = "notes", nullable = true, length = 1000)
  public String notes = "";

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;
}
