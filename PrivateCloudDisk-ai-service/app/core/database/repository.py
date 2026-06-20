"""
AI Processing Service - 数据仓库层 (Repository)

提供 AI 处理结果的 CRUD 操作。
使用原生 SQL + 参数化查询，避免 ORM 开销。
"""
from __future__ import annotations
import json
import logging
from datetime import datetime, timezone
from typing import Any, Optional

from app.core.database.connection import db_manager

logger = logging.getLogger("ai_service.repository")


class AITagRepository:
    """AI 标签仓库"""

    @staticmethod
    async def insert_tags(
        file_id: str,
        user_id: str,
        tag_type: str,
        tags: list[dict[str, Any]],
        tenant_id: str = "",
    ) -> None:
        """批量插入 AI 标签"""
        if not tags:
            return

        sql = """
            INSERT INTO pcd_ai_tags
                (file_id, user_id, tenant_id, tag_type, tag_name, tag_label_zh,
                 confidence, bounding_box, metadata, model_name, model_version,
                 processing_time_ms)
            VALUES
                (:file_id, :user_id, :tenant_id, :tag_type, :tag_name, :tag_label_zh,
                 :confidence, :bounding_box, :metadata, :model_name, :model_version,
                 :processing_time_ms)
        """

        params_list = []
        for tag in tags:
            params_list.append({
                "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
                "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
                "tenant_id": tenant_id,
                "tag_type": tag_type,
                "tag_name": tag.get("name", ""),
                "tag_label_zh": tag.get("label_zh", tag.get("name", "")),
                "confidence": tag.get("confidence", 0.0),
                "bounding_box": json.dumps(tag.get("bbox")) if tag.get("bbox") else None,
                "metadata": json.dumps(tag.get("metadata")) if tag.get("metadata") else None,
                "model_name": tag.get("model_name", ""),
                "model_version": tag.get("model_version", ""),
                "processing_time_ms": tag.get("processing_time_ms", 0),
            })

        await db_manager.execute_many(sql, params_list)

    @staticmethod
    async def insert_tag(
        file_id: str,
        user_id: str,
        tag_type: str,
        tag_name: str,
        tag_label_zh: str = "",
        confidence: float = 0.0,
        metadata: dict | None = None,
        tenant_id: str = "",
    ) -> None:
        """插入单条 AI 标签"""
        sql = """
            INSERT INTO pcd_ai_tags
                (file_id, user_id, tenant_id, tag_type, tag_name, tag_label_zh,
                 confidence, metadata)
            VALUES
                (:file_id, :user_id, :tenant_id, :tag_type, :tag_name, :tag_label_zh,
                 :confidence, :metadata)
        """
        await db_manager.execute(sql, {
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "tenant_id": tenant_id,
            "tag_type": tag_type,
            "tag_name": tag_name,
            "tag_label_zh": tag_label_zh,
            "confidence": confidence,
            "metadata": json.dumps(metadata) if metadata else None,
        })

    @staticmethod
    async def delete_tags_by_file(file_id: str) -> None:
        """删除文件的所有 AI 标签 (重新处理前清理)"""
        sql = "DELETE FROM pcd_ai_tags WHERE file_id = :file_id"
        await db_manager.execute(sql, {
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
        })

    @staticmethod
    async def get_tags_by_file(file_id: str) -> list[dict]:
        """获取文件的所有 AI 标签"""
        sql = """
            SELECT tag_type, tag_name, tag_label_zh, confidence, bounding_box, metadata
            FROM pcd_ai_tags
            WHERE file_id = :file_id
            ORDER BY tag_type, confidence DESC
        """
        return await db_manager.fetch_all(sql, {
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
        })


