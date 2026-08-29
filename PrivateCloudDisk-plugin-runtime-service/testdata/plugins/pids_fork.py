"""进程耗尽入口：子进程风暴，验证 pids-limit 边界（5.16）。"""

import os


def main(context):
    children = []
    try:
        for _ in range(4096):
            pid = os.fork()
            if pid == 0:
                os._exit(0)
            children.append(pid)
    except Exception as exc:
        return {"fork_error": type(exc).__name__, "children": len(children)}
    for pid in children:
        try:
            os.waitpid(pid, 0)
        except Exception:
            pass
    return {"forked": len(children)}
