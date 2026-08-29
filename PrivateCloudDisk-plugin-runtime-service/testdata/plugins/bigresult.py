"""超大结构化输出入口：result.json 超过 1 MiB，验证输出大小上限（5.11）。"""


def main(context):
    return {"blob": "x" * 2000000}
