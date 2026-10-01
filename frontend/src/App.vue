<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, watch } from "vue";
import {
  LayoutDashboard,
  Wrench,
  Car,
  Users,
  Package,
  ClipboardList,
  Settings,
  BarChart3,
  Plus,
  Search,
  LogOut,
  RefreshCw,
  Download,
  Printer,
  X,
  Menu,
  ArrowLeft,
  ChevronRight,
} from "@lucide/vue";
import { api, resetCsrf } from "./api.js";
import { money, quantity } from "./format.js";
const lang = ref(localStorage.getItem("autocare-language") || "zh");
const L = (zh, en) => (lang.value === "en" ? en : zh);
const user = ref(null),
  page = ref("dashboard"),
  catalog = ref({}),
  rows = ref([]),
  total = ref(0),
  index = ref(0),
  search = ref(""),
  status = ref(""),
  sort = ref("id"),
  desc = ref(true),
  loading = ref(false),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  mobileNav = ref(false),
  dash = ref({}),
  report = ref({}),
  detail = ref(null),
  dialog = ref(null),
  form = ref({}),
  editing = ref(null),
  fields = ref([]),
  quoteLines = ref([]),
  history = ref([]),
  printQuote = ref(null),
  range = ref({ from: "", to: "" });
const login = ref({ username: "admin", password: "" });
const can = (p) => user.value?.permissions.includes(p);
const masterPages = ["customers", "vehicles", "parts", "labor"];
const adminPages = [
  "users",
  "roles",
  "departments",
  "menus",
  "permissions",
  "dictionaries",
  "settings",
];
const settings = computed(() =>
  Object.fromEntries(
    (catalog.value.settings || []).map((s) => [s.code, s.value]),
  ),
);
const cash = (v, c = settings.value.currency || "CNY") =>
  money(v, c, lang.value);
