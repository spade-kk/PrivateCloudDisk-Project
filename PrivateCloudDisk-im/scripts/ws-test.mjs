/**
 * IM Server WebSocket 测试脚本
 *
 * 连接 IM Server 的 WebSocket 服务，打印收到的所有消息。
 * 不做任何反序列化或解析，直接输出原始字符串。
 *
 * 用法：
 *   node ws-test.mjs <token>
 *
 * 其中 token 是从 platform-service 登录接口获取的 JWT Token。
 *
 * 默认连接 V1 服务器（JSON 协议）：
 *   ws://localhost:9090/ws?token=YOUR_JWT_TOKEN
 *
 * 依赖安装：
 *   npm install ws
 *
 * 示例：
 *   # 1. 先从 platform-service 登录获取 token
 *   curl -X POST http://localhost:8080/api/v1/auth/login \
 *     -H "Content-Type: application/json" \
 *     -d '{"account":"your_account","password":"your_password"}'
 *
 *   # 2. 使用返回的 token 连接 IM Server
 *   node ws-test.mjs eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2Vy...
 */

import WebSocket from 'ws';

// ====== 配置 ======
const HOST = process.env.IM_HOST || 'localhost';
const V1_PORT = process.env.IM_V1_PORT || '9090';
const V1_PATH = '/ws';

// Token 必须通过命令行参数或环境变量提供
const TOKEN = process.argv[2] || process.env.IM_TOKEN;

if (!TOKEN) {
    console.error('========================================');
    console.error('  错误：缺少 JWT Token');
    console.error('========================================');
    console.error('');
    console.error('用法：node ws-test.mjs <token>');
    console.error('');
    console.error('示例：');
    console.error('  node ws-test.mjs eyJhbGciOiJSUzI1NiJ9...');
    console.error('');
    console.error('或者设置环境变量：');
    console.error('  IM_TOKEN=eyJhbGciOi... node ws-test.mjs');
    console.error('');
    console.error('Token 获取方式：');
    console.error('  curl -X POST http://localhost:8080/api/v1/auth/login \\');
    console.error('    -H "Content-Type: application/json" \\');
    console.error('    -d \'{"account":"your_account","password":"your_password"}\'');
    process.exit(1);
}

const wsUrl = `ws://${HOST}:${V1_PORT}${V1_PATH}?token=${TOKEN}`;

console.log('========================================');
console.log('  IM WebSocket 测试客户端');
console.log('========================================');
console.log(`连接地址: ${wsUrl.substring(0, 60)}...`);
console.log(`Token:   ${TOKEN.substring(0, 30)}...`);
console.log('----------------------------------------');
console.log('等待消息中... (Ctrl+C 退出)');
console.log('========================================\n');

// ====== 建立连接 ======
const ws = new WebSocket(wsUrl);

ws.on('open', () => {
    console.log(`[✓] WebSocket 握手成功 — ${new Date().toISOString()}`);
    console.log('[✓] 认证通过，等待接收消息...\n');
});

ws.on('message', (data, isBinary) => {
    const timestamp = new Date().toISOString();
    const raw = isBinary ? `<binary ${data.length} bytes>` : data.toString();

    console.log(`[←] ${timestamp} ────────────────────────`);
    console.log(raw);
    console.log('─────────────────────────────────────────\n');
});

ws.on('close', (code, reason) => {
    const reasonStr = reason ? reason.toString() : '(无原因)';
    console.log(`\n[✗] 连接关闭 — code=${code}, reason=${reasonStr}`);
    if (code === 1006) {
        console.error('[!] 连接异常关闭，可能原因：');
        console.error('    1. Token 无效或已过期');
        console.error('    2. IM Server 未启动');
        console.error('    3. 网络问题');
    }
    process.exit(0);
});

ws.on('error', (err) => {
    console.error(`[!] 连接错误: ${err.message}`);
    process.exit(1);
});

// ====== 定时心跳（每 25 秒发送一次，防止被服务端断开） ======
// 心跳命令码 103 = CommandType.HEARTBEAT
setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) {
        const heartbeat = {
            version: 1,
            command: 103,
            senderId: 'test-client',
            timestamp: Date.now()
        };
        ws.send(JSON.stringify(heartbeat));
        console.log(`[→] 心跳 — ${new Date().toISOString()}`);
    }
}, 25000);

// ====== 优雅退出 ======
process.on('SIGINT', () => {
    console.log('\n[...] 正在关闭连接...');
    ws.close();
});

process.on('SIGTERM', () => {
    ws.close();
});
