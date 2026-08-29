#!/usr/bin/env python3
"""Generate the auditable PrivateCloudDisk architecture diagrams.

The repository does not have the draw.io desktop CLI or Graphviz available in
the current environment.  This generator therefore emits uncompressed,
editable draw.io XML and companion SVG/PNG exports from the same node model.
The XML intentionally keeps the service boundaries and the deployment status
visible instead of treating every repository directory as a running service.
"""

from __future__ import annotations

import html
import math
import subprocess
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


OUT = Path(__file__).resolve().parent


COLORS = {
    "edge": ("#fff2cc", "#d6b656", "#5a4a00"),
    "client": ("#e1f0ff", "#6c8ebf", "#123b5d"),
    "core": ("#dae8fc", "#6c8ebf", "#123b5d"),
    "automation": ("#e8dcf2", "#9673a6", "#4d2f5e"),
    "data": ("#d5e8d4", "#82b366", "#24521e"),
    "middleware": ("#fff2cc", "#d6b656", "#5a4a00"),
    "external": ("#f5f5f5", "#666666", "#333333"),
    "note": ("#f8f9fb", "#9aa7b5", "#3e4b59"),
}


@dataclass(frozen=True)
class Node:
    key: str
    label: str
    x: int
    y: int
    w: int = 260
    h: int = 82
    role: str = "core"
    status: str = "active"


@dataclass(frozen=True)
class Group:
    key: str
    label: str
    x: int
    y: int
    w: int
    h: int
    role: str


@dataclass(frozen=True)
class Edge:
    source: str
    target: str
    label: str = ""
    kind: str = "sync"
    route: tuple[tuple[int, int], ...] = field(default_factory=tuple)
    ports: tuple[float, float, float, float] | None = None


def esc(value: str) -> str:
    """Escape XML while retaining draw.io's numeric line-break entity."""

    return html.escape(value, quote=True).replace("\n", "&#xa;")


def style_for(node: Node) -> str:
    fill, stroke, font = COLORS[node.role]
    dash = "dashed=1;" if node.status != "active" else ""
    return (
        "rounded=1;whiteSpace=wrap;html=1;arcSize=12;"
        f"fillColor={fill};strokeColor={stroke};fontColor={font};"
        f"fontSize=13;spacing=8;{dash}"
    )


def group_style(group: Group) -> str:
    fill, stroke, font = COLORS[group.role]
    return (
        "swimlane;startSize=30;html=1;whiteSpace=wrap;"
        f"fillColor={fill};strokeColor={stroke};fontColor={font};"
        "fontStyle=1;fontSize=16;rounded=1;arcSize=10;"
    )


def edge_style(edge: Edge) -> str:
    style = (
        "edgeStyle=orthogonalEdgeStyle;rounded=1;orthogonalLoop=1;"
        "jettySize=auto;html=1;fontSize=11;labelBackgroundColor=#ffffff;"
        "endArrow=blockThin;endFill=1;endSize=8;"
    )
    if edge.kind == "async":
        style += "dashed=1;dashPattern=8 4;strokeColor=#8a6f00;"
    elif edge.kind == "data":
        style += "dashed=1;dashPattern=2 3;strokeColor=#5f8c58;"
    else:
        style += "strokeColor=#5f6b78;"
    return style


