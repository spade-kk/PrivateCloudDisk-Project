"""日志风暴后失败：验证日志缓冲截断（5.23）与失败路径错误脱敏。"""


def main(context):
    for index in range(200000):
        print("biglog-line-%d-%s" % (index, "x" * 40))
    raise RuntimeError("boom after logging")