class FaceClusterRepository:
    """人脸聚类仓库"""

    @staticmethod
    async def upsert_cluster(
        cluster_id: str,
        user_id: str,
        face_count: int = 0,
        file_count: int = 0,
        representative_file_id: str = "",
        representative_encoding: bytes = b"",
        tenant_id: str = "",
    ) -> None:
        """插入或更新聚类"""
        sql = """
            INSERT INTO pcd_ai_face_clusters
                (cluster_id, user_id, tenant_id, representative_file_id,
                 representative_encoding, face_count, file_count)
            VALUES
                (:cluster_id, :user_id, :tenant_id, :representative_file_id,
                 :representative_encoding, :face_count, :file_count)
            ON DUPLICATE KEY UPDATE
                representative_file_id = VALUES(representative_file_id),
                representative_encoding = VALUES(representative_encoding),
                face_count = VALUES(face_count),
                file_count = VALUES(file_count),
                updated_at = CURRENT_TIMESTAMP
        """
        await db_manager.execute(sql, {
            "cluster_id": cluster_id,
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "tenant_id": tenant_id,
            "representative_file_id": representative_file_id.encode() if representative_file_id else None,
            "representative_encoding": representative_encoding,
            "face_count": face_count,
            "file_count": file_count,
        })

    @staticmethod
    async def insert_face_file(
        file_id: str,
        user_id: str,
        cluster_id: str,
        face_index: int = 0,
        face_encoding: bytes = b"",
        face_bbox: dict | None = None,
        face_landmarks: dict | None = None,
        confidence: float = 0.0,
    ) -> None:
        """插入人脸文件关联"""
        sql = """
            INSERT INTO pcd_ai_face_files
                (file_id, user_id, cluster_id, face_index, face_encoding,
                 face_bbox, face_landmarks, confidence)
            VALUES
                (:file_id, :user_id, :cluster_id, :face_index, :face_encoding,
                 :face_bbox, :face_landmarks, :confidence)
        """
        await db_manager.execute(sql, {
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "cluster_id": cluster_id,
            "face_index": face_index,
            "face_encoding": face_encoding,
            "face_bbox": json.dumps(face_bbox) if face_bbox else None,
            "face_landmarks": json.dumps(face_landmarks) if face_landmarks else None,
            "confidence": confidence,
        })

    @staticmethod
    async def get_all_face_encodings(user_id: str) -> list[dict]:
        """获取用户的所有人脸编码"""
        sql = """
            SELECT f.id, f.file_id, f.face_index, f.face_encoding, f.face_bbox
            FROM pcd_ai_face_files f
            WHERE f.user_id = :user_id AND f.face_encoding IS NOT NULL
            ORDER BY f.file_id, f.face_index
        """
        return await db_manager.fetch_all(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
        })

    @staticmethod
    async def delete_user_clusters(user_id: str) -> None:
        """删除用户的所有聚类 (重新聚类前清理)"""
        sql = "DELETE FROM pcd_ai_face_clusters WHERE user_id = :user_id"
        await db_manager.execute(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
        })

    @staticmethod
    async def update_face_file_cluster(face_file_id: int, cluster_id: str) -> None:
        """更新人脸文件所属聚类"""
        sql = "UPDATE pcd_ai_face_files SET cluster_id = :cluster_id WHERE id = :id"
        await db_manager.execute(sql, {"cluster_id": cluster_id, "id": face_file_id})


class OCRResultRepository:
    """OCR 结果仓库"""

    @staticmethod
    async def upsert_ocr_result(
        file_id: str,
        user_id: str,
        ocr_text: str,
        language: str = "unknown",
        confidence: float = 0.0,
        pages: int = 1,
        engine: str = "paddleocr",
        model_version: str = "",
        processing_time_ms: int = 0,
        tenant_id: str = "",
    ) -> None:
        """插入或更新 OCR 结果"""
        sql = """
            INSERT INTO pcd_ai_ocr_results
                (file_id, user_id, tenant_id, ocr_text, language, confidence,
                 pages, engine, model_version, processing_time_ms)
            VALUES
                (:file_id, :user_id, :tenant_id, :ocr_text, :language, :confidence,
                 :pages, :engine, :model_version, :processing_time_ms)
            ON DUPLICATE KEY UPDATE
                ocr_text = VALUES(ocr_text),
                language = VALUES(language),
                confidence = VALUES(confidence),
                pages = VALUES(pages),
                engine = VALUES(engine),
                model_version = VALUES(model_version),
                processing_time_ms = VALUES(processing_time_ms),
                updated_at = CURRENT_TIMESTAMP
        """
        await db_manager.execute(sql, {
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "tenant_id": tenant_id,
            "ocr_text": ocr_text,
            "language": language,
            "confidence": confidence,
            "pages": pages,
            "engine": engine,
            "model_version": model_version,
            "processing_time_ms": processing_time_ms,
        })


