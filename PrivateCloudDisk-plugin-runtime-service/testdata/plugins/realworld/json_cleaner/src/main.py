"""JSON 数据清洗与规范化（需求二 2.4）。

读取 JSON 输入，过滤空值字段、字段名转 snake_case、按指定键排序记录。
"""
import json

from pycloud import file


def _snake_case(name):
    out = []
    for index, char in enumerate(name):
        if char.isupper() and index > 0 and out and out[-1] != "_":
            out.append("_" + char.lower())
        elif char.isspace():
            out.append("_")
        else:
            out.append(char.lower())
    return "".join(out)


def _clean(value):
    if value is None:
        return None
    if isinstance(value, dict):
        cleaned = {}
        for key, item in value.items():
            sanitized = _clean(item)
            if sanitized is None:
                continue
            cleaned[_snake_case(str(key))] = sanitized
        return cleaned
    if isinstance(value, list):
        result = []
        for item in value:
            sanitized = _clean(item)
            if sanitized is not None:
                result.append(sanitized)
        return result
    return value


def _sort_records(payload, key_field):
    records = payload.get("records") or []
    return sorted(records, key=lambda record: record.get(key_field) or "")


def main(context):
    raw = file.read().decode("utf-8")
    payload = json.loads(raw)
    cleaned = _clean(payload)
    if isinstance(cleaned, dict) and "records" in cleaned:
        cleaned["records"] = _sort_records(cleaned, "display_name")
    text = json.dumps(cleaned, ensure_ascii=False, indent=2)
    file.write_pre_activation(text.encode("utf-8"))
    return {"records": len(cleaned.get("records") or []), "bytes": len(text)}
