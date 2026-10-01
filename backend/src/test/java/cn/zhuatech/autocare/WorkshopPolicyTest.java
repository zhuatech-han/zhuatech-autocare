// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import static org.junit.jupiter.api.Assertions.*;

import java.math.*;
import org.junit.jupiter.api.Test;

/** 数量、尾差与公式防护的独立会计边界测试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class WorkshopPolicyTest {
  @Test
  void partialReturnsPreserveOriginalIssueCost() {
    var first = WorkshopPolicy.cost(new BigDecimal("3"), new BigDecimal("10.01"), BigDecimal.ONE);
    var second =
        WorkshopPolicy.cost(
            new BigDecimal("2"), new BigDecimal("10.01").subtract(first), BigDecimal.ONE);
    var third =
        WorkshopPolicy.cost(
            BigDecimal.ONE,
            new BigDecimal("10.01").subtract(first).subtract(second),
            BigDecimal.ONE);
    assertEquals(new BigDecimal("10.01"), first.add(second).add(third));
  }

  @Test
  void passwordMustFitBcryptByteLimit() {
    assertThrows(Problem.class, () -> AdminService.validatePassword("Aa9" + "字".repeat(24)));
    assertDoesNotThrow(() -> AdminService.validatePassword("Aa9" + "z".repeat(20)));
  }

  @Test
  void finalUnitRemovesResidual() {
    assertEquals(
        new BigDecimal("0.01"),
        WorkshopPolicy.cost(new BigDecimal("1"), new BigDecimal("0.01"), new BigDecimal("1")));
  }

  @Test
  void negativeAndExcessQuantityRejected() {
    assertThrows(Problem.class, () -> WorkshopPolicy.quantity(new BigDecimal("-1"), false));
    assertThrows(
        Problem.class,
        () -> WorkshopPolicy.cost(new BigDecimal("2"), new BigDecimal("5"), new BigDecimal("3")));
  }

  @Test
  void noSilentPrecisionLoss() {
    assertThrows(Problem.class, () -> WorkshopPolicy.quantity(new BigDecimal("0.0001"), true));
    assertThrows(Problem.class, () -> WorkshopPolicy.money(new BigDecimal("1.001")));
  }

  @Test
  void csvFormulaQuoted() {
    assertEquals("\"'=SUM(A1)\"", WorkshopPolicy.csv("=SUM(A1)"));
    assertEquals("\"a\"\"b\"", WorkshopPolicy.csv("a\"b"));
  }
}