class SummaryRepository:
    """AI 摘要仓库"""

    @staticmethod
    async def upsert_summary(
        file_id: str,
        user_id: str,
        summary: str = "",
        summary_en: str = "",
        keywords: list[str] | None = None,
        category: str = "",
        reading_time_min: int = 0,
        model_name: str = "",
        processing_time_ms: int = 0,
        tenant_id: str = "",
    ) -> None:
        """插入或更新文件摘要"""
        sql = """
            INSERT INTO pcd_ai_summaries
                (file_id, user_id, tenant_id, summary, summary_en, keywords,
                 category, reading_time_min, model_name, processing_time_ms)
            VALUES
                (:file_id, :user_id, :tenant_id, :summary, :summary_en, :keywords,
                 :category, :reading_time_min, :model_name, :processing_time_ms)
            ON DUPLICATE KEY UPDATE
                summary = VALUES(summary),
                summary_en = VALUES(summary_en),
                keywords = VALUES(keywords),
                category = VALUES(category),
                reading_time_min = VALUES(reading_time_min),
                model_name = VALUES(model_name),
                processing_time_ms = VALUES(processing_time_ms),
                updated_at = CURRENT_TIMESTAMP
        """
        await db_manager.execute(sql, {
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "tenant_id": tenant_id,
            "summary": summary,
            "summary_en": summary_en,
            "keywords": json.dumps(keywords) if keywords else None,
            "category": category,
            "reading_time_min": reading_time_min,
            "model_name": model_name,
            "processing_time_ms": processing_time_ms,
        })


class RecommendationRepository:
    """推荐系统仓库"""

    @staticmethod
    async def replace_recommendations(
        user_id: str,
        recommendations: list[dict[str, Any]],
    ) -> None:
        """替换用户的所有推荐 (先删后插)"""
        # 删除旧推荐
        sql_delete = "DELETE FROM pcd_ai_recommendations WHERE user_id = :user_id"
        await db_manager.execute(sql_delete, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
        })

        if not recommendations:
            return

        # 批量插入新推荐
        sql_insert = """
            INSERT INTO pcd_ai_recommendations
                (user_id, file_id, score, reason, reason_type)
            VALUES
                (:user_id, :file_id, :score, :reason, :reason_type)
        """
        params_list = []
        for rec in recommendations:
            params_list.append({
                "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
                "file_id": rec["file_id"].encode() if isinstance(rec["file_id"], str) else rec["file_id"],
                "score": rec.get("score", 0.0),
                "reason": rec.get("reason", ""),
                "reason_type": rec.get("reason_type", ""),
            })

        await db_manager.execute_many(sql_insert, params_list)

    @staticmethod
    async def get_user_behaviors(
        user_id: str,
        days: int = 30,
    ) -> list[dict]:
        """获取用户最近的行为记录"""
        sql = """
            SELECT file_id, behavior_type, behavior_weight, created_at
            FROM pcd_ai_user_behaviors
            WHERE user_id = :user_id
              AND created_at >= DATE_SUB(NOW(), INTERVAL :days DAY)
            ORDER BY created_at DESC
        """
        return await db_manager.fetch_all(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "days": days,
        })

    @staticmethod
    async def get_user_files(user_id: str) -> list[dict]:
        """获取用户的所有文件 (用于内容推荐)"""
        sql = """
            SELECT file_id, file_name, file_type, file_size
            FROM pcd_file_info_table
            WHERE file_author_id = :user_id
              AND file_status = 'active'
        """
        return await db_manager.fetch_all(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
        })

    @staticmethod
    async def get_user_tags(user_id: str) -> list[dict]:
        """获取用户所有文件的 AI 标签"""
        sql = """
            SELECT t.file_id, t.tag_type, t.tag_name, t.confidence
            FROM pcd_ai_tags t
            WHERE t.user_id = :user_id
            ORDER BY t.confidence DESC
        """
        return await db_manager.fetch_all(sql, {
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
        })


class TaskLogRepository:
    """任务日志仓库"""

    @staticmethod
    async def insert_task_log(
        task_id: str,
        file_id: str,
        user_id: str,
        task_type: str,
        status: str,
        error_message: str = "",
        processing_time_ms: int = 0,
        retry_count: int = 0,
    ) -> None:
        """插入任务执行日志"""
        sql = """
            INSERT INTO pcd_ai_task_logs
                (task_id, file_id, user_id, task_type, status,
                 error_message, processing_time_ms, retry_count)
            VALUES
                (:task_id, :file_id, :user_id, :task_type, :status,
                 :error_message, :processing_time_ms, :retry_count)
        """
        await db_manager.execute(sql, {
            "task_id": task_id,
            "file_id": file_id.encode() if isinstance(file_id, str) else file_id,
            "user_id": user_id.encode() if isinstance(user_id, str) else user_id,
            "task_type": task_type,
            "status": status,
            "error_message": error_message,
            "processing_time_ms": processing_time_ms,
            "retry_count": retry_count,
        })