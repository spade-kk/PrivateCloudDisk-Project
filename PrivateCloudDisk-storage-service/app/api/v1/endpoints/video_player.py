"""
播放器全局配置 API 端点
提供前端播放器的全局配置参数

API 列表:
- GET /files/video/player/config  — 获取播放器全局配置
"""
from __future__ import annotations
import logging
from fastapi import APIRouter

logger = logging.getLogger("video_player")

router = APIRouter(prefix="/files/video/player", tags=["播放器配置"])


@router.get("/config", summary="获取播放器全局配置")
async def get_player_config():
    """
    获取播放器的全局配置参数

    返回前端播放器初始化所需的全局配置，包括：
    - 自动播放策略
    - 默认音量
    - 进度上报间隔
    - 支持的播放速度列表
    - 缓冲策略参数
    - 快捷键配置

    对标 YouTube/Bilibili 播放器配置体验。
    """
    return {
        "code": 200,
        "data": {
            # 播放策略
            "autoplay": False,                    # 默认不自动播放
            "autoplay_next": False,               # 不自动播放下一集
            "default_volume": 0.7,                # 默认音量 70%
            "default_playback_rate": 1.0,         # 默认播放速度 1x
            "default_resolution": "auto",         # 默认自适应分辨率

            # 进度上报
            "progress_report_interval": 5,        # 进度上报间隔（秒）
            "progress_save_debounce": 5,          # 进度保存最小变化阈值（秒）

            # 缓冲策略
            "max_buffer_length": 30,              # 最大缓冲时长（秒）
            "max_max_buffer_length": 600,         # 绝对最大缓冲时长（秒）
            "back_buffer_length": 90,             # 后退缓冲时长（秒）
            "max_buffer_size": 60 * 1000 * 1000,  # 最大缓冲大小（字节）= 60MB

            # ABR 自适应码率
            "abr_ewma_default_estimate": 500000,  # 默认带宽估计（bps）
            "abr_bandwidth_factor": 0.95,         # 带宽平滑因子
            "abr_bandwidth_up_factor": 0.7,       # 带宽上调因子

            # 播放速度选项
            "playback_rates": [
                0.25, 0.5, 0.75, 1.0,
                1.25, 1.5, 1.75, 2.0, 2.5, 3.0
            ],

            # 快捷键
            "shortcuts_enabled": True,
            "shortcuts": {
                "space": "播放/暂停",
                "arrow_left": "快退 5 秒",
                "arrow_right": "快进 5 秒",
                "arrow_up": "音量 +5%",
                "arrow_down": "音量 -5%",
                "f": "全屏切换",
                "m": "静音切换",
                "j": "快退 10 秒",
                "l": "快进 10 秒",
                "s": "截图",
                "n": "下一集",
                "p": "上一集",
                "0-9": "跳转到 0%-90%",
                "?": "快捷键帮助",
            },

            # 控制栏
            "controls_auto_hide_delay": 3000,     # 控制栏自动隐藏延迟（毫秒）

            # 手势
            "touch_gestures_enabled": True,

            # 后端端点基础路径（供前端参考）
            "endpoints": {
                "stream_info": "/api/v1/files/video/stream/{file_id}/info",
                "stream_token": "/api/v1/files/video/stream/{file_id}/token",
                "hls_master": "/api/v1/files/video/stream/{file_id}/master.m3u8",
                "progress": "/api/v1/files/video/progress/{file_id}",
                "history": "/api/v1/files/video/history/{file_id}",
                "subtitle": "/api/v1/files/video/subtitle/{file_id}",
                "sprite": "/api/v1/files/video/sprite/{file_id}",
                "thumbnail": "/api/v1/files/files/{file_id}/thumbnail",
            },
        },
    }