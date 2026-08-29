"""内容反转并写回候选输出（需求二 2.13）。"""
from pycloud import file


def main(context):
    raw = file.read()
    reversed_data = raw[::-1]
    file.write_pre_activation(reversed_data)
    return {"reversed": len(reversed_data)}
