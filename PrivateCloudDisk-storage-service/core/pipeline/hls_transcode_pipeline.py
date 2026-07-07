"""
HLS 流媒体转码流水线
使用 ffmpeg-python 库将视频转码为多码率 HLS (HTTP Live Streaming) 格式

企业级特性:
- 多分辨率自适应码率 (ABR): 360p/480p/720p/1080p
- 硬件加速: 自动检测 GPU 编码器 (VideoToolbox/VAAPI/NVENC)
- 关键帧对齐: 各分辨率关键帧间隔一致，确保无缝切换
- 分片时长: 6 秒 TS 分片，平衡延迟与缓存效率
- 主播放列表: 自动生成 master.m3u8，包含各码率流信息
- 容错处理: 单个分辨率失败不影响其他分辨率
- 文件命名: {file_id}/{resolution}/index.m3u8 + segment-N.ts
"""
from __future__ import annotations
import logging
import os
import json
import asyncio
import platform
from dataclasses import dataclass, field
import ffmpeg
import math
from core.config import settings, FailureReason, VIDEO_TYPES

logger = logging.getLogger("hls_transcode_pipeline")


# =============================================================================
# HLS 转码预设配置
# 多分辨率自适应码率 (ABR): 360p / 480p / 720p / 1080p
# 所有分辨率使用相同的关键帧间隔 (2s) 确保无缝切换
# =============================================================================
HLS_TRANSCODE_PRESETS = [
    {
        "label": "360p",
        "width": 640,
        "height": 360,
        "video_bitrate": "800k",
        "audio_bitrate": "96k",
        "maxrate": "856k",
        "bufsize": "1200k",
        "bandwidth": 800_000,
    },
    {
        "label": "480p",
        "width": 854,
        "height": 480,
        "video_bitrate": "1400k",
        "audio_bitrate": "128k",
        "maxrate": "1498k",
        "bufsize": "2100k",
        "bandwidth": 1_400_000,
    },
    {
        "label": "720p",
        "width": 1280,
        "height": 720,
        "video_bitrate": "2800k",
        "audio_bitrate": "192k",
        "maxrate": "2996k",
        "bufsize": "4200k",
        "bandwidth": 2_800_000,
    },
    {
        "label": "1080p",
        "width": 1920,
        "height": 1080,
        "video_bitrate": "5000k",
        "audio_bitrate": "192k",
        "maxrate": "5350k",
        "bufsize": "7500k",
        "bandwidth": 5_000_000,
    },
]

# HLS 分片时长 (秒) - Apple 推荐 6s
HLS_SEGMENT_DURATION = 6

# 播放列表保留的分片数量 (0 = 全部保留)
HLS_PLAYLIST_LENGTH = 0

# 企业级雪碧图配置 (对标 B站)
SPRITE_THUMB_WIDTH = 160      # 缩略图宽度
SPRITE_THUMB_HEIGHT = 90      # 缩略图高度 (16:9)
SPRITE_COLS = 10              # 每行 10 个缩略图
SPRITE_INTERVAL = 10          # 每 10 秒截取一帧


@dataclass
class HlsTranscodeResult:
    """HLS 转码结果"""
    success: bool
    skipped: bool = False
    skipped_reason: str = ""
    hls_dir: str = ""                     # HLS 输出根目录
    hls_master_playlist: str = ""         # master.m3u8 路径
    hls_resolutions: list = field(default_factory=list)  # 各分辨率详情
    resolutions: list = field(default_factory=list)      # 前端兼容: 分辨率列表
    error: str = ""
    failure_reason: str = ""


