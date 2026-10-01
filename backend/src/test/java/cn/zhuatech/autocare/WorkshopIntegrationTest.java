// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.autocare;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 验证完整维修、授权、回滚、资金、库存与隔离。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkshopIntegrationTest {
  static final String PASSWORD = "Test" + UUID.randomUUID() + "Aa9";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:autocare;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("autocare.admin-password", () -> PASSWORD);
    r.add("autocare.seed-demo", () -> false);
  }

  @Autowired MockMvc mvc;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession session;
  Long customer, vehicle, part, labor, job, technician;
  String suffix;

  @BeforeEach
  void setup() throws Exception {
    session = login("admin", PASSWORD);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    customer =
        ok(
                "/master/customers",
                Map.of(
                    "code", "C" + suffix, "name", "Customer", "departmentId", 1, "enabled", true))
            .get("id")
            .asLong();
    vehicle =
        ok(
                "/master/vehicles",
                Map.of(
                    "plate",
                    "TEST" + suffix,
                    "model",
                    "Sedan",
                    "customerId",
                    customer,
                    "odometer",
                    1000,
                    "departmentId",
                    1,
                    "enabled",
                    true))
            .get("id")
            .asLong();
    part = ok("/master/parts", partInput("P" + suffix, 1)).get("id").asLong();
    labor =
        ok(
                "/master/labor",
                Map.of(
                    "code",
                    "L" + suffix,
                    "name",
                    "Labor",
                    "unit",
                    "hour",
                    "price",
                    "20.00",
                    "departmentId",
                    1,
                    "enabled",
                    true))
            .get("id")
            .asLong();
    technician = user("tech" + suffix, "技师 / Technician", 1);
    job =
        ok(
                "/jobs",
                Map.of(
                    "vehicleId",
                    vehicle,
                    "odometer",
                    1100,
                    "complaint",
                    "Noise",
                    "intakeNote",
                    "Recorded condition"))
            .get("id")
            .asLong();
  }

  Map<String, Object> partInput(String c, long d) {
    return Map.of(
        "code",
        c,
        "name",
        "Part",
        "unit",
        "piece",
        "price",
        "15.00",
        "reorderLevel",
        "2.000",
        "departmentId",
        d,
        "enabled",
        true);
  }

  MockHttpSession login(String u, String p) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", u, "password", p))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  JsonNode sendAs(MockHttpSession s, String path, Object b, int status) throws Exception {
    var r =
        mvc.perform(
                post("/api" + path)
                    .session(s)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(b)))
            .andReturn();
    assertEquals(status, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  JsonNode send(String p, Object b, int s) throws Exception {
    return sendAs(session, p, b, s);
  }

  JsonNode ok(String p, Object b) throws Exception {
    return send(p, b, 200);
  }

  JsonNode read(String p) throws Exception {
    var r = mvc.perform(get("/api" + p).session(session)).andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  Map<String, Object> stock(String q, String p) {
    return Map.of(
        "quantity",
        q,
        "price",
        p,
        "reference",
        "TEST receipt",
        "requestKey",
        UUID.randomUUID().toString());
  }

  Map<String, Object> move(String q) {
    return Map.of(
        "quantity", q, "reference", "TEST issue", "requestKey", UUID.randomUUID().toString());
  }

  Map<String, Object> quoteBody() {
    return Map.of(
        "lines",
        List.of(
            Map.of("kind", "PART", "itemId", part, "quantity", "2.000", "price", "15.00"),
            Map.of("kind", "LABOR", "itemId", labor, "quantity", "1.500", "price", "20.00")));
  }

  Long quote() throws Exception {
    return ok("/jobs/" + job + "/estimates", quoteBody()).get("id").asLong();
  }

  void authorize(Long q) throws Exception {
    ok("/estimates/" + q + "/submit", Map.of());
    ok("/estimates/" + q + "/accept", Map.of("reference", "Telephone consent logged"));
  }

  void start() throws Exception {
    authorize(quote());
    ok("/jobs/" + job + "/assign", Map.of("technicianId", technician));
    ok("/jobs/" + job + "/start", Map.of());
  }

  Long line(String k) throws Exception {
    for (var l : read("/jobs/" + job).get("lines"))
      if (l.get("kind").asText().equals(k)) return l.get("id").asLong();
    throw new AssertionError();
  }

  void quality() throws Exception {
    start();
    ok("/parts/" + part + "/receive", stock("5.000", "10.00"));
    ok("/lines/" + line("PART") + "/issue", move("2.000"));
    for (var l : read("/jobs/" + job).get("lines"))
      ok("/lines/" + l.get("id").asLong() + "/complete", Map.of("note", "Work complete"));
    ok("/jobs/" + job + "/quality", Map.of());
  }

  void ready() throws Exception {
    quality();
    ok("/jobs/" + job + "/review", Map.of("passed", true, "note", "Physical checklist passed"));
  }

  Long user(String n, String role, long d) throws Exception {
    long id = 0;
    for (var r : read("/lists/roles?size=100").get("items"))
      if (r.get("name").asText().equals(role)) id = r.get("id").asLong();
    return ok(
            "/admin/users",
            Map.of(
                "username",
                n,
                "displayName",
                n,
                "password",
                PASSWORD,
                "roleId",
                id,
                "departmentId",
                d,
                "enabled",
                true))
        .get("id")
        .asLong();
  }

  @Test
  void completeRepairPaymentDeliveryAndHistory() throws Exception {
    ready();
    assertEquals(
        0,
        read("/jobs/" + job)
            .get("gross")
            .decimalValue()
            .compareTo(new java.math.BigDecimal("60.00")));
    ok(
        "/jobs/" + job + "/pay",
        Map.of(
            "amount",
            "60.00",
            "reference",
            "Cash receipt",
            "requestKey",
            UUID.randomUUID().toString()));
    ok("/jobs/" + job + "/deliver", Map.of("reference", "Collected by owner"));
    assertEquals("DELIVERED", read("/jobs/" + job).get("job").get("status").asText());
    assertEquals(1, read("/vehicles/" + vehicle + "/history").size());
  }

  @Test
  void duplicateIntakeRejected() throws Exception {
    send("/jobs", Map.of("vehicleId", vehicle, "odometer", 1200, "complaint", "Again"), 409);
  }

  @Test
  void odometerCannotGoBackwards() throws Exception {
    var r =
        mvc.perform(
                put("/api/jobs/" + job)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("vehicleId", vehicle, "odometer", 500, "complaint", "Bad"))))
            .andReturn();
    assertEquals(409, r.getResponse().getStatus());
  }

  @Test
  void startRequiresApproval() throws Exception {
    send("/jobs/" + job + "/start", Map.of(), 409);
  }

  @Test
  void approvalRequiresEvidenceReference() throws Exception {
    long q = quote();
    ok("/estimates/" + q + "/submit", Map.of());
    send("/estimates/" + q + "/accept", Map.of(), 400);
    assertEquals("QUOTED", read("/jobs/" + job).get("job").get("status").asText());
  }

  @Test
  void acceptedEstimateCannotChange() throws Exception {
    long q = quote();
    authorize(q);
    var r =
        mvc.perform(
                put("/api/estimates/" + q)
                    .session(session)
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(quoteBody())))
            .andReturn();
    assertEquals(409, r.getResponse().getStatus());
  }

  @Test
  void supplementUnapprovedCannotIssueAndBlocksQuality() throws Exception {
    start();
    long q = quote();
    long l = 0;
    for (var row : read("/jobs/" + job).get("lines"))
      if (row.get("estimateId").asLong() == q) l = row.get("id").asLong();
    send("/lines/" + l + "/issue", move("1.000"), 409);
    send("/jobs/" + job + "/quality", Map.of(), 409);
  }

  @Test
  void declinedQuoteNotCharged() throws Exception {
    start();
    long q = quote();
    ok("/estimates/" + q + "/submit", Map.of());
    ok("/estimates/" + q + "/decline", Map.of("reference", "Declined by owner"));
    assertEquals(
        0,
        read("/jobs/" + job)
            .get("authorized")
            .decimalValue()
            .compareTo(new java.math.BigDecimal("60.00")));
  }

  @Test
  void workMustBeComplete() throws Exception {
    start();
    send("/jobs/" + job + "/quality", Map.of(), 409);
  }

  @Test
  void assignmentRequired() throws Exception {
    authorize(quote());
    send("/jobs/" + job + "/start", Map.of(), 409);
  }

  @Test
  void technicianCannotSeeUnassignedJobs() throws Exception {
    var s = login("tech" + suffix, PASSWORD);
    assertEquals(
        403, mvc.perform(get("/api/jobs/" + job).session(s)).andReturn().getResponse().getStatus());
  }

  @Test
  void technicianWorksOnlyOwnAssignmentCannotTakePayments() throws Exception {
    start();
    var s = login("tech" + suffix, PASSWORD);
    sendAs(s, "/lines/" + line("LABOR") + "/complete", Map.of("note", "Done"), 200);
    sendAs(s, "/jobs/" + job + "/pay", Map.of(), 403);
  }

  @Test
  void insufficientStockRollsBack() throws Exception {
    start();
    send("/lines/" + line("PART") + "/issue", move("1.000"), 409);
    assertEquals(0, read("/jobs/" + job).get("movements").size());
  }

  @Test
  void issueRetriesNotDoublePosted() throws Exception {
    start();
    ok("/parts/" + part + "/receive", stock("5.000", "10.00"));
    var v = new HashMap<>(move("1.000"));
    long l = line("PART");
    ok("/lines/" + l + "/issue", v);
    ok("/lines/" + l + "/issue", v);
    assertEquals(1, read("/jobs/" + job).get("movements").size());
    v.put("quantity", "2.000");
    send("/lines/" + l + "/issue", v, 409);
  }

  @Test
  void unusedReturnsRestoreOriginalCostAndNetBill() throws Exception {
    start();
    ok("/parts/" + part + "/receive", stock("3.000", "10.00"));
    long l = line("PART");
    ok("/lines/" + l + "/issue", move("2.000"));
    long source = read("/jobs/" + job).get("movements").get(0).get("id").asLong();
    ok("/parts/" + part + "/receive", stock("1.000", "30.00"));
    ok(
        "/lines/" + l + "/return",
        Map.of(
            "sourceId",
            source,
            "quantity",
            "1.000",
            "reference",
            "Unused",
            "requestKey",
            UUID.randomUUID().toString()));
    ok("/lines/" + l + "/complete", Map.of("note", "Used one"));
    assertEquals(
        0,
        read("/jobs/" + job)
            .get("gross")
            .decimalValue()
            .compareTo(new java.math.BigDecimal("15.00")));
    assertEquals(
        0,
        read("/jobs/" + job)
            .get("movements")
            .get(1)
            .get("inventoryValue")
            .decimalValue()
            .compareTo(new java.math.BigDecimal("10.00")));
  }

  @Test
  void cannotReturnMoreThanIssued() throws Exception {
    start();
    ok("/parts/" + part + "/receive", stock("3.000", "10.00"));
    long l = line("PART");
    ok("/lines/" + l + "/issue", move("1.000"));
    long source = read("/jobs/" + job).get("movements").get(0).get("id").asLong();
    send(
        "/lines/" + l + "/return",
        Map.of(
            "sourceId",
            source,
            "quantity",
            "2.000",
            "reference",
            "Bad",
            "requestKey",
            UUID.randomUUID().toString()),
        409);
  }

  @Test
  void qualityRejectedReturnsToWork() throws Exception {
    quality();
    ok("/jobs/" + job + "/review", Map.of("passed", false, "note", "Recheck required"));
    assertEquals("IN_PROGRESS", read("/jobs/" + job).get("job").get("status").asText());
  }

  @Test
  void unpaidCannotDeliver() throws Exception {
    ready();
    send("/jobs/" + job + "/deliver", Map.of("reference", "Collected"), 409);
  }

  @Test
  void paymentCannotExceedBill() throws Exception {
    ready();
    send(
        "/jobs/" + job + "/pay",
        Map.of("amount", "60.01", "reference", "Cash", "requestKey", UUID.randomUUID().toString()),
        409);
    assertEquals(0, read("/jobs/" + job).get("payments").size());
  }

  @Test
  void paymentReversalPreservesReceipt() throws Exception {
    ready();
    ok(
        "/jobs/" + job + "/pay",
        Map.of(
            "amount", "30.00", "reference", "Wrong", "requestKey", UUID.randomUUID().toString()));
    long s = read("/jobs/" + job).get("payments").get(0).get("id").asLong();
    ok(
        "/jobs/" + job + "/reverse",
        Map.of(
            "sourceId",
            s,
            "reference",
            "Correction",
            "note",
            "Wrong receipt",
            "requestKey",
            UUID.randomUUID().toString()));
    assertEquals(2, read("/jobs/" + job).get("payments").size());
    assertEquals(
        0,
        read("/jobs/" + job)
            .get("due")
            .decimalValue()
            .compareTo(new java.math.BigDecimal("60.00")));
    send(
        "/jobs/" + job + "/reverse",
        Map.of(
            "sourceId",
            s,
            "reference",
            "Repeat",
            "note",
            "Wrong",
            "requestKey",
            UUID.randomUUID().toString()),
        409);
  }

  @Test
  void staleStockCountCannotOverwriteReceipt() throws Exception {
    ok("/parts/" + part + "/receive", stock("5.000", "10.00"));
    send(
        "/parts/" + part + "/count",
        Map.of(
            "quantity",
            "4.000",
            "expected",
            "0.000",
            "expectedValue",
            "0.00",
            "price",
            "10.00",
            "reference",
            "Count",
            "requestKey",
            UUID.randomUUID().toString()),
        409);
  }

  @Test
  void invalidImportIsAtomic() throws Exception {
    var code = "IMPORT" + suffix;
    send("/master/parts/import", List.of(partInput(code, 1), partInput(code, 1)), 409);
    assertEquals(0, read("/lists/parts?search=" + code).get("total").asInt());
  }

  @Test
  void departmentIsolation() throws Exception {
    long dept = ok("/admin/departments", Map.of("name", "Branch" + suffix)).get("id").asLong();
    user("advisor" + suffix, "服务顾问 / Service advisor", dept);
    sendAs(
        login("advisor" + suffix, PASSWORD),
        "/jobs/" + job + "/cancel",
        Map.of("note", "Cross department"),
        403);
  }

  @Test
  void anonymousAndCsrfDenied() throws Exception {
    assertEquals(401, mvc.perform(get("/api/catalog")).andReturn().getResponse().getStatus());
    assertEquals(
        403,
        mvc.perform(
                post("/api/jobs").session(session).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void weakPasswordAndLastAdminProtected() throws Exception {
    send(
        "/admin/users",
        Map.of(
            "username",
            "bad" + suffix,
            "displayName",
            "Bad",
            "password",
            "short",
            "roleId",
            1,
            "departmentId",
            1,
            "enabled",
            true),
        400);
    assertEquals(
        409,
        mvc.perform(delete("/api/admin/users/1").session(session).with(csrf()))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void concurrentIssueNeverExceedsApprovedAmount() throws Exception {
    start();
    ok("/parts/" + part + "/receive", stock("5.000", "10.00"));
    long l = line("PART");
    var pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> tasks = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            pool.submit(
                () ->
                    mvc.perform(
                            post("/api/lines/" + l + "/issue")
                                .session(session)
                                .with(csrf())
                                .contentType("application/json")
                                .content(json.writeValueAsString(move("2.000"))))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
      var statuses = List.of(tasks.get(0).get(), tasks.get(1).get());
      assertTrue(statuses.contains(200));
      assertTrue(statuses.contains(409));
      assertEquals(1, read("/jobs/" + job).get("movements").size());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void estimateTotalsUseSamePerLineRoundingAsSettlement() throws Exception {
    var q =
        ok(
                "/jobs/" + job + "/estimates",
                Map.of(
                    "lines",
                    List.of(
                        Map.of(
                            "kind", "PART", "itemId", part, "quantity", "0.333", "price", "0.01"),
                        Map.of(
                            "kind",
                            "LABOR",
                            "itemId",
                            labor,
                            "quantity",
                            "0.333",
                            "price",
                            "0.01"))))
            .get("id")
            .asLong();
    authorize(q);
    var d = read("/jobs/" + job);
    assertEquals(0, d.get("quoteTotals").get(String.valueOf(q)).decimalValue().signum());
    assertEquals(0, d.get("authorized").decimalValue().signum());
  }

  @Test
  void retryFingerprintSeparatesReferencesContainingDelimiters() throws Exception {
    ready();
    var key = UUID.randomUUID().toString();
    ok(
        "/jobs/" + job + "/pay",
        Map.of("amount", "30.00", "note", "a, reference=b", "reference", "c", "requestKey", key));
    send(
        "/jobs/" + job + "/pay",
        Map.of("amount", "30.00", "note", "a", "reference", "b, reference=c", "requestKey", key),
        409);
    assertEquals(1, read("/jobs/" + job).get("payments").size());
  }
}
