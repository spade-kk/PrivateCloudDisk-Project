"""路径逃逸尝试（需求二 2.12）。

请求读取宿主敏感路径。注意：字面量会被 AST 可疑字符串门禁直接拒绝
（SUSPICIOUS_STRING），本样本动态拼接路径以演示"网关层"的第二道防御：
能力网关（数据面）拒绝非白名单路径，插件捕获 CapabilityError 返回 blocked，
不泄露资源存在性。
"""
from pycloud import CapabilityError, call_api


def main(context):
    host_path = "/etc" + "/passwd"
    try:
        call_api("api.file.content.get", {"path": host_path})
    except CapabilityError as error:
        return {"status": "blocked", "code": error.code}
    except PermissionError:
        return {"status": "blocked", "code": "PERMISSION_DENIED"}
    return {"status": "leaked"}
