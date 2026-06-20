"""
AI Processing Service - 人脸聚类

基于 DBSCAN 算法对人脸编码 (128维 embedding) 进行聚类。
将同一用户的所有人脸检测结果分组为不同的"人物"聚类。

算法:
- 特征: 128维 face_recognition 编码
- 聚类: DBSCAN (基于密度的聚类)
- 聚类后处理: 合并相似的聚类, 过滤噪声
"""
from __future__ import annotations
import logging
import time
import uuid
from typing import List, Optional

import numpy as np
from sklearn.cluster import DBSCAN
from sklearn.preprocessing import StandardScaler

from app.core.config import settings, AITaskType, FailureReason
from app.core.database.repository import FaceClusterRepository
from app.core.events.ai_process_event import FaceClusterEvent, AIProcessResult

logger = logging.getLogger("ai_service.face_clustering")


class FaceClusteringEngine:
    """
    人脸聚类引擎

    流程:
    1. 从数据库加载用户的所有人脸编码
    2. 标准化编码向量
    3. DBSCAN 聚类
    4. 噪声点处理 (归入最近聚类或丢弃)
    5. 结果写入数据库
    """

    MODEL_NAME = "face_clustering_dbscan"
    MODEL_VERSION = "1.0.0"

    def __init__(self):
        self._repo = FaceClusterRepository()

    async def cluster(
        self,
        event: FaceClusterEvent,
    ) -> AIProcessResult:
        """
        执行人脸聚类

        Args:
            event: 人脸聚类事件

        Returns:
            AIProcessResult
        """
        t_start = time.monotonic()
        user_id = event.user_id
        logger.info(f"人脸聚类开始: user_id={user_id}")

        try:
            # 1. 加载人脸编码
            face_data = await self._repo.get_all_face_encodings(user_id)

            if len(face_data) < settings.face_cluster_min_faces:
                logger.info(
                    f"人脸数量不足，跳过聚类: user_id={user_id}, "
                    f"faces={len(face_data)} < {settings.face_cluster_min_faces}"
                )
                return AIProcessResult(
                    file_id="",
                    task_type=AITaskType.FACE_CLUSTERING,
                    success=True,
                    skipped=True,
                    skipped_reason=f"人脸数量不足 ({len(face_data)} < {settings.face_cluster_min_faces})",
                )

            # 2. 提取编码向量
            encodings = []
            face_ids = []
            for row in face_data:
                if row.get("face_encoding"):
                    encoding = np.frombuffer(row["face_encoding"], dtype=np.float64)
                    if len(encoding) == 128:  # 验证维度
                        encodings.append(encoding)
                        face_ids.append(row["id"])

            if len(encodings) < settings.face_cluster_min_faces:
                return AIProcessResult(
                    file_id="",
                    task_type=AITaskType.FACE_CLUSTERING,
                    success=True,
                    skipped=True,
                    skipped_reason=f"有效编码不足 ({len(encodings)})",
                )

            encodings_array = np.array(encodings)

            # 3. 标准化
            scaler = StandardScaler()
            encodings_scaled = scaler.fit_transform(encodings_array)

            # 4. DBSCAN 聚类
            dbscan = DBSCAN(
                eps=settings.face_cluster_eps,
                min_samples=settings.face_cluster_min_samples,
                metric="euclidean",
            )
            labels = dbscan.fit_predict(encodings_scaled)

            # 5. 统计聚类结果
            unique_labels = set(labels)
            n_clusters = len(unique_labels) - (1 if -1 in unique_labels else 0)
            n_noise = int(np.sum(labels == -1))

            logger.info(
                f"人脸聚类结果: user_id={user_id}, "
                f"clusters={n_clusters}, noise={n_noise}, "
                f"total={len(encodings)}"
            )

            if n_clusters == 0:
                return AIProcessResult(
                    file_id="",
                    task_type=AITaskType.FACE_CLUSTERING,
                    success=True,
                    data={
                        "clusters": 0,
                        "noise": n_noise,
                        "total_faces": len(encodings),
                    },
                )

            # 6. 清理旧聚类结果
            if event.force_recluster:
                await self._repo.delete_user_clusters(user_id)

            # 7. 写入聚类结果
            cluster_summaries = {}
            for label in unique_labels:
                if label == -1:
                    continue

                cluster_indices = np.where(labels == label)[0]
                cluster_id = str(uuid.uuid4())
                cluster_encodings = encodings_array[cluster_indices]

                # 计算聚类中心 (平均编码)
                center_encoding = np.mean(cluster_encodings, axis=0)

                # 找到最接近中心的样本
                distances = np.linalg.norm(cluster_encodings - center_encoding, axis=1)
                representative_idx = cluster_indices[distances.argmin()]

                # 写入聚类表
                representative_file_id = face_data[representative_idx].get("file_id", b"")
                await self._repo.upsert_cluster(
                    cluster_id=cluster_id,
                    user_id=user_id,
                    face_count=len(cluster_indices),
                    file_count=len(set(
                        face_data[i]["file_id"] for i in cluster_indices
                    )),
                    representative_file_id=representative_file_id.hex() if isinstance(representative_file_id, bytes) else representative_file_id,
                    representative_encoding=center_encoding.tobytes(),
                    tenant_id=event.tenant_id,
                )

                # 更新每个人脸所属聚类
                for idx in cluster_indices:
                    await self._repo.update_face_file_cluster(
                        face_file_id=face_ids[idx],
                        cluster_id=cluster_id,
                    )

                cluster_summaries[cluster_id] = {
                    "face_count": len(cluster_indices),
                    "file_count": len(set(face_data[i]["file_id"] for i in cluster_indices)),
                }

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"人脸聚类完成: user_id={user_id}, "
                f"clusters={n_clusters}, noise={n_noise}, "
                f"elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id="",
                task_type=AITaskType.FACE_CLUSTERING,
                success=True,
                data={
                    "clusters": n_clusters,
                    "noise": n_noise,
                    "total_faces": len(encodings),
                    "cluster_summaries": cluster_summaries,
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"人脸聚类失败: user_id={user_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id="",
                task_type=AITaskType.FACE_CLUSTERING,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )


# =============================================================================
# 全局单例
# =============================================================================
face_clustering_engine = FaceClusteringEngine()