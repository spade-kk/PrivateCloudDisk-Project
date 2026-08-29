"""输出无效文件（需求二 2.15）。

返回不可 JSON 序列化的对象（set）；runner 序列化失败 → 安全异常边界 →
PLUGIN_EXECUTION_FAILED，不产生有效候选输出。
"""

def main(context):
    return {1, 2, 3}
