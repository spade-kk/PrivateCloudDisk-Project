"""
AI Processing Service - AI 处理流水线

编排所有 AI 任务的执行顺序，支持:
- 按文件类型自动选择 AI 任务
- 并行执行独立任务
- 串行执行依赖任务
- 任务结果汇总与持久化
- 失败重试与降级
- 处理超时控制

任务执行顺序:
┌─────────────────────────────────────────────────────────────┐
│                     AI Pipeline                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  图片类文件 (image/*):                                       │
│    ┌─────────────────────────────────────────────────┐      │
│    │ Stage 1 (并行):                                   │      │
│    │   ├── NSFW 检测 (最高优先级，内容审核)              │      │
│    │   ├── 图片分类                                     │      │
│    │   ├── 物体检测                                     │      │
│    │   └── 人脸检测                                     │      │
│    │                                                    │      │
│    │ Stage 2 (串行，依赖 Stage 1):                       │      │
│    │   └── OCR 文字识别 (如果文字区域占比 > 阈值)        │      │
│    └─────────────────────────────────────────────────┘      │
│                                                             │
│  文档类文件 (text/*, application/pdf, application/msword):   │
│    ┌─────────────────────────────────────────────────┐      │
│    │ Stage 1 (并行):                                   │      │
│    │   ├── NLP 标签提取                                 │      │
│    │   └── AI 摘要生成                                  │      │
│    │                                                    │      │
│    │ Stage 2 (串行):                                    │      │
│    │   └── OCR 文字识别 (PDF, 扫描件)                   │      │
│    └─────────────────────────────────────────────────┘      │
│                                                             │
│  视频类文件 (video/*):                                       │
│    └── 跳过 AI 处理 (由 storage-service 的转码流水线处理)    │
│                                                             │
│  其他类型:                                                   │
│    └── 跳过，仅记录日志                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
"""
from __future__ import annotations
import asyncio
import logging
import time
from typing import Optional

from app.core.config import (
    settings, AITaskType, AITaskStatus, FailureReason,
    IMAGE_MIME_TYPES, DOCUMENT_MIME_TYPES, VIDEO_MIME_TYPES,
)
from app.core.database.repository import (
    AITagRepository, OCRResultRepository, SummaryRepository, TaskLogRepository,
)
from app.core.events.ai_process_event import AIProcessEvent, AIProcessResult

logger = logging.getLogger("ai_service.pipeline")


