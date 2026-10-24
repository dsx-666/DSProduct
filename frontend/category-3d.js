import * as THREE from "./vendor/three.module.js";

const COLORS = ["#206bc4", "#6c91bb", "#4b9275", "#8aa78d", "#527ba7", "#75a8df"];
const MONEY = new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 0 });
const NUMBER = new Intl.NumberFormat("zh-CN");
const COMPACT = new Intl.NumberFormat("zh-CN", { notation: "compact", maximumFractionDigits: 1 });

export function createCategory3D(host, tooltip, accessibleData) {
  try {
    const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: "low-power" });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
    renderer.setClearColor(0xffffff, 0);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    host.replaceChildren(renderer.domElement);

    const scene = new THREE.Scene();
    scene.background = new THREE.Color("#ffffff");
    const camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100);
    const target = new THREE.Vector3(0, 1.4, 0);
    const cameraOrbit = new THREE.Spherical().setFromVector3(new THREE.Vector3(0, 4.8, 15));
    camera.position.setFromSpherical(cameraOrbit).add(target);
    camera.lookAt(target);

    scene.add(new THREE.HemisphereLight("#ffffff", "#d7dee8", 2.2));
    const keyLight = new THREE.DirectionalLight("#ffffff", 2.4);
    keyLight.position.set(-5, 10, 8);
    scene.add(keyLight);
    const fillLight = new THREE.DirectionalLight("#dceaff", 0.8);
    fillLight.position.set(6, 6, -6);
    scene.add(fillLight);

    const plot = new THREE.Group();
    scene.add(plot);
    const bars = [];
    const raycaster = new THREE.Raycaster();
    const pointer = new THREE.Vector2();
    const materials = [];
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(host);

    const floor = new THREE.Mesh(
      new THREE.PlaneGeometry(15, 3.2),
      new THREE.MeshBasicMaterial({ color: "#f7f9fc", transparent: true, opacity: 0.9 })
    );
    floor.rotation.x = -Math.PI / 2;
    floor.position.set(0, -0.04, 0);
    plot.add(floor);

    const floorGrid = new THREE.GridHelper(15, 15, "#dbe2eb", "#e6ebf1");
    floorGrid.position.y = -0.025;
    floorGrid.scale.z = 0.42;
    plot.add(floorGrid);

    const verticalAxis = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-7, 0, 0), new THREE.Vector3(-7, 4.5, 0)]),
      new THREE.LineBasicMaterial({ color: "#b7c1ce" })
    );
    plot.add(verticalAxis);
    const depthOrigin = -0.55;
    const depthScale = 1.1;
    const depthAxis = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(6.35, 0.025, depthOrigin), new THREE.Vector3(6.35, 0.025, depthOrigin + depthScale)]),
      new THREE.LineBasicMaterial({ color: "#b7c1ce" })
    );
    plot.add(depthAxis);
    const depthTicks = [];
    for (const step of [0, 1, 2, 3, 4]) {
      const line = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-7, step, 0), new THREE.Vector3(7, step, 0)]),
        new THREE.LineBasicMaterial({ color: step === 0 ? "#aeb9c7" : "#e5e9ef", transparent: true, opacity: step === 0 ? 0.8 : 0.72 })
      );
      plot.add(line);
      const z = depthOrigin + (step / 4) * depthScale;
      const tick = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(6.17, 0.03, z), new THREE.Vector3(6.35, 0.03, z)]),
        new THREE.LineBasicMaterial({ color: "#aeb9c7" })
      );
      plot.add(tick);
      depthTicks.push(tick);
    }

    let data = [];
    let drag = null;
    let currentHover = -1;

    function addTextSprite(text, x, y, z, color = "#667085", fontSize = 24) {
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d");
      const font = `600 ${fontSize}px "Microsoft YaHei", sans-serif`;
      ctx.font = font;
      const width = Math.ceil(ctx.measureText(text).width + 20);
      canvas.width = Math.min(512, Math.max(48, width));
      canvas.height = fontSize + 18;
      ctx.font = font;
      ctx.fillStyle = color;
      ctx.textAlign = "center";
      ctx.textBaseline = "middle";
      ctx.fillText(text, canvas.width / 2, canvas.height / 2, canvas.width - 8);
      const texture = new THREE.CanvasTexture(canvas);
      texture.colorSpace = THREE.SRGBColorSpace;
      const material = new THREE.SpriteMaterial({ map: texture, transparent: true, depthTest: false });
      materials.push(material);
      const sprite = new THREE.Sprite(material);
      sprite.position.set(x, y, z);
      const aspect = canvas.width / canvas.height;
      sprite.scale.set(0.34 * aspect, 0.34, 1);
      plot.add(sprite);
      return sprite;
    }

    function clearPlot() {
      for (const child of [...plot.children]) {
        if (child === floor || child === floorGrid || child === verticalAxis || child === depthAxis || depthTicks.includes(child)) continue;
        if (child.geometry && child !== floor) child.geometry.dispose();
        if (child.material && !Array.isArray(child.material)) {
          child.material.map?.dispose();
          child.material.dispose();
        }
        plot.remove(child);
      }
      materials.length = 0;
      bars.length = 0;
      currentHover = -1;
      tooltip.hidden = true;
    }

    function update(nextData) {
      data = Array.isArray(nextData) ? nextData.slice(0, 18) : [];
      clearPlot();
      accessibleData.replaceChildren(...data.map(item => {
        const row = document.createElement("li");
        row.textContent = `${item.label || "未命名"}：销售额 ${MONEY.format(Number(item.value) || 0)}，销量 ${NUMBER.format(Number(item.quantity) || 0)} 件`;
        return row;
      }));
      if (!data.length) {
        render();
        return;
      }

      const maxSales = Math.max(1, ...data.map(item => Number(item.value) || 0));
      const maxQuantity = Math.max(1, ...data.map(item => Number(item.quantity) || 0));
      for (let step = 0; step <= 4; step++) {
        const y = step || 0.08;
        addTextSprite(`¥${COMPACT.format((maxSales * step) / 4)}`, -7.55, y, 0.05, "#667085", 16);
        const z = depthOrigin + (step / 4) * depthScale;
        addTextSprite(COMPACT.format((maxQuantity * step) / 4), 6.55, 0.12, z, "#667085", 18);
      }
      addTextSprite("销售额（元）", -7.9, 4.45, 0.05, "#354052", 17);
      addTextSprite("销量（件）", 6.55, 0.18, depthOrigin + depthScale + 0.2, "#354052", 19);
      const stride = Math.min(1.32, 12.5 / Math.max(1, data.length));
      const start = -((data.length - 1) * stride) / 2;
      const geometry = new THREE.BoxGeometry(Math.min(0.6, stride * 0.58), 1, 1);
      const topGeometry = new THREE.BoxGeometry(Math.min(0.6, stride * 0.58), 0.045, 1);

      data.forEach((item, index) => {
        const amount = Math.max(0, Number(item.value) || 0);
        const quantity = Math.max(0, Number(item.quantity) || 0);
        const height = Math.max(0.025, (amount / maxSales) * 4);
        const depth = Math.max(0.025, (quantity / maxQuantity) * depthScale);
        const x = start + index * stride;
        const material = new THREE.MeshStandardMaterial({ color: COLORS[index % COLORS.length], roughness: 0.66, metalness: 0.02 });
        const mesh = new THREE.Mesh(geometry, material);
        mesh.scale.y = height;
        mesh.scale.z = depth;
        mesh.position.set(x, height / 2, depthOrigin + depth / 2);
        mesh.userData.index = index;
        plot.add(mesh);
        bars.push(mesh);

        const capMaterial = new THREE.MeshStandardMaterial({ color: COLORS[index % COLORS.length], roughness: 0.52 });
        const cap = new THREE.Mesh(topGeometry, capMaterial);
        cap.scale.z = depth;
        cap.position.set(x, height + 0.015, depthOrigin + depth / 2);
        plot.add(cap);

        addTextSprite(item.label || "未命名", x, -0.38, 0.9, "#586579", data.length > 12 ? 19 : 22);
      });
      render();
    }

    function resize() {
      const width = Math.max(1, host.clientWidth);
      const height = Math.max(1, host.clientHeight);
      renderer.setSize(width, height, false);
      camera.aspect = width / height;
      camera.updateProjectionMatrix();
      render();
    }

    function render() {
      renderer.render(scene, camera);
    }

    function updatePointer(event) {
      const rect = renderer.domElement.getBoundingClientRect();
      pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
      pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(pointer, camera);
      const hit = raycaster.intersectObjects(bars, false)[0];
      const index = hit ? hit.object.userData.index : -1;
      if (index === currentHover) return;
      currentHover = index;
      if (index < 0) {
        tooltip.hidden = true;
        return;
      }
      const item = data[index];
      tooltip.replaceChildren();
      const title = document.createElement("strong");
      title.textContent = item.label || "未命名";
      const sales = document.createElement("span");
      sales.textContent = `销售额  ${MONEY.format(Number(item.value) || 0)}`;
      const quantity = document.createElement("span");
      quantity.textContent = `销量  ${NUMBER.format(Number(item.quantity) || 0)} 件`;
      tooltip.append(title, sales, quantity);
      const bounds = host.getBoundingClientRect();
      tooltip.style.left = `${Math.max(8, Math.min(bounds.width - 190, event.clientX - bounds.left + 12))}px`;
      tooltip.style.top = `${Math.max(8, Math.min(bounds.height - 92, event.clientY - bounds.top - 12))}px`;
      tooltip.hidden = false;
    }

    renderer.domElement.addEventListener("pointerdown", event => {
      if (event.button !== 0) return;
      drag = { x: event.clientX, y: event.clientY };
      renderer.domElement.setPointerCapture(event.pointerId);
      renderer.domElement.classList.add("is-dragging");
      tooltip.hidden = true;
    });
    renderer.domElement.addEventListener("pointermove", event => {
      if (!drag) {
        updatePointer(event);
        return;
      }
      cameraOrbit.theta -= (event.clientX - drag.x) * 0.006;
      cameraOrbit.phi = THREE.MathUtils.clamp(cameraOrbit.phi + (event.clientY - drag.y) * 0.006, 0.04, Math.PI - 0.04);
      drag = { x: event.clientX, y: event.clientY };
      updateCamera();
      render();
    });
    const endDrag = () => {
      drag = null;
      renderer.domElement.classList.remove("is-dragging");
    };
    renderer.domElement.addEventListener("pointerup", endDrag);
    renderer.domElement.addEventListener("pointercancel", endDrag);
    renderer.domElement.addEventListener("lostpointercapture", endDrag);
    renderer.domElement.addEventListener("pointerleave", () => {
      if (!drag) tooltip.hidden = true;
    });
    renderer.domElement.addEventListener("wheel", event => {
      event.preventDefault();
      cameraOrbit.radius = THREE.MathUtils.clamp(cameraOrbit.radius + event.deltaY * 0.012, 9, 24);
      updateCamera();
      render();
    }, { passive: false });
    host.addEventListener("keydown", event => {
      const rotate = event.shiftKey ? 0.18 : 0.09;
      if (event.key === "ArrowLeft") cameraOrbit.theta -= rotate;
      else if (event.key === "ArrowRight") cameraOrbit.theta += rotate;
      else if (event.key === "ArrowUp") cameraOrbit.phi = Math.max(0.04, cameraOrbit.phi - rotate);
      else if (event.key === "ArrowDown") cameraOrbit.phi = Math.min(Math.PI - 0.04, cameraOrbit.phi + rotate);
      else if (event.key === "+" || event.key === "=") cameraOrbit.radius = Math.max(9, cameraOrbit.radius - 1);
      else if (event.key === "-") cameraOrbit.radius = Math.min(24, cameraOrbit.radius + 1);
      else return;
      event.preventDefault();
      updateCamera();
      render();
    });

    resize();
    return { update, resize, dispose() { resizeObserver.disconnect(); renderer.dispose(); } };

    function updateCamera() {
      camera.position.setFromSpherical(cameraOrbit).add(target);
      camera.lookAt(target);
      camera.updateProjectionMatrix();
    }
  } catch (error) {
    console.warn("WebGL 不可用，切换为 CSS 三维品类图。", error);
    return createFallbackCategory3D(host, tooltip, accessibleData);
  }
}

