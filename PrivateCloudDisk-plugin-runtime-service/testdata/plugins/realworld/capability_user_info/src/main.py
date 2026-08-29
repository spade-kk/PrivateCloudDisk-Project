"""能力调用输出（需求二 2.14）。

调用 pycloud.user_info 能力网关（测试环境 mock 返回脱敏用户信息），
并把网关返回的结构化输出透传给工作流。
"""
from pycloud import user_info


def main(context):
    info = user_info(context.get("user_id") or "")
    return {"status": "ok", "user": info}
