import json
import logging
import asyncio
import aio_pika
from aio_pika import Message, ExchangeType
from core.config import settings
from app.core.logging_config import get_logger

logger = get_logger("core.rabbitmq")

class RabbitMQService:
    """RabbitMQ消息队列服务"""
    
    def __init__(self):
        self.connection = None
        self.channel = None
        self.exchanges = {}
    
    async def connect(self):
        """建立RabbitMQ连接"""
        try:
            logger.info(f"开始建立RabbitMQ连接 - host: {settings.rabbitmq_host}, port: {settings.rabbitmq_port}, vhost: {settings.rabbitmq_vhost}")
            
            # 使用URL格式连接
            connection_url = f"amqp://{settings.rabbitmq_username}:{settings.rabbitmq_password}@{settings.rabbitmq_host}:{settings.rabbitmq_port}/{settings.rabbitmq_vhost}"
            
            logger.debug(f"RabbitMQ连接URL: amqp://{settings.rabbitmq_username}:******@{settings.rabbitmq_host}:{settings.rabbitmq_port}/{settings.rabbitmq_vhost}")
            
            self.connection = await aio_pika.connect_robust(
                connection_url,
                heartbeat=60
            )
            logger.debug("RabbitMQ连接对象创建成功")
            
            self.channel = await self.connection.channel()
            logger.debug("RabbitMQ通道创建成功")
            
            await self.channel.set_qos(prefetch_count=1)
            logger.debug("RabbitMQ QoS设置完成 - prefetch_count: 1")
            
            # 声明交换机
            await self._declare_exchanges()
            logger.debug("RabbitMQ交换机和队列声明完成")
            
            logger.info("✅ RabbitMQ连接成功")
        except Exception as e:
            logger.error(f"❌ RabbitMQ连接失败: {str(e)}", exc_info=True)
            raise
    
    async def _declare_exchanges(self):
        """声明所有交换机"""
        logger.debug("开始声明RabbitMQ交换机和队列")
        
        # 文件处理交换机
        logger.debug(f"声明交换机: {settings.file_process_exchange}, type: DIRECT, durable: True")
        file_process_exchange = await self.channel.declare_exchange(
            settings.file_process_exchange,
            ExchangeType.DIRECT,
            durable=True
        )
        self.exchanges[settings.file_process_exchange] = file_process_exchange
        logger.debug(f"交换机 {settings.file_process_exchange} 声明成功")
        
        # 文件删除交换机
        logger.debug(f"声明交换机: {settings.file_delete_exchange}, type: DIRECT, durable: True")
        file_delete_exchange = await self.channel.declare_exchange(
            settings.file_delete_exchange,
            ExchangeType.DIRECT,
            durable=True
        )
        self.exchanges[settings.file_delete_exchange] = file_delete_exchange
        logger.debug(f"交换机 {settings.file_delete_exchange} 声明成功")
        
        # 声明队列并绑定
        # 文件处理队列
        logger.debug(f"声明队列: {settings.file_process_queue}, durable: True, TTL: 7天")
        file_process_queue = await self.channel.declare_queue(
            settings.file_process_queue,
            durable=True,
            arguments={"x-message-ttl": 604800000}  # 7天
        )
        await file_process_queue.bind(
            file_process_exchange,
            routing_key=settings.file_process_routing_key
        )
        logger.debug(f"队列 {settings.file_process_queue} 绑定到交换机 {settings.file_process_exchange}, routing_key: {settings.file_process_routing_key}")
        
        # 文件删除队列
        logger.debug(f"声明队列: {settings.file_delete_queue}, durable: True, TTL: 3天")
        file_delete_queue = await self.channel.declare_queue(
            settings.file_delete_queue,
            durable=True,
            arguments={"x-message-ttl": 259200000}  # 3天
        )
        await file_delete_queue.bind(
            file_delete_exchange,
            routing_key=settings.file_delete_routing_key
        )
        logger.debug(f"队列 {settings.file_delete_queue} 绑定到交换机 {settings.file_delete_exchange}, routing_key: {settings.file_delete_routing_key}")
        
        logger.info(f"✅ RabbitMQ交换机和队列声明完成 - 交换机: {list(self.exchanges.keys())}")
    
    async def publish_message(self, exchange_name: str, routing_key: str, message: dict):
        """发布消息到指定队列"""
        try:
            if exchange_name not in self.exchanges:
                logger.error(f"❌ 交换机 {exchange_name} 不存在")
                raise ValueError(f"Exchange {exchange_name} not found")
            
            exchange = self.exchanges[exchange_name]
            message_body = json.dumps(message).encode("utf-8")
            message_id = message.get('message_id', 'N/A')
            task_id = message.get('task_id', 'N/A')
            file_id = message.get('file_id', 'N/A')
            
            logger.debug(f"准备发布消息 - exchange: {exchange_name}, routing_key: {routing_key}, message_id: {message_id}, task_id: {task_id}, file_id: {file_id}")
            
            await exchange.publish(
                Message(
                    body=message_body,
                    content_type="application/json",
                    delivery_mode=aio_pika.DeliveryMode.PERSISTENT
                ),
                routing_key=routing_key
            )
            
            logger.info(f"📤 消息发布成功 - exchange: {exchange_name}, routing_key: {routing_key}, message_id: {message_id}, task_id: {task_id}")
        except Exception as e:
            logger.error(f"❌ 消息发布失败 - exchange: {exchange_name}, routing_key: {routing_key}, error: {str(e)}", exc_info=True)
            raise
    
    async def consume(self, queue_name: str, callback):
        """消费指定队列的消息"""
        try:
            logger.debug(f"准备启动消费者 - queue: {queue_name}")
            
            # 使用 passive=True 检查队列是否存在（队列已在 _declare_exchanges 中创建）
            queue = await self.channel.declare_queue(queue_name, durable=True, passive=True)
            await queue.consume(callback)
            
            logger.info(f"✅ 消费者启动成功 - queue: {queue_name}")
        except Exception as e:
            logger.error(f"❌ 消费者启动失败 - queue: {queue_name}, error: {str(e)}", exc_info=True)
            raise
    
    async def close(self):
        """关闭连接"""
        if self.connection:
            await self.connection.close()
            logger.info("RabbitMQ连接已关闭")

# 全局RabbitMQ服务实例
rabbitmq_service = RabbitMQService()