def drawio_page(name: str, title: str, width: int, height: int,
                groups: list[Group], nodes: list[Node], edges: list[Edge],
                notes: list[Node]) -> str:
    cells: list[str] = [
        f'<diagram name="{esc(name)}"><mxGraphModel dx="1600" dy="1000" '
        f'grid="1" gridSize="10" page="1" pageWidth="{width}" '
        f'pageHeight="{height}" background="#f7f9fc">',
        '<root><mxCell id="0" /><mxCell id="1" parent="0" />',
        '<mxCell id="diagram-title" value="' + esc(title) + '" '
        'style="text;html=1;align=left;verticalAlign=middle;fontStyle=1;'
        'fontSize=24;fontColor=#17212b;" vertex="1" parent="1">'
        f'<mxGeometry x="40" y="18" width="{width - 80}" height="42" as="geometry" />'
        '</mxCell>',
    ]
    for group in groups:
        cells.append(
            f'<mxCell id="group-{esc(group.key)}" value="{esc(group.label)}" '
            f'style="{group_style(group)}" vertex="1" parent="1">'
            f'<mxGeometry x="{group.x}" y="{group.y}" width="{group.w}" '
            f'height="{group.h}" as="geometry" /></mxCell>'
        )
    group_by_node: dict[str, Group] = {}
    for group in groups:
        for node in nodes + notes:
            if group.x <= node.x and node.x + node.w <= group.x + group.w \
                    and group.y <= node.y and node.y + node.h <= group.y + group.h:
                group_by_node[node.key] = group
    for node in nodes + notes:
        parent = group_by_node.get(node.key)
        parent_id = f"group-{parent.key}" if parent else "1"
        x = node.x - parent.x if parent else node.x
        y = node.y - parent.y - 30 if parent else node.y
        cells.append(
            f'<mxCell id="node-{esc(node.key)}" value="{esc(node.label)}" '
            f'style="{style_for(node)}" vertex="1" parent="{parent_id}">'
            f'<mxGeometry x="{x}" y="{y}" width="{node.w}" height="{node.h}" '
            'as="geometry" /></mxCell>'
        )
    node_map = {node.key: node for node in nodes + notes}
    for index, edge in enumerate(edges, start=1):
        source = node_map[edge.source]
        target = node_map[edge.target]
        style = edge_style(edge)
        if edge.ports:
            exit_x, exit_y, entry_x, entry_y = edge.ports
            style += f"exitX={exit_x};exitY={exit_y};entryX={entry_x};entryY={entry_y};"
        elif source.x + source.w < target.x:
            style += "exitX=1;exitY=0.5;entryX=0;entryY=0.5;"
        elif target.x + target.w < source.x:
            style += "exitX=0;exitY=0.5;entryX=1;entryY=0.5;"
        elif source.y + source.h < target.y:
            style += "exitX=0.5;exitY=1;entryX=0.5;entryY=0;"
        else:
            style += "exitX=0.5;exitY=0;entryX=0.5;entryY=1;"
        cells.append(
            f'<mxCell id="edge-{index}" value="{esc(edge.label)}" '
            f'style="{style}" edge="1" parent="1" '
            f'source="node-{esc(edge.source)}" target="node-{esc(edge.target)}">'
            '<mxGeometry relative="1" as="geometry">'
            + ("<Array as=\"points\">" + "".join(
                f'<mxPoint x="{x}" y="{y}" />' for x, y in edge.route
            ) + "</Array>" if edge.route else "")
            + '</mxGeometry></mxCell>'
        )
    cells.append('</root></mxGraphModel></diagram>')
    return "".join(cells)


def drawio_file(pages: Iterable[str]) -> str:
    return '<?xml version="1.0" encoding="UTF-8"?>\n<mxfile host="drawio" version="26.0.0">' \
        + "".join(pages) + "</mxfile>\n"


def node_box(node: Node, groups: list[Group]) -> tuple[int, int, int, int]:
    return node.x, node.y, node.w, node.h


def svg_text(text: str, x: int, y: int, size: int, color: str,
             weight: int = 400, anchor: str = "start") -> str:
    lines = text.split("\n")
    parts = [
        f'<text x="{x}" y="{y}" font-family="-apple-system,BlinkMacSystemFont, '
        f'PingFang SC,Microsoft YaHei,Arial,sans-serif" font-size="{size}px" '
        f'font-weight="{weight}" fill="{color}" text-anchor="{anchor}">'
    ]
    for index, line in enumerate(lines):
        dy = 0 if index == 0 else size + 3
        parts.append(f'<tspan x="{x}" dy="{dy}px">{html.escape(line)}</tspan>')
    parts.append("</text>")
    return "".join(parts)


