"""非 root 探测：返回容器内 uid（5.17）。"""

import os


def main(context):
    return {"uid": os.getuid(), "euid": os.geteuid()}
