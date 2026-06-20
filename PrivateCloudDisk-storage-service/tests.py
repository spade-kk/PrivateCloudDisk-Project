import asyncio
import time
import uuid
import random
import aiohttp

# ========== 配置 ==========
BASE_URL = "http://localhost:8000"
INIT_URL = f"{BASE_URL}/api/operations/init"
DOWNLOAD_URL = f"{BASE_URL}/api/download/d8785081-e2bf-4ced-97fa-3b785de3abc6/java_error_in_idea.hprof"   # 改为实际测试文件

USER_TOKEN = "Bearer your_access_token_here"   # 替换为有效 access_token
TICKET = None          # 将通过 init 接口获取
TICKET_EXPIRY = 600    # 与服务器一致

MAX_RANGE_BYTES = 100 * 1024 * 1024   # 服务器限制的每次最大字节（10MB）
# 文件大小（字节），请根据实际测试文件填写，或脚本启动时通过 HEAD 请求获取
FILE_SIZE = 837500268        # 假设测试文件 100MB

CONCURRENT_TASKS = 30                # 并发协程数
REQUEST_INTERVAL = 0.001              # 每个协程发起请求的间隔（秒）
TEST_DURATION = 30                  # 测试持续时间（秒）

# ========== 工具函数 ==========
async def get_ticket(session):
    """获取操作凭证"""
    payload = {
        "node_id": "d8785081-e2bf-4ced-97fa-3b785de3abc6",
        "file_name": "java_error_in_idea.hprof",
        "operation_type": "download"
    }
    headers = {"Authorization": USER_TOKEN}
    async with session.post(INIT_URL, params=payload, headers=headers) as resp:
        if resp.status == 200:
            data = await resp.json()
            return data["data"]["ticket"]
        else:
            print(f"获取 ticket 失败: {resp.status} {await resp.text()}")
            return None

async def download_worker(session, ticket, worker_id, stats):
    """单个并发任务：不断发起 Range 请求，直到被限流或测试结束"""
    end_time = time.time() + TEST_DURATION
    request_count = 0
    while time.time() < end_time:
        # 生成随机 Range（模拟真实下载分片）
        start = 0
        end = 100
        if end >= FILE_SIZE:
            end = FILE_SIZE - 1
        range_header = f"bytes={start}-{end}"

        headers = {
            "X-Operation-Ticket": ticket,
            "Range": range_header,
        }

        try:
            async with session.get(DOWNLOAD_URL, headers=headers) as resp:
                status = resp.status
                if status == 206:
                    stats["success"] += 1
                    print(f"[Worker-{worker_id}] 成功 (200)")
                elif status == 429:
                    stats["rate_limited"] += 1
                    error = await resp.text()
                    print(f"[Worker-{worker_id}] 限流 (429) {error}")
                    # 被限流后可适当等待，否则疯狂重试
                    await asyncio.sleep(0.5)
                elif status == 403:
                    stats["forbidden"] += 1
                    print(f"[Worker-{worker_id}] 凭证被拒绝 (403)，可能已过期或请求超限")
                    break
                else:
                    stats["other_errors"] += 1
                    print(f"[Worker-{worker_id}] 收到状态码 {status}")
        except Exception as e:
            stats["connection_errors"] += 1
            print(f"[Worker-{worker_id}] 连接异常: {e}")

        request_count += 1
        await asyncio.sleep(REQUEST_INTERVAL)   # 控制发送速率

    stats["total_requests"] += request_count

async def main():
    stats = {
        "success": 0,
        "rate_limited": 0,
        "forbidden": 0,
        "other_errors": 0,
        "connection_errors": 0,
        "total_requests": 0
    }

    async with aiohttp.ClientSession() as session:
        # 1. 获取 ticket
        ticket = await get_ticket(session)
        if not ticket:
            print("无法获取 ticket，测试终止")
            return
        print(f"获取 ticket 成功: {ticket[:50]}...")

        # 2. 启动并发任务
        start_time = time.time()
        tasks = []
        for i in range(CONCURRENT_TASKS):
            task = asyncio.create_task(download_worker(session, ticket, i, stats))
            tasks.append(task)

        # 3. 等待测试结束
        await asyncio.sleep(TEST_DURATION)
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)

    # 4. 统计结果
    elapsed = time.time() - start_time
    print("\n========== 测试结果 ==========")
    print(f"测试时长: {elapsed:.2f} 秒")
    print(f"并发协程数: {CONCURRENT_TASKS}")
    print(f"总请求数: {stats['total_requests']}")
    print(f"成功 (206): {stats['success']}")
    print(f"被限流 (429): {stats['rate_limited']}")
    print(f"凭证拒绝 (403/401): {stats['forbidden']}")
    print(f"其他错误: {stats['other_errors']}")
    print(f"连接错误: {stats['connection_errors']}")
    if stats['total_requests'] > 0:
        success_rate = stats['success'] / stats['total_requests'] * 100
        limit_rate = stats['rate_limited'] / stats['total_requests'] * 100
        print(f"成功率: {success_rate:.1f}%")
        print(f"限流率: {limit_rate:.1f}%")

if __name__ == "__main__":
    asyncio.run(main())