def svg_page(name: str, title: str, width: int, height: int,
             groups: list[Group], nodes: list[Node], edges: list[Edge],
             notes: list[Node]) -> str:
    node_map = {node.key: node for node in nodes + notes}
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}" role="img" aria-labelledby="title-{name}">',
        '<defs><marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" '
        'orient="auto"><path d="M0,0 L10,4 L0,8 Z" fill="#5f6b78" /></marker>',
        '<marker id="arrow-async" markerWidth="10" markerHeight="8" refX="9" refY="4" '
        'orient="auto"><path d="M0,0 L10,4 L0,8 Z" fill="#8a6f00" /></marker>',
        '<marker id="arrow-data" markerWidth="10" markerHeight="8" refX="9" refY="4" '
        'orient="auto"><path d="M0,0 L10,4 L0,8 Z" fill="#5f8c58" /></marker>',
        '<filter id="shadow" x="-20%" y="-20%" width="140%" height="150%">'
        '<feDropShadow dx="0" dy="2" stdDeviation="3" flood-color="#14212d" flood-opacity="0.12" />'
        '</filter></defs>',
        '<rect width="100%" height="100%" fill="#f7f9fc" />',
        svg_text(title, 40, 48, 25, "#17212b", 700),
    ]
    for group in groups:
        fill, stroke, font = COLORS[group.role]
        out.append(
            f'<rect x="{group.x}" y="{group.y}" width="{group.w}" height="{group.h}" '
            f'rx="12" fill="{fill}" fill-opacity="0.25" stroke="{stroke}" stroke-width="2" />'
        )
        out.append(svg_text(group.label, group.x + 16, group.y + 23, 16, font, 700))
    for edge in edges:
        source, target = node_map[edge.source], node_map[edge.target]
        sx, sy, tx, ty = edge_endpoints(source, target, edge.ports)
        points = [(sx, sy), *edge.route, (tx, ty)]
        path = "M " + " L ".join(f"{x} {y}" for x, y in points)
        if edge.kind == "async":
            stroke, dash, marker = "#8a6f00", "8 5", "url(#arrow-async)"
        elif edge.kind == "data":
            stroke, dash, marker = "#5f8c58", "3 5", "url(#arrow-data)"
        else:
            stroke, dash, marker = "#5f6b78", "", "url(#arrow)"
        out.append(
            f'<path d="{path}" fill="none" stroke="{stroke}" stroke-width="2" '
            f'{f"stroke-dasharray=\"{dash}\"" if dash else ""} marker-end="{marker}" opacity="0.85" />'
        )
        if edge.label:
            lx, ly = label_point(points)
            out.append(
                f'<rect x="{lx - 5}" y="{ly - 15}" width="{max(60, len(edge.label) * 12)}" '
                'height="20" rx="5" fill="#ffffff" fill-opacity="0.92" />'
            )
            out.append(svg_text(edge.label, lx, ly, 11, "#495563", 500))
    for node in nodes + notes:
        fill, stroke, font = COLORS[node.role]
        dash = ' stroke-dasharray="8 5"' if node.status != "active" else ""
        out.append(
            f'<rect x="{node.x}" y="{node.y}" width="{node.w}" height="{node.h}" rx="10" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="2" filter="url(#shadow)"{dash} />'
        )
        lines = node.label.split("\n")
        start_y = node.y + (node.h - (len(lines) - 1) * 17) / 2 + 5
        for index, line in enumerate(lines):
            out.append(svg_text(line, node.x + node.w / 2, int(start_y + index * 17),
                                13 if index == 0 else 11, font,
                                700 if index == 0 else 400, "middle"))
    out.append('</svg>')
    return "".join(out)


def edge_endpoints(source: Node, target: Node,
                   ports: tuple[float, float, float, float] | None = None) -> tuple[int, int, int, int]:
    if ports:
        exit_x, exit_y, entry_x, entry_y = ports
        return (int(source.x + source.w * exit_x), int(source.y + source.h * exit_y),
                int(target.x + target.w * entry_x), int(target.y + target.h * entry_y))
    if source.x + source.w < target.x:
        return source.x + source.w, source.y + source.h // 2, target.x, target.y + target.h // 2
    if target.x + target.w < source.x:
        return source.x, source.y + source.h // 2, target.x + target.w, target.y + target.h // 2
    if source.y + source.h < target.y:
        return source.x + source.w // 2, source.y + source.h, target.x + target.w // 2, target.y
    return source.x + source.w // 2, source.y, target.x + target.w // 2, target.y + target.h


