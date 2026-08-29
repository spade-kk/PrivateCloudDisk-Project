"""失败入口：抛出带宿主路径的异常，验证错误脱敏（5.5/5.8）。"""


def main(context):
    raise FileNotFoundError("cannot open /var/lib/pcd-runtime/work/xxx/main.py")
