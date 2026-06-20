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

            # 5. 写入 manifest.json 元数据
            manifest_path = os.path.join(hls_dir, "manifest.json")
            manifest = {
                "file_id": file_id,
                "source_width": source_width,
                "source_height": source_height,
                "duration": source_duration,
                "resolutions": resolutions,
                "preview_path": preview_path,
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
            # 构建基础输入流
            stream = ffmpeg.input(input_path)

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
                        .output(stream, output_playlist, **output_kwargs)
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
            # 音频编码
            "c:a": "aac",
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
            "c:a": "aac",
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