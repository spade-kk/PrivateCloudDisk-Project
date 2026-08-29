"""多入口链第二步：在首行前追加处理标记（需求二 2.17）。"""
from pycloud import file


def main(context):
    text = file.read().decode("utf-8")
    report = "# PROCESSED\n" + text
    file.write_pre_activation(report.encode("utf-8"))
    return {"step": "b", "chars": len(report)}
