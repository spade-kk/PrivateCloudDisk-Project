"""超时模拟（需求二 2.9）：无限自旋直到容器被 ExecutionTimeout 强制终止。"""
def main(context):
    counter = 0
    while True:
        counter += 1
        if counter > 1000000000000:
            break
    return {"status": "unreachable"}