def label_point(points: list[tuple[int, int]]) -> tuple[int, int]:
    midpoint = points[len(points) // 2]
    return int(midpoint[0]), int(midpoint[1])


def make_overview() -> tuple[str, str, str]:
    groups = [
        Group("clients", "多平台客户端", 40, 90, 2320, 190, "client"),
        Group("edge", "边缘接入层", 40, 330, 2320, 210, "edge"),
        Group("core", "核心业务与实时通信微服务", 40, 580, 2320, 390, "core"),
        Group("automation", "自动化、插件与工作流微服务（automation profile / 独立运行时）", 40, 1010, 2320, 330, "automation"),
        Group("data", "数据、中间件与可观测性", 40, 1380, 2320, 260, "data"),
    ]
    nodes = [
        Node("web", "Web SPA\nVue 3 + Vite", 70, 160, role="client"),
        Node("admin", "Admin Web\nReact + TypeScript", 440, 160, role="client"),
        Node("desktop", "桌面客户端\nElectron / Win / macOS", 810, 160, role="client"),
        Node("mobile", "移动客户端\nuni-app / Android / iOS", 1180, 160, role="client"),
        Node("cli", "CLI\nGo", 1550, 160, role="client"),
        Node("extensions", "本地扩展与系统集成\n客户端能力按端适配", 1920, 160, role="client"),
        Node("nginx", "Nginx + Certbot 边缘层\nTLS / 静态资源 / API 与 WS 反向代理", 260, 405, 470, 88, role="edge"),
        Node("apisix", "APISIX\n可选本地部署入口", 820, 405, 300, 88, role="edge", status="optional"),
        Node("gateway", "API Gateway\nSpring Cloud Gateway · JWT · 限流 · 路由", 1450, 405, 580, 88, role="edge"),
        Node("platform", "Platform Service\n用户 / 空间 / 文件元数据 / 分享 / 权限", 70, 650, role="core"),
        Node("storage", "Storage API\nFastAPI · 上传下载 / 预览 / Range", 380, 650, role="core"),
        Node("worker", "Storage Worker\nPython · 文件 Task Bus 后处理", 690, 650, role="core"),
        Node("git", "Git Service\nGo · Smart HTTP / SSH / Object 映射", 1000, 650, role="core"),
        Node("clientreg", "Client Registration\nGo / Gin · 设备身份与客户端注册", 1310, 650, role="core"),
        Node("billing", "Billing Service\nSpring Boot · 订阅 / 账单 / 支付适配", 1620, 650, role="core"),
        Node("ai", "AI Service\nFastAPI · 可选模型推理与异步任务", 1930, 650, role="core"),
        Node("implatform", "IM Platform\nSpring Boot · 会话 / 消息 / 群组 REST", 70, 820, role="core"),
        Node("imserver", "IM Server\nNetty WebSocket · Protobuf · 在线推送", 380, 820, role="core"),
        Node("imrouter", "IM Router\nGo · Redis 路由 / gRPC 回调", 690, 820, role="core", status="independent"),
        Node("notify", "Notification Service\nGo · 验证码 / 邮件短信 / 通知通道", 1000, 820, role="core", status="independent"),
        Node("plugin", "Plugin Service\n插件定义 / 版本 / 市场 / 包仓库", 70, 1080, role="automation"),
        Node("pluginruntime", "Plugin Runtime\nGo + Python Sandbox · 受控执行", 380, 1080, role="automation", status="independent"),
        Node("automation-service", "Automation Service\n文件事件匹配 / Inbox-Outbox / 恢复", 690, 1080, role="automation"),
        Node("workflow", "Workflow Service\nDSL / 能力中心 / 执行记录 / 市场", 1000, 1080, role="automation"),
        Node("scheduler", "Scheduler Service\nCron / 租约 / 幂等触发", 1310, 1080, role="automation"),
        Node("cloudflow", "CloudFlow Runtime\nRust · 编译 / IR / 持久化执行", 1620, 1080, role="automation"),
        Node("mysql", "MySQL 8\n主数据 + 各服务独立 schema", 70, 1450, role="data"),
        Node("redis", "Redis 7\n缓存 / 在线状态 / 限流 / 锁", 380, 1450, role="data"),
        Node("rabbit", "RabbitMQ\nTask Bus / 事件 / Outbox / DLQ", 690, 1450, role="middleware"),
        Node("object", "MinIO + uploads-data\n对象 / 上传临时文件 / Git Object Provider", 1000, 1450, 330, 82, role="data"),
        Node("opensearch", "OpenSearch\n文件索引 / 内容检索 / 观测查询", 1410, 1450, 260, 82, role="data"),
        Node("skywalking", "SkyWalking OAP + UI\nTracing / Metrics / 日志观测", 1750, 1450, 360, 82, role="data"),
    ]
    edges = [
        Edge("web", "nginx"), Edge("admin", "nginx"), Edge("desktop", "nginx"),
        Edge("mobile", "nginx"), Edge("cli", "nginx"), Edge("extensions", "nginx"),
        Edge("nginx", "gateway", "HTTPS / REST"),
        # Keep the WebSocket path in the outer left corridor so it does not
        # visually cross the REST service boxes in the core row.
        Edge("nginx", "imserver", "", "sync",
             ((730, 520), (2340, 520), (2340, 1020), (670, 1020), (670, 861)),
             (1, 0.5, 1, 0.5)),
        Edge("apisix", "gateway", "可选入口", "sync"),
        Edge("gateway", "platform", "业务 API"), Edge("gateway", "storage", "文件 API"),
        Edge("gateway", "git", "Git API / Smart HTTP"), Edge("gateway", "clientreg", "客户端身份"),
        Edge("gateway", "plugin", "插件市场", "async"), Edge("gateway", "workflow", "工作流 API", "async"),
        Edge("storage", "platform", "内部回调", "sync"), Edge("git", "platform", "空间 / 权限", "sync"),
        Edge("git", "storage", "Object Broker", "data"), Edge("implatform", "imserver", "消息推送", "async"),
        Edge("imrouter", "imserver", "gRPC Push", "sync"), Edge("notify", "rabbit", "通知任务", "async"),
        Edge("platform", "mysql", "JDBC", "data"), Edge("platform", "redis", "缓存", "data"),
        Edge("platform", "rabbit", "业务事件", "async"), Edge("storage", "redis", "并发 / 授权", "data"),
        Edge("storage", "rabbit", "文件任务", "async"), Edge("storage", "object", "I/O", "data"),
        Edge("worker", "rabbit", "Task Bus", "async"), Edge("worker", "object", "处理文件", "data"),
        Edge("worker", "opensearch", "索引", "data"), Edge("implatform", "mysql", "IM 数据", "data"),
        Edge("implatform", "redis", "在线 / 会话", "data"), Edge("imserver", "redis", "连接状态", "data"),
        Edge("imserver", "rabbit", "推送事件", "async"), Edge("git", "rabbit", "git.push.completed", "async"),
        Edge("plugin", "pluginruntime", "运行时调用", "sync"), Edge("automation-service", "plugin", "入口匹配", "sync"),
        Edge("automation-service", "pluginruntime", "插件执行", "sync"), Edge("workflow", "cloudflow", "编译 / 执行", "sync"),
        Edge("scheduler", "workflow", "fire 事件", "async"), Edge("cloudflow", "workflow", "Capability Agent", "sync"),
        Edge("automation-service", "rabbit", "文件事件", "async"), Edge("workflow", "rabbit", "Outbox / 命令", "async"),
        Edge("scheduler", "rabbit", "调度事件", "async"), Edge("cloudflow", "rabbit", "持久命令", "async"),
        Edge("plugin", "mysql", "插件元数据", "data"), Edge("workflow", "mysql", "工作流元数据", "data"),
        Edge("cloudflow", "mysql", "执行状态", "data"), Edge("cloudflow", "opensearch", "追踪查询", "data"),
        Edge("platform", "skywalking", "Tracing", "data"), Edge("storage", "skywalking", "Tracing", "data"),
        Edge("workflow", "skywalking", "Tracing", "data"),
    ]
    notes = [
        Node("legend", "实线：HTTP / gRPC\n虚线：RabbitMQ 异步事件 / Task Bus\n点划线：数据或观测连接", 70, 1580, 440, 48, role="note"),
        Node("status-note", "部署状态：无边框实线 = 根 Compose；虚线边框 = automation profile 或独立部署\n以代码、Compose、README 与 Gateway 路由为准", 550, 1580, 1210, 48, role="note"),
    ]
    page = drawio_page("全局拓扑", "PrivateCloudDisk 平台全局架构（审计版）", 2400, 1700, groups, nodes, edges, notes)
    svg = svg_page("overview", "PrivateCloudDisk 平台全局架构（审计版）", 2400, 1700, groups, nodes, edges, notes)
    return page, svg, "overview"


def make_service_detail() -> tuple[str, str, str]:
    groups = [
        Group("entry", "入口与协议边界", 40, 90, 2520, 220, "edge"),
        Group("business", "核心业务服务边界", 40, 360, 1210, 800, "core"),
        Group("automation", "自动化与插件服务边界", 1320, 360, 1240, 800, "automation"),
        Group("data", "共享基础设施与数据面", 40, 1220, 2520, 260, "data"),
    ]
    nodes = [
        Node("clients", "Web / Admin / Desktop / Mobile / CLI\n多端通过边缘层访问", 90, 160, 420, 90, role="client"),
        Node("nginx", "Nginx + Certbot\nwww / api / ws / admin 域名边缘", 600, 160, 430, 90, role="edge"),
        Node("gateway", "Spring Cloud Gateway\n/api/v1/business · files · im · git · plugins · workflows\n/ws → IM Server", 1120, 150, 620, 110, role="edge"),
        Node("apisix", "APISIX（deploy/local 可选）\n独立于根 Compose", 1880, 160, 470, 90, role="edge", status="optional"),
        Node("platform", "Platform Service\nSpring Boot + MyBatis\n用户、空间、文件元数据、分享、权限、配额", 90, 430, 330, 130, role="core"),
        Node("storage", "Storage API\nFastAPI + Uvicorn\n分片、下载、预览、Range、授权", 460, 430, 330, 130, role="core"),
        Node("worker", "Storage Worker\nPython + aio-pika\nMerge / Hash / Virus / Index / Active", 830, 430, 330, 130, role="core"),
        Node("git", "Git Service\nGo\nSmart HTTP、SSH、refs、Object、MR、审计", 90, 650, 330, 130, role="core"),
        Node("billing", "Billing Service\nSpring Boot\n订阅、账单、支付适配（Compose）", 460, 650, 330, 130, role="core"),
        Node("clientreg", "Client Registration\nGo / Gin\n客户端挑战、设备身份、扩展绑定", 830, 650, 330, 130, role="core"),
        Node("implatform", "IM Platform\nSpring Boot + MyBatis\n消息、会话、好友、群组 HTTP", 90, 870, 330, 130, role="core"),
        Node("imserver", "IM Server\nNetty + Protobuf\nWebSocket、握手、在线推送", 460, 870, 330, 130, role="core"),
        Node("imrouter", "IM Router\nGo + gRPC\n用户在线路由与回调\n独立于根 Compose", 830, 870, 330, 130, role="core", status="independent"),
        Node("notify", "Notification Service\nGo\n验证码、邮件短信、通知通道\n独立于根 Compose", 90, 1040, 330, 90, role="core", status="independent"),
        Node("plugin", "Plugin Service\nJava / Spring Boot\n插件市场、版本、包与权限", 1370, 430, 330, 130, role="automation"),
        Node("pluginruntime", "Plugin Runtime\nGo + Python Sandbox\nmanifest 校验与受控执行\n独立运行时", 1740, 430, 330, 130, role="automation", status="independent"),
        Node("automation-service", "Automation Service\nJava / Spring Boot\n文件事件匹配、Inbox/Outbox、恢复", 2110, 430, 330, 130, role="automation"),
        Node("workflow", "Workflow Service\nJava / Spring Boot\nDSL、能力中心、执行、市场", 1370, 650, 330, 130, role="automation"),
        Node("scheduler", "Scheduler Service\nJava / Spring Boot\nCron、租约、幂等 fire", 1740, 650, 330, 130, role="automation"),
        Node("cloudflow", "CloudFlow Runtime\nRust / Tokio / Axum\n编译、IR、DAG 执行、状态", 2110, 650, 330, 130, role="automation"),
        Node("ai", "AI Service\nFastAPI + PyTorch/ONNX\n可选推理 / 异步模型任务", 1370, 870, 330, 130, role="automation"),
        Node("observability", "SkyWalking Agent → OAP / UI\nJava、Python、Go 服务统一观测", 1740, 870, 700, 130, role="data"),
        Node("mysql", "MySQL 8\nprivate_cloud_disk + pcd_* schemas", 100, 1280, 360, 100, role="data"),
        Node("redis", "Redis 7\n缓存、限流、在线状态、锁、会话", 520, 1280, 360, 100, role="data"),
        Node("rabbit", "RabbitMQ 3\nTask Bus、Outbox、事件、DLQ", 940, 1280, 360, 100, role="middleware"),
        Node("object", "MinIO / uploads-data\n文件对象、Git Object Provider、预览产物", 1360, 1280, 460, 100, role="data"),
        Node("opensearch", "OpenSearch 2.10\n文件基础索引、内容索引、观测", 1880, 1280, 360, 100, role="data"),
    ]
    edges = [
        Edge("clients", "nginx", "HTTPS / WSS"), Edge("nginx", "gateway", "反向代理"), Edge("apisix", "gateway", "可选"),
        Edge("gateway", "platform", "REST"), Edge("gateway", "storage", "REST"), Edge("gateway", "git", "REST + Smart HTTP"),
        Edge("gateway", "implatform", "REST"), Edge("gateway", "imserver", "WSS"), Edge("gateway", "plugin", "REST", "async"),
        Edge("gateway", "workflow", "REST", "async"), Edge("storage", "platform", "内部回调"), Edge("git", "platform", "空间 / 权限"),
        Edge("implatform", "imserver", "MQ / push", "async"), Edge("imrouter", "imserver", "gRPC"),
        Edge("plugin", "pluginruntime", "HTTP / Broker"), Edge("automation-service", "plugin", "匹配入口"),
        Edge("automation-service", "pluginruntime", "执行", "async"), Edge("workflow", "cloudflow", "编译 / 执行"),
        Edge("scheduler", "workflow", "fire", "async"), Edge("cloudflow", "workflow", "Capability Agent"),
        Edge("platform", "mysql", "JDBC", "data"), Edge("storage", "redis", "缓存 / 限流", "data"),
        Edge("storage", "rabbit", "文件任务", "async"), Edge("worker", "rabbit", "Task Bus", "async"),
        Edge("worker", "object", "文件 I/O", "data"), Edge("git", "object", "Object Broker", "data"),
        Edge("implatform", "redis", "在线 / 会话", "data"), Edge("implatform", "mysql", "IM 数据", "data"),
        Edge("imserver", "redis", "连接状态", "data"), Edge("plugin", "mysql", "插件元数据", "data"),
        Edge("workflow", "mysql", "工作流元数据", "data"), Edge("cloudflow", "mysql", "执行状态", "data"),
        Edge("cloudflow", "rabbit", "命令 / DLQ", "async"), Edge("storage", "opensearch", "文件 / 内容索引", "data"),
        Edge("worker", "opensearch", "索引更新", "data"), Edge("observability", "opensearch", "观测查询", "data"),
    ]
    notes = [
        Node("note", "边界原则：Platform 持有用户、空间与文件元数据；Storage 持有文件 I/O；Git 持有 Git 索引并通过 Storage 共享物理对象池；IM 不直接读用户表。", 90, 1130, 1120, 55, role="note"),
        Node("status", "状态说明：根 Compose 默认服务 / automation profile 服务 / 仓库代码中存在的独立部署组件分别用实线或虚线边框标记。", 1370, 1040, 1070, 55, role="note"),
    ]
    page = drawio_page("服务依赖", "PrivateCloudDisk 微服务边界与依赖关系（审计版）", 2600, 1540, groups, nodes, edges, notes)
    svg = svg_page("services", "PrivateCloudDisk 微服务边界与依赖关系（审计版）", 2600, 1540, groups, nodes, edges, notes)
    return page, svg, "services"


def main() -> None:
    overview_page, overview_svg, _ = make_overview()
    service_page, service_svg, _ = make_service_detail()
    OUT.mkdir(parents=True, exist_ok=True)
    drawio_path = OUT / "privateclouddisk-platform-architecture.drawio"
    drawio_path.write_text(drawio_file([overview_page, service_page]), encoding="utf-8")
    for stem, svg in (("privateclouddisk-platform-architecture-overview", overview_svg),
                      ("privateclouddisk-platform-architecture-services", service_svg)):
        svg_path = OUT / f"{stem}.svg"
        svg_path.write_text(svg, encoding="utf-8")
        png_path = OUT / f"{stem}.png"
        subprocess.run(["rsvg-convert", "-o", str(png_path), str(svg_path)], check=True)
    print(f"wrote {drawio_path}")


if __name__ == "__main__":
    main()