const qty = quantity;
const title = computed(() => {
  const m = user.value?.menus.find((m) => m.code === page.value);
  return m ? (lang.value === "en" ? m.nameEn : m.name) : L("关于系统", "About");
});
const menus = computed(() => user.value?.menus || []);
const stateNames = {
  CHECKED_IN: ["待报价", "Checked in"],
  QUOTED: ["待车主确认", "Awaiting consent"],
  AUTHORIZED: ["待开工", "Authorized"],
  IN_PROGRESS: ["维修中", "In progress"],
  QUALITY: ["待质检", "Quality review"],
  READY: ["待结账交车", "Ready for collection"],
  DELIVERED: ["已交车", "Delivered"],
  CANCELLED: ["已作废", "Cancelled"],
  DRAFT: ["草稿", "Draft"],
  PRESENTED: ["待确认", "Presented"],
  ACCEPTED: ["已授权", "Accepted"],
  DECLINED: ["已拒绝", "Declined"],
  RECEIVE: ["收货", "Receipt"],
  ISSUE: ["领料", "Issue"],
  RETURN: ["配件退回", "Unused return"],
  COUNT: ["盘点", "Count"],
};
const label = (s) => (stateNames[s] ? L(...stateNames[s]) : s);
const errs = {
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password."],
  UNAUTHENTICATED: [
    "登录已失效，请重新登录",
    "Session expired. Sign in again.",
  ],
  FORBIDDEN: ["没有此操作权限", "You do not have permission."],
  OUT_OF_SCOPE: [
    "记录不属于你的部门或派工范围",
    "Outside your department or assignment.",
  ],
  CONFLICT: ["编码重复或记录已被引用", "Duplicate code or record in use."],
  INVALID_INPUT: [
    "请核对必填项、格式和长度",
    "Check required fields, formats and lengths.",
  ],
  INVALID_MASTER: [
    "请选择同门店的启用档案",
    "Select active records in the same workshop.",
  ],
  INVALID_STATE: [
    "当前状态不能操作，请刷新核对",
    "Action unavailable in the current state. Refresh to verify.",
  ],
  INSUFFICIENT_STOCK: [
    "库存不足，请先核对配件",
    "Insufficient stock. Check the parts ledger.",
  ],
  EXCEEDS_QUANTITY: [
    "数量超过授权量或可退量",
    "Quantity exceeds the authorized or returnable amount.",
  ],
  EXCEEDS_BALANCE: [
    "金额超过可收余额",
    "Amount exceeds the outstanding balance.",
  ],
  PENDING_AUTHORIZATION: [
    "仍有报价未确认，请先处理",
    "Resolve outstanding estimates first.",
  ],
  WORK_INCOMPLETE: [
    "还有维修项未完成",
    "Complete all accepted work items first.",
  ],
  UNASSIGNED: ["请先指派技师", "Assign a technician first."],
  INVALID_TECHNICIAN: [
    "技师须启用、同门店且有维修权限",
    "Select an active authorized technician in the same workshop.",
  ],
  UNPAID: [
    "尚未结清，不能交车",
    "Settle the outstanding amount before handover.",
  ],
  STALE_STOCK: [
    "库存已变化，请刷新再盘点",
    "Stock changed. Refresh before counting.",
  ],
  VEHICLE_BUSY: [
    "此车辆已有未结束工单",
    "This vehicle already has an active job.",
  ],
  ODOMETER_BACKWARD: [
    "里程不能小于已有记录",
    "Mileage cannot be lower than the previous record.",
  ],
  INVALID_ODOMETER: [
    "里程须为非负整数",
    "Mileage must be a nonnegative integer.",
  ],
  INVALID_QUANTITY: [
    "数量最多三位小数且须在有效范围",
    "Use up to three decimals within the allowed range.",
  ],
  INVALID_MONEY: [
    "金额须非负且最多两位小数",
    "Use nonnegative amounts with up to two decimals.",
  ],
  KEY_REUSED: [
    "此提交标识已使用，请先核对台账",
    "This request identifier was already used. Verify the ledger.",
  ],
  INVALID_SOURCE: [
    "原凭证不匹配或已经冲销",
    "Original entry does not match or was already reversed.",
  ],
  IMMUTABLE_FIELD: [
    "此字段已有业务引用，不能直接改写",
    "This field is immutable after use.",
  ],
  LAST_ADMIN: [
    "必须保留一个启用的全范围管理员",
    "Keep one active administrator with full scope.",
  ],
  WEAK_PASSWORD: [
    "密码须12–72位，含大小写字母和数字",
    "Use 12–72 characters including uppercase, lowercase and digits.",
  ],
  CURRENCY_LOCKED: [
    "已有业务记录，不能更换记账币种",
    "Currency cannot change after business entries exist.",
  ],
  BUILTIN_RESOURCE: [
    "系统内建记录不能删除",
    "Built-in records cannot be deleted.",
  ],
  LOGIN_THROTTLED: [
    "尝试过多，请五分钟后重试",
    "Too many attempts. Retry in five minutes.",
  ],
  RESOURCE_LIMIT: [
    "记录量超过学习版上限，请联系扩容",
    "Resource limit reached. Contact support for scaling.",
  ],
  NO_CHANGE: [
    "盘点量与账面一致，无需调整",
    "Count matches stock; no adjustment needed.",
  ],
};
/** 显示用户可处理的错误，清空失效会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function fail(e) {
  error.value = errs[e.message]
    ? L(...errs[e.message])
    : L(
        "操作未完成，请核对输入或连接后重试",
        "Unable to complete the action. Check inputs and connection.",
      );
  if (e.message === "UNAUTHENTICATED") {
    user.value = null;
    detail.value = null;
    dialog.value = null;
    resetCsrf();
  }
}
/** 请求期间禁用重复提交；财务库存请求标识在失败重试时保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function perform(fn) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
/** 列表过滤分页与实际工作台查询。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
let loadSequence = 0;
async function refresh() {
  const sequence = ++loadSequence;
  const target = page.value;
  const detailId = detail.value?.job.id;
  loading.value = true;
  try {
    let path = null;
    if (target === "dashboard") path = "/dashboard";
    else if (target === "reports")
      path = "/reports?" + new URLSearchParams(range.value);
    else if (target !== "about")
      path =
        "/lists/" +
        target +
        "?" +
        new URLSearchParams({
          search: search.value,
          status: status.value,
          sort: sort.value,
          desc: desc.value,
          page: index.value,
          size: 20,
        });
    const [nextCatalog, result, nextDetail] = await Promise.all([
      api("/catalog"),
      path ? api(path) : Promise.resolve(null),
      detailId ? api("/jobs/" + detailId) : Promise.resolve(null),
    ]);
    if (sequence !== loadSequence || target !== page.value) return;
    catalog.value = nextCatalog;
    if (target === "dashboard") dash.value = result;
    else if (target === "reports") report.value = result;
    else if (result) {
      rows.value = result.items;
      total.value = result.total;
    }
    if (nextDetail && detail.value?.job.id === detailId)
      detail.value = nextDetail;
  } catch (e) {
    if (sequence === loadSequence) fail(e);
  } finally {
    if (sequence === loadSequence) loading.value = false;
  }
}
/** 登录后选择实际授权菜单，密码仅用于登录请求。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function signIn() {
  await perform(async () => {
    user.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    page.value = user.value.menus.find((m) => m.code === "dashboard")
      ? "dashboard"
      : user.value.menus[0]?.code || "about";
    await refresh();
  });
}
async function signOut() {
  await perform(async () => {
    await api("/auth/logout", "POST", {});
    resetCsrf();
    user.value = null;
    detail.value = null;
    dialog.value = null;
  });
}
/** 切换页面清空旧列表，过期响应由刷新序号拦截。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function go(p) {
  page.value = p;
  rows.value = [];
  total.value = 0;
  detail.value = null;
  index.value = 0;
  search.value = "";
  status.value = "";
  sort.value = "id";
  mobileNav.value = false;
  error.value = "";
  await refresh();
}
async function openJob(j) {
  await perform(async () => {
    detail.value = await api("/jobs/" + j.id);
    dialog.value = null;
  });
}
const icon = (p) =>
  ({
    dashboard: LayoutDashboard,
    jobs: Wrench,
    vehicles: Car,
    customers: Users,
    parts: Package,
    labor: ClipboardList,
    movements: ClipboardList,
    reports: BarChart3,
  })[p] || Settings;
const date = (v) =>
  v
    ? new Intl.DateTimeFormat(lang.value === "en" ? "en-GB" : "zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: settings.value.timezone || "Asia/Shanghai",
      }).format(new Date(v))
    : "—";
const lookup = (type, id, key = "name") =>
  (catalog.value[type] || []).find((x) => x.id === id)?.[key] || id || "—";
const f = (key, zh, en, type = "text", source = "") => ({
  key,
  zh,
  en,
  type,
  source,
});
const dept = f(
  "departmentId",
  "门店部门",
  "Department",
  "select",
  "departments",
);
const schemas = {
  customers: [
    f("code", "客户编码", "Code"),
    f("name", "客户姓名", "Name"),
    f("contact", "联系方式", "Contact"),
    f("notes", "备注", "Notes", "textarea"),
    dept,
    f("enabled", "启用", "Active", "check"),
  ],
  vehicles: [
    f("plate", "车牌 / 车辆编号", "Plate / vehicle ID"),
    f("vin", "VIN（可选）", "VIN (optional)"),
    f("model", "品牌与车型", "Make & model"),
    f("customerId", "车主", "Customer", "select", "customers"),
    f("odometer", "当前里程（公里）", "Odometer (km)", "integer"),
    dept,
    f("enabled", "启用", "Active", "check"),
  ],
  parts: [
    f("code", "配件编码", "Code"),
    f("name", "配件名称", "Name"),
    f("unit", "基本单位", "Base unit"),
    f("price", "参考售价", "Reference price", "money"),
    f("reorderLevel", "库存预警量", "Reorder level", "number"),
    dept,
    f("enabled", "启用", "Active", "check"),
  ],
  labor: [
    f("code", "工时编码", "Code"),
    f("name", "维修项目", "Service name"),
    f("unit", "计价单位", "Billing unit"),
    f("price", "参考单价", "Reference rate", "money"),
    dept,
    f("enabled", "启用", "Active", "check"),
  ],
  users: [
    f("username", "登录账号", "Username"),
    f("displayName", "姓名", "Display name"),
    f(
      "password",
      "密码（修改时留空保持原密码）",
      "Password (leave blank to keep)",
      "password",
    ),
    f("roleId", "角色", "Role", "select", "roles"),
    dept,
    f("enabled", "启用", "Active", "check"),
  ],
  roles: [
    f("name", "角色名称", "Role name"),
    f("scope", "数据范围", "Data scope", "scope"),
    f("permissions", "权限", "Permissions", "permissions"),
  ],
  departments: [f("name", "部门名称", "Department name")],
  permissions: [f("name", "显示名称", "Display name")],
  menus: [
    f("name", "中文名称", "Chinese name"),
    f("nameEn", "英文名称", "English name"),
    f("permissionCode", "入口权限", "Permission", "select", "permissionCodes"),
    f("position", "排序", "Position", "integer"),
    f("enabled", "启用", "Active", "check"),
  ],
  dictionaries: [
    f("type", "字典类别", "Type"),
    f("code", "编码", "Code"),
    f("name", "中文名称", "Chinese name"),
    f("nameEn", "英文名称", "English name"),
  ],
  settings: [f("value", "参数值", "Value")],
};
const columns = computed(() => {
  const p = page.value;
  return (
    {
      jobs: [
        ["number", "工单号", "Job"],
        ["plate", "车辆", "Vehicle"],
        ["customerName", "车主", "Customer"],
        ["complaint", "维修诉求", "Complaint"],
        ["status", "状态", "Status"],
        ["technicianId", "技师", "Technician"],
        ["createdAt", "接车时间", "Intake time"],
      ],
      customers: [
        ["code", "编码", "Code"],
        ["name", "姓名", "Name"],
        ["contact", "联系方式", "Contact"],
        ["departmentId", "门店", "Department"],
        ["enabled", "启用", "Active"],
      ],
      vehicles: [
        ["plate", "车辆", "Vehicle"],
        ["model", "车型", "Model"],
        ["customerId", "车主", "Customer"],
        ["odometer", "里程 km", "Odometer km"],
        ["enabled", "启用", "Active"],
      ],
      parts: [
        ["code", "配件编码", "Code"],
        ["name", "配件", "Part"],
        ["quantity", "可用库存", "On hand"],
        ["unit", "单位", "Unit"],
        ["price", "参考售价", "Reference price"],
        ["inventoryValue", "库存价值", "Stock value"],
        ["reorderLevel", "预警量", "Reorder level"],
      ],
      labor: [
        ["code", "编码", "Code"],
        ["name", "维修项目", "Service"],
        ["unit", "单位", "Unit"],
        ["price", "参考单价", "Rate"],
        ["enabled", "启用", "Active"],
      ],
      movements: [
        ["createdAt", "时间", "Time"],
        ["partId", "配件", "Part"],
        ["kind", "动作", "Kind"],
        ["quantity", "数量", "Quantity"],
        ["inventoryValue", "库存价值变动", "Value change"],
        ["reference", "凭证", "Reference"],
        ["createdBy", "操作人", "Operator"],
      ],
      users: [
        ["username", "账号", "Username"],
        ["displayName", "姓名", "Name"],
        ["roleId", "角色", "Role"],
        ["departmentId", "门店", "Department"],
        ["enabled", "启用", "Active"],
      ],
      roles: [
        ["name", "名称", "Name"],
        ["scope", "数据范围", "Scope"],
        ["permissions", "权限", "Permissions"],
      ],
      departments: [["name", "名称", "Name"]],
      permissions: [
        ["code", "编码", "Code"],
        ["name", "名称", "Name"],
      ],
      menus: [
        ["code", "页面", "Page"],
        ["name", "中文名称", "Chinese name"],
        ["nameEn", "英文名称", "English name"],
        ["permissionCode", "权限", "Permission"],
        ["position", "排序", "Order"],
        ["enabled", "启用", "Active"],
      ],
      dictionaries: [
        ["type", "类别", "Type"],
        ["code", "编码", "Code"],
        ["name", "中文名称", "Chinese name"],
        ["nameEn", "英文名称", "English name"],
      ],
      settings: [
        ["code", "参数", "Parameter"],
        ["value", "值", "Value"],
      ],
      audit: [
        ["createdAt", "时间", "Time"],
        ["actor", "账号", "Account"],
        ["action", "操作", "Action"],
        ["objectId", "记录", "Record"],
        ["departmentId", "门店", "Department"],
      ],
    }[p] || []
  );
});
function cell(row, key) {
  const value = row[key];
  if (key === "enabled")
    return value ? L("启用", "Active") : L("停用", "Inactive");
  if (["status", "kind"].includes(key)) return label(value);
  if (key === "scope")
    return value === "ALL"
      ? L("全部门店", "All departments")
      : L("所在门店", "Own department");
  if (key === "permissions")
    return (value?.length || 0) + L(" 项", " permissions");
  if (key === "createdAt") return date(value);
  if (["price", "inventoryValue"].includes(key)) return cash(value);
  if (["quantity", "reorderLevel"].includes(key)) return qty(value);
  if (key === "technicianId")
    return lookup("technicians", value, "displayName");
  if (key === "customerId") return lookup("customers", value);
  if (key === "roleId") return lookup("roles", value);
  if (key === "departmentId") return lookup("departments", value);
  if (key === "partId") return lookup("parts", value);
  return value ?? "—";
}
const editable = computed(() =>
  masterPages.includes(page.value)
    ? can("master.write")
    : adminPages.includes(page.value) && can("admin"),
);
const newAllowed = computed(
  () =>
    editable.value &&
    !["menus", "permissions", "settings"].includes(page.value),
);
function options(field) {
  if (field.source === "permissionCodes")
    return (catalog.value.permissions || []).map((p) => ({
      value: p.code,
      label: p.name,
    }));
  if (field.source === "technicians")
    return (catalog.value.technicians || [])
      .filter((p) => p.departmentId === detail.value?.job.departmentId)
      .map((p) => ({ value: p.id, label: p.displayName }));
  if (field.source === "returnSources")
    return (detail.value?.movements || [])
      .filter((m) => m.kind === "ISSUE" && m.lineId === dialog.value?.line.id)
      .map((m) => ({
        value: m.id,
        label: "#" + m.id + " · " + qty(m.quantity) + " · " + m.reference,
      }));
  if (field.source === "paymentSources")
    return (detail.value?.payments || [])
      .filter(
        (p) =>
          !p.reversalOf &&
          !(detail.value?.payments || []).some((r) => r.reversalOf === p.id),
      )
      .map((p) => ({
        value: p.id,
        label: "#" + p.id + " · " + cash(p.amount) + " · " + p.reference,
      }));
  return (catalog.value[field.source] || []).map((p) => ({
    value: p.id,
    label: p.name || p.plate || p.displayName || p.code,
  }));
}
function close() {
  dialog.value = null;
  form.value = {};
  quoteLines.value = [];
  error.value = "";
}
function edit(row = null) {
  editing.value = row;
  fields.value = schemas[page.value];
  form.value = row
    ? {
        ...row,
        password: "",
        permissions: row.permissions ? [...row.permissions] : [],
      }
    : {
        departmentId: user.value.departmentId,
        enabled: true,
        scope: "DEPARTMENT",
        permissions: [],
        price: "0.00",
        reorderLevel: "0.000",
        odometer: 0,
        position: 0,
      };
  dialog.value = {
    kind: "master",
    title: row ? L("编辑", "Edit") : L("新增", "New"),
  };
}
function intake(row = null) {
  editing.value = row;
  fields.value = [
    f("vehicleId", "车辆", "Vehicle", "select", "vehicles"),
    f("odometer", "接车里程（公里）", "Intake mileage (km)", "integer"),
    f("complaint", "维修诉求", "Customer complaint", "textarea"),
    f(
      "intakeNote",
      "接车外观 / 随车物品",
      "Condition / belongings",
      "textarea",
    ),
  ];
  form.value = row
    ? { ...row }
    : { vehicleId: "", odometer: 0, complaint: "", intakeNote: "" };
  dialog.value = { kind: "intake", title: L("接车登记", "Vehicle intake") };
}
function chooseVehicle() {
  if (dialog.value?.kind === "intake" && !editing.value)
    form.value.odometer =
      (catalog.value.vehicles || []).find((v) => v.id === form.value.vehicleId)
        ?.odometer || 0;
}
function addQuoteLine() {
  quoteLines.value.push({
    kind: "LABOR",
    itemId: "",
    quantity: "1.000",
    price: "0.00",
  });
}
function pickQuoteItem(line) {
  const a = (
    catalog.value[line.kind === "PART" ? "parts" : "labor"] || []
  ).find((a) => a.id === line.itemId);
  if (a) line.price = String(a.price);
}
function quoteItems(line) {
  return (catalog.value[line.kind === "PART" ? "parts" : "labor"] || []).filter(
    (a) => a.enabled && a.departmentId === detail.value.job.departmentId,
  );
}
/** 编辑独立报价草稿，不重写已接受报价。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function editQuote(e = null) {
  editing.value = e;
  form.value = { note: e?.note || "" };
  quoteLines.value = e
    ? detail.value.lines
        .filter((l) => l.estimateId === e.id)
        .map((l) => ({ ...l }))
    : [];
  if (!e) addQuoteLine();
  dialog.value = {
    kind: "quote",
    title: e
      ? L("编辑报价", "Edit estimate")
      : L("新增 / 追加报价", "New / additional estimate"),
  };
}
function action(path, title, fs = [], defaults = {}, extra = {}) {
  fields.value = fs;
  form.value = { requestKey: crypto.randomUUID(), ...defaults };
  dialog.value = { kind: "action", path, title, ...extra };
}
const refField = f(
  "reference",
  "确认方式 / 外部凭证号",
  "Consent method / external reference",
);
const noteField = f("note", "操作说明", "Note", "textarea");
/** 按当前工单状态生成维修、质检与结算动作表单。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function jobAction(name) {
  const j = detail.value.job;
  const defs = {
    assign: [
      L("指派技师", "Assign technician"),
      [f("technicianId", "技师", "Technician", "select", "technicians")],
      { technicianId: j.technicianId || "" },
    ],
    start: [L("开始维修", "Start work"), [], {}],
    quality: [L("送质检", "Submit for quality review"), [], {}],
    review: [
      L("质检验收", "Quality review"),
      [f("passed", "质检通过", "Passed quality review", "check"), noteField],
      { passed: true, note: "" },
    ],
    discount: [
      L("结账优惠", "Settlement discount"),
      [f("amount", "优惠金额", "Discount", "money")],
      { amount: j.discount },
    ],
    pay: [
      L("登记实际收款", "Record actual payment"),
      [
        f("amount", "实收金额", "Amount received", "money"),
        refField,
        noteField,
      ],
      { amount: detail.value.due, reference: "", note: "" },
    ],
    reverse: [
      L("冲销错误收款", "Reverse incorrect payment"),
      [
        f("sourceId", "原收款", "Original receipt", "select", "paymentSources"),
        refField,
        noteField,
      ],
      { sourceId: "", reference: "", note: "" },
    ],
    deliver: [L("确认交车", "Confirm handover"), [refField], { reference: "" }],
    cancel: [L("作废工单", "Cancel job"), [noteField], { note: "" }],
  };
  const [title, fs, defaults] = defs[name];
  action("/jobs/" + j.id + "/" + name, title, fs, defaults);
}
/** 维修项领退料引用原凭证，完成时填写实际说明。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function lineAction(l, name) {
  if (name === "complete")
    action(
      "/lines/" + l.id + "/complete",
      L("完成维修项", "Complete work item"),
      [noteField],
      { note: "" },
      { line: l },
    );
  else if (name === "issue")
    action(
      "/lines/" + l.id + "/issue",
      L("配件领用", "Issue parts"),
      [
        f("quantity", "本次领用量", "Issue quantity", "number"),
        refField,
        noteField,
      ],
      {
        quantity: Math.max(
          0,
          Number(l.quantity) - Number(l.issued) + Number(l.returned),
        ),
        reference: "",
        note: "",
      },
      { line: l },
    );
  else
    action(
      "/lines/" + l.id + "/return",
      L("未用配件退回", "Return unused parts"),
      [
        f(
          "sourceId",
          "原领料凭证",
          "Original issue",
          "select",
          "returnSources",
        ),
        f("quantity", "退回量", "Return quantity", "number"),
        refField,
        noteField,
      ],
      { sourceId: "", quantity: "1.000", reference: "", note: "" },
      { line: l },
    );
}
/** 盘点带数量和价值快照，避免覆盖后续收货。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
function stockAction(p, name) {
  const fs = [
    f(
      "quantity",
      name === "count" ? "实盘库存" : "收货数量",
      name === "count" ? "Counted on hand" : "Received quantity",
      "number",
    ),
    f("price", "新增配件单位成本", "Incoming unit cost", "money"),
    refField,
    noteField,
  ];
  action(
    "/parts/" + p.id + "/" + name,
    name === "count"
      ? L("库存盘点", "Stock count")
      : L("配件收货", "Receive parts"),
    fs,
    {
      quantity: name === "count" ? p.quantity : "1.000",
      price: "0.00",
      reference: "",
      note: "",
      expected: p.quantity,
      expectedValue: p.inventoryValue,
    },
  );
}
/** 提交冻结报价，人工确认记录必须填写外部凭据。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function estimateAction(e, name) {
  if (name === "accept" || name === "decline")
    action(
      "/estimates/" + e.id + "/" + name,
      name === "accept"
        ? L("记录车主同意", "Record customer consent")
        : L("记录车主拒绝", "Record customer refusal"),
      [refField],
      { reference: "" },
    );
  else if (name === "delete")
    action(
      "/estimates/" + e.id + "/delete",
      L("删除报价草稿", "Delete draft estimate"),
    );
  else
    await perform(async () => {
      await api("/estimates/" + e.id + "/submit", "POST", {});
      await refresh();
    });
}
/** 所有表单提交由同源 API 完成，成功后重新读数据库。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function save() {
  await perform(async () => {
    const d = dialog.value;
    if (d.kind === "master") {
      const base =
        (adminPages.includes(page.value) ? "/admin/" : "/master/") + page.value;
      await api(
        base + (editing.value ? "/" + editing.value.id : ""),
        editing.value ? "PUT" : "POST",
        form.value,
      );
    } else if (d.kind === "intake") {
      const result = await api(
        "/jobs" + (editing.value ? "/" + editing.value.id : ""),
        editing.value ? "PUT" : "POST",
        form.value,
      );
      detail.value = await api("/jobs/" + result.id);
    } else if (d.kind === "quote") {
      await api(
        editing.value
          ? "/estimates/" + editing.value.id
          : "/jobs/" + detail.value.job.id + "/estimates",
        editing.value ? "PUT" : "POST",
        {
          note: form.value.note,
          lines: quoteLines.value.map(({ kind, itemId, quantity, price }) => ({
            kind,
            itemId,
            quantity: String(quantity),
            price: String(price),
          })),
        },
      );
    } else if (d.kind === "action") await api(d.path, "POST", form.value);
    else if (d.kind === "import") {
      const v = JSON.parse(form.value.text);
      await api("/master/parts/import", "POST", v);
    } else if (d.kind === "password") {
      await api("/auth/password", "POST", form.value);
      user.value = null;
      detail.value = null;
      resetCsrf();
    }
    close();
    if (user.value) await refresh();
    notice.value = L("已保存", "Saved");
  });
}
function remove(row) {
  action(
    "/" +
      (adminPages.includes(page.value) ? "admin" : "master") +
      "/" +
      page.value +
      "/" +
      row.id,
    L("删除记录", "Delete record"),
    [],
    {},
    { delete: true },
  );
}
async function deleteSave() {
  await perform(async () => {
    await api(dialog.value.path, "DELETE");
    close();
    await refresh();
  });
}
async function vehicleHistory(v) {
  await perform(async () => {
    history.value = await api("/vehicles/" + v.id + "/history");
    dialog.value = {
      kind: "history",
      title: v.plate + " · " + L("维修历史", "Repair history"),
    };
  });
}
function importParts() {
  form.value = {
    text: JSON.stringify(
      [
        {
          code: "PART-001",
          name: "Example part",
          unit: "piece",
          price: "15.00",
          reorderLevel: "5.000",
          departmentId: user.value.departmentId,
          enabled: true,
        },
      ],
      null,
      2,
    ),
  };
  dialog.value = {
    kind: "import",
    title: L("导入配件 JSON", "Import parts JSON"),
  };
}
function password() {
  fields.value = [
    f("oldPassword", "原密码", "Current password", "password"),
    f("newPassword", "新密码", "New password", "password"),
  ];
  form.value = { oldPassword: "", newPassword: "" };
  dialog.value = { kind: "password", title: L("修改密码", "Change password") };
}
/** 下载权限校验且按日期筛选的业务 CSV。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function exportReport() {
  await perform(async () => {
    const r = await fetch(
      "/api/reports.csv?" + new URLSearchParams(range.value),
    );
    if (!r.ok) throw new Error("FORBIDDEN");
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = "workshop.csv";
    a.click();
    URL.revokeObjectURL(url);
  });
}
function print(e = null) {
  printQuote.value = e?.id || null;
  requestAnimationFrame(() => window.print());
}
const printedLines = computed(
  () =>
    detail.value?.lines.filter((l) =>
      printQuote.value
        ? l.estimateId === printQuote.value
        : detail.value.estimates.find((e) => e.id === l.estimateId)?.status ===
          "ACCEPTED",
    ) || [],
);
const jobStatus = computed(() => detail.value?.job.status);
const jobStates = [
  "CHECKED_IN",
  "QUOTED",
  "AUTHORIZED",
  "IN_PROGRESS",
  "QUALITY",
  "READY",
  "DELIVERED",
  "CANCELLED",
];
const quoteTotal = (e) => detail.value?.quoteTotals?.[e.id] ?? 0;
watch(lang, (v) => localStorage.setItem("autocare-language", v));
onMounted(async () => {
  try {
    user.value = await api("/auth/me");
    page.value = user.value.menus[0]?.code || "about";
    await refresh();
  } catch (e) {
    if (e.message !== "UNAUTHENTICATED") fail(e);
  }
});
</script>

<template>
  <div v-if="!user" class="login-page">
    <div class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" /><span
        >ZhuaTech <strong>AutoCare</strong></span
      >
    </div>
    <form class="login-card" @submit.prevent="signIn">
      <div class="login-heading">
        <span class="eyebrow">WORKSHOP OPERATIONS</span>
        <h1>{{ L("汽修门店管理", "Workshop management") }}</h1>
      </div>
      <label
        >{{ L("登录账号", "Username")
        }}<input
          v-model="login.username"
          autocomplete="username"
          required /></label
      ><label
        >{{ L("密码", "Password")
        }}<input
          v-model="login.password"
          type="password"
          autocomplete="current-password"
          required
      /></label>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <button class="primary full" :disabled="busy">
        {{ busy ? L("登录中…", "Signing in…") : L("登录", "Sign in") }}
      </button>
      <div class="login-foot">
        <button type="button" @click="lang = lang === 'zh' ? 'en' : 'zh'">
          {{ lang === "zh" ? "English" : "中文" }}</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noopener">{{
          L("知华科技", "ZhuaTech")
        }}</a>
      </div>
    </form>
    <small>{{
      L(
        "公开源码学习版 · 商用须书面授权",
        "Non-commercial source edition · Commercial use requires written authorization",
      )
    }}</small>
  </div>
  <div v-else class="shell">
    <aside :class="{ open: mobileNav }" class="sidebar">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" />
        <div>
          <b>AutoCare</b
          ><span>{{ L("知华汽修门店管理", "ZhuaTech workshop") }}</span>
        </div>
      </div>
      <nav>
        <button
          v-for="m in menus"
          :key="m.id"
          :class="{ active: page === m.code }"
          @click="go(m.code)"
        >
          <component :is="icon(m.code)" :size="17" /><span>{{
            lang === "en" ? m.nameEn : m.name
          }}</span>
        </button>
      </nav>
      <div class="sidebar-foot">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >{{ L("知华科技官网", "ZhuaTech website") }}
          <ChevronRight :size="13" /></a
        ><button @click="go('about')">
          {{ L("授权与联系", "License & contact") }}
        </button>
      </div>
    </aside>
    <div v-if="mobileNav" class="nav-mask" @click="mobileNav = false"></div>
    <div class="workspace">
      <header class="topbar">
        <button
          class="nav-toggle"
          :aria-label="L('导航', 'Navigation')"
          @click="mobileNav = !mobileNav"
        >
          <Menu :size="20" /></button
        ><span class="crumb"
          >{{ L("门店运营", "Workshop operations") }}
          <ChevronRight :size="14" /> {{ title }}</span
        >
        <div class="account-bar">
          <button @click="lang = lang === 'zh' ? 'en' : 'zh'">
            {{ lang === "zh" ? "EN" : "中文" }}</button
          ><span>{{ user.displayName }}</span
          ><button @click="password">{{ L("改密", "Password") }}</button
          ><button :aria-label="L('退出', 'Sign out')" @click="signOut">
            <LogOut :size="16" />
          </button>
        </div>
      </header>
      <main :aria-busy="loading">
        <div v-if="error" class="error banner" role="alert">
          {{ error
          }}<button :aria-label="L('关闭', 'Close')" @click="error = ''">
            ×
          </button>
        </div>
        <div v-if="notice" class="notice" role="status">{{ notice }}</div>
        <template v-if="detail">
          <div class="page-heading">
            <div>
              <button class="back" @click="detail = null">
                <ArrowLeft :size="15" />{{ L("返回", "Back") }}
              </button>
              <div class="heading-line">
                <h1>{{ detail.job.plate }}</h1>
                <span :class="'badge ' + jobStatus">{{
                  label(jobStatus)
                }}</span>
              </div>
              <p>
                {{ detail.job.number }} · {{ detail.job.model }} ·
                {{ detail.job.customerName }}
              </p>
            </div>
            <div class="actions">
              <button @click="print()">
                <Printer :size="16" />{{
                  L("打印结算单", "Print settlement")
                }}</button
              ><button :disabled="busy || loading" @click="refresh">
                <RefreshCw :size="16" />
              </button>
            </div>
          </div>
          <div class="job-summary">
            <div>
              <small>{{ L("接车里程", "Intake mileage") }}</small
              ><b>{{ qty(detail.job.odometer) }} km</b>
            </div>
            <div>
              <small>{{ L("技师", "Technician") }}</small
              ><b>{{
                lookup("technicians", detail.job.technicianId, "displayName")
              }}</b>
            </div>
            <div>
              <small>{{ L("接车时间", "Intake time") }}</small
              ><b>{{ date(detail.job.createdAt) }}</b>
            </div>
            <div>
              <small>{{ L("授权上限", "Authorized maximum") }}</small
              ><b>{{ cash(detail.authorized, detail.job.currency) }}</b>
            </div>
          </div>
          <div class="job-layout">
            <div class="job-main">
              <section class="panel">
                <div class="panel-title">
                  <h2>{{ L("接车记录", "Intake record") }}</h2>
                  <button
                    v-if="
                      can('job.write') &&
                      jobStatus === 'CHECKED_IN' &&
                      !detail.estimates.length
                    "
                    @click="intake(detail.job)"
                  >
                    {{ L("编辑", "Edit") }}
                  </button>
                </div>
                <dl class="record">
                  <dt>{{ L("维修诉求", "Complaint") }}</dt>
                  <dd>{{ detail.job.complaint }}</dd>
                  <dt>{{ L("外观与物品", "Condition & belongings") }}</dt>
                  <dd>{{ detail.job.intakeNote || "—" }}</dd>
                </dl>
              </section>
              <section class="panel">
                <div class="panel-title">
                  <h2>{{ L("维修报价与授权", "Estimates & consent") }}</h2>
                  <button
                    v-if="
                      can('job.write') &&
                      [
                        'CHECKED_IN',
                        'QUOTED',
                        'AUTHORIZED',
                        'IN_PROGRESS',
                      ].includes(jobStatus)
                    "
                    class="small-button"
                    @click="editQuote()"
                  >
                    <Plus :size="15" />{{
                      L("新增 / 追加", "New / additional")
                    }}
                  </button>
                </div>
                <div v-if="!detail.estimates.length" class="empty">
                  {{ L("暂无报价", "No estimates") }}
                </div>
                <div v-for="e in detail.estimates" :key="e.id" class="estimate">
                  <div class="estimate-heading">
                    <div>
                      <b>{{ e.number }}</b
                      ><span :class="'badge ' + e.status">{{
                        label(e.status)
                      }}</span>
                    </div>
                    <strong>{{
                      cash(quoteTotal(e), detail.job.currency)
                    }}</strong>
                  </div>
                  <div class="table-scroll">
                    <table class="line-table">
                      <thead>
                        <tr>
                          <th>{{ L("维修项", "Work item") }}</th>
                          <th>{{ L("授权数量", "Authorized qty") }}</th>
                          <th>{{ L("单价", "Rate") }}</th>
                          <th>{{ L("净领用", "Net issued") }}</th>
                          <th>{{ L("执行", "Execution") }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="l in detail.lines.filter(
                            (l) => l.estimateId === e.id,
                          )"
                          :key="l.id"
                        >
                          <td>
                            <b>{{ l.name }}</b
                            ><small
                              >{{
                                l.kind === "PART"
                                  ? L("配件", "Part")
                                  : L("工时", "Labor")
                              }}
                              · {{ l.unit }}</small
                            ><small v-if="l.workNote">{{ l.workNote }}</small>
                          </td>
                          <td>{{ qty(l.quantity) }}</td>
                          <td>{{ cash(l.price, detail.job.currency) }}</td>
                          <td>
                            {{
                              l.kind === "PART"
                                ? qty(Number(l.issued) - Number(l.returned))
                                : "—"
                            }}
                          </td>
                          <td>
                            <span v-if="l.completed" class="complete">{{
                              L("已完成", "Complete")
                            }}</span>
                            <div
                              v-else-if="
                                e.status === 'ACCEPTED' &&
                                jobStatus === 'IN_PROGRESS'
                              "
                              class="row-actions"
                            >
                              <button
                                v-if="l.kind === 'PART' && can('stock.write')"
                                @click="lineAction(l, 'issue')"
                              >
                                {{ L("领料", "Issue") }}</button
                              ><button
                                v-if="
                                  l.kind === 'PART' &&
                                  can('stock.write') &&
                                  Number(l.issued) > Number(l.returned)
                                "
                                @click="lineAction(l, 'return')"
                              >
                                {{ L("退回", "Return") }}</button
                              ><button
                                v-if="can('work')"
                                @click="lineAction(l, 'complete')"
                              >
                                {{ L("完成", "Complete") }}
                              </button>
                            </div>
                            <span v-else>—</span>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                  <p v-if="e.note" class="estimate-note">{{ e.note }}</p>
                  <div class="estimate-footer">
                    <small v-if="e.approvalReference"
                      >{{ L("车主确认记录", "Customer consent record") }}:
                      {{ e.approvalReference }}
                      <span v-if="e.approvedAt">
                        · {{ date(e.approvedAt) }}</span
                      ></small
                    >
                    <div class="row-actions">
                      <button @click="print(e)">
                        <Printer :size="14" />{{ L("打印", "Print") }}</button
                      ><template
                        v-if="
                          can('job.write') &&
                          [
                            'CHECKED_IN',
                            'QUOTED',
                            'AUTHORIZED',
                            'IN_PROGRESS',
                          ].includes(jobStatus)
                        "
                        ><template v-if="e.status === 'DRAFT'"
                          ><button @click="editQuote(e)">
                            {{ L("编辑", "Edit") }}</button
                          ><button
                            :disabled="busy"
                            @click="estimateAction(e, 'submit')"
                          >
                            {{ L("提交报价", "Present") }}</button
                          ><button
                            class="danger-text"
                            @click="estimateAction(e, 'delete')"
                          >
                            {{ L("删除", "Delete") }}
                          </button></template
                        ><template v-if="e.status === 'PRESENTED'"
                          ><button @click="estimateAction(e, 'accept')">
                            {{ L("车主同意", "Customer accepts") }}</button
                          ><button @click="estimateAction(e, 'decline')">
                            {{ L("车主拒绝", "Customer declines") }}
                          </button></template
                        ></template
                      >
                    </div>
                  </div>
                </div>
              </section>
              <section class="panel">
                <div class="panel-title">
                  <h2>
                    {{ L("配件领退记录", "Parts issue & return ledger") }}
                  </h2>
                </div>
                <div v-if="!detail.movements.length" class="empty">
                  {{ L("暂无记录", "No entries") }}
                </div>
                <div v-else class="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>{{ L("凭证", "Entry") }}</th>
                        <th>{{ L("配件", "Part") }}</th>
                        <th>{{ L("动作", "Kind") }}</th>
                        <th>{{ L("数量", "Quantity") }}</th>
                        <th>{{ L("外部凭证", "Reference") }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="m in detail.movements" :key="m.id">
                        <td>
                          #{{ m.id }}<small>{{ date(m.createdAt) }}</small>
                        </td>
                        <td>{{ lookup("parts", m.partId) }}</td>
                        <td>
                          {{ label(m.kind)
                          }}<small v-if="m.sourceId">← #{{ m.sourceId }}</small>
                        </td>
                        <td>{{ qty(m.quantity) }}</td>
                        <td>{{ m.reference }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </section>
            </div>
            <aside class="job-side">
              <section class="panel">
                <div class="panel-title">
                  <h2>{{ L("下一步操作", "Next action") }}</h2>
                </div>
                <div class="flow-actions">
                  <button
                    v-if="
                      can('job.write') &&
                      ['AUTHORIZED', 'IN_PROGRESS'].includes(jobStatus)
                    "
                    @click="jobAction('assign')"
                  >
                    {{ L("指派 / 调整技师", "Assign technician") }}</button
                  ><button
                    v-if="can('work') && jobStatus === 'AUTHORIZED'"
                    class="primary"
                    @click="jobAction('start')"
                  >
                    {{ L("开始维修", "Start repair") }}</button
                  ><button
                    v-if="can('work') && jobStatus === 'IN_PROGRESS'"
                    class="primary"
                    @click="jobAction('quality')"
                  >
                    {{ L("送质检", "Submit for review") }}</button
                  ><button
                    v-if="can('quality') && jobStatus === 'QUALITY'"
                    class="primary"
                    @click="jobAction('review')"
                  >
                    {{ L("质检验收", "Quality review") }}</button
                  ><button
                    v-if="
                      can('finance') &&
                      jobStatus === 'READY' &&
                      Number(detail.due) > 0
                    "
                    class="primary"
                    @click="jobAction('pay')"
                  >
                    {{ L("登记收款", "Record payment") }}</button
                  ><button
                    v-if="
                      can('finance') &&
                      jobStatus === 'READY' &&
                      Number(detail.due) === 0
                    "
                    class="primary"
                    @click="jobAction('deliver')"
                  >
                    {{ L("确认交车", "Confirm handover") }}</button
                  ><button
                    v-if="
                      can('job.write') &&
                      ['CHECKED_IN', 'QUOTED', 'AUTHORIZED'].includes(jobStatus)
                    "
                    class="danger-text"
                    @click="jobAction('cancel')"
                  >
                    {{ L("作废工单", "Cancel job") }}
                  </button>
                </div>
                <dl v-if="detail.job.qualityNote" class="record">
                  <dt>{{ L("质检记录", "Quality record") }}</dt>
                  <dd>{{ detail.job.qualityNote }}</dd>
                </dl>
                <dl v-if="detail.job.handoverReference" class="record">
                  <dt>{{ L("交车记录", "Handover record") }}</dt>
                  <dd>{{ detail.job.handoverReference }}</dd>
                </dl>
              </section>
              <section class="panel settlement">
                <div class="panel-title">
                  <h2>{{ L("结算", "Settlement") }}</h2>
                </div>
                <dl>
                  <dt>{{ L("已完成项目", "Completed work") }}</dt>
                  <dd>{{ cash(detail.gross, detail.job.currency) }}</dd>
                  <dt>{{ L("优惠", "Discount") }}</dt>
                  <dd>−{{ cash(detail.job.discount, detail.job.currency) }}</dd>
                  <dt>{{ L("已收", "Received") }}</dt>
                  <dd>{{ cash(detail.job.netPaid, detail.job.currency) }}</dd>
                  <dt class="due">{{ L("待收", "Outstanding") }}</dt>
                  <dd class="due">
                    {{ cash(detail.due, detail.job.currency) }}
                  </dd>
                </dl>
                <button
                  v-if="can('finance') && jobStatus === 'READY'"
                  @click="jobAction('discount')"
                >
                  {{ L("修改优惠", "Edit discount") }}
                </button>
              </section>
              <section class="panel">
                <div class="panel-title">
                  <h2>{{ L("收款凭证", "Payment entries") }}</h2>
                </div>
                <div v-if="!detail.payments.length" class="empty">
                  {{ L("暂无收款", "No payments") }}
                </div>
                <div v-for="p in detail.payments" :key="p.id" class="payment">
                  <b
                    >#{{ p.id }} ·
                    {{
                      p.reversalOf
                        ? L("冲销", "Reversal")
                        : L("收款", "Receipt")
                    }}</b
                  ><strong
                    >{{ p.reversalOf ? "−" : ""
                    }}{{ cash(p.amount, detail.job.currency) }}</strong
                  ><small>{{ p.reference }} · {{ date(p.createdAt) }}</small
                  ><small v-if="p.reversalOf">← #{{ p.reversalOf }}</small>
                </div>
                <button
                  v-if="
                    can('finance') &&
                    jobStatus === 'READY' &&
                    detail.payments.some(
                      (p) =>
                        !p.reversalOf &&
                        !detail.payments.some((r) => r.reversalOf === p.id),
                    )
                  "
                  @click="jobAction('reverse')"
                >
                  {{ L("冲销错误记录", "Reverse incorrect entry") }}
                </button>
              </section>
            </aside>
          </div>
        </template>
        <template v-else>
          <div class="page-heading">
            <div>
              <span class="eyebrow">{{
                page === "dashboard"
                  ? "WORKSHOP"
                  : page === "jobs"
                    ? "SERVICE DESK"
                    : "AUTOCARE"
              }}</span>
              <h1>{{ title }}</h1>
            </div>
            <div class="actions">
              <button
                v-if="page === 'jobs' && can('job.write')"
                class="primary"
                @click="intake()"
              >
                <Plus :size="16" />{{ L("接车登记", "Vehicle intake") }}</button
              ><button v-if="newAllowed" class="primary" @click="edit()">
                <Plus :size="16" />{{ L("新增", "New") }}</button
              ><button
                v-if="page === 'parts' && can('master.write')"
                @click="importParts"
              >
                <Download :size="16" />{{ L("导入", "Import") }}</button
              ><button v-if="page === 'reports'" @click="exportReport">
                <Download :size="16" />CSV</button
              ><button
                v-if="page !== 'about'"
                :disabled="loading || busy"
                :aria-label="L('刷新', 'Refresh')"
                @click="refresh"
              >
                <RefreshCw :size="16" />
              </button>
            </div>
          </div>
          <template v-if="page === 'dashboard'"
            ><div class="stat-grid">
              <button
                v-for="s in [
                  'QUOTED',
                  'AUTHORIZED',
                  'IN_PROGRESS',
                  'QUALITY',
                  'READY',
                ]"
                :key="s"
                @click="
                  go('jobs').then(() => {
                    status = s;
                    refresh();
                  })
                "
              >
                <small>{{ label(s) }}</small
                ><strong>{{ dash.counts?.[s] || 0 }}</strong
                ><ChevronRight :size="16" />
              </button>
            </div>
            <div class="dashboard-grid">
              <section class="panel">
                <div class="panel-title">
                  <h2>{{ L("最近接车", "Recent intake") }}</h2>
                  <button v-if="can('job.read')" @click="go('jobs')">
                    {{ L("全部工单", "All jobs") }} →
                  </button>
                </div>
                <div v-if="!dash.recent?.length" class="empty">
                  {{ L("暂无可见工单", "No visible jobs") }}
                </div>
                <div
                  v-for="j in dash.recent"
                  :key="j.id"
                  class="recent-row"
                  @click="openJob(j)"
                >
                  <div class="vehicle-symbol"><Car :size="20" /></div>
                  <div>
                    <b>{{ j.plate }}</b
                    ><small>{{ j.customerName }} · {{ j.model }}</small>
                  </div>
                  <div class="recent-complaint">{{ j.complaint }}</div>
                  <span :class="'badge ' + j.status">{{ label(j.status) }}</span
                  ><ChevronRight :size="16" />
                </div>
              </section>
              <section class="panel stock-alert">
                <Package :size="24" />
                <h2>{{ L("配件库存预警", "Low stock") }}</h2>
                <strong>{{ dash.lowStock || 0 }}</strong>
                <p>
                  {{ L("种配件到达预警量", "parts at or below reorder level") }}
                </p>
                <button v-if="can('stock.read')" @click="go('parts')">
                  {{ L("查看库存", "View stock") }} →
                </button>
              </section>
            </div></template
          >
          <template v-else-if="page === 'reports'"
            ><div class="filter-bar">
              <label
                >{{ L("接车日期起", "Intake date from")
                }}<input v-model="range.from" type="date" /></label
              ><label
                >{{ L("至", "To")
                }}<input v-model="range.to" type="date" /></label
              ><button class="primary" @click="refresh">
                {{ L("查询", "Apply") }}
              </button>
            </div>
            <div class="report-summary">
              <div
                v-for="[k, zh, en] in [
                  ['amount', '净结算额', 'Net billed'],
                  ['paid', '实收', 'Received'],
                  ['due', '待收', 'Outstanding'],
                  ['partsCost', '配件成本', 'Parts cost'],
                  ['contribution', '结算减配件成本', 'Billed less parts cost'],
                ]"
                :key="k"
              >
                <small>{{ L(zh, en) }}</small
                ><strong>{{ cash(report[k]) }}</strong>
              </div>
            </div>
            <small class="report-note">{{
              L(
                "统计已质检工单，按接车日期筛选；未扣除工时工资、税费与经营费用。",
                "Quality-passed jobs, filtered by intake date. Labor wages, taxes and overhead are excluded.",
              )
            }}</small>
            <section class="panel table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{{ L("工单", "Job") }}</th>
                    <th>{{ L("车辆 / 车主", "Vehicle / customer") }}</th>
                    <th>{{ L("状态", "Status") }}</th>
                    <th>{{ L("结算额", "Billed") }}</th>
                    <th>{{ L("实收", "Received") }}</th>
                    <th>{{ L("待收", "Outstanding") }}</th>
                    <th>{{ L("配件成本", "Parts cost") }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in report.items" :key="r.id">
                    <td>
                      <button class="link-button" @click="openJob(r)">
                        {{ r.number }}
                      </button>
                    </td>
                    <td>
                      {{ r.plate }}<small>{{ r.customerName }}</small>
                    </td>
                    <td>{{ label(r.status) }}</td>
                    <td>{{ cash(r.amount) }}</td>
                    <td>{{ cash(r.paid) }}</td>
                    <td>{{ cash(r.due) }}</td>
                    <td>{{ cash(r.partsCost) }}</td>
                  </tr>
                  <tr v-if="!report.items?.length">
                    <td colspan="7" class="empty">
                      {{ L("暂无已质检工单", "No quality-passed jobs") }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </section></template
          >
          <section v-else-if="page === 'about'" class="panel about">
            <img src="/brand/logo.jpg" alt="知华科技" />
            <h2>{{ L("知华科技汽修门店管理", "ZhuaTech AutoCare") }}</h2>
            <p>
              {{
                L(
                  "公开源码学习版。未经书面授权不得商用。",
                  "Non-commercial source edition. Commercial use requires written authorization.",
                )
              }}
            </p>
            <p>上海如静知华信息科技有限公司</p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              >https://www.zhuatech.cn/</a
            >
            <p>
              {{
                L(
                  "商业授权、定制开发、部署与系统集成",
                  "Commercial licensing, customization, deployment and integration",
                )
              }}
            </p>
            <p>{{ L("咨询微信", "WeChat") }}：zhuatech / zhuatech2</p>
          </section>
          <template v-else
            ><form
              class="filter-bar"
              @submit.prevent="
                index = 0;
                refresh();
              "
            >
              <div class="search-field">
                <Search :size="16" /><input
                  v-model="search"
                  :placeholder="L('搜索当前列表', 'Search records')"
                  :aria-label="L('搜索', 'Search')"
                />
              </div>
              <select
                v-if="page === 'jobs'"
                v-model="status"
                :aria-label="L('状态', 'Status')"
              >
                <option value="">{{ L("全部状态", "All statuses") }}</option>
                <option v-for="s in jobStates" :key="s" :value="s">
                  {{ label(s) }}
                </option></select
              ><select v-model="sort" :aria-label="L('排序', 'Sort')">
                <option value="id">{{ L("记录顺序", "Record order") }}</option>
                <option v-if="page === 'jobs'" value="plate">
                  {{ L("车牌", "Plate") }}
                </option>
                <option v-else-if="masterPages.includes(page)" value="name">
                  {{ L("名称", "Name") }}
                </option>
                <option v-else value="createdAt">
                  {{ L("时间", "Time") }}
                </option></select
              ><button
                type="button"
                @click="
                  desc = !desc;
                  refresh();
                "
              >
                {{ desc ? "↓" : "↑" }}</button
              ><button class="primary">{{ L("查询", "Search") }}</button>
            </form>
            <section class="panel">
              <div class="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th v-for="c in columns" :key="c[0]">
                        {{ L(c[1], c[2]) }}
                      </th>
                      <th
                        v-if="
                          editable ||
                          page === 'jobs' ||
                          page === 'vehicles' ||
                          (page === 'parts' && can('stock.write'))
                        "
                      >
                        {{ L("操作", "Actions") }}
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="r in rows" :key="r.id">
                      <td
                        v-for="c in columns"
                        :key="c[0]"
                        :class="{
                          numeric: [
                            'price',
                            'quantity',
                            'inventoryValue',
                            'reorderLevel',
                          ].includes(c[0]),
                        }"
                      >
                        <span
                          v-if="c[0] === 'status'"
                          :class="'badge ' + r.status"
                          >{{ cell(r, c[0]) }}</span
                        ><button
                          v-else-if="page === 'jobs' && c[0] === 'number'"
                          class="link-button"
                          @click="openJob(r)"
                        >
                          {{ r.number }}</button
                        ><span v-else>{{ cell(r, c[0]) }}</span
                        ><small
                          v-if="
                            page === 'parts' &&
                            c[0] === 'quantity' &&
                            r.enabled &&
                            Number(r.quantity) <= Number(r.reorderLevel)
                          "
                          class="warning"
                          >{{ L("库存预警", "Low stock") }}</small
                        >
                      </td>
                      <td
                        v-if="
                          editable ||
                          page === 'jobs' ||
                          page === 'vehicles' ||
                          (page === 'parts' && can('stock.write'))
                        "
                      >
                        <div class="row-actions">
                          <button v-if="page === 'jobs'" @click="openJob(r)">
                            {{ L("查看", "Open") }}</button
                          ><button
                            v-if="page === 'vehicles' && can('job.read')"
                            @click="vehicleHistory(r)"
                          >
                            {{ L("历史", "History") }}</button
                          ><template
                            v-if="page === 'parts' && can('stock.write')"
                            ><button @click="stockAction(r, 'receive')">
                              {{ L("收货", "Receive") }}</button
                            ><button @click="stockAction(r, 'count')">
                              {{ L("盘点", "Count") }}
                            </button></template
                          ><template v-if="editable"
                            ><button @click="edit(r)">
                              {{ L("编辑", "Edit") }}</button
                            ><button
                              v-if="
                                !['menus', 'permissions', 'settings'].includes(
                                  page,
                                )
                              "
                              class="danger-text"
                              @click="remove(r)"
                            >
                              {{ L("删除", "Delete") }}
                            </button></template
                          >
                        </div>
                      </td>
                    </tr>
                    <tr v-if="!rows.length">
                      <td :colspan="columns.length + 1" class="empty">
                        {{
                          loading
                            ? L("加载中…", "Loading…")
                            : L("暂无记录", "No records")
                        }}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div class="pagination">
                <small>{{ total }} {{ L("条记录", "records") }}</small>
                <div>
                  <button
                    :disabled="index === 0 || loading"
                    @click="
                      index--;
                      refresh();
                    "
                  >
                    {{ L("上一页", "Previous") }}</button
                  ><span
                    >{{ index + 1 }} /
                    {{ Math.max(1, Math.ceil(total / 20)) }}</span
                  ><button
                    :disabled="(index + 1) * 20 >= total || loading"
                    @click="
                      index++;
                      refresh();
                    "
                  >
                    {{ L("下一页", "Next") }}
                  </button>
                </div>
              </div>
            </section></template
          >
        </template>
      </main>
      <footer class="workspace-foot">
        ZhuaTech AutoCare <span>·</span>
        {{ L("公开源码学习版", "Non-commercial source edition") }}
      </footer>
    </div>
  </div>
  <div v-if="dialog" class="modal-mask" @click.self="!busy && close()">
    <section
      role="dialog"
      aria-modal="true"
      :aria-label="dialog.title"
      :class="[
        'modal',
        { wide: dialog.kind === 'quote' || dialog.kind === 'history' },
      ]"
    >
      <header>
        <h2>{{ dialog.title }}</h2>
        <button
          :disabled="busy"
          :aria-label="L('关闭', 'Close')"
          @click="close"
        >
          <X :size="19" />
        </button>
      </header>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <template v-if="dialog.kind === 'history'"
        ><div class="table-scroll">
          <table>
            <thead>
              <tr>
                <th>{{ L("工单", "Job") }}</th>
                <th>{{ L("诉求", "Complaint") }}</th>
                <th>{{ L("状态", "Status") }}</th>
                <th>{{ L("接车时间", "Intake time") }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="j in history" :key="j.id">
                <td>
                  <button class="link-button" @click="openJob(j)">
                    {{ j.number }}
                  </button>
                </td>
                <td>{{ j.complaint }}</td>
                <td>{{ label(j.status) }}</td>
                <td>{{ date(j.createdAt) }}</td>
              </tr>
              <tr v-if="!history.length">
                <td colspan="4" class="empty">
                  {{ L("暂无可见维修记录", "No visible repair history") }}
                </td>
              </tr>
            </tbody>
          </table>
        </div></template
      >
      <form v-else @submit.prevent="dialog.delete ? deleteSave() : save()">
        <div v-if="dialog.kind === 'quote'" class="quote-editor">
          <div v-for="(line, i) in quoteLines" :key="i" class="quote-edit-line">
            <label
              >{{ L("类型", "Kind")
              }}<select
                v-model="line.kind"
                @change="
                  line.itemId = '';
                  line.price = '0.00';
                "
              >
                <option value="LABOR">{{ L("工时", "Labor") }}</option>
                <option value="PART">{{ L("配件", "Part") }}</option>
              </select></label
            ><label
              >{{ L("维修项目 / 配件", "Service / part")
              }}<select
                v-model="line.itemId"
                required
                @change="pickQuoteItem(line)"
              >
                <option value="" disabled>{{ L("选择", "Select") }}</option>
                <option v-for="a in quoteItems(line)" :key="a.id" :value="a.id">
                  {{ a.code }} · {{ a.name }}
                </option>
              </select></label
            ><label
              >{{ L("数量", "Quantity")
              }}<input
                v-model="line.quantity"
                type="number"
                min="0.001"
                step="0.001"
                required /></label
            ><label
              >{{ L("单价", "Rate")
              }}<input
                v-model="line.price"
                type="number"
                min="0"
                step="0.01"
                required /></label
            ><button
              type="button"
              :disabled="quoteLines.length === 1"
              :aria-label="L('删除报价行', 'Remove line')"
              @click="quoteLines.splice(i, 1)"
            >
              <X :size="17" />
            </button>
          </div>
          <button type="button" class="small-button" @click="addQuoteLine">
            <Plus :size="15" />{{ L("增加项目", "Add item") }}</button
          ><label
            >{{ L("报价说明", "Estimate note")
            }}<textarea v-model="form.note" maxlength="1000"></textarea>
          </label>
        </div>
        <label v-else-if="dialog.kind === 'import'"
          >JSON<textarea
            v-model="form.text"
            rows="12"
            spellcheck="false"
            required
          ></textarea>
        </label>
        <p v-else-if="dialog.delete" class="confirm-copy">
          {{
            L(
              "删除后不可恢复，已引用记录会拒绝删除。",
              "Deletion cannot be undone. Records in use cannot be deleted.",
            )
          }}
        </p>
        <p
          v-else-if="dialog.kind === 'action' && !fields.length"
          class="confirm-copy"
        >
          {{ L("确认执行此操作？", "Confirm this action?") }}
        </p>
        <div v-else class="form-grid">
          <label
            v-for="field in fields"
            :key="field.key"
            :class="{
              span: field.type === 'textarea' || field.type === 'permissions',
              check: field.type === 'check',
            }"
            ><template v-if="field.type === 'check'"
              ><input v-model="form[field.key]" type="checkbox" />{{
                L(field.zh, field.en)
              }}</template
            ><template v-else
              >{{ L(field.zh, field.en)
              }}<textarea
                v-if="field.type === 'textarea'"
                v-model="form[field.key]"
                :maxlength="field.key === 'note' ? 500 : 1000"
              ></textarea
              ><select
                v-else-if="field.type === 'select'"
                v-model="form[field.key]"
                required
                @change="field.key === 'vehicleId' && chooseVehicle()"
              >
                <option value="" disabled>{{ L("请选择", "Select") }}</option>
                <option
                  v-for="o in options(field)"
                  :key="o.value"
                  :value="o.value"
                >
                  {{ o.label }}
                </option></select
              ><select v-else-if="field.type === 'scope'" v-model="form.scope">
                <option value="DEPARTMENT">
                  {{ L("所在门店", "Own department") }}
                </option>
                <option value="ALL">
                  {{ L("全部门店", "All departments") }}
                </option>
              </select>
              <div
                v-else-if="field.type === 'permissions'"
                class="permission-grid"
              >
                <label
                  v-for="p in catalog.permissions"
                  :key="p.id"
                  class="check"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ p.name }}</label
                >
              </div>
              <input
                v-else
                v-model="form[field.key]"
                :type="
                  ['money', 'number', 'integer'].includes(field.type)
                    ? 'number'
                    : field.type
                "
                :step="
                  field.type === 'money'
                    ? '0.01'
                    : field.type === 'number'
                      ? '0.001'
                      : field.type === 'integer'
                        ? '1'
                        : undefined
                "
                :min="
                  ['money', 'number', 'integer'].includes(field.type)
                    ? 0
                    : undefined
                "
                :maxlength="field.type === 'password' ? 72 : 200"
                :autocomplete="
                  field.type === 'password' ? 'new-password' : 'off'
                "
                :required="
                  !['contact', 'notes', 'vin', 'intakeNote'].includes(
                    field.key,
                  ) && !(field.key === 'password' && editing)
                " /></template
          ></label>
        </div>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="close">
            {{ L("取消", "Cancel") }}</button
          ><button class="primary" :disabled="busy">
            {{ busy ? L("保存中…", "Saving…") : L("确认保存", "Save") }}
          </button>
        </div>
      </form>
    </section>
  </div>
  <section v-if="detail" class="print-document">
    <h1>{{ settings.companyName }}</h1>
    <h2>
      {{
        printQuote
          ? L("维修报价单", "Repair estimate")
          : L("维修结算单", "Repair settlement")
      }}
    </h2>
    <p>
      {{ detail.job.number }} · {{ detail.job.plate }} · {{ detail.job.model }}
    </p>
    <p>
      {{ L("车主", "Customer") }}: {{ detail.job.customerName }} ·
      {{ L("接车里程", "Mileage") }}: {{ detail.job.odometer }} km ·
      {{ label(detail.job.status) }}
    </p>
    <p>{{ detail.job.complaint }}</p>
    <table>
      <thead>
        <tr>
          <th>{{ L("项目", "Item") }}</th>
          <th>{{ L("数量", "Quantity") }}</th>
          <th>{{ L("单价", "Rate") }}</th>
          <th>{{ L("金额", "Amount") }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="l in printedLines" :key="l.id">
          <td>{{ l.name }}</td>
          <td>
            {{
              qty(
                printQuote
                  ? l.quantity
                  : l.completed
                    ? l.kind === "PART"
                      ? Number(l.issued) - Number(l.returned)
                      : l.quantity
                    : 0,
              )
            }}
            {{ l.unit }}
          </td>
          <td>{{ cash(l.price, detail.job.currency) }}</td>
          <td>
            {{
              cash(
                Number(l.price) *
                  (printQuote
                    ? Number(l.quantity)
                    : l.completed
                      ? l.kind === "PART"
                        ? Number(l.issued) - Number(l.returned)
                        : Number(l.quantity)
                      : 0),
                detail.job.currency,
              )
            }}
          </td>
        </tr>
      </tbody>
    </table>
    <template v-if="printQuote"
      ><p>
        {{ label(detail.estimates.find((e) => e.id === printQuote).status) }} ·
        {{
          detail.estimates.find((e) => e.id === printQuote).approvalReference ||
          L("尚未记录车主确认", "Customer consent not recorded")
        }}
      </p></template
    ><template v-else
      ><p>
        {{ L("项目金额", "Completed work") }}:
        {{ cash(detail.gross, detail.job.currency) }} ·
        {{ L("优惠", "Discount") }}:
        {{ cash(detail.job.discount, detail.job.currency) }} ·
        {{ L("实收", "Received") }}:
        {{ cash(detail.job.netPaid, detail.job.currency) }} ·
        {{ L("待收", "Outstanding") }}:
        {{ cash(detail.due, detail.job.currency) }}
      </p>
      <p>{{ detail.job.qualityNote }}</p>
      <p>{{ detail.job.handoverReference }}</p></template
    >
  </section>
</template>
