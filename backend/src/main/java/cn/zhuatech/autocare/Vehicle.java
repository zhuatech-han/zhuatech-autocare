// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import jakarta.persistence.*;

/** 车主车辆档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "vehicle")
public class Vehicle {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "plate", nullable = true, length = 60)
  public String plate = "";

  @Column(name = "vin", nullable = true, length = 60)
  public String vin = "";

  @Column(name = "model", nullable = true, length = 120)
  public String model = "";

  @Column(name = "customer_id", nullable = true)
  public Long customerId;

  @Column(name = "department_id", nullable = true)
  public Long departmentId;

  @Column(name = "odometer", nullable = true)
  public Long odometer;

  @Column(name = "enabled", nullable = true)
  public boolean enabled;
}
