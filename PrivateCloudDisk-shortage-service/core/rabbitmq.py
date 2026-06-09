import json
import logging
import asyncio
import aio_pika
from aio_pika import Message, ExchangeType
from core.config import settings

logger = logging.getLogger("rabbitmq")

class RabbitMQService:
    """RabbitMQ消息队列服务"""
    
    def __init__(self):
        self.connection = None
        self.channel = None
        self.exchanges = {}
    
    async def connect(self):
        """建立RabbitMQ连接"""
        try:
            credentials = aio_pika.PlainCredentials(
                settings.rabbitmq_username,
                settings.rabbitmq_password
            )
            
            self.connection = await aio_pika.connect_robust(
                host=settings.rabbitmq_host,
                port=settings.rabbitmq_port,
                credentials=credentials,
                virtualhost=settings.rabbitmq_vhost,
                heartbeat=60
            )
            
            self.channel = await self.connection.channel()
            await self.channel.set_qos(prefetch_count=1)
            
            # 声明交换机
            await self._declare_exchanges()
            
            logger.info("RabbitMQ连接成功")
        except Exception as e:
            logger.error(f"RabbitMQ连接失败: {str(e)}")
            raise
    
    async def _declare_exchanges(self):
        """声明所有交换机"""
        # 文件处理交换机
        file_process_exchange = await self.channel.declare_exchange(
            settings.file_process_exchange,
            ExchangeType.DIRECT,
            durable=True
        )
        self.exchanges[settings.file_process_exchange] = file_process_exchange
        
        # 文件删除交换机
        file_delete_exchange = await self.channel.declare_exchange(
            settings.file_delete_exchange,
            ExchangeType.DIRECT,
            durable=True
        )
        self.exchanges[settings.file_delete_exchange] = file_delete_exchange
        
        # 声明队列并绑定
        # 文件处理队列
        file_process_queue = await self.channel.declare_queue(
            settings.file_process_queue,
            durable=True,
            arguments={"x-message-ttl": 604800000}  # 7天
        )
        await file_process_queue.bind(
            file_process_exchange,
            routing_key=settings.file_process_routing_key
        )
        
        # 文件删除队列
        file_delete_queue = await self.channel.declare_queue(
            settings.file_delete_queue,
            durable=True,
            arguments={"x-message-ttl": 259200000}  # 3天
        )
        await file_delete_queue.bind(
            file_delete_exchange,
            routing_key=settings.file_delete_routing_key
        )
    
    async def publish_message(self, exchange_name: str, routing_key: str, message: dict):
        """发布消息到指定队列"""
        if exchange_name not in self.exchanges:
            raise ValueError(f"Exchange {exchange_name} not found")
        
        exchange = self.exchanges[exchange_name]
        message_body = json.dumps(message).encode("utf-8")
        
        await exchange.publish(
            Message(
                body=message_body,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT
            ),
            routing_key=routing_key
        )
        
        logger.debug(f"消息已发布: exchange={exchange_name}, routing_key={routing_key}, message_id={message.get('message_id')}")
    
    async def consume(self, queue_name: str, callback):
        """消费指定队列的消息"""
        queue = await self.channel.declare_queue(queue_name, durable=True)
        await queue.consume(callback)
    
    async def close(self):
        """关闭连接"""
        if self.connection:
            await self.connection.close()
            logger.info("RabbitMQ连接已关闭")

# 全局RabbitMQ服务实例
rabbitmq_service = RabbitMQService()