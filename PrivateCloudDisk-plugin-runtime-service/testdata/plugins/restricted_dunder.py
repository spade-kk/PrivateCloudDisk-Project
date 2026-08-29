"""受限层运行时拦截（36.28）：双下划线逃逸链在运行时被 AST 改写拦截。"""


def main(context):
    return ().__class__.__bases__[0].__subclasses__()