class AIPipeline:
    """
    AI 处理流水线

    职责:
    - 根据文件类型决定执行哪些 AI 任务
    - 管理任务执行顺序和依赖关系
    - 汇总处理结果写入数据库
    - 处理错误和降级
    """

    # 任务超时 (秒)
    TASK_TIMEOUT = {
        AITaskType.NSFW_DETECTION: 30,
        AITaskType.IMAGE_CLASSIFICATION: 30,
        AITaskType.OBJECT_DETECTION: 60,
        AITaskType.FACE_DETECTION: 60,
        AITaskType.OCR: 120,
        AITaskType.NLP_TAGGING: 60,
        AITaskType.SUMMARIZATION: 120,
    }

    def __init__(self):
        self._tag_repo = AITagRepository()
        self._ocr_repo = OCRResultRepository()
        self._summary_repo = SummaryRepository()
        self._task_log = TaskLogRepository()

    async def process(self, event: AIProcessEvent) -> None:
        """
        处理 AI 事件

        Args:
            event: AI 处理事件
        """
        t_start = time.monotonic()
        file_type = event.file_type or ""
        logger.info(
            f"AI Pipeline 开始: file_id={event.file_id}, "
            f"file_type={file_type}, "
            f"file_name={event.file_name}"
        )

        try:
            # 1. 确定执行的 AI 任务列表
            tasks_to_run = self._determine_tasks(event)

            if not tasks_to_run:
                logger.info(f"无需 AI 处理: file_id={event.file_id}, file_type={file_type}")
                await self._task_log.insert_task_log(
                    task_id=event.message_id,
                    file_id=event.file_id,
                    user_id=event.user_id,
                    task_type="all",
                    status=AITaskStatus.SKIPPED,
                )
                return

            # 2. 执行 Stage 1 (并行任务)
            stage1_results = await self._execute_stage1(event, tasks_to_run)

            # 3. 执行 Stage 2 (串行任务，依赖 Stage 1 结果)
            stage2_results = await self._execute_stage2(event, tasks_to_run, stage1_results)

            # 4. 汇总所有结果
            all_results = stage1_results + stage2_results

            # 5. 持久化结果到数据库
            await self._persist_results(event, all_results)

            total_elapsed = (time.monotonic() - t_start) * 1000
            succeeded = sum(1 for r in all_results if r.success)
            skipped = sum(1 for r in all_results if r.skipped)
            failed = sum(1 for r in all_results if not r.success and not r.skipped)

            logger.info(
                f"AI Pipeline 完成: file_id={event.file_id}, "
                f"tasks={len(all_results)}, "
                f"succeeded={succeeded}, skipped={skipped}, failed={failed}, "
                f"elapsed={total_elapsed:.0f}ms"
            )

            # 6. 记录任务日志
            overall_status = AITaskStatus.COMPLETED
            if failed > 0 and succeeded > 0:
                overall_status = AITaskStatus.DEGRADED
            elif failed > 0 and succeeded == 0:
                overall_status = AITaskStatus.FAILED
            elif skipped == len(all_results):
                overall_status = AITaskStatus.SKIPPED

            await self._task_log.insert_task_log(
                task_id=event.message_id,
                file_id=event.file_id,
                user_id=event.user_id,
                task_type="all",
                status=overall_status,
                processing_time_ms=int(total_elapsed),
                retry_count=event.retry_count,
            )

        except Exception as e:
            total_elapsed = (time.monotonic() - t_start) * 1000
            logger.error(
                f"AI Pipeline 异常: file_id={event.file_id}, error={e}",
                exc_info=True,
            )
            await self._task_log.insert_task_log(
                task_id=event.message_id,
                file_id=event.file_id,
                user_id=event.user_id,
                task_type="all",
                status=AITaskStatus.FAILED,
                error_message=str(e),
                processing_time_ms=int(total_elapsed),
                retry_count=event.retry_count,
            )

    def _determine_tasks(self, event: AIProcessEvent) -> list[str]:
        """
        根据文件类型和配置决定执行的 AI 任务

        如果 event.enabled_tasks 不为空，使用指定的任务列表。
        否则根据文件 MIME 类型自动选择。
        """
        if event.enabled_tasks:
            return event.enabled_tasks

        file_type = event.file_type or ""

        # 图片类
        if file_type in IMAGE_MIME_TYPES:
            tasks = []
            if settings.ai_nsfw_detection_enabled:
                tasks.append(AITaskType.NSFW_DETECTION)
            if settings.ai_image_classification_enabled:
                tasks.append(AITaskType.IMAGE_CLASSIFICATION)
            if settings.ai_object_detection_enabled:
                tasks.append(AITaskType.OBJECT_DETECTION)
            if settings.ai_face_detection_enabled:
                tasks.append(AITaskType.FACE_DETECTION)
            if settings.ai_ocr_enabled:
                tasks.append(AITaskType.OCR)
            return tasks

        # 文档类
        if file_type in DOCUMENT_MIME_TYPES or file_type.startswith("text/"):
            tasks = []
            if settings.ai_nlp_tagging_enabled:
                tasks.append(AITaskType.NLP_TAGGING)
            if settings.ai_summarization_enabled:
                tasks.append(AITaskType.SUMMARIZATION)
            if settings.ai_ocr_enabled and file_type == "application/pdf":
                tasks.append(AITaskType.OCR)
            return tasks

        # 视频类 - 跳过 (由 storage-service 转码流水线处理)
        if file_type in VIDEO_MIME_TYPES:
            return []

        # 未识别类型 - 尝试通用处理
        logger.info(f"未识别的文件类型: {file_type}, 尝试通用处理")
        return []

    async def _execute_stage1(
        self, event: AIProcessEvent, tasks: list[str],
    ) -> list[AIProcessResult]:
        """
        执行 Stage 1: 并行独立任务

        Stage 1 任务互不依赖，可以并行执行:
        - NSFW 检测
        - 图片分类
        - 物体检测
        - 人脸检测
        - NLP 标签提取
        - AI 摘要生成
        """
        stage1_tasks = [
            t for t in tasks
            if t in (
                AITaskType.NSFW_DETECTION,
                AITaskType.IMAGE_CLASSIFICATION,
                AITaskType.OBJECT_DETECTION,
                AITaskType.FACE_DETECTION,
                AITaskType.NLP_TAGGING,
                AITaskType.SUMMARIZATION,
            )
        ]

        if not stage1_tasks:
            return []

        # 并行执行
        coroutines = [
            self._execute_single_task(event, task_type)
            for task_type in stage1_tasks
        ]

        results = await asyncio.gather(*coroutines, return_exceptions=True)

        # 处理异常
        processed_results = []
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                logger.error(
                    f"Stage 1 任务异常: task={stage1_tasks[i]}, "
                    f"error={result}"
                )
                processed_results.append(AIProcessResult(
                    file_id=event.file_id,
                    task_type=stage1_tasks[i],
                    success=False,
                    failure_reason=FailureReason.INFERENCE_ERROR,
                    error=str(result),
                ))
            else:
                processed_results.append(result)

        return processed_results

    async def _execute_stage2(
        self,
        event: AIProcessEvent,
        tasks: list[str],
        stage1_results: list[AIProcessResult],
    ) -> list[AIProcessResult]:
        """
        执行 Stage 2: 串行依赖任务

        OCR 可能需要依赖 Stage 1 的结果 (如: 如果 NSFW 检测到敏感内容，跳过 OCR)
        """
        stage2_tasks = [t for t in tasks if t == AITaskType.OCR]

        if not stage2_tasks:
            return []

        results = []
        for task_type in stage2_tasks:
            # 检查是否应该跳过
            if self._should_skip_task(task_type, stage1_results):
                results.append(AIProcessResult(
                    file_id=event.file_id,
                    task_type=task_type,
                    success=True,
                    skipped=True,
                    skipped_reason="Stage 1 结果要求跳过此任务",
                ))
                continue

            result = await self._execute_single_task(event, task_type)
            results.append(result)

        return results

    def _should_skip_task(
        self,
        task_type: str,
        stage1_results: list[AIProcessResult],
    ) -> bool:
        """检查是否应根据 Stage 1 结果跳过 Stage 2 任务"""
        # 如果 NSFW 检测到敏感内容，跳过 OCR (不处理敏感内容)
        if task_type == AITaskType.OCR:
            for r in stage1_results:
                if r.task_type == AITaskType.NSFW_DETECTION:
                    if r.success and r.data.get("is_sensitive"):
                        logger.info(
                            f"NSFW 检测到敏感内容，跳过 OCR: "
                            f"file_id={r.file_id}"
                        )
                        return True
        return False

    async def _execute_single_task(
        self,
        event: AIProcessEvent,
        task_type: str,
    ) -> AIProcessResult:
        """
        执行单个 AI 任务

        包含超时控制和错误处理。
        """
        timeout = self.TASK_TIMEOUT.get(task_type, 60)

        try:
            result = await asyncio.wait_for(
                self._run_task(event, task_type),
                timeout=timeout,
            )

            # 记录每个任务的日志
            task_status = AITaskStatus.COMPLETED
            if result.skipped:
                task_status = AITaskStatus.SKIPPED
            elif not result.success:
                task_status = AITaskStatus.FAILED

            await self._task_log.insert_task_log(
                task_id=event.message_id,
                file_id=event.file_id,
                user_id=event.user_id,
                task_type=task_type,
                status=task_status,
                error_message=result.error if not result.success else "",
                processing_time_ms=int(result.processing_time_ms),
                retry_count=event.retry_count,
            )

            return result

        except asyncio.TimeoutError:
            logger.error(f"AI 任务超时: task={task_type}, file_id={event.file_id}")
            return AIProcessResult(
                file_id=event.file_id,
                task_type=task_type,
                success=False,
                failure_reason=FailureReason.INFERENCE_TIMEOUT,
                error=f"任务超时 ({timeout}s)",
            )
        except Exception as e:
            logger.error(
                f"AI 任务异常: task={task_type}, file_id={event.file_id}, "
                f"error={e}",
                exc_info=True,
            )
            return AIProcessResult(
                file_id=event.file_id,
                task_type=task_type,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
            )

    async def _run_task(
        self,
        event: AIProcessEvent,
        task_type: str,
    ) -> AIProcessResult:
        """
        执行具体的 AI 任务

        懒加载模型实例，避免启动时加载所有模型。
        """
        if task_type == AITaskType.IMAGE_CLASSIFICATION:
            from app.core.models.image_classifier import image_classifier
            return await image_classifier.classify(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        elif task_type == AITaskType.FACE_DETECTION:
            from app.core.models.face_detector import face_detector
            return await face_detector.detect(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        elif task_type == AITaskType.OBJECT_DETECTION:
            from app.core.models.object_detector import object_detector
            return await object_detector.detect(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        elif task_type == AITaskType.NSFW_DETECTION:
            from app.core.models.nsfw_detector import nsfw_detector
            return await nsfw_detector.detect(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        elif task_type == AITaskType.NLP_TAGGING:
            from app.core.models.nlp_tagger import nlp_tagger
            return await nlp_tagger.tag(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        elif task_type == AITaskType.OCR:
            from app.core.models.ocr_engine import ocr_engine
            return await ocr_engine.recognize(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        elif task_type == AITaskType.SUMMARIZATION:
            from app.core.models.summarizer import ai_summarizer
            return await ai_summarizer.summarize(
                event.file_id, event.user_id,
                event.storage_path, event.file_name,
            )

        else:
            return AIProcessResult(
                file_id=event.file_id,
                task_type=task_type,
                success=False,
                skipped=True,
                skipped_reason=f"不支持的任务类型: {task_type}",
            )

    async def _persist_results(
        self,
        event: AIProcessEvent,
        results: list[AIProcessResult],
    ) -> None:
        """持久化 AI 处理结果到数据库"""
        for result in results:
            if not result.success or result.skipped:
                continue

            try:
                await self._persist_single_result(event, result)
            except Exception as e:
                logger.error(
                    f"持久化 AI 结果失败: task_type={result.task_type}, "
                    f"file_id={event.file_id}, error={e}",
                    exc_info=True,
                )

    async def _persist_single_result(
        self,
        event: AIProcessEvent,
        result: AIProcessResult,
    ) -> None:
        """持久化单个 AI 任务的结果"""
        data = result.data

        if result.task_type == AITaskType.IMAGE_CLASSIFICATION:
            if data.get("classifications"):
                tags = []
                for cls in data["classifications"]:
                    tags.append({
                        "name": cls["name"],
                        "label_zh": cls.get("label_zh", cls["name"]),
                        "confidence": cls["confidence"],
                        "model_name": data.get("model_name", ""),
                        "model_version": data.get("model_version", ""),
                        "processing_time_ms": data.get("processing_time_ms", 0),
                    })
                await self._tag_repo.insert_tags(
                    event.file_id, event.user_id,
                    AITaskType.IMAGE_CLASSIFICATION, tags,
                )

        elif result.task_type == AITaskType.OBJECT_DETECTION:
            if data.get("detections"):
                tags = []
                for det in data["detections"]:
                    tags.append({
                        "name": det["name"],
                        "label_zh": det.get("label_zh", det["name"]),
                        "confidence": det["confidence"],
                        "bbox": det.get("bbox"),
                        "model_name": data.get("model_name", ""),
                        "model_version": data.get("model_version", ""),
                        "processing_time_ms": data.get("processing_time_ms", 0),
                    })
                await self._tag_repo.insert_tags(
                    event.file_id, event.user_id,
                    AITaskType.OBJECT_DETECTION, tags,
                )

        elif result.task_type == AITaskType.FACE_DETECTION:
            if data.get("faces"):
                tags = []
                for face in data["faces"]:
                    tags.append({
                        "name": "face",
                        "label_zh": "人脸",
                        "confidence": face.get("confidence", 1.0),
                        "bbox": face.get("bbox"),
                        "metadata": {
                            "face_index": face.get("face_index"),
                            "landmarks": face.get("landmarks"),
                        },
                        "model_name": data.get("model_name", ""),
                        "model_version": data.get("model_version", ""),
                        "processing_time_ms": data.get("processing_time_ms", 0),
                    })
                await self._tag_repo.insert_tags(
                    event.file_id, event.user_id,
                    AITaskType.FACE_DETECTION, tags,
                )

        elif result.task_type == AITaskType.NSFW_DETECTION:
            if data.get("classifications"):
                tags = []
                for cls in data["classifications"]:
                    tags.append({
                        "name": cls["name"],
                        "label_zh": cls.get("label_zh", cls["name"]),
                        "confidence": cls["confidence"],
                        "metadata": {
                            "is_sensitive": data.get("is_sensitive", False),
                            "sensitive_categories": data.get("sensitive_categories", []),
                        },
                        "model_name": data.get("model_name", ""),
                        "model_version": data.get("model_version", ""),
                        "processing_time_ms": data.get("processing_time_ms", 0),
                    })
                await self._tag_repo.insert_tags(
                    event.file_id, event.user_id,
                    AITaskType.NSFW_DETECTION, tags,
                )

        elif result.task_type == AITaskType.NLP_TAGGING:
            if data.get("keywords"):
                tags = []
                for kw in data["keywords"]:
                    if isinstance(kw, dict):
                        name = kw.get("name", kw.get("text", ""))
                        conf = kw.get("confidence", kw.get("score", 0.0))
                    else:
                        name = str(kw)
                        conf = 0.5
                    tags.append({
                        "name": name,
                        "label_zh": name,
                        "confidence": conf,
                        "model_name": data.get("model_name", "tfidf_bert"),
                        "model_version": data.get("model_version", ""),
                        "processing_time_ms": data.get("processing_time_ms", 0),
                    })

                # 添加文档分类标签
                category = data.get("category", "")
                if category:
                    tags.append({
                        "name": category,
                        "label_zh": data.get("category_label_zh", category),
                        "confidence": data.get("category_score", 0.5),
                        "model_name": "bert-base-chinese",
                        "model_version": data.get("model_version", ""),
                        "processing_time_ms": data.get("processing_time_ms", 0),
                    })

                await self._tag_repo.insert_tags(
                    event.file_id, event.user_id,
                    AITaskType.NLP_TAGGING, tags,
                )

        elif result.task_type == AITaskType.OCR:
            if data.get("full_text"):
                await self._ocr_repo.upsert_ocr_result(
                    file_id=event.file_id,
                    user_id=event.user_id,
                    ocr_text=data["full_text"],
                    language=data.get("language", "unknown"),
                    confidence=data.get("pages", [{}])[0].get("confidence", 0.0)
                    if data.get("pages") else 0.0,
                    pages=data.get("total_pages", 1),
                    engine=data.get("engine", "paddleocr"),
                    model_version=data.get("model_version", ""),
                    processing_time_ms=int(data.get("processing_time_ms", 0)),
                )

        elif result.task_type == AITaskType.SUMMARIZATION:
            if data.get("summary"):
                await self._summary_repo.upsert_summary(
                    file_id=event.file_id,
                    user_id=event.user_id,
                    summary=data["summary"],
                    summary_en=data.get("summary_en", ""),
                    keywords=data.get("keywords", []),
                    category=data.get("category", ""),
                    reading_time_min=data.get("reading_time_min", 0),
                    model_name=data.get("model_name", ""),
                    processing_time_ms=int(data.get("processing_time_ms", 0)),
                )


# =============================================================================
# 全局单例
# =============================================================================
ai_pipeline = AIPipeline()