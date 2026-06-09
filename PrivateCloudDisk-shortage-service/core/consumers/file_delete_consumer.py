import json
import logging
import os
from datetime import datetime
from typing import Dict, Any
from core.config import settings
from core.rabbitmq import rabbitmq_service

logger = logging.getLogger("file_delete_consumer")

class FileDeleteConsumer:
    """文件删除消息消费者"""
    
    @staticmethod
    async def process_message(message: Any):
        """处理文件删除消息"""
        try:
            message_body = message.body.decode("utf-8")
            message_data = json.loads(message_body)
            
            logger.info(f"收到文件删除消息: message_id={message_data.get('message_id')}, file_id={message_data.get('file_id')}")
            
            file_id = message_data.get("file_id")
            storage_path = message_data.get("storage_path")
            thumbnail_paths = message_data.get("thumbnail_paths", [])
            transcoded_paths = message_data.get("transcoded_paths", [])
            retry_count = message_data.get("retry_count", 0)
            
            try:
                success = await FileDeleteConsumer._handle_delete(
                    file_id, storage_path, thumbnail_paths, transcoded_paths
                )
                
                if success:
                    await message.ack()
                    logger.info(f"文件删除成功: file_id={file_id}")
                else:
                    await FileDeleteConsumer._handle_failure(message, message_data, retry_count)
            
            except Exception as e:
                logger.error(f"文件删除异常: file_id={file_id}, error={str(e)}")
                await FileDeleteConsumer._handle_failure(message, message_data, retry_count)
        
        except json.JSONDecodeError as e:
            logger.error(f"消息格式错误: {str(e)}")
            await message.ack()
        except Exception as e:
            logger.error(f"消息处理异常: {str(e)}")
            await message.ack()
    
    @staticmethod
    async def _handle_delete(file_id: str, storage_path: str, 
                           thumbnail_paths: list, transcoded_paths: list) -> bool:
        """处理文件删除"""
        logger.info(f"开始删除文件: file_id={file_id}")
        
        deleted_files = []
        errors = []
        
        try:
            # 删除主文件
            if storage_path and os.path.exists(storage_path):
                os.remove(storage_path)
                deleted_files.append(storage_path)
                logger.debug(f"删除主文件: {storage_path}")
            
            # 删除缩略图
            for thumb_path in thumbnail_paths:
                path = thumb_path if isinstance(thumb_path, str) else thumb_path.get("path")
                if path and os.path.exists(path):
                    os.remove(path)
                    deleted_files.append(path)
                    logger.debug(f"删除缩略图: {path}")
            
            # 删除转码文件
            for trans_path in transcoded_paths:
                path = trans_path if isinstance(trans_path, str) else trans_path.get("path")
                if path and os.path.exists(path):
                    os.remove(path)
                    deleted_files.append(path)
                    logger.debug(f"删除转码文件: {path}")
            
            # 通知业务服务删除完成
            await FileDeleteConsumer._notify_business_service(file_id, deleted_files)
            
            logger.info(f"文件删除完成: file_id={file_id}, deleted={len(deleted_files)}个文件")
            return True
        
        except Exception as e:
            logger.error(f"文件删除失败: file_id={file_id}, error={str(e)}")
            return False
    
    @staticmethod
    async def _notify_business_service(file_id: str, deleted_files: list):
        """通知业务服务删除完成"""
        try:
            import requests
            
            response = requests.post(
                f"{settings.business_service_url}/api/v1/business/internal/storage/files/{file_id}/delete-complete",
                json={"deleted_files": deleted_files}
            )
            
            if response.status_code != 200:
                logger.warning(f"通知业务服务失败: file_id={file_id}, status={response.status_code}")
        
        except Exception as e:
            logger.error(f"通知业务服务异常: file_id={file_id}, error={str(e)}")
    
    @staticmethod
    async def _handle_failure(message: Any, message_data: dict, retry_count: int):
        """处理删除失败"""
        max_retries = 3
        
        if retry_count < max_retries:
            # 重试
            message_data["retry_count"] = retry_count + 1
            message_data["created_at"] = datetime.now().isoformat()
            
            await rabbitmq_service.publish_message(
                settings.file_delete_exchange,
                settings.file_delete_routing_key,
                message_data
            )
            
            await message.ack()
            logger.info(f"删除重试: message_id={message_data.get('message_id')}, retry_count={retry_count + 1}")
        else:
            # 超过重试次数
            await message.ack()
            logger.error(f"删除失败，已达最大重试次数: message_id={message_data.get('message_id')}, file_id={message_data.get('file_id')}")