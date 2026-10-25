import http from "node:http";
import { createReadStream, statSync } from "node:fs";
import { extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("./", import.meta.url));
const port = Number(process.env.FRONTEND_PORT || 4173);
const apiOrigin = new URL(process.env.API_ORIGIN || "http://127.0.0.1:8080");

const contentTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".webp": "image/webp",
  ".woff2": "font/woff2"
};

function proxyApi(request, response) {
  const target = new URL(request.url, apiOrigin);
  const headers = { ...request.headers, host: apiOrigin.host };
  const upstream = http.request(target, { method: request.method, headers }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers);
    upstreamResponse.pipe(response);
  });
  upstream.on("error", () => {
    response.writeHead(502, { "content-type": "application/json; charset=utf-8" });
    response.end(JSON.stringify({ code: 502, message: `无法连接 API：${apiOrigin.origin}` }));
  });
  request.pipe(upstream);
}

function serveStatic(request, response) {
  const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
  const relative = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const filePath = resolve(root, relative);
  if (!filePath.startsWith(resolve(root))) {
    response.writeHead(403).end("Forbidden");
    return;
  }
  try {
    const file = statSync(filePath);
    if (!file.isFile()) throw new Error("Not a file");
    response.writeHead(200, {
      "content-type": contentTypes[extname(filePath)] || "application/octet-stream",
      "cache-control": "no-cache"
    });
    createReadStream(filePath).pipe(response);
  } catch {
    response.writeHead(404, { "content-type": "text/plain; charset=utf-8" });
    response.end("页面资源不存在");
  }
}

const server = http.createServer((request, response) => {
  if (request.url?.startsWith("/api/")) proxyApi(request, response);
  else serveStatic(request, response);
});

server.on("upgrade", (request, socket, head) => {
  if (!request.url?.startsWith("/ws/")) {
    socket.destroy();
    return;
  }
  const target = new URL(request.url, apiOrigin);
  const upstreamRequest = http.request(target, {
    method: "GET",
    headers: { ...request.headers, host: apiOrigin.host }
  });
  upstreamRequest.on("upgrade", (upstreamResponse, upstreamSocket, upstreamHead) => {
    const statusLine = `HTTP/1.1 ${upstreamResponse.statusCode} ${upstreamResponse.statusMessage}\r\n`;
    const headers = [];
    for (let index = 0; index < upstreamResponse.rawHeaders.length; index += 2) {
      headers.push(`${upstreamResponse.rawHeaders[index]}: ${upstreamResponse.rawHeaders[index + 1]}`);
    }
    socket.write(`${statusLine}${headers.join("\r\n")}\r\n\r\n`);
    if (upstreamHead.length) socket.write(upstreamHead);
    if (head.length) upstreamSocket.write(head);
    upstreamSocket.pipe(socket).pipe(upstreamSocket);
  });
  upstreamRequest.on("response", response => {
    socket.end(`HTTP/1.1 ${response.statusCode || 502} ${response.statusMessage || "Bad Gateway"}\r\n\r\n`);
  });
  upstreamRequest.on("error", () => socket.destroy());
  upstreamRequest.end();
});

server.listen(port, "127.0.0.1", () => {
  console.log(`衡账前端：http://127.0.0.1:${port}`);
  console.log(`API 代理：${apiOrigin.origin}`);
});
