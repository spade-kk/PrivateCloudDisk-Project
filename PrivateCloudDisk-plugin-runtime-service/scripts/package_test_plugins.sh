#!/usr/bin/env sh
# 打包真实场景测试插件为受约束发布格式 .pcdpkg（zip）（需求三 3.7-3.12 / 六 6.10）。
#
# 包结构与 internal/package 校验对齐：manifest.yaml + src/** + schemas/** + assets/**
# + README.md；排除 input.*（开发样例输入，不进发布包）；红色样本默认跳过。
#
# 用法：
#   scripts/package_test_plugins.sh            # 打包全部（合规样本；红色样本跳过）
#   scripts/package_test_plugins.sh --all      # 包含红色样本（用于安全测试）
#   scripts/package_test_plugins.sh text_stats # 打包单个插件
set -eu
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
out_dir="$root/testdata/packages"
mkdir -p "$out_dir"
python3 - "$out_dir" "$@" <<'PY'
import os, re, sys, zipfile

out_dir, targets = sys.argv[1], sys.argv[2:]
all_samples = targets and targets[0] == "--all"
if all_samples:
    targets = targets[1:]
root = os.getcwd()  # 脚本已 cd 到服务根目录
rwe = os.path.join(root, "testdata", "plugins", "realworld")
red = {"malicious_import"}
plugins = targets or sorted(name for name in os.listdir(rwe)
                     if os.path.isdir(os.path.join(rwe, name)))

def version_of(plugin):
    """从 manifest.yaml 的 plugin.version 读取语义化版本（6.4）。"""
    manifest = os.path.join(rwe, plugin, "manifest.yaml")
    if os.path.exists(manifest):
        text = open(manifest, encoding="utf-8").read()
        match = re.search(r"^  version:\s*(\S+)", text, re.M)
        if match:
            return match.group(1).strip()
    return "1.0.0"

def collect_files(plugin, directory):
    """递归收集包内文件；排除 __pycache__/input.*/点文件，保持相对路径。

    排除 `__pycache__` 目录与 `.pyc` 产物，防止 Python 缓存字节码进入受约束
    `.pcdpkg`（否则随源码 mtime 变化导致重打包 sha256 漂移，也违反包内
    "禁止二进制/冗余文件" 的安全基线）。
    """
    files = []
    for top, dirs, names in os.walk(directory):
        dirs[:] = [d for d in dirs if not (d.startswith(".") or d == "__pycache__")]
        for name in names:
            if (name.startswith(".") or name.startswith("input.")
                    or name.endswith(".pyc")):
                continue
            path = os.path.join(top, name)
            rel = os.path.relpath(path, directory)
            files.append((path, rel.replace(os.sep, "/")))
    return files

def validate_allowed(plugin, files):
    sys.path.insert(0, os.path.join(root, "validator"))
    from validate_python import validate
    for path, rel in files:
        if not rel.endswith(".py"):
            continue
        source = open(path, encoding="utf-8").read()
        report = validate(source, f"{plugin}/{rel}")
        if not report["valid"]:
            raise SystemExit(
                f"拒绝打包 {plugin}/{rel}：{report['error_type']} "
                + ("; ".join(f.get('message','') for f in report['findings'][:3]))
            )

for plugin in plugins:
    directory = os.path.join(rwe, plugin)
    if not os.path.isdir(directory):
        print(f"skip  {plugin}: 目录不存在")
        continue
    files = collect_files(plugin, directory)
    if not any(rel == "manifest.yaml" for _, rel in files):
        print(f"skip  {plugin}: 缺少 manifest.yaml（非 .pcdpkg 结构）")
        continue
    if plugin in red and not all_samples:
        print(f"skip  {plugin}: 红色样本默认不打包（需 --all）")
        continue
    if not all_samples:
        validate_allowed(plugin, files)
    version = version_of(plugin)
    out_name = os.path.join(out_dir, f"{plugin}_{version}.pcdpkg")
    with zipfile.ZipFile(out_name, "w", zipfile.ZIP_DEFLATED) as archive:
        for path, rel in files:
            archive.write(path, arcname=rel)
    print(f"wrote {os.path.relpath(out_name, root)} ({os.path.getsize(out_name)} bytes)")
PY
echo "==> testdata/packages 已更新为 .pcdpkg"
