"""受限层运行时拦截（36.27）：eval 内置在运行时被删除/改写拒绝。"""


def main(context):
    return eval("1+1")