class HlsTranscodePipeline:
    """
    HLS 流媒体转码流水线

    转码流程:
    1. 检查是否为视频类型 → 不是则跳过
    2. 检查 ffmpeg 是否可用
    3. 生成视频预览图
    4. 按分辨率逐个转码 + 切片 (生成 .m3u8 + .ts)
    5. 生成 master.m3u8 主播放列表
    6. 写入 manifest.json 元数据 (供 API 查询)
    """

    @staticmethod
    async def execute(
        file_id: str,
        storage_path: str,
        file_type: str,
    ) -> HlsTranscodeResult:
        """
        执行 HLS 转码

        Args:
            file_id: 文件唯一标识
            storage_path: 源文件物理路径
            file_type: MIME 类型

        Returns:
            HlsTranscodeResult: 转码结果
        """
        logger.info(f"[HLS] 开始 HLS 转码: file_id={file_id}")

        if file_type not in VIDEO_TYPES:
            logger.info(f"[HLS] 非视频文件，跳过: file_id={file_id}, type={file_type}")
            return HlsTranscodeResult(
                success=True, skipped=True, skipped_reason="非视频文件"
            )

        if not os.path.exists(storage_path):
            return HlsTranscodeResult(
                success=False,
                failure_reason=FailureReason.TRANSCODE_ERROR,
                error=f"源文件不存在: {storage_path}",
            )

        if not await HlsTranscodePipeline._ffmpeg_available():
            logger.warning("[HLS] ffmpeg 未安装，跳过 HLS 转码")
            return HlsTranscodeResult(
                success=True, skipped=True, skipped_reason="ffmpeg 未安装"
            )

        # ---- 目录结构 ----
        hls_root = os.path.join(settings.file_upload_dir, "hls")
        hls_dir = os.path.join(hls_root, file_id)
        thumbnail_dir = os.path.join(settings.file_upload_dir, "thumbnails")
        os.makedirs(hls_dir, exist_ok=True)
        os.makedirs(thumbnail_dir, exist_ok=True)

        # ---- 探测源视频信息 ----
        source_info = await HlsTranscodePipeline._probe_source(storage_path)
        source_width = source_info.get("width", 0)
        source_height = source_info.get("height", 0)
        source_duration = source_info.get("duration", 0)

        # 过滤掉超出源视频分辨率范围的预设
        active_presets = [
            p for p in HLS_TRANSCODE_PRESETS
            if source_width == 0 or p["width"] <= source_width
        ]
        if not active_presets:
            active_presets = [HLS_TRANSCODE_PRESETS[0]]

        logger.info(
            f"[HLS] 源视频: {source_width}x{source_height}, "
            f"时长={source_duration:.1f}s, "
            f"激活预设数={len(active_presets)}"
        )

        hls_resolutions = []
        resolutions = []

        try:
            # 1. 生成视频预览图
            preview_path = await HlsTranscodePipeline._generate_preview(
                storage_path, file_id, thumbnail_dir,
            )

            # 2. 检测可用的硬件加速编码器
            hw_encoder = await HlsTranscodePipeline._detect_hw_encoder()

            # 3. 按分辨率转码 + HLS 切片
            for preset in active_presets:
                resolution_dir = os.path.join(hls_dir, preset["label"])
                playlist_path = os.path.join(resolution_dir, "index.m3u8")
                os.makedirs(resolution_dir, exist_ok=True)

                try:
                    success = await HlsTranscodePipeline._transcode_hls(
                        input_path=storage_path,
                        output_playlist=playlist_path,
                        preset=preset,
                        hw_encoder=hw_encoder,
                    )
                    if success:
                        hls_resolutions.append({
                            "label": preset["label"],
                            "width": preset["width"],
                            "height": preset["height"],
                            "bandwidth": preset["bandwidth"],
                            "video_bitrate": preset["video_bitrate"],
                            "playlist": playlist_path,
                            "playlist_url": f"hls/{file_id}/{preset['label']}/index.m3u8",
                        })
                        resolutions.append({
                            "label": preset["label"],
                            "width": preset["width"],
                            "height": preset["height"],
                            "bitrate": preset["bandwidth"],
                        })
                        logger.info(
                            f"[HLS] 转码完成: {preset['label']} "
                            f"({preset['width']}x{preset['height']}, "
                            f"{preset['video_bitrate']})"
                        )
                except asyncio.TimeoutError:
                    logger.warning(f"[HLS] 转码超时: {preset['label']}")
                except Exception as e:
                    logger.warning(f"[HLS] 转码失败 {preset['label']}: {e}")

            if not hls_resolutions:
                return HlsTranscodeResult(
                    success=False,
                    failure_reason=FailureReason.TRANSCODE_ERROR,
                    error="所有分辨率 HLS 转码均失败",
                )

            # 4. 生成 master.m3u8 主播放列表
            master_playlist_path = os.path.join(hls_dir, "master.m3u8")
            await HlsTranscodePipeline._generate_master_playlist(
                master_playlist_path=master_playlist_path,
                resolutions=hls_resolutions,
            )

            # 4.5. 生成雪碧图 (Sprite Sheet) — 进度条悬停预览
            sprite_config = await HlsTranscodePipeline._generate_sprite(
                input_path=storage_path,
                file_id=file_id,
                hls_dir=hls_dir,
                duration=source_duration,
                source_width=source_width,
                source_height=source_height,
            )

            # 5. 写入 manifest.json 元数据
            manifest_path = os.path.join(hls_dir, "manifest.json")
            manifest = {
                "file_id": file_id,
                "source_width": source_width,
                "source_height": source_height,
                "duration": source_duration,
                "resolutions": resolutions,
                "preview_path": preview_path,
                "sprite": sprite_config,
                "created_at": None,
            }
            with open(manifest_path, "w") as f:
                json.dump(manifest, f, indent=2, ensure_ascii=False)

            logger.info(
                f"[HLS] HLS 转码完成: file_id={file_id}, "
                f"resolutions={len(hls_resolutions)}, "
                f"master={master_playlist_path}"
            )

            return HlsTranscodeResult(
                success=True,
                hls_dir=hls_dir,
                hls_master_playlist=master_playlist_path,
                hls_resolutions=hls_resolutions,
                resolutions=resolutions,
            )

        except Exception as e:
            logger.error(f"[HLS] HLS 转码异常: {e}", exc_info=True)
            return HlsTranscodeResult(
                success=False,
                failure_reason=FailureReason.TRANSCODE_ERROR,
                error=str(e),
            )

    # =========================================================================
    # 私有方法: ffmpeg 操作 (全部使用 ffmpeg-python 库)
    # =========================================================================

    @staticmethod
    async def _ffmpeg_available() -> bool:
        """
        检查 ffmpeg 是否可用

        通过 ffmpeg.probe 尝试探测一个不存在的文件来验证 ffmpeg 是否在 PATH 中。
        ffmpeg 存在时会抛出 ffmpeg.Error (文件不存在)，不存在时抛出 FileNotFoundError。
        """
        try:
            await asyncio.to_thread(
                ffmpeg.probe, "__ffmpeg_check_dummy__"
            )
            return False  # 不应该走到这里
        except ffmpeg.Error:
            return True  # ffmpeg 存在，但探测文件不存在 → ffmpeg 可用
        except FileNotFoundError:
            return False  # ffmpeg 不在 PATH 中
        except Exception:
            return False

    @staticmethod
    async def _detect_hw_encoder() -> str | None:
        """
        检测可用的硬件加速编码器

        通过系统环境特征判断，而非调用 ffmpeg -encoders，避免子进程调用。
        优先级:
          1. macOS: h264_videotoolbox
          2. Linux (NVIDIA): h264_nvenc
          3. Linux (Intel): h264_vaapi
          4. 软件: libx264
        """
        try:
            system = platform.system()

            if system == "Darwin":
                logger.info("[HLS] 检测到硬件编码器: h264_videotoolbox (Apple Silicon/Intel Mac)")
                return "h264_videotoolbox"

            if system == "Linux":
                if os.path.exists("/dev/nvidia0") or os.path.exists("/dev/nvidiactl"):
                    logger.info("[HLS] 检测到硬件编码器: h264_nvenc (NVIDIA GPU)")
                    return "h264_nvenc"
                if os.path.exists("/dev/dri/renderD128"):
                    logger.info("[HLS] 检测到硬件编码器: h264_vaapi (Intel GPU)")
                    return "h264_vaapi"

            logger.info("[HLS] 未检测到硬件编码器，使用软件编码: libx264")
            return None
        except Exception:
            return None

    @staticmethod
    async def _probe_source(input_path: str) -> dict:
        """
        使用 ffmpeg.probe 探测源视频信息

        通过 ffmpeg-python 的 probe 接口获取 JSON 格式的媒体信息，
        无需手动构造 ffprobe 子进程调用。

        Returns:
            dict: {"width": int, "height": int, "duration": float}
        """
        info = {"width": 0, "height": 0, "duration": 0}
        try:
            data = await asyncio.wait_for(
                asyncio.to_thread(ffmpeg.probe, input_path),
                timeout=30,
            )

            for stream in data.get("streams", []):
                if stream.get("codec_type") == "video":
                    info["width"] = stream.get("width", 0)
                    info["height"] = stream.get("height", 0)
                    break

            info["duration"] = float(data.get("format", {}).get("duration", 0))
            logger.info(
                f"[HLS] 源视频探测: {info['width']}x{info['height']}, "
                f"时长={info['duration']:.1f}s"
            )
        except asyncio.TimeoutError:
            logger.warning("[HLS] 源视频探测超时")
        except ffmpeg.Error as e:
            stderr_text = e.stderr.decode("utf-8", errors="replace") if e.stderr else ""
            logger.warning(f"[HLS] 源视频探测失败: {stderr_text[:200]}")
        except Exception as e:
            logger.warning(f"[HLS] 源视频探测异常: {e}")

        return info

    @staticmethod
    async def _generate_preview(
        input_path: str, file_id: str, output_dir: str,
    ) -> str | None:
        """使用 ffmpeg-python 生成视频预览图（第 1 秒帧）"""
        preview_path = os.path.join(output_dir, f"{file_id}_preview.jpg")
        try:
            await asyncio.wait_for(
                asyncio.to_thread(
                    lambda: (
                        ffmpeg
                        .input(input_path, ss="00:00:01")
                        .output(preview_path, vframes=1, **{"q:v": "2"})
                        .overwrite_output()
                        .run(capture_stdout=True, capture_stderr=True)
                    )
                ),
                timeout=60,
            )
            if os.path.exists(preview_path):
                return preview_path
            return None
        except asyncio.TimeoutError:
            logger.warning("[HLS] 预览图生成超时")
            return None
        except ffmpeg.Error as e:
            stderr_text = e.stderr.decode("utf-8", errors="replace") if e.stderr else ""
            logger.warning(f"[HLS] 预览图生成失败: {stderr_text[:200]}")
            return None
        except Exception as e:
            logger.warning(f"[HLS] 预览图生成异常: {e}")
            return None

    @staticmethod
    async def _transcode_hls(
        input_path: str,
        output_playlist: str,
        preset: dict,
        hw_encoder: str | None = None,
    ) -> bool:
        """
        使用 ffmpeg-python 转码单个分辨率到 HLS 格式

        输出:
          - {resolution_dir}/index.m3u8  (播放列表)
          - {resolution_dir}/segment-XXXXX.ts  (TS 分片)
        """
        resolution_dir = os.path.dirname(output_playlist)
        segment_pattern = os.path.join(resolution_dir, "segment-%05d.ts")

        try:
            # 构建输入流（需要显式映射视频和音频流，否则 ffmpeg-python 可能丢失音频）
            input_stream = ffmpeg.input(input_path)

            # 根据编码器类型构建输出参数
            if hw_encoder:
                output_kwargs = HlsTranscodePipeline._build_hw_output_kwargs(
                    preset, hw_encoder)
            else:
                output_kwargs = HlsTranscodePipeline._build_sw_output_kwargs(preset)

            # 添加 HLS 通用参数
            output_kwargs.update({
                "f": "hls",
                "hls_time": HLS_SEGMENT_DURATION,
                "hls_list_size": HLS_PLAYLIST_LENGTH,
                "hls_segment_filename": segment_pattern,
                "hls_flags": "independent_segments",
                "hls_playlist_type": "vod",
                "start_number": 0,
            })

            logger.info(
                f"[HLS] 开始转码: {preset['label']} "
                f"({preset['width']}x{preset['height']}, "
                f"encoder={'hw:' + hw_encoder if hw_encoder else 'libx264'})"
            )

            await asyncio.wait_for(
                asyncio.to_thread(
                    lambda: (
                        ffmpeg
                        .output(input_stream, output_playlist, **output_kwargs)
                        .global_args('-map', '0:v', '-map', '0:a?')
                        .overwrite_output()
                        .run(capture_stdout=True, capture_stderr=True)
                    )
                ),
                timeout=1800,
            )

            # 验证输出文件
            if not os.path.exists(output_playlist):
                logger.warning(f"[HLS] 播放列表未生成: {output_playlist}")
                return False

            ts_files = [f for f in os.listdir(resolution_dir) if f.endswith(".ts")]
            logger.info(f"[HLS] {preset['label']}: 生成 {len(ts_files)} 个 TS 分片")

            return True

        except asyncio.TimeoutError:
            logger.warning(f"[HLS] 转码超时: {preset['label']}")
            return False
        except ffmpeg.Error as e:
            stderr_text = e.stderr.decode("utf-8", errors="replace") if e.stderr else ""
            logger.warning(f"[HLS] 转码失败 {preset['label']}: {stderr_text[:300]}")
            return False
        except Exception as e:
            logger.warning(f"[HLS] 转码异常 {preset['label']}: {e}")
            return False

    @staticmethod
    def _build_sw_output_kwargs(preset: dict) -> dict:
        """
        构建软件编码 (libx264) 的输出参数

        ffmpeg-python 使用 kwargs 方式传递参数，安全性更高:
        - 自动转义参数值
        - 避免命令注入风险
        - 类型安全
        """
        return {
            # 视频编码
            "c:v": "libx264",
            "preset": "medium",
            "profile:v": "main",
            "b:v": preset["video_bitrate"],
            "maxrate": preset["maxrate"],
            "bufsize": preset["bufsize"],
            "s": f"{preset['width']}x{preset['height']}",
            "pix_fmt": "yuv420p",
            # 关键帧对齐 (GOP=2s, 假设 30fps → 60 帧)
            "g": 60,
            "keyint_min": 60,
            "sc_threshold": 0,
            # 音频编码 — 使用 AAC-LC (Low Complexity) 确保浏览器兼容性
            # AAC-LC 是所有主流浏览器（Chrome/Safari/Firefox/Edge）都支持的音频编码
            # flags:a +bitexact 移除编码器元数据帧（如 "Lavc61.19.101"），
            # 避免浏览器 MediaSource 将元数据帧误判为无效音频数据而抛出
            # DECODER_ERROR_NOT_SUPPORTED: kUnsupportedConfig 错误
            "c:a": "aac",
            "flags:a": "+bitexact",
            "profile:a": "aac_low",
            "b:a": preset["audio_bitrate"],
            "ar": 48000,
            "ac": 2,
        }

    @staticmethod
    def _build_hw_output_kwargs(preset: dict, hw_encoder: str) -> dict:
        """
        构建硬件加速编码的输出参数

        根据不同的硬件编码器类型返回对应的输出参数。
        """
        common = {
            "b:v": preset["video_bitrate"],
            "maxrate": preset["maxrate"],
            "bufsize": preset["bufsize"],
            "s": f"{preset['width']}x{preset['height']}",
            "pix_fmt": "yuv420p",
            "g": 60,
            "keyint_min": 60,
            "sc_threshold": 0,
            # 音频编码 — 使用 AAC-LC 确保浏览器兼容性
            # flags:a +bitexact 移除编码器元数据帧
            "c:a": "aac",
            "flags:a": "+bitexact",
            "profile:a": "aac_low",
            "b:a": preset["audio_bitrate"],
            "ar": 48000,
            "ac": 2,
        }

        if hw_encoder == "h264_videotoolbox":
            common.update({
                "c:v": "h264_videotoolbox",
                "realtime": "true",
                "allow_sw": "true",
            })
        elif hw_encoder == "h264_nvenc":
            common.update({
                "c:v": "h264_nvenc",
                "preset": "p4",
                "rc": "vbr_hq",
            })
        elif hw_encoder == "h264_vaapi":
            common.update({
                "vaapi_device": "/dev/dri/renderD128",
                "c:v": "h264_vaapi",
            })

        return common

    @staticmethod
    async def _generate_master_playlist(
        master_playlist_path: str,
        resolutions: list,
    ) -> None:
        """
        生成 HLS 主播放列表 (master.m3u8)

        主播放列表包含所有码率流的索引信息，播放器根据网络状况自动选择。
        格式遵循 Apple HLS RFC 8216 规范。

        Args:
            master_playlist_path: master.m3u8 输出路径
            resolutions: 各分辨率详情列表
        """
        lines = ["#EXTM3U", "#EXT-X-VERSION:3"]

        for res in resolutions:
            width = res["width"]
            height = res["height"]
            bandwidth = res["bandwidth"]
            label = res["label"]
            playlist_url = f"{label}/index.m3u8"

            lines.append(
                f'#EXT-X-STREAM-INF:'
                f'BANDWIDTH={bandwidth},'
                f'RESOLUTION={width}x{height},'
                f'NAME="{label}"'
            )
            lines.append(playlist_url)

        lines.append("")  # 文件末尾换行

        content = "\n".join(lines)
        with open(master_playlist_path, "w") as f:
            f.write(content)

        logger.info(
            f"[HLS] 主播放列表已生成: {master_playlist_path}, "
            f"包含 {len(resolutions)} 个码率流"
        )

    # =========================================================================
    # 雪碧图生成 (Sprite Sheet) — 企业级进度条悬停预览
    # =========================================================================
    # 策略: 在 HLS 转码时预生成雪碧图，而非播放时动态生成。
    # 原因:
    #   1. 播放时动态生成需要多次 ffmpeg seek，延迟高、CPU 密集
    #   2. 预生成可离线完成，播放时仅需一次 HTTP 请求加载雪碧图
    #   3. 前端通过 CSS background-position 在 1ms 内完成缩略图切换
    #   4. 对标 B站/YouTube 的企业级方案



    @staticmethod
    async def _generate_sprite(
        input_path: str,
        file_id: str,
        hls_dir: str,
        duration: float,
        source_width: int = 0,
        source_height: int = 0,
    ) -> dict | None:
        """
        使用 ffmpeg 生成视频雪碧图 (Sprite Sheet) 和 VTT 元数据

        企业级实现:
        - 使用 ffmpeg fps + tile 滤镜一次性生成雪碧图
        - 自动计算网格尺寸 (cols固定, rows动态)
        - 生成 WebVTT 格式元数据文件，供前端 hls.js / video.js 使用
        - 返回 sprite 配置信息写入 manifest.json

        Args:
            input_path: 源视频文件路径
            file_id: 文件 ID
            hls_dir: HLS 输出目录
            duration: 视频时长 (秒)
            source_width: 源视频宽度
            source_height: 源视频高度

        Returns:
            dict: sprite 配置信息，包含 cols, rows, interval, thumb_width, thumb_height
            None: 生成失败
        """
        sprite_image_path = os.path.join(hls_dir, "sprite.jpg")
        vtt_path = os.path.join(hls_dir, "sprite.vtt")

        if duration <= 0:
            logger.warning(f"[HLS-SPRITE] 视频时长无效 ({duration}s)，跳过雪碧图生成")
            return None

        # 计算雪碧图网格
        total_thumbnails = max(1, int(duration / SPRITE_INTERVAL))
        rows = math.ceil(total_thumbnails / SPRITE_COLS)
        total_slots = SPRITE_COLS * rows

        # 计算 fps 滤镜参数: 每 SPRITE_INTERVAL 秒取一帧
        # fps=1/SPRITE_INTERVAL 表示每 SPRITE_INTERVAL 秒输出一帧
        fps_value = f"1/{SPRITE_INTERVAL}"

        # 雪碧图总像素尺寸
        sprite_width = SPRITE_COLS * SPRITE_THUMB_WIDTH
        sprite_height = rows * SPRITE_THUMB_HEIGHT

        logger.info(
            f"[HLS-SPRITE] 开始生成雪碧图: file_id={file_id}, "
            f"duration={duration:.1f}s, "
            f"thumbnails={total_thumbnails}, "
            f"grid={SPRITE_COLS}x{rows}, "
            f"sprite_size={sprite_width}x{sprite_height}"
        )

        try:
            # 使用 ffmpeg 一次性生成雪碧图
            # ffmpeg -i input -vf "fps=1/10,scale=160:90,tile=10xN" -q:v 3 sprite.jpg
            await asyncio.wait_for(
                asyncio.to_thread(
                    lambda: (
                        ffmpeg
                        .input(input_path)
                        .output(
                            sprite_image_path,
                            vf=(
                                f"fps={fps_value},"
                                f"scale={SPRITE_THUMB_WIDTH}:{SPRITE_THUMB_HEIGHT},"
                                f"tile={SPRITE_COLS}x{rows}"
                            ),
                            **{"q:v": "3"},
                            vsync="0",
                        )
                        .overwrite_output()
                        .run(capture_stdout=True, capture_stderr=True)
                    )
                ),
                timeout=600,  # 雪碧图生成最多 10 分钟
            )

            if not os.path.exists(sprite_image_path):
                logger.warning("[HLS-SPRITE] 雪碧图文件未生成")
                return None

            file_size = os.path.getsize(sprite_image_path)
            logger.info(
                f"[HLS-SPRITE] 雪碧图生成完成: "
                f"size={file_size / 1024:.1f}KB, "
                f"grid={SPRITE_COLS}x{rows}"
            )

            # 生成 WebVTT 元数据文件
            await HlsTranscodePipeline._generate_sprite_vtt(
                vtt_path=vtt_path,
                total_thumbnails=total_thumbnails,
                sprite_filename="sprite.jpg",
            )

            return {
                "cols": SPRITE_COLS,
                "rows": rows,
                "interval": SPRITE_INTERVAL,
                "thumb_width": SPRITE_THUMB_WIDTH,
                "thumb_height": SPRITE_THUMB_HEIGHT,
                "total_thumbnails": total_thumbnails,
                "sprite_width": sprite_width,
                "sprite_height": sprite_height,
            }

        except asyncio.TimeoutError:
            logger.warning("[HLS-SPRITE] 雪碧图生成超时")
            return None
        except ffmpeg.Error as e:
            stderr_text = e.stderr.decode("utf-8", errors="replace") if e.stderr else ""
            logger.warning(f"[HLS-SPRITE] 雪碧图生成失败: {stderr_text[:300]}")
            return None
        except Exception as e:
            logger.warning(f"[HLS-SPRITE] 雪碧图生成异常: {e}")
            return None

    @staticmethod
    async def _generate_sprite_vtt(
        vtt_path: str,
        total_thumbnails: int,
        sprite_filename: str = "sprite.jpg",
    ) -> None:
        """
        生成 WebVTT 格式的雪碧图元数据文件

        VTT 格式示例:
        ```
        WEBVTT

        00:00:00.000 --> 00:00:10.000
        sprite.jpg#xywh=0,0,160,90

        00:00:10.000 --> 00:00:20.000
        sprite.jpg#xywh=160,0,160,90
        ```

        前端通过解析 VTT 文件，根据悬停时间计算对应的雪碧图坐标，
        使用 CSS background-position 高速切换预览图。
        """
        def _format_time(seconds: float) -> str:
            """格式化为 VTT 时间戳 HH:MM:SS.mmm"""
            h = int(seconds // 3600)
            m = int((seconds % 3600) // 60)
            s = seconds % 60
            return f"{h:02d}:{m:02d}:{s:06.3f}"

        lines = ["WEBVTT", ""]

        for i in range(total_thumbnails):
            start_time = i * SPRITE_INTERVAL
            end_time = min((i + 1) * SPRITE_INTERVAL, start_time + SPRITE_INTERVAL)

            # 计算雪碧图中的坐标
            col = i % SPRITE_COLS
            row = i // SPRITE_COLS
            x = col * SPRITE_THUMB_WIDTH
            y = row * SPRITE_THUMB_HEIGHT

            lines.append(f"{_format_time(start_time)} --> {_format_time(end_time)}")
            lines.append(
                f"{sprite_filename}#xywh={x},{y},"
                f"{SPRITE_THUMB_WIDTH},{SPRITE_THUMB_HEIGHT}"
            )
            lines.append("")

        content = "\n".join(lines)

        #进入携程child线程写入文件，避免阻塞主线程 函数为同步函数
        def _write_vtt():
            with open(vtt_path, "w") as f:
                f.write(content)

        await asyncio.to_thread(_write_vtt)

        logger.info(
            f"[HLS-SPRITE] VTT 元数据已生成: {vtt_path}, "
            f"entries={total_thumbnails}"
        )