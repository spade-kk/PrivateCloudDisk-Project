"""超时入口：无限睡眠，供超时强制终止测试（5.4）。"""

import time


def main(context):
    time.sleep(3600)
