"""隔离探测：尝试读宿主敏感路径 / 写只读挂载，全部结果以 JSON 返回（5.14/5.18/5.20）。"""


def main(context):
    probes = {}
    sensitive = [
        "/var/run/docker.sock",
        "/run/docker.sock",
        "/var/lib/docker",
        "/proc/1/root/etc/hostname",
        "/Users",
        "/etc/passwd",
    ]
    for path in sensitive:
        try:
            with open(path, "rb") as stream:
                probes[path] = "readable"
        except Exception as exc:
            probes[path] = type(exc).__name__
    for mount_point in ["/workspace/plugin", "/workspace/context", "/workspace/input"]:
        try:
            with open(mount_point + "/pcd-probe.tmp", "w") as stream:
                stream.write("x")
            probes[mount_point + ":write"] = "writable"
        except Exception as exc:
            probes[mount_point + ":write"] = type(exc).__name__
    try:
        with open("/workspace/work/probe-write.tmp", "w") as stream:
            stream.write("x")
        probes["/workspace/work:write"] = "writable"
    except Exception as exc:
        probes["/workspace/work:write"] = type(exc).__name__
    return probes
