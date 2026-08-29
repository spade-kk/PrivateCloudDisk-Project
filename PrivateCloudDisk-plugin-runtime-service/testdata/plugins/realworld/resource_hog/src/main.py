"""资源耗尽模拟（需求二 2.10）：申请远超容器内存限制的对象。"""
def main(context):
    data = [0] * 400000000
    return {"allocated": len(data)}
