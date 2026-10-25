(() => {
  "use strict";

  const API_BASE = "";
  const BUSINESS_DATE = "2025-11-14";
  const TOKEN_KEY = "hengzhang.accessToken";

  const numberFormat = new Intl.NumberFormat("zh-CN");
  const compactFormat = new Intl.NumberFormat("zh-CN", { notation: "compact", maximumFractionDigits: 1 });
  const currencyFormat = new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  const dateTimeFormat = new Intl.DateTimeFormat("zh-CN", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false
  });

  const STATUS_LABELS = {
    processing: "处理中",
    shipped: "已发货",
    completed: "已完成",
    cancelled: "已取消",
    returned: "已退回"
  };

  const CATEGORY_LABELS = {
    Automotive: "汽车用品",
    Beauty: "美妆个护",
    Books: "图书",
    Clothing: "服饰",
    Electronics: "电子产品",
    Groceries: "食品杂货",
    "Home & Kitchen": "家居厨具",
    "Pet Supplies": "宠物用品",
    Sports: "运动用品",
    Toys: "玩具"
  };

  const DOCUMENTED_BRANDS = ["Acme", "Astra", "Everest", "GreenLeaf", "Harbor", "NeoTech", "Nimbus", "Orion", "Pulse", "Solace", "Willow", "Zenith"];
  const ROLE_LABELS = { SUPER_ADMIN: "超级管理员", ADMIN: "品牌管理员", NORMAL_USER: "普通用户" };
  const ACCOUNT_STATUS_LABELS = { ACTIVE: "启用", DISABLED: "停用" };

  const PALETTE = {
    ink: "#1f2937",
    muted: "#667085",
    green: "#206bc4",
    greenDark: "#354052",
    greenSoft: "#75a8df",
    seriesGreen: "#8aa78d",
    amber: "#4b9275",
    blue: "#6c91bb",
    paper: "#ffffff",
    line: "#e1e5eb"
  };

  const state = {
    token: sessionStorage.getItem(TOKEN_KEY) || "",
    csrfToken: "",
    auth: { mode: "login", finishTransition: null },
    user: null,
    scope: null,
    requestVersion: 0,
    controller: null,
    filters: {
      startTime: "2025-10-16",
      endTime: BUSINESS_DATE,
      categories: [],
      brands: []
    },
    categories: [],
    brands: [],
    trendMetric: "amount",
    rankingMetric: "amount",
    distributionMetric: "status",
    categoryView: "3d",
    category3d: null,
    category3dPromise: null,
    granularity: "day",
    chartData: {
      trend: null,
      ranking: null,
      category: null,
      status: null,
      payment: null
    },
    returnFocus: null,
    order: {
      page: 1,
      pageSize: 20,
      total: 0,
      keyword: "",
      status: "",
      rowsById: new Map()
    },
    people: {
      loaded: false,
      loading: false,
      page: 1,
      pageSize: 10,
      total: 0,
      keyword: "",
      role: "",
      status: "",
      requestVersion: 0,
      controller: null,
      rowsById: new Map(),
      editingId: null
    },
    brandOps: {
      segments: { brand: "", data: null, controller: null },
      products: {
        loaded: false,
        page: 1,
        pageSize: 20,
        total: 0,
        range: "FILTERED",
        keyword: "",
        sortBy: "salesVolume",
        order: "desc",
        requestVersion: 0,
        controller: null,
        rowsById: new Map()
      },
      detail: {
        productId: null,
        data: null,
        tab: "overview",
        controller: null,
        reviews: { page: 1, pageSize: 10, total: 0, rating: "", startTime: "", endTime: "", rows: null },
        buyers: { page: 1, pageSize: 10, total: 0, keyword: "", rows: null }
      }
    },
    realtime: {
      socket: null,
      generation: 0,
      reconnectAttempt: 0,
      reconnectTimer: null,
      heartbeatTimer: null,
      pongTimer: null,
      refreshTimer: null,
      pendingEvents: [],
      seenEventIds: new Set()
    },
    charts: {}
  };

  const els = {};

  class ApiError extends Error {
    constructor(message, status = 0, code = null) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.code = code;
    }
  }

  document.addEventListener("DOMContentLoaded", init);

  function init() {
    cacheElements();
    bindEvents();
    initializeCharts();
    window.addEventListener("pageshow", event => {
      if (event.persisted && state.token) {
        if (!isRealtimeOpen()) connectRealtime();
        bootstrapDashboard().catch(() => {});
      }
    });
    window.addEventListener("online", () => {
      if (state.token && !isRealtimeOpen()) connectRealtime();
    });
    window.addEventListener("beforeunload", stopRealtime);
    if (state.token) restoreSession();
    else showAuth();
  }

  function cacheElements() {
    [
      "auth-screen", "app-shell", "auth-forms", "login-form", "login-email", "login-password", "login-error", "login-submit",
      "register-form", "register-name", "register-email", "register-password", "register-confirm-password", "register-error", "register-submit", "register-success",
      "toggle-password", "logout-button", "refresh-button", "filter-form", "start-date", "end-date", "category-select",
      "brand-select", "reset-filter", "active-scope-text", "last-updated", "connection-state", "sidebar-scope",
      "sidebar-role", "sidebar-user", "sidebar-email", "user-avatar", "kpi-sales", "kpi-orders", "kpi-average", "kpi-state",
      "kpi-hot", "kpi-hot-note", "granularity-select", "trend-chart", "ranking-chart", "ranking-copy", "category-chart",
      "distribution-chart", "category-copy", "category-unit", "category-3d", "category-3d-stage", "category-3d-data", "category-3d-tooltip", "order-filter-form", "order-keyword", "order-status", "page-size", "orders-body",
      "orders-state", "order-total", "previous-page", "next-page", "page-summary", "page-indicator", "detail-drawer",
      "detail-title", "detail-body", "close-detail", "drawer-scrim", "toast-region", "sidebar", "menu-button", "sidebar-scrim",
      "user-nav", "user-management", "users-filter-form", "users-keyword", "users-role", "users-status", "users-page-size",
      "users-body", "users-state", "users-total", "users-previous-page", "users-next-page", "users-page-summary", "users-page-indicator",
      "add-admin-button", "user-dialog", "user-form", "user-dialog-mode", "user-dialog-title", "close-user-dialog", "cancel-user-dialog",
      "managed-version", "managed-email-group", "managed-email", "managed-password-group", "managed-password", "managed-name",
      "managed-role", "managed-status", "managed-brand", "managed-brand-hint", "user-form-error", "save-user",
      "product-scope", "segment-brand-field", "segment-brand", "segment-result", "product-filter-form", "products-range-copy",
      "product-keyword", "product-sort", "product-order", "product-page-size", "products-total", "products-body", "products-state",
      "products-previous-page", "products-next-page", "products-page-summary", "products-page-indicator", "product-drawer",
      "product-detail-title", "product-detail-body", "close-product-detail"
    ].forEach(id => { els[toCamel(id)] = document.getElementById(id); });
  }

  function bindEvents() {
    els.loginForm.addEventListener("submit", handleLogin);
    els.registerForm.addEventListener("submit", handleRegister);
    els.togglePassword.addEventListener("click", togglePassword);
    document.querySelectorAll("[data-auth-switch]").forEach(button => {
      button.addEventListener("click", () => switchAuthMode(button.dataset.authSwitch));
    });
    els.logoutButton.addEventListener("click", logout);
    els.refreshButton.addEventListener("click", refreshWorkspace);
    els.filterForm.addEventListener("submit", applyFilters);
    els.resetFilter.addEventListener("click", resetFilters);
    els.orderFilterForm.addEventListener("submit", applyOrderFilters);
    els.pageSize.addEventListener("change", applyOrderFilters);
    els.previousPage.addEventListener("click", () => changePage(-1));
    els.nextPage.addEventListener("click", () => changePage(1));
    els.ordersBody.addEventListener("click", event => {
      const button = event.target.closest("[data-order-id]");
      if (button) openOrderDetail(button.dataset.orderId);
    });
    els.closeDetail.addEventListener("click", closeOrderDetail);
    els.drawerScrim.addEventListener("click", closeOrderDetail);
    els.usersFilterForm.addEventListener("submit", applyUserFilters);
    els.usersPageSize.addEventListener("change", applyUserFilters);
    els.usersPreviousPage.addEventListener("click", () => changeUserPage(-1));
    els.usersNextPage.addEventListener("click", () => changeUserPage(1));
    els.usersBody.addEventListener("click", event => {
      const button = event.target.closest("[data-edit-user]");
      if (button) openUserDialog("edit", state.people.rowsById.get(button.dataset.editUser));
    });
    els.addAdminButton.addEventListener("click", () => openUserDialog("create"));
    els.closeUserDialog.addEventListener("click", closeUserDialog);
    els.cancelUserDialog.addEventListener("click", closeUserDialog);
    els.userDialog.addEventListener("close", () => {
      state.people.editingId = null;
      delete els.userForm.dataset.original;
      syncPageScrollLock();
    });
    els.userForm.addEventListener("submit", saveManagedUser);
    els.managedRole.addEventListener("change", updateManagedBrandRequirement);
    els.managedStatus.addEventListener("change", updateManagedBrandRequirement);
    els.segmentBrand.addEventListener("change", () => {
      state.brandOps.segments.brand = els.segmentBrand.value;
      loadBrandSegments().catch(() => {});
    });
    els.productFilterForm.addEventListener("submit", applyProductFilters);
    els.productPageSize.addEventListener("change", applyProductFilters);
    els.productsPreviousPage.addEventListener("click", () => changeProductPage(-1));
    els.productsNextPage.addEventListener("click", () => changeProductPage(1));
    els.productsBody.addEventListener("click", event => {
      const button = event.target.closest("[data-product-id]");
      if (button) openProductDetail(button.dataset.productId);
    });
    document.querySelectorAll("[data-product-range]").forEach(button => {
      button.addEventListener("click", () => setProductRange(button.dataset.productRange));
    });
    els.closeProductDetail.addEventListener("click", closeProductDetail);
    els.productDrawer.addEventListener("cancel", event => {
      event.preventDefault();
      closeProductDetail();
    });
    els.productDetailBody.addEventListener("click", handleProductDetailClick);
    els.productDetailBody.addEventListener("submit", handleProductDetailSubmit);
    document.querySelectorAll("[data-category-view]").forEach(button => {
      button.addEventListener("click", () => setCategoryView(button.dataset.categoryView));
    });
    els.menuButton.addEventListener("click", toggleSidebar);
    els.sidebarScrim.addEventListener("click", closeSidebar);

    document.querySelectorAll("[data-days]").forEach(button => {
      button.addEventListener("click", () => setQuickRange(Number(button.dataset.days)));
    });
    document.querySelectorAll("[data-trend]").forEach(button => {
      button.addEventListener("click", () => {
        setSegmentActive("data-trend", button.dataset.trend);
        state.trendMetric = button.dataset.trend;
        renderTrendChart();
      });
    });
    document.querySelectorAll("[data-ranking]").forEach(button => {
      button.addEventListener("click", async () => {
        setSegmentActive("data-ranking", button.dataset.ranking);
        state.rankingMetric = button.dataset.ranking;
        await loadRanking();
      });
    });
    document.querySelectorAll("[data-distribution]").forEach(button => {
      button.addEventListener("click", () => {
        setSegmentActive("data-distribution", button.dataset.distribution);
        state.distributionMetric = button.dataset.distribution;
        renderDistributionChart();
      });
    });
    els.granularitySelect.addEventListener("change", async () => {
      state.granularity = els.granularitySelect.value;
      await loadTrend();
    });

    document.addEventListener("click", event => {
      document.querySelectorAll("details.multi-select[open]").forEach(detail => {
        if (!detail.contains(event.target)) detail.removeAttribute("open");
      });
    });
    [els.categorySelect, els.brandSelect].forEach(detail => {
      detail.addEventListener("toggle", () => handleFilterSelectToggle(detail));
    });
    document.querySelectorAll("[data-nav]").forEach(link => {
      link.addEventListener("click", () => {
        document.querySelectorAll("[data-nav]").forEach(item => item.classList.remove("is-active"));
        link.classList.add("is-active");
        if (link.dataset.nav === "users" && canManageUsers() && !state.people.loaded && !state.people.loading) loadUsers().catch(() => {});
        closeSidebar();
      });
    });
    initializeScrollNavigation();
    window.addEventListener("keydown", event => {
      if (event.key === "Escape") {
        closeOrderDetail();
        closeSidebar();
      } else if (event.key === "Tab") {
        if (els.detailDrawer.classList.contains("is-open")) trapFocus(event, els.detailDrawer);
      }
    });
  }

  function initializeCharts() {
    if (!window.echarts) return;
    state.charts.trend = echarts.init(els.trendChart, null, { renderer: "canvas" });
    state.charts.ranking = echarts.init(els.rankingChart, null, { renderer: "canvas" });
    state.charts.category = echarts.init(els.categoryChart, null, { renderer: "canvas" });
    state.charts.distribution = echarts.init(els.distributionChart, null, { renderer: "canvas" });
    const resizeObserver = new ResizeObserver(() => Object.values(state.charts).forEach(chart => chart?.resize()));
    [els.trendChart, els.rankingChart, els.categoryChart, els.distributionChart].forEach(node => resizeObserver.observe(node));
    document.querySelector('[data-category-view="3d"]').title = "切换到可拖动旋转的三维品类图";
  }

  function setCategoryView(view) {
    if (view !== "flat" && view !== "3d") return;
    state.categoryView = view;
    els.categoryChart.hidden = view === "3d";
    els.category3d.hidden = view !== "3d";
    if (view === "3d") {
      els.category3dStage.focus({ preventScroll: true });
      ensureCategory3D();
    }
    document.querySelectorAll("[data-category-view]").forEach(button => {
      const active = button.dataset.categoryView === view;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    if (view === "flat") {
      renderCategoryChart();
      requestAnimationFrame(() => state.charts.category?.resize());
    } else {
      requestAnimationFrame(() => state.category3d?.resize());
    }
  }

  function ensureCategory3D() {
    if (state.category3d) {
      state.category3d.update(state.chartData.category || []);
      requestAnimationFrame(() => state.category3d?.resize());
      return;
    }
    if (state.category3dPromise) return;
    els.category3dStage.classList.add("is-loading");
    els.category3dStage.setAttribute("aria-busy", "true");
    state.category3dPromise = import("./category-3d.js").then(({ createCategory3D }) => {
      state.category3d = createCategory3D(els.category3dStage, els.category3dTooltip, els.category3dData);
      state.category3d.update(state.chartData.category || []);
      els.category3dStage.classList.remove("is-loading");
      els.category3dStage.setAttribute("aria-busy", "false");
      requestAnimationFrame(() => state.category3d?.resize());
    }).catch(error => {
      els.category3dStage.classList.remove("is-loading");
      els.category3dStage.setAttribute("aria-busy", "false");
      state.category3dPromise = null;
      setCategoryView("flat");
      showToast("三维品类图加载失败", readableError(error, "请刷新页面后重试。"), "error");
    });
  }

  async function restoreSession() {
    setConnection("loading", "正在恢复会话");
    try {
      const data = await api("/api/auth/me");
      applySession(data);
      showApp();
      connectRealtime();
      await bootstrapDashboard();
    } catch (error) {
      clearSession();
      showAuth();
      if (error.status !== 401) showLoginError(readableError(error));
    }
  }

  async function handleLogin(event) {
    event.preventDefault();
    const email = els.loginEmail.value.trim();
    const password = els.loginPassword.value;
    els.loginError.hidden = true;
    if (!email || !password) {
      showLoginError("请输入邮箱和密码后再登录。");
      return;
    }
    setButtonLoading(els.loginSubmit, true, "正在登录…");
    try {
      const data = await api("/api/auth/login", { method: "POST", body: { email, password }, auth: false });
      const token = pick(data, ["jwtToken", "JwtToken", "token", "accessToken"]);
      if (!token) throw new ApiError("登录成功，但响应中没有可用的访问令牌。");
      state.token = token;
      sessionStorage.setItem(TOKEN_KEY, token);
      applySession(data);
      showApp();
      connectRealtime();
      await bootstrapDashboard();
    } catch (error) {
      showLoginError(readableError(error, "登录失败，请检查账号、密码和后端服务。"));
    } finally {
      setButtonLoading(els.loginSubmit, false);
    }
  }

  async function handleRegister(event) {
    event.preventDefault();
    const userName = els.registerName.value.trim();
    const email = els.registerEmail.value.trim();
    const password = els.registerPassword.value;
    const confirmPassword = els.registerConfirmPassword.value;
    els.registerError.hidden = true;

    if (!userName) return showRegisterError("请填写姓名。");
    if (!email || !els.registerEmail.validity.valid) return showRegisterError("请输入有效的邮箱地址。");
    if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d]{6,20}$/.test(password)) {
      return showRegisterError("密码需为 6–20 位，并同时包含大写字母、小写字母和数字。");
    }
    if (password !== confirmPassword) return showRegisterError("两次输入的密码不一致，请重新确认。");

    setButtonLoading(els.registerSubmit, true, "正在提交…");
    try {
      await api("/api/auth/register", {
        method: "POST",
        body: { email, password, confirmPassword, userName },
        auth: false
      });
      els.loginEmail.value = email;
      els.loginPassword.value = "";
      els.loginError.hidden = true;
      els.registerSuccess.textContent = "注册已完成，请使用刚注册的邮箱和密码手动登录。";
      els.registerSuccess.hidden = false;
      els.registerForm.reset();
      switchAuthMode("login");
    } catch (error) {
      showRegisterError(readableError(error, "注册失败，请检查填写内容或稍后重试。"));
    } finally {
      setButtonLoading(els.registerSubmit, false);
    }
  }

  function switchAuthMode(mode) {
    if (!["login", "register"].includes(mode)) return;
    state.auth.finishTransition?.();
    if (mode === state.auth.mode) return;

    const outgoing = mode === "login" ? els.registerForm : els.loginForm;
    const incoming = mode === "login" ? els.loginForm : els.registerForm;
    const duration = window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 300;
    const startHeight = outgoing.getBoundingClientRect().height;
    els.authForms.style.height = `${startHeight}px`;
    incoming.hidden = false;
    incoming.inert = false;
    incoming.setAttribute("aria-hidden", "false");
    incoming.classList.add("is-entering");
    outgoing.inert = true;
    outgoing.setAttribute("aria-hidden", "true");
    els.authForms.offsetHeight;
    const animationFrame = requestAnimationFrame(() => {
      outgoing.classList.remove("is-active");
      outgoing.classList.add("is-leaving");
      incoming.classList.remove("is-entering");
      incoming.classList.add("is-active");
      els.authForms.style.height = `${incoming.scrollHeight}px`;
    });

    const finishTransition = () => {
      cancelAnimationFrame(animationFrame);
      outgoing.hidden = true;
      outgoing.classList.remove("is-active", "is-leaving", "is-entering");
      outgoing.inert = true;
      outgoing.setAttribute("aria-hidden", "true");
      incoming.hidden = false;
      incoming.classList.remove("is-entering", "is-leaving");
      incoming.classList.add("is-active");
      incoming.inert = false;
      incoming.setAttribute("aria-hidden", "false");
      els.authForms.style.height = "";
      els.authForms.dataset.mode = mode;
      state.auth.mode = mode;
      state.auth.finishTransition = null;
      (mode === "login" ? els.loginEmail : els.registerName).focus();
    };
    state.auth.finishTransition = finishTransition;
    if (duration === 0) finishTransition();
    else window.setTimeout(() => {
      if (state.auth.finishTransition === finishTransition) finishTransition();
    }, duration);
  }

  function applySession(data) {
    state.user = pick(data, ["user"], data) || {};
    state.scope = pick(data, ["scope"], null);
    state.csrfToken = String(pick(data, ["csrfToken"], ""));
    const role = normalizeRole(pick(state.user, ["role"], ""));
    if (role === "NORMAL_USER") {
      clearSession();
      throw new ApiError("当前账号是普通用户，不能访问经营数据接口。", 403);
    }
    updateUserUI();
  }

  function updateUserUI() {
    const user = state.user || {};
    const name = pick(user, ["userName", "name", "username"], "未命名用户");
    const email = pick(user, ["email"], "—");
    const role = normalizeRole(pick(user, ["role"], ""));
    const brand = pick(user, ["brand", "managedBrand"], "");
    const roleLabel = ROLE_LABELS[role] || "普通用户";
    const scopeText = role === "SUPER_ADMIN" ? "全部授权品牌" : brand || "未分配品牌";
    els.sidebarUser.textContent = name;
    els.sidebarEmail.textContent = email;
    els.userAvatar.textContent = Array.from(name)[0] || "衡";
    els.sidebarScope.textContent = scopeText;
    els.sidebarRole.textContent = roleLabel;
    const showPeople = canManageUsers();
    els.userNav.hidden = !showPeople;
    els.userManagement.hidden = !showPeople;
  }

  async function bootstrapDashboard() {
    resetDashboardFiltersToDefaults();
    showGlobalLoading();
    if (canManageUsers() && !state.people.loaded && !state.people.loading) loadUsers().catch(() => {});
    const [categoryResult, brandResult] = await Promise.allSettled([loadCategories(), loadBrands()]);
    if (categoryResult.status === "rejected") showToast("品类加载失败", readableError(categoryResult.reason), "error");
    if (brandResult.status === "rejected") showToast("品牌加载失败", readableError(brandResult.reason), "error");
    await Promise.allSettled([loadDashboard(), loadBrandSegments(), loadProducts()]);
  }

  async function loadCategories() {
    const data = await api("/api/categories");
    const list = asArray(data).map(item => ({
      value: String(pick(item, ["value", "code", "name"], item)),
      label: String(pick(item, ["label", "displayName"], CATEGORY_LABELS[String(pick(item, ["value"], item))] || item))
    })).filter(item => item.value);
    state.categories = uniqueBy(list, item => item.value);
    renderMultiSelect("category", state.categories);
  }

  async function loadBrands() {
    const role = normalizeRole(pick(state.user, ["role"], ""));
    const managedBrand = pick(state.user, ["brand", "managedBrand"], "");
    if (role === "ADMIN") {
      state.brands = managedBrand ? [{ value: managedBrand, label: managedBrand }] : [];
      state.filters.brands = managedBrand ? [managedBrand] : [];
      renderMultiSelect("brand", state.brands, true, "当前账号未分配品牌", "未分配品牌");
      populateSegmentBrandSelect();
      return;
    }
    const data = await api("/api/brands");
    const list = asArray(pick(data, ["brands"], data)).map(item => ({
      value: String(pick(item, ["value", "code", "name"], item)),
      label: String(pick(item, ["label", "displayName"], pick(item, ["value", "name"], item)))
    })).filter(item => item.value);
    state.brands = uniqueBy(list, item => item.value);
    state.filters.brands = state.filters.brands.filter(brand => state.brands.some(item => item.value === brand));
    renderMultiSelect("brand", state.brands, false, "数据库暂无可用品牌", "全部授权品牌");
    populateSegmentBrandSelect();
  }

  async function refreshWorkspace() {
    await Promise.allSettled([
      loadDashboard({ announce: true }),
      loadBrandSegments(),
      loadProducts()
    ]);
  }

  function populateSegmentBrandSelect() {
    const role = normalizeRole(pick(state.user, ["role"], ""));
    els.segmentBrandField.hidden = role !== "SUPER_ADMIN";
    els.segmentBrand.replaceChildren(new Option("全部品牌", ""));
    state.brands.forEach(item => els.segmentBrand.appendChild(new Option(item.label, item.value)));
    const selected = state.brandOps.segments.brand;
    els.segmentBrand.value = state.brands.some(item => item.value === selected) ? selected : "";
    state.brandOps.segments.brand = els.segmentBrand.value;
    const managedBrand = pick(state.user, ["brand", "managedBrand"], "");
    els.productScope.textContent = role === "SUPER_ADMIN" ? "超级管理员 · 可跨品牌查询" : `${managedBrand || "未分配品牌"} · 品牌权限`;
  }

  async function loadBrandSegments() {
    state.brandOps.segments.controller?.abort();
    state.brandOps.segments.controller = new AbortController();
    els.segmentResult.innerHTML = `<div class="state-message"><div class="loading-rule"></div><p>正在读取品牌全生命周期用户分层…</p></div>`;
    const role = normalizeRole(pick(state.user, ["role"], ""));
    const params = {};
    if (role === "SUPER_ADMIN" && state.brandOps.segments.brand) params.brand = state.brandOps.segments.brand;
    try {
      const data = await api("/api/brand/customer-segments", {
        params,
        signal: state.brandOps.segments.controller.signal
      });
      state.brandOps.segments.data = data;
      renderBrandSegments(data);
    } catch (error) {
      if (error.name === "AbortError") return;
      els.segmentResult.innerHTML = stateMessageHtml("!", "品牌用户分层加载失败", readableError(error));
      const button = document.createElement("button");
      button.className = "secondary-button";
      button.type = "button";
      button.textContent = "重新加载";
      button.addEventListener("click", () => loadBrandSegments().catch(() => {}));
      els.segmentResult.querySelector(".state-message").appendChild(button);
      throw error;
    }
  }

  function renderBrandSegments(data) {
    const fresh = toNumber(pick(data, ["newBuyerCount"], 0));
    const old = toNumber(pick(data, ["oldBuyerCount"], 0));
    const classified = toNumber(pick(data, ["classifiedBuyerCount"], fresh + old));
    const total = toNumber(pick(data, ["totalBuyerCount"], classified));
    const unknown = toNumber(pick(data, ["unclassifiedBuyerCount"], Math.max(0, total - classified)));
    const freshRatio = nullableRatio(pick(data, ["newBuyerRatio"], classified ? fresh / classified : null));
    const oldRatio = nullableRatio(pick(data, ["oldBuyerRatio"], classified ? old / classified : null));
    const threshold = toNumber(pick(data, ["newUserPurchaseThreshold"], 3));
    const filters = pick(data, ["filters"], {}) || {};
    const brands = asArray(pick(filters, ["brand"], []));
    const scopeLabel = brands.length ? brands.join("、") : "全部授权品牌";
    els.segmentResult.innerHTML = `
      <div class="segment-result__grid">
        <div class="segment-result__distribution">
          <div><strong>${escapeHtml(scopeLabel)}</strong><small>全量历史 · ${escapeHtml(formatDateTime(pick(data, ["generatedAt"], "")))}</small></div>
          <div class="segment-bar" aria-label="新客 ${formatPercent(freshRatio)}，老客 ${formatPercent(oldRatio)}"><i class="segment-bar__new" style="width:${freshRatio === null ? 0 : freshRatio * 100}%"></i><i class="segment-bar__old" style="width:${oldRatio === null ? 0 : oldRatio * 100}%"></i></div>
          <div class="segment-result__legend"><span><i></i>新客 ${formatPercent(freshRatio)}</span><span><i></i>老客 ${formatPercent(oldRatio)}</span></div>
        </div>
        <div class="segment-stat"><small>新购买用户</small><strong>${numberFormat.format(fresh)}</strong><span>累计完成订单不超过 ${threshold} 次</span></div>
        <div class="segment-stat"><small>老购买用户</small><strong>${numberFormat.format(old)}</strong><span>累计完成订单第 ${threshold + 1} 次起</span></div>
        <div class="segment-stat"><small>无法分类</small><strong>${numberFormat.format(unknown)}</strong><span>总购买用户 ${numberFormat.format(total)} 人</span></div>
      </div>`;
  }

  function productListParams() {
    const productState = state.brandOps.products;
    const params = {
      page: productState.page,
      num: productState.pageSize,
      range: productState.range,
      keyWord: productState.keyword,
      sortBy: productState.sortBy,
      order: productState.order
    };
    if (productState.range === "FILTERED") {
      params.startTime = state.filters.startTime;
      params.endTime = state.filters.endTime;
    }
    if (normalizeRole(pick(state.user, ["role"], "")) === "SUPER_ADMIN") params.brand = state.filters.brands;
    return params;
  }

  async function loadProducts() {
    const productState = state.brandOps.products;
    const version = ++productState.requestVersion;
    productState.controller?.abort();
    productState.controller = new AbortController();
    showProductsLoading();
    try {
      const data = await api("/api/brand/products", {
        params: productListParams(),
        signal: productState.controller.signal
      });
      if (version !== productState.requestVersion) return;
      const products = asArray(pick(data, ["productList"], []));
      productState.total = toNumber(pick(data, ["total"], products.length));
      productState.loaded = true;
      renderProducts(products);
      const scope = pick(data, ["scope"], {}) || {};
      const allowed = asArray(pick(scope, ["allowedBrands", "brands"], []));
      if (allowed.length) els.productScope.textContent = `当前授权：${allowed.join("、")}`;
    } catch (error) {
      if (error.name === "AbortError") return;
      showProductsError(error);
      throw error;
    }
  }

  function showProductsLoading() {
    els.productsBody.replaceChildren();
    els.productsState.hidden = false;
    els.productsState.innerHTML = `<div class="state-message"><div class="loading-rule"></div><p>正在读取商品经营数据…</p></div>`;
  }

  function showProductsError(error) {
    els.productsBody.replaceChildren();
    els.productsState.hidden = false;
    els.productsState.innerHTML = stateMessageHtml("!", "商品经营数据加载失败", readableError(error));
    const button = document.createElement("button");
    button.className = "secondary-button";
    button.type = "button";
    button.textContent = "重新加载";
    button.addEventListener("click", () => loadProducts().catch(() => {}));
    els.productsState.querySelector(".state-message").appendChild(button);
    state.brandOps.products.total = 0;
    updateProductsPagination();
  }

  function renderProducts(products) {
    const productState = state.brandOps.products;
    els.productsBody.replaceChildren();
    productState.rowsById.clear();
    if (!products.length) {
      els.productsState.hidden = false;
      els.productsState.innerHTML = stateMessageHtml("—", "没有符合条件的商品", "可清除商品关键字、扩大品牌范围或切换全量历史后重试。");
      els.productsTotal.textContent = "共 0 件";
      updateProductsPagination();
      return;
    }
    els.productsState.hidden = true;
    const fragment = document.createDocumentFragment();
    products.forEach(product => {
      const productId = String(pick(product, ["productId", "id"], ""));
      const name = String(pick(product, ["productName", "name"], "未命名商品"));
      const category = String(pick(product, ["category"], "未分类"));
      const status = pick(product, ["metricStatus"], {}) || {};
      productState.rowsById.set(productId, product);
      const row = document.createElement("tr");
      row.innerHTML = `
        <td><div class="product-name"><strong>${escapeHtml(name)}</strong><small>商品 #${escapeHtml(productId)}</small></div></td>
        <td><div class="product-name"><strong>${escapeHtml(String(pick(product, ["brand"], "—")))}</strong><small>${escapeHtml(CATEGORY_LABELS[category] || category)}</small></div></td>
        <td class="align-right money">${nullableCurrency(pick(product, ["currentPrice"], null))}</td>
        <td class="align-right money">${numberFormat.format(toNumber(pick(product, ["salesVolume"], 0)))}</td>
        <td class="align-right money">${formatCurrency(pick(product, ["salesAmount"], 0))}</td>
        <td class="align-right money">${numberFormat.format(toNumber(pick(product, ["buyerCount"], 0)))}</td>
        <td class="align-right">${metricRatio(pick(product, ["repurchaseRate"], null), pick(status, ["repurchaseRate"], "AVAILABLE"))}</td>
        <td class="align-right">${nullableNumber(pick(product, ["rating"], null), 1)}</td>
        <td><button class="row-action" type="button" data-product-id="${escapeHtml(productId)}">查看经营详情</button></td>`;
      fragment.appendChild(row);
    });
    els.productsBody.appendChild(fragment);
    els.productsTotal.textContent = `共 ${numberFormat.format(productState.total)} 件`;
    updateProductsPagination();
  }

  function updateProductsPagination() {
    const productState = state.brandOps.products;
    const pageCount = Math.max(1, Math.ceil(productState.total / productState.pageSize));
    if (productState.page > pageCount) productState.page = pageCount;
    const from = productState.total ? (productState.page - 1) * productState.pageSize + 1 : 0;
    const to = Math.min(productState.total, productState.page * productState.pageSize);
    els.productsPageSummary.textContent = productState.total ? `显示第 ${numberFormat.format(from)}–${numberFormat.format(to)} 件商品` : "暂无结果";
    els.productsPageIndicator.textContent = `${productState.page} / ${pageCount}`;
    els.productsPreviousPage.disabled = productState.page <= 1;
    els.productsNextPage.disabled = productState.page >= pageCount;
  }

  function applyProductFilters(event) {
    event?.preventDefault();
    const productState = state.brandOps.products;
    productState.keyword = els.productKeyword.value.trim();
    productState.sortBy = els.productSort.value;
    productState.order = els.productOrder.value;
    productState.pageSize = Number(els.productPageSize.value);
    productState.page = 1;
    loadProducts().catch(() => {});
  }

  function setProductRange(range) {
    if (!['FILTERED', 'ALL'].includes(range) || state.brandOps.products.range === range) return;
    closeProductDetail();
    state.brandOps.products.range = range;
    state.brandOps.products.page = 1;
    setSegmentActive("data-product-range", range);
    els.productsRangeCopy.textContent = range === "ALL"
      ? "使用商品与品牌全部历史；请求不会携带起止日期。"
      : "使用页面日期与品牌筛选；销售与用户指标仅统计完成订单。";
    loadProducts().catch(() => {});
  }

  function changeProductPage(delta) {
    const productState = state.brandOps.products;
    const pageCount = Math.max(1, Math.ceil(productState.total / productState.pageSize));
    const next = Math.min(pageCount, Math.max(1, productState.page + delta));
    if (next === productState.page) return;
    productState.page = next;
    loadProducts().catch(() => {});
    document.getElementById("product-operations").scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function loadDashboard({ announce = false } = {}) {
    const version = ++state.requestVersion;
    state.controller?.abort();
    state.controller = new AbortController();
    setConnection("loading", "正在更新数据");
    showPanelLoading("trend");
    showPanelLoading("ranking");
    showPanelLoading("category");
    showPanelLoading("distribution");
    showOrdersLoading();

    const results = await Promise.allSettled([
      loadKpi(version),
      loadTrend(version),
      loadRanking(version),
      loadCategoryStats(version),
      loadDistributionStats(version),
      loadOrders(version)
    ]);
    if (version !== state.requestVersion) return;
    const failures = results.filter(item => item.status === "rejected" && item.reason?.name !== "AbortError");
    if (failures.length) setConnection("error", `${failures.length} 项数据未加载`);
    else if (isRealtimeOpen()) setConnection("online", "实时更新已连接");
    else if (state.realtime.socket?.readyState === WebSocket.CONNECTING) setConnection("loading", "正在连接实时更新");
    else setConnection("error", "实时连接暂不可用");
    els.lastUpdated.textContent = `更新于 ${dateTimeFormat.format(new Date()).replaceAll("/", ".")}`;
    updateActiveScopeText();
    if (announce) showToast(failures.length ? "刷新完成，部分数据失败" : "数据已刷新", failures.length ? "失败区域已保留明确提示，可单独重试。" : "所有经营数据已按当前范围更新。", failures.length ? "error" : "success");
  }

  async function loadKpi(version = state.requestVersion) {
    try {
      const data = await api("/api/orders/kpi", { params: commonParams(), signal: state.controller?.signal });
      if (version !== state.requestVersion) return;
      els.kpiSales.textContent = formatCurrency(pick(data, ["totalSalesAmount", "totalSales", "salesAmount"], 0));
      els.kpiOrders.textContent = numberFormat.format(toNumber(pick(data, ["uniqueOrderCount", "totalOrders", "orderCount"], 0)));
      els.kpiAverage.textContent = formatCurrency(pick(data, ["averageOrderValue", "averageAmount", "avgOrderValue"], 0));
      els.kpiHot.textContent = numberFormat.format(toNumber(pick(data, ["hotProductCount", "hotProducts"], 0)));
      const sold = toNumber(pick(data, ["soldProductCount"], 0));
      els.kpiHotNote.textContent = sold ? `在售 ${numberFormat.format(sold)} 件商品` : "当前范围销量前 30%";
      els.kpiState.hidden = true;
      state.scope = pick(data, ["scope"], state.scope);
      updateScopeFromResponse();
    } catch (error) {
      if (error.name !== "AbortError") {
        els.kpiState.textContent = `核心指标加载失败：${readableError(error)}`;
        els.kpiState.hidden = false;
      }
      throw error;
    }
  }

  async function loadTrend(version = state.requestVersion) {
    showPanelLoading("trend");
    try {
      const data = await api("/api/orders/stats", {
        params: { ...commonParams(), granularity: state.granularity },
        signal: state.controller?.signal
      });
      if (version !== state.requestVersion) return;
      state.chartData.trend = normalizeTrend(data);
      renderTrendChart();
    } catch (error) {
      if (error.name !== "AbortError") showPanelError("trend", error, loadTrend);
      throw error;
    }
  }

  async function loadRanking(version = state.requestVersion) {
    showPanelLoading("ranking");
    try {
      const data = await api("/api/product/ranking", {
        params: rankingParams(),
        signal: state.controller?.signal
      });
      if (version !== state.requestVersion) return;
      state.chartData.ranking = normalizeRanking(data);
      renderRankingChart();
    } catch (error) {
      if (error.name !== "AbortError") showPanelError("ranking", error, loadRanking);
      throw error;
    }
  }

  async function loadCategoryStats(version = state.requestVersion) {
    showPanelLoading("category");
    try {
      const data = await api("/api/product/category-stats", { params: commonParams(), signal: state.controller?.signal });
      if (version !== state.requestVersion) return;
      state.chartData.category = normalizeCategoryStats(data);
      renderCategoryChart();
    } catch (error) {
      if (error.name !== "AbortError") showPanelError("category", error, loadCategoryStats);
      throw error;
    }
  }

  async function loadDistributionStats(version = state.requestVersion) {
    showPanelLoading("distribution");
    try {
      const [customerData, paymentData] = await Promise.all([
        api("/api/user/customer-stats", { params: commonParams(), signal: state.controller?.signal }),
        api("/api/orders/payment-stats", { params: commonParams(), signal: state.controller?.signal })
      ]);
      if (version !== state.requestVersion) return;
      state.chartData.status = [
        { name: "新购买用户", value: toNumber(pick(customerData, ["newUserCount"], 0)) },
        { name: "老购买用户", value: toNumber(pick(customerData, ["oldUserCount"], 0)) }
      ];
      const abnormal = toNumber(pick(customerData, ["abnormalUserCount"], 0));
      if (abnormal) state.chartData.status.push({ name: "无法分类", value: abnormal });
      state.chartData.payment = asArray(pick(paymentData, ["payMethodList", "items"], [])).map(item => ({
        name: String(pick(item, ["payWayLabel", "payWay"], "未知")),
        value: toNumber(pick(item, ["orderCount"], 0))
      }));
      renderDistributionChart();
    } catch (error) {
      if (error.name !== "AbortError") showPanelError("distribution", error, loadDistributionStats);
      throw error;
    }
  }

  async function loadOrders(version = state.requestVersion) {
    showOrdersLoading();
    try {
      const params = {
        ...commonParams(),
        page: state.order.page,
        num: state.order.pageSize
      };
      if (state.order.keyword) params.keyWord = state.order.keyword;
      if (state.order.status) params.statuses = [state.order.status];
      const data = await api("/api/orders", { params, signal: state.controller?.signal });
      if (version !== state.requestVersion) return;
      const orders = asArray(pick(data, ["orderList", "content", "items"], []));
      state.order.total = toNumber(pick(data, ["total", "totalElements", "totalCount"], orders.length));
      renderOrders(orders);
    } catch (error) {
      if (error.name !== "AbortError") {
        showOrdersError(error);
      }
      throw error;
    }
  }

  function renderTrendChart() {
    const data = state.chartData.trend;
    if (!ensureChart("trend", data)) return;
    const amountVisible = state.trendMetric !== "orders";
    const orderVisible = state.trendMetric !== "amount";
    state.charts.trend.setOption({
      animationDuration: 520,
      animationEasing: "cubicOut",
      color: [PALETTE.green, PALETTE.seriesGreen],
      grid: { left: 18, right: orderVisible ? 28 : 12, top: 32, bottom: 18, containLabel: true },
      tooltip: {
        trigger: "axis",
        backgroundColor: PALETTE.greenDark,
        borderWidth: 0,
        padding: [10, 12],
        textStyle: { color: "#fff", fontFamily: "Microsoft YaHei" },
        valueFormatter: value => typeof value === "number" ? numberFormat.format(value) : value
      },
      legend: { show: state.trendMetric === "both", top: 0, right: 0, itemWidth: 16, itemHeight: 3, textStyle: chartText(12) },
      xAxis: {
        type: "category",
        boundaryGap: false,
        data: data.labels,
        axisLine: { lineStyle: { color: PALETTE.line } },
        axisTick: { show: false },
        axisLabel: { ...chartText(12), hideOverlap: true, margin: 11 }
      },
      yAxis: [
        {
          type: "value",
          show: amountVisible,
          splitNumber: 4,
          axisLabel: { ...chartText(12), formatter: value => `¥${compactFormat.format(value)}` },
          splitLine: { lineStyle: { color: "#e5e9ef", type: "dashed" } }
        },
        {
          type: "value",
          show: orderVisible,
          splitLine: { show: false },
          axisLabel: { ...chartText(12), formatter: value => compactFormat.format(value) }
        }
      ],
      series: [
        {
          name: "销售额",
          type: "line",
          showSymbol: false,
          smooth: .24,
          yAxisIndex: 0,
          data: amountVisible ? data.amounts : [],
          lineStyle: { width: 3 },
          areaStyle: { color: "rgba(32,107,196,.10)" },
          emphasis: { focus: "series" }
        },
        {
          name: "订单量",
          type: "line",
          showSymbol: false,
          smooth: .18,
          yAxisIndex: 1,
          data: orderVisible ? data.orders : [],
          lineStyle: { width: 2, type: state.trendMetric === "both" ? "dashed" : "solid" },
          emphasis: { focus: "series" }
        }
      ]
    }, true);
    hidePanelState("trend");
  }

  function renderRankingChart() {
    const data = state.chartData.ranking;
    if (!ensureChart("ranking", data)) return;
    const items = data.slice(0, 10).reverse();
    const hotCount = items.filter(item => item.hot).length;
    els.rankingCopy.textContent = `完整展示 ${items.length} 个入榜商品；其中 ${hotCount} 个属于当前全量范围销量前 30%`;
    state.charts.ranking.setOption({
      animationDuration: 520,
      animationEasing: "cubicOut",
      grid: { left: 6, right: 28, top: 5, bottom: 8, containLabel: true },
      tooltip: {
        trigger: "item",
        backgroundColor: PALETTE.greenDark,
        borderWidth: 0,
        textStyle: { color: "#fff", fontFamily: "Microsoft YaHei" },
        formatter: params => {
          const item = items[params.dataIndex];
          return `${escapeHtml(item?.fullName || params.name)}${item?.hot ? "<br>热销：全量商品销量前 30%" : ""}<br>${state.rankingMetric === "amount" ? formatCurrency(params.value) : `${numberFormat.format(params.value)} 件`}`;
        }
      },
      xAxis: { type: "value", show: false },
      yAxis: {
        type: "category",
        data: items.map(item => truncate(item.name, 10)),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { ...chartText(12), width: 104, overflow: "truncate", margin: 12 }
      },
      series: [{
        type: "bar",
        data: items.map(item => ({
          value: state.rankingMetric === "amount" ? item.amount : item.quantity,
          itemStyle: { color: item.hot ? PALETTE.amber : PALETTE.green }
        })),
        barWidth: 12,
        showBackground: true,
        backgroundStyle: { color: "#edf0f4" },
        label: {
          show: true,
          position: "right",
          color: PALETTE.muted,
          fontFamily: "Microsoft YaHei",
          fontSize: 12,
          formatter: params => state.rankingMetric === "amount" ? `¥${compactFormat.format(params.value)}` : numberFormat.format(params.value)
        }
      }]
    }, true);
    hidePanelState("ranking");
  }

  function renderCategoryChart() {
    const data = state.chartData.category;
    if (!ensureChart("category", data)) return;
    els.categoryCopy.textContent = `数据库返回 ${data.length} 个品类分组，展示当前筛选范围全部销售额`;
    els.categoryUnit.textContent = "销售额 · 销量";
    if (state.categoryView === "3d") {
      if (state.category3d) state.category3d.update(data);
      else ensureCategory3D();
      hidePanelState("category");
      return;
    }
    state.charts.category.setOption({
      animationDuration: 520,
      animationEasing: "cubicOut",
      grid: { left: 10, right: 10, top: 20, bottom: 8, containLabel: true },
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "shadow", shadowStyle: { color: "rgba(28,39,54,.05)" } },
        backgroundColor: PALETTE.greenDark,
        borderWidth: 0,
        textStyle: { color: "#fff", fontFamily: "Microsoft YaHei" },
        formatter: params => {
          const item = data[params[0]?.dataIndex];
          const quantity = item?.quantity === null || item?.quantity === undefined ? "—" : `${numberFormat.format(item.quantity)} 件`;
          return `${escapeHtml(item?.label || "品类")}<br>${formatCurrency(item?.value || 0)}<br>${quantity} · ${numberFormat.format(item?.orders || 0)} 单 · ${(toNumber(item?.ratio) * 100).toFixed(1)}%`;
        }
      },
      xAxis: {
        type: "category",
        data: data.map(item => item.label),
        axisLine: { lineStyle: { color: PALETTE.line } },
        axisTick: { show: false },
        axisLabel: { ...chartText(12), interval: 0, rotate: data.length > 7 ? 28 : 0, margin: 11 }
      },
      yAxis: {
        type: "value",
        splitNumber: 4,
        axisLabel: { ...chartText(12), formatter: value => compactFormat.format(value) },
          splitLine: { lineStyle: { color: "#e5e9ef", type: "dashed" } }
      },
      series: [{
        type: "bar",
        data: data.map((item, index) => ({ value: item.value, itemStyle: { color: item.value <= 0 ? "#dce2eb" : index === 0 ? PALETTE.amber : PALETTE.green } })),
        barMaxWidth: 30,
        barMinHeight: 2,
        itemStyle: { borderRadius: [3, 3, 0, 0] }
      }]
    }, true);
    requestAnimationFrame(() => state.charts.category?.resize());
    hidePanelState("category");
  }

  function renderDistributionChart() {
    const data = state.chartData[state.distributionMetric];
    if (!ensureChart("distribution", data)) return;
    const total = data.reduce((sum, item) => sum + toNumber(item.value), 0);
    const colors = [PALETTE.green, PALETTE.blue, PALETTE.amber, PALETTE.seriesGreen, PALETTE.greenSoft, "#708399"];
    state.charts.distribution.setOption({
      animationDuration: 520,
      animationEasing: "cubicOut",
      color: colors,
      tooltip: {
        trigger: "item",
        backgroundColor: PALETTE.greenDark,
        borderWidth: 0,
        textStyle: { color: "#fff", fontFamily: "Microsoft YaHei" },
        formatter: params => `${escapeHtml(params.name)}<br>${numberFormat.format(params.value)} · ${params.percent}%`
      },
      legend: {
        orient: "vertical",
        right: 8,
        top: "middle",
        itemWidth: 9,
        itemHeight: 9,
        itemGap: 13,
        textStyle: chartText(12),
        formatter: name => truncate(name, 12)
      },
      graphic: [
        { type: "text", left: "29%", top: "43%", style: { text: compactFormat.format(total), fill: PALETTE.ink, font: "700 20px Microsoft YaHei", textAlign: "center" } },
        { type: "text", left: "29%", top: "54%", style: { text: state.distributionMetric === "payment" ? "完成订单" : "购买用户", fill: PALETTE.muted, font: "12px Microsoft YaHei", textAlign: "center" } }
      ],
      series: [{
        type: "pie",
        radius: ["48%", "68%"],
        center: ["31%", "50%"],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: PALETTE.paper, borderWidth: 3 },
        label: { show: false },
        emphasis: { scaleSize: 5 },
        data
      }]
    }, true);
    hidePanelState("distribution");
  }

  function renderOrders(orders) {
    els.ordersBody.replaceChildren();
    state.order.rowsById.clear();
    if (!orders.length) {
      showOrdersEmpty();
      updatePagination();
      return;
    }
    els.ordersState.hidden = true;
    const fragment = document.createDocumentFragment();
    orders.forEach(order => {
      const orderId = pick(order, ["orderId", "id"], "—");
      const amount = pick(order, ["visiblePrice", "visibleAmount", "matchedAmount", "amount", "totalAmount"], null);
      const status = String(pick(order, ["status", "orderStatus"], "processing")).toLowerCase();
      state.order.rowsById.set(String(orderId), order);
      const row = document.createElement("tr");
      row.innerHTML = `
        <td><span class="order-id">#${escapeHtml(String(orderId))}</span></td>
        <td><div class="product-summary" title="${escapeHtml(String(pick(order, ["productSummary", "productName", "products"], "暂无商品摘要")))}">${escapeHtml(String(pick(order, ["productSummary", "productName", "products"], "暂无商品摘要")))}</div></td>
        <td>${escapeHtml(String(pick(order, ["userName", "customerName"], "—")))}</td>
        <td><span class="status-badge status-badge--${escapeHtml(status)}">${escapeHtml(STATUS_LABELS[status] || status)}</span></td>
        <td>${escapeHtml(String(pick(order, ["payWay", "paymentMethod"], "未知")))}</td>
        <td class="align-right money">${amount === null ? "—" : formatCurrency(amount)}</td>
        <td>${escapeHtml(formatDateTime(pick(order, ["createTime", "orderDate", "createdAt"], "")))}</td>
        <td><button class="row-action" type="button" data-order-id="${escapeHtml(String(orderId))}">查看摘要</button></td>`;
      fragment.appendChild(row);
    });
    els.ordersBody.appendChild(fragment);
    els.orderTotal.textContent = `共 ${numberFormat.format(state.order.total)} 笔`;
    updatePagination();
  }

  function openOrderDetail(orderId) {
    const order = state.order.rowsById.get(String(orderId));
    if (!order) {
      showToast("订单摘要不可用", "当前结果页中找不到这笔订单，请刷新列表后重试。", "error");
      return;
    }
    state.returnFocus = document.activeElement;
    els.detailTitle.textContent = `订单 #${orderId}`;
    els.detailDrawer.classList.add("is-open");
    els.detailDrawer.setAttribute("aria-hidden", "false");
    els.drawerScrim.hidden = false;
    syncPageScrollLock();
    els.appShell.inert = true;
    setTimeout(() => els.closeDetail.focus(), 40);
    renderOrderSummary(order);
  }

  function renderOrderSummary(order) {
    const status = String(pick(order, ["status", "orderStatus"], "processing")).toLowerCase();
    const amount = pick(order, ["visibleAmount", "visiblePrice", "matchedAmount", "amount", "totalAmount"], null);
    const productSummary = String(pick(order, ["productSummary", "productName", "products"], "暂无商品摘要"));
    const productCount = toNumber(pick(order, ["productCount"], 0));
    const contentScope = String(pick(order, ["contentScope"], "")).toUpperCase();
    const scopeLabel = contentScope === "FULL" ? "完整订单范围" : contentScope === "BRAND" ? "当前品牌可见范围" : "当前账号可见范围";
    els.detailBody.innerHTML = `
      <div class="detail-notice" role="note"><strong>列表摘要</strong><p>当前后端未提供订单明细接口，以下内容来自订单列表返回数据。</p></div>
      <div class="detail-meta">
        <div><small>订单状态</small><strong><span class="status-badge status-badge--${escapeHtml(status)}">${escapeHtml(STATUS_LABELS[status] || status)}</span></strong></div>
        <div><small>下单时间</small><strong>${escapeHtml(formatDateTime(pick(order, ["createTime", "orderDate", "createdAt"], "")))}</strong></div>
        <div><small>购买用户</small><strong>${escapeHtml(String(pick(order, ["userName", "customerName"], "—")))}</strong></div>
        <div><small>支付方式</small><strong>${escapeHtml(String(pick(order, ["payWay", "paymentMethod"], "未知")))}</strong></div>
        <div><small>商品数量</small><strong>${productCount ? `${numberFormat.format(productCount)} 件` : "—"}</strong></div>
        <div><small>内容范围</small><strong>${escapeHtml(scopeLabel)}</strong></div>
      </div>
      <section class="detail-section"><h3>商品摘要</h3>
        <div class="detail-item"><div><p>${escapeHtml(productSummary)}</p><small>${escapeHtml(scopeLabel)}</small></div></div>
      </section>
      <div class="detail-total"><span>当前范围可见金额</span><strong>${amount === null ? "—" : formatCurrency(amount)}</strong></div>`;
  }

  function closeOrderDetail() {
    const wasOpen = els.detailDrawer.classList.contains("is-open");
    els.detailDrawer.classList.remove("is-open");
    els.detailDrawer.setAttribute("aria-hidden", "true");
    els.drawerScrim.hidden = true;
    els.appShell.inert = false;
    syncPageScrollLock();
    if (wasOpen && state.returnFocus instanceof HTMLElement) state.returnFocus.focus();
    state.returnFocus = null;
  }

  function productDetailParams() {
    const params = { range: state.brandOps.products.range };
    if (state.brandOps.products.range === "FILTERED") {
      params.startTime = state.filters.startTime;
      params.endTime = state.filters.endTime;
    }
    return params;
  }

  async function openProductDetail(productId) {
    const product = state.brandOps.products.rowsById.get(String(productId));
    if (!product) {
      showToast("商品信息已失效", "请重新加载商品列表后再查看详情。", "error");
      return;
    }
    closeOrderDetail();
    state.returnFocus = document.activeElement;
    const detailState = state.brandOps.detail;
    detailState.controller?.abort();
    detailState.controller = new AbortController();
    detailState.productId = String(productId);
    detailState.data = null;
    detailState.tab = "overview";
    detailState.reviews = { page: 1, pageSize: 10, total: 0, rating: "", startTime: "", endTime: "", rows: null };
    detailState.buyers = { page: 1, pageSize: 10, total: 0, keyword: "", rows: null };
    els.productDetailTitle.textContent = String(pick(product, ["productName", "name"], `商品 #${productId}`));
    els.productDetailBody.innerHTML = `<div class="product-detail-state"><div class="state-message"><div class="loading-rule"></div><p>正在聚合商品经营详情…</p></div></div>`;
    if (!els.productDrawer.open) els.productDrawer.showModal();
    syncPageScrollLock();
    setTimeout(() => els.closeProductDetail.focus(), 40);
    try {
      const data = await api(`/api/brand/products/${encodeURIComponent(productId)}`, {
        params: productDetailParams(),
        signal: detailState.controller.signal
      });
      if (detailState.productId !== String(productId)) return;
      detailState.data = data;
      renderProductDetail();
    } catch (error) {
      if (error.name === "AbortError") return;
      els.productDetailBody.innerHTML = `<div class="product-detail-state">${stateMessageHtml("!", "商品经营详情加载失败", readableError(error))}</div>`;
      const button = document.createElement("button");
      button.className = "secondary-button";
      button.type = "button";
      button.textContent = "重新加载";
      button.addEventListener("click", () => openProductDetail(productId));
      els.productDetailBody.querySelector(".state-message").appendChild(button);
    }
  }

  function renderProductDetail() {
    const data = state.brandOps.detail.data || {};
    const product = pick(data, ["product"], {}) || {};
    const metrics = pick(data, ["metrics"], {}) || {};
    const metricStatus = pick(data, ["metricStatus"], {}) || {};
    const category = String(pick(product, ["category"], "未分类"));
    els.productDetailTitle.textContent = String(pick(product, ["productName"], `商品 #${state.brandOps.detail.productId}`));
    els.productDetailBody.innerHTML = `
      <section class="product-detail-summary">
        <div class="product-detail-summary__identity">
          <div><p>商品 #${escapeHtml(String(pick(product, ["productId"], state.brandOps.detail.productId)))}</p><strong>${escapeHtml(String(pick(product, ["productName"], "未命名商品")))}</strong><span>${escapeHtml(String(pick(product, ["brand"], "—")))} · ${escapeHtml(CATEGORY_LABELS[category] || category)}</span></div>
          <div><p>当前售价</p><strong>${nullableCurrency(pick(product, ["currentPrice"], null))}</strong><span>商品表评分 ${nullableNumber(pick(product, ["rating"], null), 1)}</span></div>
        </div>
        <div class="product-metrics">
          ${productMetricHtml("销售量", `${numberFormat.format(toNumber(pick(metrics, ["salesVolume"], 0)))} 件`, "完成订单商品件数")}
          ${productMetricHtml("销售额", formatCurrency(pick(metrics, ["salesAmount"], 0)), "按订单明细累计")}
          ${productMetricHtml("购买用户", `${numberFormat.format(toNumber(pick(metrics, ["buyerCount", "purchasingUserCount"], 0)))} 人`, "当前范围去重")}
          ${productMetricHtml("评价记录", `${numberFormat.format(toNumber(pick(metrics, ["reviewCount"], 0)))} 条`, "当前评价范围")}
          ${productMetricHtml("评价平均分", nullableNumber(pick(metrics, ["reviewAverageRating"], null), 2), "区别于商品表评分")}
          ${productMetricHtml("复购率", metricRatioText(pick(metrics, ["repurchaseRate"], null), pick(metricStatus, ["repurchaseRate"], "AVAILABLE")), "至少两笔完成订单")}
          ${productMetricHtml("退货率", metricRatioText(pick(metrics, ["returnRate"], null), pick(metricStatus, ["returnRate"], "AVAILABLE")), "订单生命周期口径")}
          ${productMetricHtml("取消率", metricRatioText(pick(metrics, ["cancelRate"], null), pick(metricStatus, ["cancelRate"], "AVAILABLE")), "订单生命周期口径")}
          ${productMetricHtml("浏览量", metricCountText(pick(metrics, ["viewCount"], null), pick(metricStatus, ["viewCount"], "AVAILABLE")), "可信浏览事件")}
          ${productMetricHtml("购物车用户", metricCountText(pick(metrics, ["cartUserCount"], null), pick(metricStatus, ["cartUserCount"], "AVAILABLE")), "去重用户")}
          ${productMetricHtml("愿望清单用户", metricCountText(pick(metrics, ["wishlistUserCount"], null), pick(metricStatus, ["wishlistUserCount"], "AVAILABLE")), "去重用户")}
          ${productMetricHtml("统计口径", state.brandOps.products.range === "ALL" ? "全量历史" : "当前筛选", state.brandOps.products.range === "ALL" ? "不携带日期" : `${formatDate(state.filters.startTime)} 至 ${formatDate(state.filters.endTime)}`)}
        </div>
      </section>
      <nav class="product-detail-tabs" aria-label="商品详情视图">
        <button class="is-active" type="button" data-product-tab="overview" aria-pressed="true">用户构成</button>
        <button type="button" data-product-tab="reviews" aria-pressed="false">全部评价</button>
        <button type="button" data-product-tab="buyers" aria-pressed="false">购买用户</button>
      </nav>
      <div class="product-subview" id="product-subview"></div>`;
    renderProductSubview();
  }

  function productMetricHtml(label, value, note) {
    return `<div class="product-metric"><small>${escapeHtml(label)}</small><strong>${escapeHtml(value)}</strong><span>${escapeHtml(note)}</span></div>`;
  }

  function renderProductSubview() {
    const container = document.getElementById("product-subview");
    if (!container) return;
    window.ProductCharts?.dispose();
    const detailState = state.brandOps.detail;
    els.productDetailBody.querySelectorAll("[data-product-tab]").forEach(button => {
      const active = button.dataset.productTab === detailState.tab;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    if (detailState.tab === "reviews") return renderReviewSubview(container);
    if (detailState.tab === "buyers") return renderBuyerSubview(container);
    const data = detailState.data || {};
    const genders = asArray(pick(data, ["genderDistribution"], []));
    const segments = pick(data, ["customerSegments"], {}) || {};
    const genderItems = genders.map(item => ({
      label: genderLabel(pick(item, ["gender"], "UNKNOWN")),
      value: toNumber(pick(item, ["buyerCount"], 0))
    }));
    const segmentItems = [
      { label: "新客", value: toNumber(pick(segments, ["newBuyerCount"], 0)) },
      { label: "老客", value: toNumber(pick(segments, ["oldBuyerCount"], 0)) },
      { label: "无法分类", value: toNumber(pick(segments, ["unclassifiedBuyerCount"], 0)) }
    ];
    container.innerHTML = `
      <div class="product-insight-grid">
        <section class="product-detail-section product-insight-panel">
          <div class="product-insight-panel__head"><div><h3>购买用户性别分布</h3><p>当前范围去重购买用户构成</p></div><span>人数占比</span></div>
          <div class="product-insight-chart" id="product-gender-chart" role="img" aria-label="购买用户性别分布环形图"></div>
        </section>
        <section class="product-detail-section product-insight-panel">
          <div class="product-insight-panel__head"><div><h3>注册时间新老客</h3><p>按账号注册时间划分</p></div><span>人数对比</span></div>
          <div class="product-insight-chart" id="product-segment-chart" role="img" aria-label="注册时间新老客横向条形图"></div>
        </section>
      </div>`;
    window.ProductCharts?.renderGender(document.getElementById("product-gender-chart"), genderItems);
    window.ProductCharts?.renderSegments(document.getElementById("product-segment-chart"), segmentItems);
  }

  function reviewHtml(review) {
    const buyer = pick(review, ["buyer"], null);
    const buyerName = buyer ? pick(buyer, ["displayName"], "匿名用户") : "用户关联缺失";
    const text = pick(review, ["reviewText"], null);
    return `<article class="review-item"><div class="review-item__meta"><span><strong class="review-item__stars">${toNumber(pick(review, ["rating"], 0))}/5 分</strong> · ${escapeHtml(String(buyerName))}</span><time>${escapeHtml(formatDateTime(pick(review, ["reviewDate"], "")))}</time></div><p>${escapeHtml(text === null ? "未填写评价内容" : String(text))}</p></article>`;
  }

  function handleProductDetailClick(event) {
    const tabButton = event.target.closest("[data-product-tab]");
    if (tabButton) {
      state.brandOps.detail.tab = tabButton.dataset.productTab;
      renderProductSubview();
      if (state.brandOps.detail.tab === "reviews" && state.brandOps.detail.reviews.rows === null) loadReviews().catch(() => {});
      if (state.brandOps.detail.tab === "buyers" && state.brandOps.detail.buyers.rows === null) loadBuyers().catch(() => {});
      return;
    }
    const reviewPage = event.target.closest("[data-review-page]");
    if (reviewPage) {
      changeDetailPage("reviews", Number(reviewPage.dataset.reviewPage));
      return;
    }
    const buyerPage = event.target.closest("[data-buyer-page]");
    if (buyerPage) changeDetailPage("buyers", Number(buyerPage.dataset.buyerPage));
  }

  function handleProductDetailSubmit(event) {
    if (event.target.id === "review-filter-form") {
      event.preventDefault();
      const reviews = state.brandOps.detail.reviews;
      reviews.startTime = document.getElementById("review-start").value;
      reviews.endTime = document.getElementById("review-end").value;
      reviews.rating = document.getElementById("review-rating").value;
      reviews.pageSize = Number(document.getElementById("review-page-size").value);
      if (reviews.startTime && reviews.endTime && reviews.startTime > reviews.endTime) return showToast("评价日期无效", "评价开始日期不能晚于结束日期。", "error");
      reviews.page = 1;
      loadReviews().catch(() => {});
    } else if (event.target.id === "buyer-filter-form") {
      event.preventDefault();
      const buyers = state.brandOps.detail.buyers;
      const keyword = document.getElementById("buyer-keyword").value.trim();
      if (keyword && !/^\d+$/.test(keyword)) return showToast("只支持用户 ID", "购买用户查询不支持姓名或邮箱，请输入数字用户 ID。", "error");
      buyers.keyword = keyword;
      buyers.pageSize = Number(document.getElementById("buyer-page-size").value);
      buyers.page = 1;
      loadBuyers().catch(() => {});
    }
  }

  function renderReviewSubview(container) {
    const reviews = state.brandOps.detail.reviews;
    container.innerHTML = `
      <form class="product-subview__toolbar" id="review-filter-form">
        <label class="inline-select">开始日期<input id="review-start" type="date" max="${BUSINESS_DATE}" value="${escapeHtml(reviews.startTime)}"></label>
        <label class="inline-select">结束日期<input id="review-end" type="date" max="${BUSINESS_DATE}" value="${escapeHtml(reviews.endTime)}"></label>
        <label class="inline-select">评分<select id="review-rating"><option value="">全部评分</option>${[5,4,3,2,1].map(value => `<option value="${value}"${String(value) === reviews.rating ? " selected" : ""}>${value} 分</option>`).join("")}</select></label>
        <label class="inline-select">每页<select id="review-page-size"><option value="10"${reviews.pageSize === 10 ? " selected" : ""}>10 条</option><option value="20"${reviews.pageSize === 20 ? " selected" : ""}>20 条</option><option value="50"${reviews.pageSize === 50 ? " selected" : ""}>50 条</option></select></label>
        <button class="secondary-button" type="submit">查询评价</button>
      </form>
      <div class="product-subview-list" id="review-list">${reviews.rows === null ? `<div class="state-message"><div class="loading-rule"></div><p>正在读取评价…</p></div>` : renderReviewRows(reviews.rows)}</div>
      ${detailPaginationHtml("review", reviews.page, reviews.pageSize, reviews.total)}`;
  }

  async function loadReviews() {
    const detailState = state.brandOps.detail;
    const reviews = detailState.reviews;
    const params = { page: reviews.page, num: reviews.pageSize, startTime: reviews.startTime, endTime: reviews.endTime, rating: reviews.rating };
    try {
      const data = await api(`/api/brand/products/${encodeURIComponent(detailState.productId)}/reviews`, { params, signal: detailState.controller?.signal });
      if (!els.productDrawer.open) return;
      reviews.rows = asArray(pick(data, ["reviewList"], []));
      reviews.total = toNumber(pick(data, ["total"], reviews.rows.length));
      renderProductSubview();
    } catch (error) {
      if (error.name === "AbortError") return;
      reviews.rows = [];
      renderProductSubview();
      const list = document.getElementById("review-list");
      if (list) list.innerHTML = stateMessageHtml("!", "评价加载失败", readableError(error));
    }
  }

  function renderReviewRows(rows) {
    return rows.length ? `<div class="reviews-preview">${rows.map(reviewHtml).join("")}</div>` : stateMessageHtml("—", "没有符合条件的评价", "可清除评分或日期条件后重试。");
  }

  function renderBuyerSubview(container) {
    const buyers = state.brandOps.detail.buyers;
    container.innerHTML = `
      <form class="product-subview__toolbar" id="buyer-filter-form">
        <label class="search-field" for="buyer-keyword"><svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="9" cy="9" r="5.5"/><path d="m13 13 4 4"/></svg><input id="buyer-keyword" type="search" inputmode="numeric" value="${escapeHtml(buyers.keyword)}" placeholder="精确输入用户 ID"></label>
        <label class="inline-select">每页<select id="buyer-page-size"><option value="10"${buyers.pageSize === 10 ? " selected" : ""}>10 条</option><option value="20"${buyers.pageSize === 20 ? " selected" : ""}>20 条</option><option value="50"${buyers.pageSize === 50 ? " selected" : ""}>50 条</option></select></label>
        <button class="secondary-button" type="submit">查询用户</button>
      </form>
      <div class="product-subview-list" id="buyer-list">${buyers.rows === null ? `<div class="state-message"><div class="loading-rule"></div><p>正在读取购买用户…</p></div>` : renderBuyerRows(buyers.rows)}</div>
      ${detailPaginationHtml("buyer", buyers.page, buyers.pageSize, buyers.total)}`;
  }

  async function loadBuyers() {
    const detailState = state.brandOps.detail;
    const buyers = detailState.buyers;
    const params = { page: buyers.page, num: buyers.pageSize, range: state.brandOps.products.range, keyWord: buyers.keyword };
    if (state.brandOps.products.range === "FILTERED") {
      params.startTime = state.filters.startTime;
      params.endTime = state.filters.endTime;
    }
    try {
      const data = await api(`/api/brand/products/${encodeURIComponent(detailState.productId)}/buyers`, { params, signal: detailState.controller?.signal });
      if (!els.productDrawer.open) return;
      buyers.rows = asArray(pick(data, ["buyerList"], []));
      buyers.total = toNumber(pick(data, ["total"], buyers.rows.length));
      renderProductSubview();
    } catch (error) {
      if (error.name === "AbortError") return;
      buyers.rows = [];
      renderProductSubview();
      const list = document.getElementById("buyer-list");
      if (list) list.innerHTML = stateMessageHtml("!", "购买用户加载失败", readableError(error));
    }
  }

  function renderBuyerRows(rows) {
    if (!rows.length) return stateMessageHtml("—", "没有符合条件的购买用户", "只统计可关联到用户的完成订单。");
    return rows.map(buyer => `<div class="buyer-row"><div><strong>${escapeHtml(String(pick(buyer, ["displayName"], "未提供显示名")))}</strong><small>用户 #${escapeHtml(String(pick(buyer, ["userId"], "—")))}</small></div><span>${escapeHtml(genderLabel(pick(buyer, ["gender"], "UNKNOWN")))}</span><span class="buyer-type">${escapeHtml(customerTypeLabel(pick(buyer, ["customerType"], "UNKNOWN")))}</span><span>${numberFormat.format(toNumber(pick(buyer, ["purchaseOrderCount"], 0)))} 笔订单</span><span>${escapeHtml(formatDateTime(pick(buyer, ["lastPurchaseAt"], "")))}<small>注册 ${escapeHtml(formatDate(pick(buyer, ["signupDate"], "")))}</small></span></div>`).join("");
  }

  function detailPaginationHtml(prefix, page, pageSize, total) {
    const pageCount = Math.max(1, Math.ceil(total / pageSize));
    return `<footer class="pagination subview-pagination"><p>${total ? `共 ${numberFormat.format(total)} 条` : "暂无结果"}</p><div><button class="icon-button" type="button" data-${prefix}-page="-1" aria-label="上一页"${page <= 1 ? " disabled" : ""}><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12.5 4-6 6 6 6"/></svg></button><span>${page} / ${pageCount}</span><button class="icon-button" type="button" data-${prefix}-page="1" aria-label="下一页"${page >= pageCount ? " disabled" : ""}><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7.5 4 6 6-6 6"/></svg></button></div></footer>`;
  }

  function changeDetailPage(type, delta) {
    const collection = state.brandOps.detail[type];
    const pageCount = Math.max(1, Math.ceil(collection.total / collection.pageSize));
    const next = Math.min(pageCount, Math.max(1, collection.page + delta));
    if (next === collection.page) return;
    collection.page = next;
    collection.rows = null;
    renderProductSubview();
    (type === "reviews" ? loadReviews() : loadBuyers()).catch(() => {});
  }

  function closeProductDetail() {
    const wasOpen = els.productDrawer.open;
    window.ProductCharts?.dispose();
    if (wasOpen) els.productDrawer.close();
    state.brandOps.detail.controller?.abort();
    state.brandOps.detail.controller = null;
    state.brandOps.detail.productId = null;
    syncPageScrollLock();
    if (wasOpen && state.returnFocus instanceof HTMLElement) state.returnFocus.focus();
    state.returnFocus = null;
  }

  function canManageUsers() {
    const role = normalizeRole(pick(state.user, ["role"], ""));
    return role === "SUPER_ADMIN";
  }

  async function loadUsers() {
    if (!canManageUsers()) {
      showToast("无人员管理权限", "仅超级管理员可以读取人员列表。", "error");
      return;
    }
    const version = ++state.people.requestVersion;
    state.people.controller?.abort();
    state.people.controller = new AbortController();
    state.people.loading = true;
    showUsersLoading();
    const params = { page: state.people.page, num: state.people.pageSize };
    if (state.people.keyword) params.keyWord = state.people.keyword;
    if (state.people.role) params.role = state.people.role;
    if (state.people.status) params.status = state.people.status;
    try {
      const data = await api("/api/admin/users", { params, signal: state.people.controller.signal });
      if (version !== state.people.requestVersion) return;
      const users = asArray(pick(data, ["userList", "users", "items"], []));
      state.people.total = toNumber(pick(data, ["total", "totalElements", "totalCount"], users.length));
      state.people.loaded = true;
      renderUsers(users);
    } catch (error) {
      if (error.name !== "AbortError") showUsersError(error);
      throw error;
    } finally {
      if (version === state.people.requestVersion) state.people.loading = false;
    }
  }

  function renderUsers(users) {
    els.usersBody.replaceChildren();
    state.people.rowsById.clear();
    if (!users.length) {
      els.usersState.hidden = false;
      els.usersState.innerHTML = stateMessageHtml("—", "没有符合条件的人员", "可清除关键字或调整角色、状态条件后重试。");
      els.usersTotal.textContent = "共 0 人";
      updateUsersPagination();
      return;
    }
    els.usersState.hidden = true;
    const currentUserId = String(pick(state.user, ["userId", "id"], ""));
    const fragment = document.createDocumentFragment();
    users.forEach(rawUser => {
      const user = normalizeManagedUser(rawUser);
      state.people.rowsById.set(String(user.userId), user);
      const protectedUser = user.role === "SUPER_ADMIN" || String(user.userId) === currentUserId;
      const row = document.createElement("tr");
      row.innerHTML = `
        <td><span class="order-id">#${escapeHtml(user.userId)}</span></td>
        <td><div class="user-identity"><strong>${escapeHtml(user.name)}</strong><small title="${escapeHtml(user.email || "未设置邮箱")}">${escapeHtml(user.email || "未设置邮箱")}</small></div></td>
        <td><span class="role-label">${escapeHtml(ROLE_LABELS[user.role] || user.role)}</span></td>
        <td>${escapeHtml(user.brand || "—")}</td>
        <td><span class="account-status account-status--${user.status.toLowerCase()}">${escapeHtml(ACCOUNT_STATUS_LABELS[user.status] || user.status)}</span></td>
        <td>${escapeHtml(formatDate(user.createdAt))}</td>
        <td>${protectedUser ? '<span class="protected-label">受保护</span>' : `<button class="row-action" type="button" data-edit-user="${escapeHtml(String(user.userId))}">编辑</button>`}</td>`;
      fragment.appendChild(row);
    });
    els.usersBody.appendChild(fragment);
    els.usersTotal.textContent = `共 ${numberFormat.format(state.people.total)} 人`;
    updateUsersPagination();
  }

  function normalizeManagedUser(user) {
    return {
      userId: String(pick(user, ["userId", "id"], "—")),
      email: String(pick(user, ["email"], "")),
      name: String(pick(user, ["name", "userName", "username"], "未命名用户")),
      role: normalizeRole(pick(user, ["role"], "NORMAL_USER")),
      brand: String(pick(user, ["brand", "managedBrand"], "")),
      status: String(pick(user, ["status", "accountStatus"], "ACTIVE")).toUpperCase(),
      version: toNumber(pick(user, ["version", "lockVersion"], 0)),
      createdAt: String(pick(user, ["createdAt", "createTime", "signupDate"], ""))
    };
  }

  function showUsersLoading() {
    els.usersBody.replaceChildren();
    els.usersState.hidden = false;
    els.usersState.innerHTML = `<div class="state-message"><div class="loading-rule"></div><p>正在读取人员列表…</p></div>`;
    els.usersTotal.textContent = "正在读取";
  }

  function showUsersError(error) {
    els.usersBody.replaceChildren();
    els.usersState.hidden = false;
    const detail = error.status === 404
      ? "当前 Java 后端尚未实现接口文档中的 GET /api/admin/users；前端已按 P1–P3 契约就绪。"
      : readableError(error);
    els.usersState.innerHTML = stateMessageHtml("!", "人员列表暂未加载", detail);
    const button = document.createElement("button");
    button.className = "secondary-button";
    button.type = "button";
    button.textContent = "重新加载";
    button.addEventListener("click", () => loadUsers().catch(() => {}));
    els.usersState.querySelector(".state-message").appendChild(button);
    state.people.total = 0;
    els.usersTotal.textContent = "加载失败";
    updateUsersPagination();
  }

  function applyUserFilters(event) {
    event.preventDefault();
    state.people.keyword = els.usersKeyword.value.trim();
    state.people.role = els.usersRole.value;
    state.people.status = els.usersStatus.value;
    state.people.pageSize = Number(els.usersPageSize.value);
    state.people.page = 1;
    loadUsers().catch(() => {});
  }

  function changeUserPage(delta) {
    const pageCount = Math.max(1, Math.ceil(state.people.total / state.people.pageSize));
    const next = Math.min(pageCount, Math.max(1, state.people.page + delta));
    if (next === state.people.page) return;
    state.people.page = next;
    loadUsers().catch(() => {});
    els.userManagement.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function updateUsersPagination() {
    const pageCount = Math.max(1, Math.ceil(state.people.total / state.people.pageSize));
    if (state.people.page > pageCount) state.people.page = pageCount;
    const from = state.people.total ? (state.people.page - 1) * state.people.pageSize + 1 : 0;
    const to = Math.min(state.people.total, state.people.page * state.people.pageSize);
    els.usersPageSummary.textContent = state.people.total ? `显示第 ${numberFormat.format(from)}–${numberFormat.format(to)} 人` : "暂无结果";
    els.usersPageIndicator.textContent = `${state.people.page} / ${pageCount}`;
    els.usersPreviousPage.disabled = state.people.page <= 1;
    els.usersNextPage.disabled = state.people.page >= pageCount;
  }

  function openUserDialog(mode, user = null) {
    if (!canManageUsers()) {
      showToast("无人员管理权限", "仅超级管理员可以修改人员。", "error");
      return;
    }
    if (mode === "edit" && !user) {
      showToast("人员信息已失效", "请重新加载人员列表后再试。", "error");
      return;
    }
    populateManagedBrands(user?.brand || "");
    state.people.editingId = mode === "edit" ? String(user.userId) : null;
    els.userForm.dataset.original = mode === "edit" ? JSON.stringify(user) : "";
    els.userDialogMode.textContent = mode === "edit" ? `编辑用户 #${user.userId}` : "P2 新增管理员";
    els.userDialogTitle.textContent = mode === "edit" ? "编辑人员权限" : "新增品牌管理员";
    els.managedEmailGroup.hidden = false;
    els.managedEmail.disabled = mode === "edit";
    els.managedEmail.value = mode === "edit" ? user.email : "";
    els.managedPasswordGroup.hidden = mode === "edit";
    els.managedPassword.required = mode === "create";
    els.managedPassword.value = "";
    els.managedName.value = mode === "edit" ? user.name : "";
    els.managedRole.value = mode === "edit" ? user.role : "ADMIN";
    els.managedRole.disabled = mode === "create";
    els.managedStatus.value = mode === "edit" ? user.status : "ACTIVE";
    els.managedBrand.value = mode === "edit" ? user.brand : "";
    els.managedVersion.value = mode === "edit" ? String(user.version) : "";
    els.userFormError.hidden = true;
    updateManagedBrandRequirement();
    els.userDialog.showModal();
    syncPageScrollLock();
    setTimeout(() => (mode === "create" ? els.managedEmail : els.managedName).focus(), 40);
  }

  function populateManagedBrands(currentBrand = "") {
    const liveBrands = state.brands.map(item => item.value);
    const brands = uniqueBy([...(liveBrands.length ? liveBrands : DOCUMENTED_BRANDS), currentBrand].filter(Boolean), item => item).sort((a, b) => a.localeCompare(b));
    els.managedBrand.replaceChildren(new Option("不分配品牌", ""));
    brands.forEach(brand => els.managedBrand.appendChild(new Option(brand, brand)));
  }

  function updateManagedBrandRequirement() {
    const role = els.managedRole.value;
    const status = els.managedStatus.value;
    if (role === "NORMAL_USER") {
      els.managedBrand.value = "";
      els.managedBrand.disabled = true;
      els.managedBrand.required = false;
      els.managedBrandHint.textContent = "普通用户不允许绑定管理品牌。";
      return;
    }
    els.managedBrand.disabled = false;
    els.managedBrand.required = status === "ACTIVE";
    els.managedBrandHint.textContent = status === "ACTIVE" ? "启用的品牌管理员必须选择一个品牌。" : "停用管理员可以暂不分配品牌。";
  }

  async function saveManagedUser(event) {
    event.preventDefault();
    const editing = Boolean(state.people.editingId);
    const name = els.managedName.value.trim();
    const role = editing ? els.managedRole.value : "ADMIN";
    const status = els.managedStatus.value;
    const brand = role === "ADMIN" ? (els.managedBrand.value || null) : null;
    els.userFormError.hidden = true;
    if (!name || name.length > 40) return showUserFormError("姓名需要填写 1–40 个字符。");
    if (role === "ADMIN" && status === "ACTIVE" && !brand) return showUserFormError("启用的品牌管理员必须选择一个品牌。");
    let body;
    let path;
    let method;
    if (editing) {
      const version = Number(els.managedVersion.value);
      if (!Number.isInteger(version) || version < 0) return showUserFormError("人员版本号无效，请重新加载列表后再编辑。");
      body = { version, name, role, brand, status };
      path = `/api/admin/users/${encodeURIComponent(state.people.editingId)}`;
      method = "PATCH";
    } else {
      const email = els.managedEmail.value.trim();
      const password = els.managedPassword.value;
      if (!email || !els.managedEmail.validity.valid) return showUserFormError("请输入有效邮箱地址。");
      if (password.length < 8 || password.length > 64) return showUserFormError("初始密码需要填写 8–64 个字符。");
      body = { email, password, name, role: "ADMIN", brand, status };
      path = "/api/admin/users";
      method = "POST";
    }
    setButtonLoading(els.saveUser, true, "正在保存…");
    try {
      await api(path, { method, body });
      closeUserDialog();
      showToast(editing ? "人员信息已更新" : "管理员已创建", editing ? "姓名、角色、品牌和账号状态已按后端结果刷新。" : "新管理员已加入当前人员列表。", "success");
      await loadUsers();
    } catch (error) {
      const databaseRoleConflict = /chk_role_user_type|constraint.*role|user_type/i.test(error.message || "");
      showUserFormError(databaseRoleConflict
        ? "数据库的角色与历史用户类型约束阻止了本次角色转换。请先只修改姓名、品牌或状态；角色迁移需要后端同步兼容 user_type 约束。"
        : readableError(error));
    } finally {
      setButtonLoading(els.saveUser, false);
    }
  }

  function showUserFormError(message) {
    els.userFormError.textContent = message;
    els.userFormError.hidden = false;
    els.userFormError.focus?.();
  }

  function closeUserDialog() {
    if (els.userDialog.open) els.userDialog.close();
    state.people.editingId = null;
    delete els.userForm.dataset.original;
    syncPageScrollLock();
  }

  function syncPageScrollLock() {
    const locked = els.userDialog.open
      || els.detailDrawer.classList.contains("is-open")
      || els.productDrawer.open
      || els.sidebar.classList.contains("is-open");
    document.body.classList.toggle("is-locked", locked);
  }

  function initializeScrollNavigation() {
    const sections = [
      { id: "overview", nav: "overview" },
      { id: "product-operations", nav: "products" },
      { id: "orders", nav: "orders" },
      { id: "user-management", nav: "users" }
    ];
    let scheduled = false;
    const update = () => {
      scheduled = false;
      const marker = Math.min(window.innerHeight * .34, 260);
      let active = sections[0];
      sections.forEach(section => {
        const element = document.getElementById(section.id);
        if (element && !element.hidden && element.getBoundingClientRect().top <= marker) active = section;
      });
      document.querySelectorAll("[data-nav]").forEach(link => {
        const selected = link.dataset.nav === active.nav;
        link.classList.toggle("is-active", selected);
        if (selected) link.setAttribute("aria-current", "page");
        else link.removeAttribute("aria-current");
      });
      if (active.nav === "users" && canManageUsers() && !state.people.loaded && !state.people.loading) loadUsers().catch(() => {});
    };
    const schedule = () => {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(update);
    };
    window.addEventListener("scroll", schedule, { passive: true });
    window.addEventListener("resize", schedule);
    schedule();
  }

  function applyFilters(event) {
    event.preventDefault();
    const startTime = els.startDate.value;
    const endTime = els.endDate.value;
    if (!startTime || !endTime) {
      showToast("日期不完整", "请选择起始日期和结束日期。", "error");
      return;
    }
    if (startTime > endTime) {
      showToast("日期范围无效", "起始日期不能晚于结束日期。", "error");
      return;
    }
    if (endTime > BUSINESS_DATE) {
      showToast("超出业务日", `结束日期不能晚于固定业务日 ${BUSINESS_DATE}。`, "error");
      return;
    }
    state.filters.startTime = startTime;
    state.filters.endTime = endTime;
    state.filters.categories = selectedValues("category");
    state.filters.brands = selectedValues("brand");
    closeProductDetail();
    state.order.page = 1;
    syncQuickRangeSelection(startTime, endTime);
    loadDashboard({ announce: true });
    if (state.brandOps.products.range === "FILTERED") {
      state.brandOps.products.page = 1;
      loadProducts().catch(() => {});
    }
  }

  function resetDashboardFiltersToDefaults() {
    state.filters.startTime = "2025-10-16";
    state.filters.endTime = BUSINESS_DATE;
    state.filters.categories = [];
    state.filters.brands = [];
    els.filterForm.reset();
    els.startDate.value = state.filters.startTime;
    els.endDate.value = state.filters.endTime;
    [els.categorySelect, els.brandSelect].forEach(detail => {
      detail.removeAttribute("open");
      delete detail.dataset.selectionSnapshot;
    });
    syncQuickRangeSelection(state.filters.startTime, state.filters.endTime);
  }

  function handleFilterSelectToggle(detail) {
    const type = detail.id === "category-select" ? "category" : "brand";
    if (detail.open) {
      detail.dataset.selectionSnapshot = selectedValues(type).join("\u001f");
      return;
    }
    if (!Object.prototype.hasOwnProperty.call(detail.dataset, "selectionSnapshot")) return;
    const before = detail.dataset.selectionSnapshot;
    delete detail.dataset.selectionSnapshot;
    if (before !== selectedValues(type).join("\u001f")) els.filterForm.requestSubmit();
  }

  function syncQuickRangeSelection(startTime, endTime) {
    document.querySelectorAll("[data-days]").forEach(button => {
      const end = parseDate(BUSINESS_DATE);
      const start = new Date(end);
      start.setDate(start.getDate() - (Number(button.dataset.days) - 1));
      const active = endTime === BUSINESS_DATE && startTime === toDateInput(start);
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
  }

  function resetFilters() {
    closeProductDetail();
    els.startDate.value = "2025-10-16";
    els.endDate.value = BUSINESS_DATE;
    document.querySelectorAll('[data-options="category"] input, [data-options="brand"] input:not(:disabled)').forEach(input => { input.checked = false; });
    if (normalizeRole(pick(state.user, ["role"])) === "ADMIN") {
      document.querySelectorAll('[data-options="brand"] input').forEach(input => { input.checked = true; });
    }
    updateMultiSummary("category");
    updateMultiSummary("brand");
    setQuickRange(30, false);
    state.filters.categories = [];
    state.filters.brands = normalizeRole(pick(state.user, ["role"])) === "ADMIN" ? state.brands.map(item => item.value) : [];
    state.order.page = 1;
    loadDashboard({ announce: true });
    if (state.brandOps.products.range === "FILTERED") {
      state.brandOps.products.page = 1;
      loadProducts().catch(() => {});
    }
  }

  function setQuickRange(days, load = true) {
    const end = parseDate(BUSINESS_DATE);
    const start = new Date(end);
    start.setDate(start.getDate() - (days - 1));
    els.startDate.value = toDateInput(start);
    els.endDate.value = BUSINESS_DATE;
    document.querySelectorAll("[data-days]").forEach(button => {
      const active = Number(button.dataset.days) === days;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    if (load) {
      closeProductDetail();
      state.filters.startTime = els.startDate.value;
      state.filters.endTime = els.endDate.value;
      state.filters.categories = selectedValues("category");
      state.filters.brands = selectedValues("brand");
      state.order.page = 1;
      loadDashboard({ announce: true });
      if (state.brandOps.products.range === "FILTERED") {
        state.brandOps.products.page = 1;
        loadProducts().catch(() => {});
      }
    }
  }

  function applyOrderFilters(event) {
    event.preventDefault();
    const keyword = els.orderKeyword.value.trim();
    if (/^\d+$/.test(keyword)) {
      showToast("仅支持商品名称", "订单搜索不接受纯数字订单号，请输入商品名称。", "error");
      els.orderKeyword.focus();
      return;
    }
    state.order.keyword = keyword;
    state.order.status = els.orderStatus.value;
    state.order.pageSize = Number(els.pageSize.value);
    state.order.page = 1;
    loadOrders().catch(() => {});
  }

  function changePage(delta) {
    const pageCount = Math.max(1, Math.ceil(state.order.total / state.order.pageSize));
    const next = Math.min(pageCount, Math.max(1, state.order.page + delta));
    if (next === state.order.page) return;
    state.order.page = next;
    loadOrders().catch(() => {});
    document.getElementById("orders").scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function renderMultiSelect(type, options, locked = false, emptyText = "暂无可选项", emptySummary = "暂无可选项") {
    const detail = document.getElementById(`${type}-select`);
    const container = document.querySelector(`[data-options="${type}"]`);
    const summaryElement = detail.querySelector("summary");
    container.replaceChildren();
    detail.classList.toggle("is-disabled", !options.length);
    summaryElement.setAttribute("aria-disabled", String(!options.length));
    summaryElement.tabIndex = options.length ? 0 : -1;
    if (!options.length) {
      detail.dataset.emptySummary = emptySummary;
      const note = document.createElement("p");
      note.textContent = emptyText;
      note.className = "state-note";
      container.appendChild(note);
      detail.removeAttribute("open");
      detail.querySelector("[data-summary]").textContent = emptySummary;
      return;
    }
    delete detail.dataset.emptySummary;
    const selected = type === "category" ? state.filters.categories : state.filters.brands;
    options.forEach(option => {
      const label = document.createElement("label");
      label.className = "multi-option";
      const input = document.createElement("input");
      input.type = "checkbox";
      input.value = option.value;
      input.checked = locked || selected.includes(option.value);
      input.disabled = locked;
      input.addEventListener("change", () => updateMultiSummary(type));
      const text = document.createElement("span");
      text.textContent = option.label;
      const code = document.createElement("small");
      code.textContent = option.label === option.value ? "" : option.value;
      text.appendChild(code);
      label.append(input, text);
      container.appendChild(label);
    });
    updateMultiSummary(type);
  }

  function updateMultiSummary(type) {
    const detail = document.getElementById(`${type}-select`);
    const summary = detail.querySelector("[data-summary]");
    if (detail.classList.contains("is-disabled")) {
      summary.textContent = detail.dataset.emptySummary || "暂无可选项";
      return;
    }
    const checked = [...detail.querySelectorAll("input:checked")];
    const total = detail.querySelectorAll("input").length;
    const noun = type === "category" ? "品类" : "品牌";
    if (!checked.length) summary.textContent = `全部${noun}`;
    else if (checked.length === 1) summary.textContent = checked[0].parentElement.querySelector("span").childNodes[0].textContent;
    else if (checked.length === total) summary.textContent = type === "category" ? "全部品类" : `${checked.length} 个品牌`;
    else summary.textContent = `已选 ${checked.length} 项`;
  }

  function selectedValues(type) {
    return [...document.querySelectorAll(`[data-options="${type}"] input:checked`)].map(input => input.value);
  }

  function normalizeTrend(data) {
    const labels = asArray(pick(data, ["dateList", "labels", "periodStartList"], []));
    const amounts = asArray(pick(data, ["salesAmountList", "amounts", "sales"], [])).map(toNumber);
    const orders = asArray(pick(data, ["orderCountList", "orders", "counts"], [])).map(toNumber);
    return { labels: labels.map(formatAxisDate), amounts, orders };
  }

  function normalizeRanking(data) {
    return asArray(pick(data, ["rankList", "ranking", "items"], [])).map(item => {
      const product = pick(item, ["product"], item) || {};
      const fullName = String(pick(product, ["productName", "name"], "未命名商品"));
      const category = String(pick(product, ["category"], "未分类"));
      return {
        name: fullName,
        fullName,
        amount: toNumber(pick(item, ["salesAmount", "amount", "value"], 0)),
        quantity: toNumber(pick(item, ["salesVolume", "quantity", "count"], 0)),
        hot: Boolean(pick(item, ["isHot", "hot"], false)),
        categoryValue: category,
        category: String(pick(product, ["categoryLabel"], CATEGORY_LABELS[category] || category))
      };
    });
  }

  function normalizeCategoryStats(data) {
    return asArray(pick(data, ["categoryList", "items", "categories"], [])).map(item => ({
      value: toNumber(pick(item, ["salesAmount", "amount"], 0)),
      label: String(pick(item, ["categoryName", "label", "category"], "未归类")),
      category: pick(item, ["category"], null),
      uncategorized: Boolean(pick(item, ["isUncategorized"], false)),
      quantity: pick(item, ["salesVolume"], null),
      orders: toNumber(pick(item, ["uniqueOrderCount", "orderCount"], 0)),
      ratio: toNumber(pick(item, ["salesRatio", "ratio"], 0))
    }));
  }

  function deriveOrderDistribution(orders, type) {
    const totals = new Map();
    orders.forEach(order => {
      const raw = type === "status"
        ? String(pick(order, ["status", "orderStatus"], "unknown")).toLowerCase()
        : String(pick(order, ["payWay", "paymentMethod"], "未知")).trim() || "未知";
      const label = type === "status" ? (STATUS_LABELS[raw] || raw || "未知状态") : raw;
      totals.set(label, (totals.get(label) || 0) + 1);
    });
    return [...totals.entries()].map(([name, value]) => ({ name, value }));
  }

  function commonParams() {
    return {
      startTime: state.filters.startTime,
      endTime: state.filters.endTime,
      category: state.filters.categories,
      brand: state.filters.brands
    };
  }

  function rankingParams() {
    const params = commonParams();
    return {
      ...params,
      metric: state.rankingMetric,
      // A4 使用 ProductRankingRequest 包装 Filter；保留文档中的扁平参数，
      // 同时提供 Spring 可稳定绑定的嵌套属性，避免筛选被当成默认范围。
      "filter.startTime": params.startTime,
      "filter.endTime": params.endTime,
      "filter.category": params.category,
      "filter.brand": params.brand
    };
  }

  async function api(path, options = {}) {
    const { method = "GET", body, params, signal, auth = true } = options;
    const url = new URL(`${API_BASE}${path}`, window.location.origin);
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (Array.isArray(value)) value.filter(item => item !== "").forEach(item => url.searchParams.append(key, item));
        else if (value !== undefined && value !== null && value !== "") url.searchParams.set(key, value);
      });
    }
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth && state.token) headers.Authorization = `Bearer ${state.token}`;
    if (method !== "GET" && state.csrfToken) headers["X-CSRF-Token"] = state.csrfToken;
    let response;
    try {
      response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal, credentials: "include" });
    } catch (error) {
      if (error.name === "AbortError") throw error;
      throw new ApiError("无法连接后端服务，请确认 Spring Boot 已在当前地址运行。", 0);
    }
    const contentType = response.headers.get("content-type") || "";
    const payload = contentType.includes("json") ? await response.json().catch(() => null) : await response.text().catch(() => "");
    if (response.status === 401) {
      clearSession();
      showAuth();
      throw new ApiError("登录已失效，请重新登录。", 401);
    }
    if (!response.ok) {
      throw new ApiError(readMessage(payload) || `请求失败（HTTP ${response.status}）`, response.status, pick(payload, ["code"], null));
    }
    if (payload && typeof payload === "object" && Object.prototype.hasOwnProperty.call(payload, "code")) {
      const code = Number(payload.code);
      if (code !== 200) throw new ApiError(readMessage(payload) || "接口返回失败。", response.status, code);
      return payload.data;
    }
    return payload;
  }

  async function connectRealtime() {
    if (!state.token || typeof WebSocket === "undefined") return;
    const generation = ++state.realtime.generation;
    clearTimeout(state.realtime.reconnectTimer);
    state.realtime.reconnectTimer = null;
    clearRealtimeHeartbeat();
    if (state.realtime.socket) {
      state.realtime.socket.onclose = null;
      state.realtime.socket.close(1000, "reconnect");
      state.realtime.socket = null;
    }
    setConnection("loading", "正在连接实时更新");
    try {
      const ticketResponse = await api("/api/realtime/tickets", { method: "POST" });
      if (generation !== state.realtime.generation || !state.token) return;
      const ticket = String(pick(ticketResponse, ["ticket"], ""));
      if (!ticket) throw new ApiError("后端未返回实时连接票据。");
      const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
      const url = new URL("/ws/updates", `${protocol}//${window.location.host}`);
      url.searchParams.set("ticket", ticket);
      const socket = new WebSocket(url);
      state.realtime.socket = socket;

      socket.addEventListener("open", () => {
        if (generation !== state.realtime.generation) return;
        state.realtime.reconnectAttempt = 0;
        setConnection("online", "实时更新已连接");
        startRealtimeHeartbeat(socket, generation);
      });
      socket.addEventListener("message", message => {
        if (generation === state.realtime.generation) handleRealtimeMessage(message.data);
      });
      socket.addEventListener("close", () => {
        if (generation !== state.realtime.generation) return;
        state.realtime.socket = null;
        clearRealtimeHeartbeat();
        scheduleRealtimeReconnect(generation);
      });
      socket.addEventListener("error", () => {
        if (generation === state.realtime.generation) {
          setConnection("error", "实时连接异常");
        }
      });
    } catch (error) {
      if (generation !== state.realtime.generation || !state.token) return;
      setConnection("error", "实时连接暂不可用");
      scheduleRealtimeReconnect(generation);
    }
  }

  function handleRealtimeMessage(rawMessage) {
    let message;
    try {
      message = JSON.parse(rawMessage);
    } catch {
      return;
    }
    if (message.type === "PONG") {
      clearTimeout(state.realtime.pongTimer);
      state.realtime.pongTimer = null;
      return;
    }
    if (message.type !== "DATA_CHANGED" || !message.event) return;
    const eventId = String(message.event.eventId || "");
    if (eventId && state.realtime.seenEventIds.has(eventId)) return;
    if (eventId) {
      state.realtime.seenEventIds.add(eventId);
      if (state.realtime.seenEventIds.size > 200) {
        const oldest = state.realtime.seenEventIds.values().next().value;
        state.realtime.seenEventIds.delete(oldest);
      }
    }
    state.realtime.pendingEvents.push(message.event);
    clearTimeout(state.realtime.refreshTimer);
    state.realtime.refreshTimer = setTimeout(flushRealtimeEvents, 450);
  }

  async function flushRealtimeEvents() {
    const events = state.realtime.pendingEvents.splice(0);
    state.realtime.refreshTimer = null;
    if (!events.length || !state.token) return;
    const currentUserId = String(pick(state.user, ["userId", "id"], ""));
    const userEvents = events.filter(event => event.topic === "USERS_CHANGED");
    const currentUserChanged = userEvents.some(event =>
      String(event.entityId ?? "") === currentUserId
    );
    const visibleUserListChanged = userEvents.length
      && canManageUsers()
      && state.people.loaded;
    const workspaceChanged = events.some(event =>
      ["DASHBOARD_CHANGED", "ORDERS_CHANGED", "PRODUCTS_CHANGED"].includes(event.topic)
    );
    if (!currentUserChanged && !visibleUserListChanged && !workspaceChanged) return;
    try {
      if (currentUserChanged) {
        const session = await api("/api/auth/me");
        applySession(session);
        await loadBrands();
      }
      const updates = [];
      if (workspaceChanged || currentUserChanged) updates.push(refreshWorkspace());
      if (visibleUserListChanged) updates.push(loadUsers());
      const results = await Promise.allSettled(updates);
      const failure = results.find(result => result.status === "rejected");
      if (failure) throw failure.reason;
      showToast("数据已自动同步", "已根据实时更新通知重新读取最新数据。", "success");
    } catch (error) {
      if (!state.token) {
        showAuth();
      } else if (error.status !== 401) {
        showToast("自动同步失败", "实时通知已收到，请使用刷新按钮重试。", "error");
      }
    }
  }

  function startRealtimeHeartbeat(socket, generation) {
    clearRealtimeHeartbeat();
    state.realtime.heartbeatTimer = setInterval(() => {
      if (generation !== state.realtime.generation || socket.readyState !== WebSocket.OPEN) return;
      socket.send(JSON.stringify({ type: "PING" }));
      clearTimeout(state.realtime.pongTimer);
      state.realtime.pongTimer = setTimeout(() => {
        if (socket.readyState === WebSocket.OPEN) socket.close(4000, "heartbeat timeout");
      }, 10000);
    }, 25000);
  }

  function scheduleRealtimeReconnect(generation) {
    if (generation !== state.realtime.generation || !state.token || state.realtime.reconnectTimer) return;
    const attempt = state.realtime.reconnectAttempt++;
    const delay = Math.min(30000, 1000 * (2 ** Math.min(attempt, 5))) + Math.floor(Math.random() * 500);
    setConnection("error", "实时连接重试中");
    state.realtime.reconnectTimer = setTimeout(() => {
      state.realtime.reconnectTimer = null;
      connectRealtime();
    }, delay);
  }

  function isRealtimeOpen() {
    return state.realtime.socket?.readyState === WebSocket.OPEN;
  }

  function stopRealtime() {
    state.realtime.generation += 1;
    clearRealtimeTimers();
    state.realtime.pendingEvents.length = 0;
    state.realtime.seenEventIds.clear();
    if (state.realtime.socket) {
      state.realtime.socket.onclose = null;
      state.realtime.socket.close(1000, "session ended");
      state.realtime.socket = null;
    }
  }

  function clearRealtimeTimers() {
    clearTimeout(state.realtime.reconnectTimer);
    clearTimeout(state.realtime.refreshTimer);
    state.realtime.reconnectTimer = null;
    state.realtime.refreshTimer = null;
    clearRealtimeHeartbeat();
  }

  function clearRealtimeHeartbeat() {
    clearInterval(state.realtime.heartbeatTimer);
    clearTimeout(state.realtime.pongTimer);
    state.realtime.heartbeatTimer = null;
    state.realtime.pongTimer = null;
  }

  function showAuth() {
    els.appShell.hidden = true;
    els.authScreen.hidden = false;
    syncPageScrollLock();
    if (state.auth.mode !== "login") switchAuthMode("login");
    setTimeout(() => els.loginEmail.focus(), state.auth.mode === "login" ? 50 : 450);
  }

  function showApp() {
    els.authScreen.hidden = true;
    els.appShell.hidden = false;
    setConnection("loading", "正在加载数据");
    setTimeout(() => Object.values(state.charts).forEach(chart => chart?.resize()), 40);
  }

  function logout() {
    clearSession();
    state.controller?.abort();
    state.people.controller?.abort();
    state.brandOps.segments.controller?.abort();
    state.brandOps.products.controller?.abort();
    closeUserDialog();
    closeOrderDetail();
    closeProductDetail();
    Object.values(state.charts).forEach(chart => chart?.clear());
    showAuth();
    els.loginPassword.value = "";
  }

  function clearSession() {
    stopRealtime();
    state.token = "";
    state.csrfToken = "";
    state.user = null;
    state.scope = null;
    state.people.requestVersion += 1;
    state.people.loaded = false;
    state.people.loading = false;
    state.people.total = 0;
    state.people.controller = null;
    state.people.rowsById.clear();
    state.brandOps.products.requestVersion += 1;
    state.brandOps.products.loaded = false;
    state.brandOps.products.total = 0;
    state.brandOps.products.controller = null;
    state.brandOps.products.rowsById.clear();
    state.brandOps.segments.controller = null;
    state.brandOps.segments.data = null;
    sessionStorage.removeItem(TOKEN_KEY);
  }

  function togglePassword() {
    const showing = els.loginPassword.type === "text";
    els.loginPassword.type = showing ? "password" : "text";
    els.togglePassword.textContent = showing ? "显示" : "隐藏";
    els.togglePassword.setAttribute("aria-pressed", String(!showing));
  }

  function toggleSidebar() {
    const open = els.sidebar.classList.toggle("is-open");
    els.menuButton.setAttribute("aria-expanded", String(open));
    els.sidebarScrim.hidden = !open;
    syncPageScrollLock();
  }

  function closeSidebar() {
    els.sidebar.classList.remove("is-open");
    els.menuButton.setAttribute("aria-expanded", "false");
    els.sidebarScrim.hidden = true;
    syncPageScrollLock();
  }

  function showGlobalLoading() {
    ["kpiSales", "kpiOrders", "kpiAverage", "kpiHot"].forEach(key => { els[key].textContent = "—"; });
  }

  function showPanelLoading(name) {
    const panel = document.querySelector(`[data-state="${name}"]`);
    if (!panel) return;
    if (name === "category" && Array.isArray(state.chartData.category) && state.chartData.category.length) {
      panel.className = "panel-state is-visible is-refreshing";
      panel.innerHTML = `<div class="refreshing-note"><span></span>数据更新中</div>`;
      return;
    }
    panel.className = "panel-state is-visible is-loading";
    panel.innerHTML = `<div class="state-message"><div class="loading-rule"></div><p>正在读取数据…</p></div>`;
  }

  function showPanelError(name, error, retry) {
    const panel = document.querySelector(`[data-state="${name}"]`);
    if (!panel) return;
    panel.className = "panel-state is-visible";
    panel.innerHTML = stateMessageHtml("!", "这部分数据暂未加载", readableError(error));
    const button = document.createElement("button");
    button.className = "secondary-button";
    button.type = "button";
    button.textContent = "重新加载";
    button.addEventListener("click", () => retry().catch(() => {}));
    panel.querySelector(".state-message").appendChild(button);
  }

  function showPanelEmpty(name) {
    const panel = document.querySelector(`[data-state="${name}"]`);
    panel.className = "panel-state is-visible";
    panel.innerHTML = stateMessageHtml("—", "当前范围暂无数据", "可尝试扩大日期、品类或品牌范围。");
  }

  function hidePanelState(name) {
    const panel = document.querySelector(`[data-state="${name}"]`);
    if (panel) panel.className = "panel-state";
  }

  function ensureChart(name, data) {
    if (!window.echarts || !state.charts[name]) {
      showPanelError(name, new ApiError("ECharts 资源未加载，请刷新页面。"), () => location.reload());
      return false;
    }
    if (!data || (Array.isArray(data) && !data.length) || (data.labels && !data.labels.length)) {
      state.charts[name].clear();
      showPanelEmpty(name);
      return false;
    }
    return true;
  }

  function showOrdersLoading() {
    els.ordersBody.replaceChildren();
    els.ordersState.hidden = false;
    els.ordersState.innerHTML = `<div class="state-message"><div class="loading-rule"></div><p>正在读取订单明细…</p></div>`;
  }

  function showOrdersError(error) {
    els.ordersBody.replaceChildren();
    els.ordersState.hidden = false;
    els.ordersState.innerHTML = stateMessageHtml("!", "订单明细加载失败", readableError(error));
    const button = document.createElement("button");
    button.className = "secondary-button";
    button.type = "button";
    button.textContent = "重新加载";
    button.addEventListener("click", () => loadOrders().catch(() => {}));
    els.ordersState.querySelector(".state-message").appendChild(button);
    state.order.total = 0;
    updatePagination();
  }

  function showOrdersEmpty() {
    els.ordersState.hidden = false;
    els.ordersState.innerHTML = stateMessageHtml("—", "没有符合条件的订单", "清除订单关键字或调整上方经营筛选后再试。");
    els.orderTotal.textContent = "共 0 笔";
  }

  function updatePagination() {
    const pageCount = Math.max(1, Math.ceil(state.order.total / state.order.pageSize));
    if (state.order.page > pageCount) state.order.page = pageCount;
    const from = state.order.total ? (state.order.page - 1) * state.order.pageSize + 1 : 0;
    const to = Math.min(state.order.total, state.order.page * state.order.pageSize);
    els.pageSummary.textContent = state.order.total ? `显示第 ${numberFormat.format(from)}–${numberFormat.format(to)} 条` : "暂无结果";
    els.pageIndicator.textContent = `${state.order.page} / ${pageCount}`;
    els.previousPage.disabled = state.order.page <= 1;
    els.nextPage.disabled = state.order.page >= pageCount;
  }

  function updateActiveScopeText() {
    const daysText = `${state.filters.startTime.replaceAll("-", ".")} — ${state.filters.endTime.replaceAll("-", ".")}`;
    const categoryText = state.filters.categories.length ? `${state.filters.categories.length} 个品类` : "全部品类";
    const brandText = state.filters.brands.length ? `${state.filters.brands.length} 个品牌` : "全部授权品牌";
    els.activeScopeText.textContent = `${daysText} · ${categoryText} · ${brandText} · 已完成订单`;
  }

  function updateScopeFromResponse() {
    const mode = String(pick(state.scope, ["mode"], ""));
    const brands = asArray(pick(state.scope, ["brands", "brand"], []));
    if (mode === "ALL") els.sidebarScope.textContent = "全部授权品牌";
    else if (brands.length) els.sidebarScope.textContent = brands.join("、");
  }

  function setConnection(kind, text) {
    els.connectionState.className = `connection-state${kind === "online" ? " is-online" : kind === "error" ? " is-error" : ""}`;
    els.connectionState.querySelector("span").textContent = text;
  }

  function setButtonLoading(button, loading, text = "处理中…") {
    if (loading) {
      button.dataset.original = button.innerHTML;
      button.textContent = text;
      button.disabled = true;
    } else {
      if (button.dataset.original) button.innerHTML = button.dataset.original;
      button.disabled = false;
    }
  }

  function setSegmentActive(attribute, value) {
    document.querySelectorAll(`[${attribute}]`).forEach(button => {
      const active = button.getAttribute(attribute) === value;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
  }

  function trapFocus(event, container) {
    const focusable = [...container.querySelectorAll('button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex="-1"])')]
      .filter(element => !element.hidden && element.offsetParent !== null);
    if (!focusable.length) {
      event.preventDefault();
      container.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function showLoginError(message) {
    els.loginError.textContent = message;
    els.loginError.hidden = false;
  }

  function showRegisterError(message) {
    els.registerError.textContent = message;
    els.registerError.hidden = false;
    els.registerError.focus();
  }

  function showToast(title, message, type = "success") {
    const toast = document.createElement("div");
    toast.className = `toast toast--${type}`;
    toast.innerHTML = `<span class="toast__mark"></span><div><strong>${escapeHtml(title)}</strong><p>${escapeHtml(message)}</p></div><button type="button" aria-label="关闭通知">×</button>`;
    const remove = () => toast.remove();
    toast.querySelector("button").addEventListener("click", remove);
    els.toastRegion.appendChild(toast);
    setTimeout(remove, 5200);
  }

  function chartText(fontSize = 12) {
    return { color: PALETTE.muted, fontFamily: "Microsoft YaHei", fontSize };
  }

  function stateMessageHtml(mark, title, detail) {
    return `<div class="state-message"><i>${escapeHtml(mark)}</i><strong>${escapeHtml(title)}</strong><p>${escapeHtml(detail)}</p></div>`;
  }

  function readableError(error, fallback = "请稍后重试，或联系管理员确认接口状态。") {
    if (!error) return fallback;
    if (error.status === 403) return "当前账号没有访问该数据的权限。";
    if (error.status === 404) return "接口或数据不存在，请核对后端版本。";
    if (error.status === 409) return "数据已被其他操作更新，请刷新后重试。";
    if (error.status >= 500) return "后端服务处理失败，请查看服务日志后重试。";
    return error.message || fallback;
  }

  function readMessage(payload) {
    if (!payload) return "";
    if (typeof payload === "string") return payload;
    return String(pick(payload, ["message", "msg", "error", "detail"], ""));
  }

  function asArray(value) {
    if (Array.isArray(value)) return value;
    if (value === null || value === undefined) return [];
    if (typeof value === "object") {
      for (const key of ["items", "list", "content", "data", "records"]) {
        if (Array.isArray(value[key])) return value[key];
      }
    }
    return [];
  }

  function pick(object, keys, fallback = undefined) {
    if (!object || typeof object !== "object") return fallback;
    for (const key of keys) {
      if (Object.prototype.hasOwnProperty.call(object, key) && object[key] !== undefined && object[key] !== null) return object[key];
    }
    return fallback;
  }

  function uniqueBy(items, selector) {
    const seen = new Set();
    return items.filter(item => {
      const key = selector(item);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  function toNumber(value) {
    const number = Number(value);
    return Number.isFinite(number) ? number : 0;
  }

  function nullableRatio(value) {
    if (value === null || value === undefined || value === "") return null;
    const number = Number(value);
    return Number.isFinite(number) ? number : null;
  }

  function formatPercent(value) {
    const ratio = nullableRatio(value);
    return ratio === null ? "—" : `${(ratio * 100).toFixed(ratio * 100 < 10 ? 1 : 0)}%`;
  }

  function nullableCurrency(value) {
    return value === null || value === undefined || value === "" ? "—" : formatCurrency(value);
  }

  function nullableNumber(value, digits = 0) {
    if (value === null || value === undefined || value === "") return "—";
    const number = Number(value);
    return Number.isFinite(number) ? number.toFixed(digits) : "—";
  }

  function metricRatio(value, status) {
    if (String(status).toUpperCase() === "UNAVAILABLE") return `<span class="metric-unavailable"><abbr title="后端暂无可信数据来源">不可用</abbr></span>`;
    return escapeHtml(formatPercent(value));
  }

  function metricRatioText(value, status) {
    return String(status).toUpperCase() === "UNAVAILABLE" ? "暂无可信数据" : formatPercent(value);
  }

  function metricCountText(value, status) {
    if (String(status).toUpperCase() === "UNAVAILABLE") return "暂无可信数据";
    return value === null || value === undefined ? "—" : numberFormat.format(toNumber(value));
  }

  function genderLabel(value) {
    return ({ Male: "男性", Female: "女性", Other: "其他", UNKNOWN: "未知" })[String(value)] || "未知";
  }

  function customerTypeLabel(value) {
    return ({ NEW: "新客", OLD: "老客", UNKNOWN: "未知" })[String(value)] || "未知";
  }

  function formatCurrency(value) {
    return currencyFormat.format(toNumber(value));
  }

  function formatDateTime(value) {
    if (!value) return "—";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value).replace("T", " ");
    return dateTimeFormat.format(date).replaceAll("/", ".");
  }

  function formatDate(value) {
    if (!value) return "—";
    const text = String(value);
    const match = text.match(/\d{4}-\d{2}-\d{2}/);
    return match ? match[0].replaceAll("-", ".") : text;
  }

  function formatAxisDate(value) {
    const text = String(value || "");
    const match = text.match(/(\d{4})-(\d{2})-(\d{2})/);
    return match ? `${match[2]}.${match[3]}` : text;
  }

  function parseDate(text) {
    const [year, month, day] = text.split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  function toDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function truncate(value, length) {
    const chars = Array.from(String(value));
    return chars.length > length ? `${chars.slice(0, length).join("")}…` : chars.join("");
  }

  function normalizeRole(value) {
    if (typeof value === "string") return value;
    return String(pick(value, ["role", "value", "name"], ""));
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function toCamel(value) {
    return value.replace(/-([a-z0-9])/gi, (_, character) => character.toUpperCase());
  }
})();
