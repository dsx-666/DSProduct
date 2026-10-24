(function () {
  "use strict";

  const charts = new Map();
  const colors = ["#206bc4", "#2fb344", "#d97706", "#8a97a8", "#6f42c1"];

  function init(element) {
    if (!element || !window.echarts) return null;
    const current = charts.get(element.id);
    if (current && !current.isDisposed()) return current;
    const chart = window.echarts.init(element, null, { renderer: "canvas" });
    charts.set(element.id, chart);
    return chart;
  }

  function cleanItems(items) {
    return (Array.isArray(items) ? items : []).map(item => ({
      name: String(item.label || "未分类"),
      value: Math.max(0, Number(item.value) || 0)
    }));
  }

  function showEmpty(element, message) {
    if (!element) return;
    element.innerHTML = `<p class="product-chart-empty">${message}</p>`;
  }

  function renderGender(element, source) {
    const items = cleanItems(source);
    const total = items.reduce((sum, item) => sum + item.value, 0);
    if (!total) return showEmpty(element, "当前范围暂无可关联的性别数据");
    const chart = init(element);
    if (!chart) return showEmpty(element, "图表组件未加载");
    element.setAttribute("aria-label", `购买用户性别分布，共 ${total} 人；${items.map(item => `${item.name} ${item.value} 人`).join("，")}`);
    chart.setOption({
      animationDuration: 380,
      color: colors,
      tooltip: {
        trigger: "item",
        formatter: params => `${params.marker}${params.name}<br><strong>${params.value} 人</strong> · ${params.percent}%`
      },
      legend: {
        orient: "vertical",
        right: 8,
        top: "middle",
        icon: "circle",
        itemWidth: 8,
        itemHeight: 8,
        itemGap: 12,
        textStyle: { color: "#66758a", fontSize: 11 }
      },
      series: [{
        name: "性别分布",
        type: "pie",
        radius: ["48%", "70%"],
        center: ["34%", "52%"],
        minAngle: 3,
        avoidLabelOverlap: true,
        itemStyle: { borderColor: "#ffffff", borderWidth: 3, borderRadius: 3 },
        label: { show: false },
        emphasis: { scaleSize: 6, label: { show: true, formatter: "{b}\n{d}%", fontWeight: 700 } },
        data: items
      }],
      graphic: [{
        type: "group",
        left: "27%",
        top: "43%",
        children: [
          { type: "text", style: { text: String(total), fill: "#1f2937", font: "700 24px Microsoft YaHei UI", textAlign: "center" } },
          { type: "text", top: 29, style: { text: "购买用户", fill: "#66758a", font: "11px Microsoft YaHei UI", textAlign: "center" } }
        ]
      }]
    }, true);
  }

  function renderSegments(element, source) {
    const items = cleanItems(source);
    const total = items.reduce((sum, item) => sum + item.value, 0);
    if (!total) return showEmpty(element, "当前范围暂无可关联的新老客数据");
    const chart = init(element);
    if (!chart) return showEmpty(element, "图表组件未加载");
    element.setAttribute("aria-label", `注册时间新老客分布，共 ${total} 人；${items.map(item => `${item.name} ${item.value} 人`).join("，")}`);
    chart.setOption({
      animationDuration: 380,
      color: colors,
      grid: { left: 8, right: 48, top: 24, bottom: 18, containLabel: true },
      tooltip: {
        trigger: "axis",
        axisPointer: { type: "shadow", shadowStyle: { color: "rgba(32, 107, 196, .06)" } },
        formatter: params => {
          const item = params[0];
          const ratio = total ? (item.value / total * 100).toFixed(1) : "0.0";
          return `${item.marker}${item.name}<br><strong>${item.value} 人</strong> · ${ratio}%`;
        }
      },
      xAxis: {
        type: "value",
        minInterval: 1,
        axisLabel: { color: "#66758a", fontSize: 10 },
        splitLine: { lineStyle: { color: "#edf0f4" } }
      },
      yAxis: {
        type: "category",
        inverse: true,
        data: items.map(item => item.name),
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: "#354052", fontSize: 11, fontWeight: 600 }
      },
      series: [{
        type: "bar",
        barWidth: 18,
        data: items.map((item, index) => ({ value: item.value, itemStyle: { color: colors[index % colors.length], borderRadius: [0, 3, 3, 0] } })),
        label: { show: true, position: "right", color: "#66758a", fontSize: 11, formatter: params => `${params.value} 人` }
      }]
    }, true);
  }

  function resize() {
    charts.forEach(chart => {
      if (!chart.isDisposed()) chart.resize();
    });
  }

  function dispose() {
    charts.forEach(chart => {
      if (!chart.isDisposed()) chart.dispose();
    });
    charts.clear();
  }

  window.addEventListener("resize", resize);
  window.ProductCharts = { renderGender, renderSegments, resize, dispose };
})();
