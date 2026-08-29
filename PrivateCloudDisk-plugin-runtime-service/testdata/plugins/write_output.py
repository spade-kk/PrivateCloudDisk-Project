"""修改入口：通过 pycloud.write 写入 output.bin，触发候选输出路径。"""

import pycloud


def main(context):
    pycloud.write(b"modified-content-from-plugin")
    return {"wrote": True}
