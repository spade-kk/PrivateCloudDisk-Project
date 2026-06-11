import json
import logging
import asyncio
import hashlib
import os
import subprocess
from datetime import datetime
from typing import Dict, Any
from core.config import settings, TaskTypes, TaskStatus
from core.rabbitmq import rabbitmq_service
from server import redis_client

logger = logging.getLogger("file_process_consumer")

# 任务处理顺序定义
TASK_PIPELINE = [
    TaskTypes.MERGE,
    TaskTypes.HASH_CALCULATE,
    TaskTypes.VIRUS_SCAN,
    TaskTypes.THUMBNAIL,
    TaskTypes.VIDEO_TRANSCODE,
    TaskTypes.MARK_ACTIVE
]

# 文件类型映射
IMAGE_TYPES = {"image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"}
VIDEO_TYPES = {"video/mp4", "video/mpeg", "video/quicktime", "video/webm", "video/x-msvideo"}

class FileProcessConsumer:
    """文件处理消息消费者"""
    
    @staticmethod
    async def process_message(message: Any):
        """处理文件处理消息"""
        try:
            message_body = message.body.decode("utf-8")
            message_data = json.loads(message_body)
            
            logger.info(f"收到文件处理消息: message_id={message_data.get('message_id')}, task_type={message_data.get('task_type')}, file_id={message_data.get('file_id')}")
            
            # 获取消息属性
            task_type = message_data.get("task_type")
            uploads_id = message_data.get("uploads_id")
            user_id = message_data.get("user_id")
            file_name = message_data.get("file_name")
            file_type = message_data.get("file_type")
            file_size = message_data.get("file_size")
            storage_path = message_data.get("storage_path")
            node_id = message_data.get("node_id")
            total_chunks = message_data.get("total_chunks")
            file_checksum = message_data.get("file_checksum")
            retry_count = message_data.get("retry_count", 0)
            file_id = message_data.get("file_id", None)
            task_id = message_data.get("task_id")
            
            # 根据任务类型处理
            success = False
            result = {}
            
            try:
                match task_type:
                    case TaskTypes.MERGE:
                        result = await FileProcessConsumer._handle_merge(
                            uploads_id, user_id, total_chunks, file_name, file_checksum
                        )
                        success = result.get("success", False)
                        file_id = result.get("file_id")
                    
                    case TaskTypes.HASH_CALCULATE:
                        result = await FileProcessConsumer._handle_hash_calculate(
                            file_id, user_id, storage_path, file_checksum
                        )
                        success = result.get("success", False)
                    
                    case TaskTypes.VIRUS_SCAN:
                        result = await FileProcessConsumer._handle_virus_scan(
                            file_id, user_id, storage_path, file_name
                        )
                        success = result.get("success", False)
                    
                    case TaskTypes.THUMBNAIL:
                        result = await FileProcessConsumer._handle_thumbnail(
                            file_id, user_id, storage_path, file_name, file_type
                        )
                        success = result.get("success", False)
                    
                    case TaskTypes.VIDEO_TRANSCODE:
                        result = await FileProcessConsumer._handle_video_transcode(
                            file_id, user_id, storage_path, file_name, file_type
                        )
                        success = result.get("success", False)
                    
                    case TaskTypes.MARK_ACTIVE:
                        result = await FileProcessConsumer._handle_mark_active(
                            file_id, user_id, storage_path
                        )
                        success = result.get("success", False)
                    
                    case _:
                        logger.warn(f"未知的任务类型: {task_type}")
                
                if success:
                    # 确认消息
                    await message.ack()
                    logger.info(f"任务处理成功: message_id={message_data.get('message_id')}, task_type={task_type}")
                    
                    # 更新任务状态
                    await FileProcessConsumer._update_task_status(task_id, task_type, TaskStatus.COMPLETED, result)
                    
                    # 发送下一个任务
                    await FileProcessConsumer._send_next_task(
                        task_id, task_type, file_id, user_id, file_name, file_type, file_size, 
                        storage_path, node_id, result
                    )
                else:
                    # 重试或失败处理
                    await FileProcessConsumer._handle_failure(message, message_data, retry_count)
            
            except Exception as e:
                logger.error(f"任务执行异常: message_id={message_data.get('message_id')}, task_type={task_type}, error={str(e)}")
                await FileProcessConsumer._handle_failure(message, message_data, retry_count)
        
        except json.JSONDecodeError as e:
            logger.error(f"消息格式错误: {str(e)}")
            await message.ack()
        except Exception as e:
            logger.error(f"消息处理异常: {str(e)}")
            await message.ack()
    
    @staticmethod
    async def _handle_merge(uploads_id: str,  user_id: str, 
                           total_chunks: int, file_name: str, expected_checksum: str) -> Dict[str, Any]:
        """处理文件合并任务"""
        logger.info(f"开始合并文件: uploads_id={uploads_id}, file_name={file_name}")
        
        try:
            session_dir = f"{settings.file_upload_dir}/{uploads_id}"
            final_path = f"{settings.file_upload_dir}/storage/{uploads_id}-{total_chunks}.cloud"
            
            # 确保存储目录存在
            os.makedirs(os.path.dirname(final_path), exist_ok=True)
            
            # 创建最终文件并合并分片
            file_hash = hashlib.sha256()
            with open(final_path, "wb") as final_file:
                for i in range(1, total_chunks + 1):
                    chunk_path = f"{session_dir}-{i}.part"
                    if not os.path.exists(chunk_path):
                        return {"success": False, "error": f"分片文件不存在: {chunk_path}"}
                    
                    with open(chunk_path, "rb") as chunk_file:
                        while content := chunk_file.read(128 * 1024):  # 128KB块
                            final_file.write(content)
                            file_hash.update(content)
                    
                    # 删除分片文件
                    os.remove(chunk_path)
            
            # 验证完整文件校验码
            actual_checksum = file_hash.hexdigest()
            if expected_checksum and actual_checksum != expected_checksum:
                # 删除已合并的文件
                os.remove(final_path)
                return {"success": False, "error": f"文件校验失败，期望: {expected_checksum[:8]}..., 实际: {actual_checksum[:8]}..."}
            
            logger.info(f"文件合并完成: file_name={file_name}, checksum={actual_checksum}")
            
            import requests
            response = requests.post(
            f"{settings.business_service_url}/api/v1/business/internal/storage/files",
            params = {
                "uploads_id": uploads_id,
                "file_storage_path": final_path
                }
            )
            result = response.json()
            file_id = result["data"]

            return {
                "success": True,
                "file_id": file_id,
                "storage_path": final_path,
                "checksum": actual_checksum,
                "file_size": os.path.getsize(final_path)
            }
        
        except Exception as e:
            logger.error(f"文件合并失败: file_name={file_name}, error={str(e)}")
            return {"success": False, "error": str(e)}
    
    @staticmethod
    async def _handle_hash_calculate(file_id: str, user_id: str, storage_path: str, 
                                    expected_checksum: str) -> Dict[str, Any]:
        """处理哈希计算任务"""
        logger.info(f"开始计算文件哈希: file_id={file_id}")
        
        try:
            if not os.path.exists(storage_path):
                return {"success": False, "error": f"文件不存在: {storage_path}"}
            
            file_hash = hashlib.sha256()
            with open(storage_path, "rb") as f:
                while content := f.read(128 * 1024):
                    file_hash.update(content)
            
            actual_checksum = file_hash.hexdigest()
            
            # 如果提供了期望的校验和，进行比对
            if expected_checksum and actual_checksum != expected_checksum:
                return {"success": False, "error": f"哈希校验失败"}
            
            logger.info(f"哈希计算完成: file_id={file_id}, checksum={actual_checksum}")
            
            return {
                "success": True,
                "checksum": actual_checksum
            }
        
        except Exception as e:
            logger.error(f"哈希计算失败: file_id={file_id}, error={str(e)}")
            return {"success": False, "error": str(e)}
    
    # @staticmethod
    # async def _handle_virus_scan(file_id: str, user_id: str, storage_path: str, 
    #                             file_name: str) -> Dict[str, Any]:
    #     """处理病毒扫描任务"""
    #     logger.info(f"开始病毒扫描: file_id={file_id}, file_name={file_name}")
        
    #     try:
    #         if not os.path.exists(storage_path):
    #             return {"success": False, "error": f"文件不存在: {storage_path}"}
            
    #         # 使用ClamAV进行病毒扫描
    #         # 注意：需要安装clamav并配置
    #         try:
    #             result = subprocess.run(
    #                 ["clamscan", "--stdout", "--no-summary", storage_path],
    #                 capture_output=True,
    #                 text=True,
    #                 timeout=300  # 5分钟超时
    #             )
                
    #             if result.returncode == 0:
    #                 # 扫描通过，无病毒
    #                 logger.info(f"病毒扫描通过: file_id={file_id}")
    #                 return {"success": True, "infected": False}
    #             elif result.returncode == 1:
    #                 # 发现病毒
    #                 logger.warning(f"病毒扫描发现威胁: file_id={file_id}, result={result.stdout}")
    #                 return {"success": False, "error": "文件包含病毒", "infected": True}
    #             else:
    #                 # 扫描出错
    #                 logger.error(f"病毒扫描出错: file_id={file_id}, error={result.stderr}")
    #                 return {"success": False, "error": f"扫描出错: {result.stderr}"}
            
    #         except FileNotFoundError:
    #             # ClamAV未安装，跳过扫描
    #             logger.warning("ClamAV未安装，跳过病毒扫描")
    #             return {"success": True, "infected": False, "skipped": True}
    #         except subprocess.TimeoutExpired:
    #             logger.error(f"病毒扫描超时: file_id={file_id}")
    #             return {"success": False, "error": "扫描超时"}
        
    #     except Exception as e:
    #         logger.error(f"病毒扫描失败: file_id={file_id}, error={str(e)}")
    #         return {"success": False, "error": str(e)}
    
    @staticmethod
    async def _handle_virus_scan(file_id: str, user_id: str, storage_path: str,
                                file_name: str) -> Dict[str, Any]:
        import pyclamd
        logger.info(f"开始病毒扫描: file_id={file_id}, file_name={file_name}")

        try:
            if not os.path.exists(storage_path):
                return {"success": False, "error": f"文件不存在: {storage_path}"}

            #如果路径path为相对路径改为绝对路径 不管怎么样都要做一次转换保证业务进行
            storage_path = os.path.abspath(storage_path)

            # 尝试连接 clamd 服务
            cd = pyclamd.ClamdUnixSocket('/opt/homebrew/var/run/clamav/clamd.sock')  # 使用 Unix Socket
            # 或者使用 TCP： cd = pyclamd.ClamdNetworkSocket('127.0.0.1', 3310)

            if not cd.ping():
                logger.error("无法连接到 clamd 服务")
                return {"success": False, "error": "clamd 服务不可用"}

            # 扫描文件（返回结果可能是 None=无病毒, 或 {'文件路径': ('病毒名', '状态')}）
            scan_result = cd.scan_file(storage_path)

            if scan_result is None:
                logger.info(f"病毒扫描通过: file_id={file_id}")
                return {"success": True, "infected": False}
            else:
                virus_name = scan_result[storage_path][0]
                logger.warning(f"发现病毒: {virus_name} 在文件: {file_id}")
                return {"success": False, "error": f"文件包含病毒: {virus_name}", "infected": True}

        except pyclamd.ConnectionError:
            logger.warning("clamd 服务未运行，跳过病毒扫描")
            return {"success": True, "infected": False, "skipped": True}
        except Exception as e:
            logger.error(f"病毒扫描失败: file_id={file_id}, error={str(e)}")
            return {"success": False, "error": str(e)}

    @staticmethod
    async def _handle_thumbnail(file_id: str, user_id: str, storage_path: str, 
                               file_name: str, file_type: str) -> Dict[str, Any]:
        """处理缩略图生成任务"""
        logger.info(f"开始生成缩略图: file_id={file_id}, file_name={file_name}")
        
        try:
            import pyvips
            
            if not os.path.exists(storage_path):
                return {"success": False, "error": f"文件不存在: {storage_path}"}
            
            # 检查是否为图片类型
            if file_type not in IMAGE_TYPES:
                logger.info(f"非图片文件，跳过缩略图生成: file_id={file_id}, type={file_type}")
                return {"success": True, "skipped": True, "reason": "非图片文件"}
            
            # 生成多种尺寸的缩略图
            thumbnail_sizes = [
                (100, 100),   # 小缩略图
                (200, 200),   # 中缩略图
                (400, 400)    # 大缩略图
            ]
            
            thumbnail_paths = []
            
            for width, height in thumbnail_sizes:
                thumbnail_path = f"{settings.file_upload_dir}/thumbnails/{file_id}_{width}x{height}.jpg"
                os.makedirs(os.path.dirname(thumbnail_path), exist_ok=True)
                
                # 使用pyvips生成缩略图
                image = pyvips.Image.new_from_file(storage_path, access='sequential')
                scale = min(width / image.width, height / image.height)
                if scale < 1.0:
                    image = image.resize(scale, kernel='lanczos3')
                if image.interpretation != pyvips.Interpretation.SRGB:
                    image = image.colourspace(pyvips.Interpretation.SRGB)
                
                image.jpegsave(
                    thumbnail_path,
                    Q=85,
                    optimize_coding=True,
                    trellis_quant=True,
                    overshoot_deringing=True,
                    interlace=False
                )
                
                thumbnail_paths.append({
                    "size": f"{width}x{height}",
                    "path": thumbnail_path
                })
            
            logger.info(f"缩略图生成完成: file_id={file_id}, sizes={[t['size'] for t in thumbnail_paths]}")
            
            return {
                "success": True,
                "thumbnails": thumbnail_paths
            }
        
        except ImportError:
            logger.warning("pyvips未安装，跳过缩略图生成")
            return {"success": True, "skipped": True, "reason": "pyvips未安装"}
        except Exception as e:
            logger.error(f"缩略图生成失败: file_id={file_id}, error={str(e)}")
            return {"success": False, "error": str(e)}
    
    @staticmethod
    async def _handle_video_transcode(file_id: str, user_id: str, storage_path: str, 
                                     file_name: str, file_type: str) -> Dict[str, Any]:
        """处理视频转码任务"""
        logger.info(f"开始视频转码: file_id={file_id}, file_name={file_name}")
        
        try:
            if not os.path.exists(storage_path):
                return {"success": False, "error": f"文件不存在: {storage_path}"}
            
            # 检查是否为视频类型
            if file_type not in VIDEO_TYPES:
                logger.info(f"非视频文件，跳过视频转码: file_id={file_id}, type={file_type}")
                return {"success": True, "skipped": True, "reason": "非视频文件"}
            
            # 转码配置：不同分辨率
            resolutions = [
                {"width": 480, "height": 360, "bitrate": "500k"},    # SD
                {"width": 960, "height": 540, "bitrate": "1500k"},   # HD
                {"width": 1920, "height": 1080, "bitrate": "4000k"}  # Full HD
            ]
            
            transcoded_paths = []
            
            for res in resolutions:
                output_path = f"{settings.file_upload_dir}/transcoded/{file_id}_{res['width']}p.mp4"
                os.makedirs(os.path.dirname(output_path), exist_ok=True)
                
                # 使用ffmpeg转码
                cmd = [
                    "ffmpeg",
                    "-i", storage_path,
                    "-s", f"{res['width']}x{res['height']}",
                    "-b:v", res['bitrate'],
                    "-c:v", "libx264",
                    "-preset", "medium",
                    "-crf", "23",
                    "-c:a", "aac",
                    "-b:a", "128k",
                    "-y",  # 覆盖输出文件
                    output_path
                ]
                
                try:
                    result = subprocess.run(
                        cmd,
                        capture_output=True,
                        text=True,
                        timeout=600  # 10分钟超时
                    )
                    
                    if result.returncode == 0:
                        transcoded_paths.append({
                            "resolution": f"{res['width']}p",
                            "path": output_path,
                            "bitrate": res['bitrate']
                        })
                    else:
                        logger.warning(f"视频转码失败: file_id={file_id}, resolution={res['width']}p, error={result.stderr}")
                
                except subprocess.TimeoutExpired:
                    logger.warning(f"视频转码超时: file_id={file_id}, resolution={res['width']}p")
                except FileNotFoundError:
                    logger.warning("ffmpeg未安装，跳过视频转码")
                    return {"success": True, "skipped": True, "reason": "ffmpeg未安装"}
            
            # 生成视频预览图
            preview_path = f"{settings.file_upload_dir}/thumbnails/{file_id}_preview.jpg"
            os.makedirs(os.path.dirname(preview_path), exist_ok=True)
            
            try:
                subprocess.run(
                    ["ffmpeg", "-i", storage_path, "-ss", "00:00:01", "-vframes", "1", preview_path],
                    capture_output=True,
                    text=True,
                    timeout=60
                )
                
                if os.path.exists(preview_path):
                    transcoded_paths.append({
                        "type": "preview",
                        "path": preview_path
                    })
            except Exception as e:
                logger.warning(f"生成预览图失败: {str(e)}")
            
            logger.info(f"视频转码完成: file_id={file_id}, outputs={len(transcoded_paths)}")
            
            return {
                "success": True,
                "transcoded_files": transcoded_paths
            }
        
        except Exception as e:
            logger.error(f"视频转码失败: file_id={file_id}, error={str(e)}")
            return {"success": False, "error": str(e)}
    
    @staticmethod
    async def _handle_mark_active(file_id: str, user_id: str, storage_path: str) -> Dict[str, Any]:
        """处理标记文件为活跃状态任务"""
        logger.info(f"标记文件为活跃状态: file_id={file_id}")
        
        try:
            # 通知业务服务文件已处理完成，可以标记为活跃状态
            import requests
            
            response = requests.post(
                f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/activate",
                params={"user_id": user_id}
            )
            
            if response.status_code == 200:
                logger.info(f"文件已标记为活跃: file_id={file_id}")
                return {"success": True}
            else:
                logger.error(f"标记活跃失败: file_id={file_id}, status={response.status_code}")
                return {"success": False, "error": "通知业务服务失败"}
        
        except Exception as e:
            logger.error(f"标记活跃失败: file_id={file_id}, error={str(e)}")
            return {"success": False, "error": str(e)}
    
    @staticmethod
    async def _update_task_status(task_id: str, task_type: str, status: str, result: Dict[str, Any]):
        """更新任务状态到Redis"""
        try:
            task_key = f"task:{task_id}:{task_type}"
            await redis_client.hset(task_key, mapping={
                "status": status,
                "updated_at": datetime.now().isoformat() # **result
            })
            await redis_client.expire(task_key, 86400 * 7)  # 7天过期
        except Exception as e:
            logger.error(f"更新任务状态失败: {str(e)}")
    
    @staticmethod
    async def _get_next_task(current_task: str, file_type: str) -> str:
        """获取下一个任务类型"""
        try:
            current_index = TASK_PIPELINE.index(current_task)
            
            # 如果是最后一个任务，返回None
            if current_index >= len(TASK_PIPELINE) - 1:
                return None
            
            # 获取下一个任务
            next_task = TASK_PIPELINE[current_index + 1]
            
            # 根据文件类型决定是否跳过某些任务
            if next_task == TaskTypes.THUMBNAIL and file_type not in IMAGE_TYPES:
                return await FileProcessConsumer._get_next_task(next_task, file_type)
            
            if next_task == TaskTypes.VIDEO_TRANSCODE and file_type not in VIDEO_TYPES:
                return await FileProcessConsumer._get_next_task(next_task, file_type)
            
            return next_task
        
        except ValueError:
            return None
    
    @staticmethod
    async def _send_next_task(task_id:str, task_type, file_id: str, user_id: str, file_name: str, 
                             file_type: str, file_size: int, storage_path: str, 
                             node_id: str, previous_result: Dict[str, Any]):
        """发送下一个任务消息"""
        next_task = await FileProcessConsumer._get_next_task(
            task_type or previous_result.get("task_type") or TaskTypes.MERGE, file_type
        )
        
        if next_task:
            # 使用previous_result中的storage_path（合并任务会返回新的路径）
            actual_storage_path = previous_result.get("storage_path") or storage_path
            
            message = {
                "message_id": str(os.urandom(16).hex()),
                "task_id": task_id,
                "task_type": next_task,
                "file_id": file_id,
                "user_id": user_id,
                "file_name": file_name,
                "file_type": file_type,
                "file_size": file_size,
                "storage_path": actual_storage_path,
                "node_id": node_id,
                "checksum": previous_result.get("checksum"),
                "retry_count": 0,
                "created_at": datetime.now().isoformat()
            }
            
            await rabbitmq_service.publish_message(
                settings.file_process_exchange,
                settings.file_process_routing_key,
                message
            )
            
            logger.info(f"发送下一个任务: file_id={file_id}, next_task={next_task}")
        else:
            logger.info(f"所有任务已完成: file_id={file_id}")
    
    @staticmethod
    async def _handle_failure(message: Any, message_data: dict, retry_count: int):
        """处理任务失败"""
        max_retries = 3
        
        if retry_count < max_retries:
            # 重试
            message_data["retry_count"] = retry_count + 1
            message_data["created_at"] = datetime.now().isoformat()
            
            await rabbitmq_service.publish_message(
                settings.file_process_exchange,
                settings.file_process_routing_key,
                message_data
            )
            
            await message.ack()
            logger.info(f"任务重试: message_id={message_data.get('message_id')}, retry_count={retry_count + 1}")
        else:
            # 超过重试次数，标记失败
            await message.ack()
            logger.error(f"任务失败，已达最大重试次数: message_id={message_data.get('message_id')}, task_type={message_data.get('task_type')}")
            
            # 更新任务状态为失败
            await FileProcessConsumer._update_task_status(
                message_data.get("task_id"),
                message_data.get("file_id"),
                message_data.get("task_type"),
                TaskStatus.FAILED,
                {"error": "已达最大重试次数"}
            )