function createFallbackCategory3D(host, tooltip, accessibleData) {
  const viewport = document.createElement("div");
  viewport.className = "category-3d__fallback";
  viewport.setAttribute("aria-hidden", "true");
  const plot = document.createElement("div");
  plot.className = "category-3d__fallback-plot";
  viewport.append(plot);
  host.replaceChildren(viewport);

  let data = [];
  let drag = null;
  let rotateX = 8;
  let rotateY = -12;
  let zoom = 1;

  function update(nextData) {
    data = Array.isArray(nextData) ? nextData.slice(0, 18) : [];
    accessibleData.replaceChildren(...data.map(item => {
      const row = document.createElement("li");
      row.textContent = `${item.label || "未命名"}：销售额 ${MONEY.format(Number(item.value) || 0)}，销量 ${NUMBER.format(Number(item.quantity) || 0)} 件`;
      return row;
    }));
    plot.replaceChildren();
    plot.style.setProperty("--item-count", String(data.length));
    const maxSales = Math.max(1, ...data.map(item => Number(item.value) || 0));
    const maxQuantity = Math.max(1, ...data.map(item => Number(item.quantity) || 0));

    data.forEach((item, index) => {
      const column = document.createElement("div");
      column.className = "category-3d__fallback-column";
      column.dataset.index = String(index);
      const amount = Math.max(0, Number(item.value) || 0);
      const quantity = Math.max(0, Number(item.quantity) || 0);
      const bar = document.createElement("div");
      bar.className = "category-3d__fallback-bar";
      bar.style.height = `${Math.max(5, (amount / maxSales) * 176)}px`;
      bar.style.setProperty("--bar-depth", `${9 + (quantity / maxQuantity) * 19}px`);
      bar.style.setProperty("--bar-color", COLORS[index % COLORS.length]);
      const label = document.createElement("span");
      label.className = "category-3d__fallback-label";
      label.textContent = item.label || "未命名";
      column.append(bar, label);
      plot.append(column);
    });
    render();
  }

  function render() {
    plot.style.transform = `scale(${zoom}) rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
  }

  function showTooltip(index, event) {
    const item = data[index];
    if (!item) {
      tooltip.hidden = true;
      return;
    }
    tooltip.replaceChildren();
    const title = document.createElement("strong");
    title.textContent = item.label || "未命名";
    const sales = document.createElement("span");
    sales.textContent = `销售额  ${MONEY.format(Number(item.value) || 0)}`;
    const quantity = document.createElement("span");
    quantity.textContent = `销量  ${NUMBER.format(Number(item.quantity) || 0)} 件`;
    tooltip.append(title, sales, quantity);
    const bounds = host.getBoundingClientRect();
    tooltip.style.left = `${Math.max(8, Math.min(bounds.width - 190, event.clientX - bounds.left + 12))}px`;
    tooltip.style.top = `${Math.max(8, Math.min(bounds.height - 92, event.clientY - bounds.top - 12))}px`;
    tooltip.hidden = false;
  }

  viewport.addEventListener("pointerdown", event => {
    if (event.button !== 0) return;
    drag = { x: event.clientX, y: event.clientY };
    viewport.setPointerCapture(event.pointerId);
    tooltip.hidden = true;
  });
  viewport.addEventListener("pointermove", event => {
    if (drag) {
      rotateY += (event.clientX - drag.x) * 0.45;
      rotateX = Math.max(-12, Math.min(28, rotateX + (event.clientY - drag.y) * 0.25));
      drag = { x: event.clientX, y: event.clientY };
      render();
      return;
    }
    const column = event.target.closest("[data-index]");
    showTooltip(column ? Number(column.dataset.index) : -1, event);
  });
  const endDrag = () => { drag = null; };
  viewport.addEventListener("pointerup", endDrag);
  viewport.addEventListener("pointercancel", endDrag);
  viewport.addEventListener("lostpointercapture", endDrag);
  viewport.addEventListener("pointerleave", () => { if (!drag) tooltip.hidden = true; });
  viewport.addEventListener("wheel", event => {
    event.preventDefault();
    zoom = Math.max(0.78, Math.min(1.3, zoom - event.deltaY * 0.0008));
    render();
  }, { passive: false });
  host.addEventListener("keydown", event => {
    if (event.key === "ArrowLeft") rotateY -= 8;
    else if (event.key === "ArrowRight") rotateY += 8;
    else if (event.key === "+" || event.key === "=") zoom = Math.min(1.3, zoom + 0.08);
    else if (event.key === "-") zoom = Math.max(0.78, zoom - 0.08);
    else return;
    event.preventDefault();
    render();
  });

  return { update, resize: render, dispose() { viewport.remove(); } };
}
