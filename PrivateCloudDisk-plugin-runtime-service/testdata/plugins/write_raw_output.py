"""绕过 SDK 直写 output.bin：验证不依赖 SDK 权限的 Modified 路径（6.11/6.15）。"""


def main(context):
    with open("/workspace/work/output.bin", "wb") as stream:
        stream.write(b"raw-output")
    return {"wrote": True}
