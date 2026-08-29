"""日志风暴入口：验证 LogLimitBytes 截断（5.23）。"""


def main(context):
    for index in range(200000):
        print("biglog-line-%d" % index)
    return {"ok": True}
