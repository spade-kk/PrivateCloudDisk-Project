"""多入口链第一步：标题行大写并写回候选（需求二 2.17）。"""
from pycloud import file


def main(context):
    text = file.read().decode("utf-8")
    lines = [line.upper() for line in text.splitlines()]
    file.write_pre_activation(("\n".join(lines) + "\n").encode("utf-8"))
    return {"step": "a", "lines": len(lines)}
