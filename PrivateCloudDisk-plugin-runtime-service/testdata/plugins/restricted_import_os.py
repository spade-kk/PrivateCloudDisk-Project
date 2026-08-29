"""受限层运行时拦截（36.26）：导入 os 在运行时被导入钩子拒绝。

业务插件不应访问宿主系统模块；本夹具仅用于验证受限 Python 层确实在运行时
（而非仅 AST 预检）强制拦截。
"""

import os


def main(context):
    return {"uid": os.getuid()}
