"""
AI Processing Service - 智能推荐引擎

基于协同过滤 + 内容推荐的混合推荐系统。
支持:
- 基于用户行为的协同过滤 (User-Based CF)
- 基于 AI 标签的内容推荐 (Content-Based)
- 基于热门趋势的推荐 (Trending)
- 混合推荐 (加权融合)
- 个性化文件推荐理由

算法:
- 协同过滤: 余弦相似度 + 行为权重
- 内容推荐: TF-IDF 标签相似度
- 混合: 加权融合 (CF: 0.4, Content: 0.4, Trending: 0.2)
"""
from __future__ import annotations
import logging
import time
import math
from collections import defaultdict
from typing import Optional

import numpy as np

from app.core.config import settings, AITaskType, FailureReason
from app.core.database.repository import RecommendationRepository
from app.core.events.ai_process_event import RecommendationEvent, AIProcessResult

logger = logging.getLogger("ai_service.recommender")

# 行为权重
BEHAVIOR_WEIGHTS = {
    "upload": 3.0,     # 上传 (高权重，用户自己的文件)
    "download": 2.0,   # 下载
    "favorite": 4.0,   # 收藏 (最高权重)
    "share": 2.5,      # 分享
    "view": 1.0,       # 查看
    "edit": 3.0,       # 编辑
    "delete": -1.0,    # 删除 (负权重)
}

# 推荐理由类型
REASON_TYPES = {
    "similar_content": "相似内容",
    "collaborative": "与你相似的用户也喜欢",
    "recent_trend": "近期热门",
    "tag_match": "标签匹配",
    "frequent_access": "你经常访问",
    "same_category": "同类文件",
    "same_author": "同作者",
}


class RecommendationEngine:
    """
    智能推荐引擎

    流程:
    1. 加载用户行为数据
    2. 加载用户文件标签
    3. 协同过滤推荐
    4. 内容推荐
    5. 热门趋势推荐
    6. 混合融合
    7. 去重 + 排序
    8. 写入数据库
    """

    MODEL_NAME = "recommendation_engine"
    MODEL_VERSION = "1.0.0"

    # 混合权重
    CF_WEIGHT = 0.4
    CONTENT_WEIGHT = 0.4
    TRENDING_WEIGHT = 0.2

    # 衰减因子 (时间衰减)
    DECAY_HALF_LIFE_DAYS = 7.0

    def __init__(self):
        self._repo = RecommendationRepository()

    async def recommend(
        self,
        event: RecommendationEvent,
    ) -> AIProcessResult:
        """
        执行个性化推荐

        Args:
            event: 推荐系统事件

        Returns:
            AIProcessResult:
                data.recommendations: [{file_id, score, reason, reason_type}, ...]
                data.total_candidates: 候选文件总数
                data.strategy: 推荐策略
        """
        t_start = time.monotonic()
        user_id = event.user_id
        logger.info(f"推荐系统开始: user_id={user_id}, update_type={event.update_type}")

        try:
            # 1. 加载用户行为数据
            behaviors = await self._repo.get_user_behaviors(user_id, days=30)

            if len(behaviors) < 5:
                logger.info(f"用户行为数据不足: user_id={user_id}, count={len(behaviors)}")
                return AIProcessResult(
                    file_id="",
                    task_type=AITaskType.RECOMMENDATION,
                    success=True,
                    skipped=True,
                    skipped_reason=f"用户行为数据不足 ({len(behaviors)} < 5)",
                )

            # 2. 加载用户文件标签
            user_tags = await self._repo.get_user_tags(user_id)

            # 3. 加载用户文件
            user_files = await self._repo.get_user_files(user_id)

            all_file_ids = {f["file_id"] for f in user_files}

            # 4. 构建用户-文件偏好矩阵
            user_prefs = self._build_user_prefs(behaviors)

            # 5. 协同过滤推荐
            cf_recs = self._collaborative_filtering(user_id, user_prefs, all_file_ids)

            # 6. 内容推荐 (基于标签)
            content_recs = self._content_based_recommend(user_id, user_tags, user_prefs, all_file_ids)

            # 7. 热门趋势推荐
            trending_recs = self._trending_recommend(behaviors, all_file_ids)

            # 8. 混合融合
            final_recs = self._hybrid_merge(cf_recs, content_recs, trending_recs)

            # 9. 写入数据库
            await self._repo.replace_recommendations(user_id, final_recs)

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"推荐系统完成: user_id={user_id}, "
                f"recommendations={len(final_recs)}, "
                f"cf={len(cf_recs)}, content={len(content_recs)}, "
                f"trending={len(trending_recs)}, "
                f"elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id="",
                task_type=AITaskType.RECOMMENDATION,
                success=True,
                data={
                    "recommendations": final_recs,
                    "total_candidates": len(all_file_ids),
                    "cf_candidates": len(cf_recs),
                    "content_candidates": len(content_recs),
                    "trending_candidates": len(trending_recs),
                    "strategy": "hybrid",
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"推荐系统失败: user_id={user_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id="",
                task_type=AITaskType.RECOMMENDATION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )

    def _build_user_prefs(self, behaviors: list[dict]) -> dict[str, dict[str, float]]:
        """
        构建用户-文件偏好矩阵

        Returns:
            {user_id: {file_id: preference_score}}
        """
        user_prefs: dict[str, dict[str, float]] = defaultdict(dict)

        for b in behaviors:
            uid = b["user_id"]
            if isinstance(uid, bytes):
                uid = uid.hex()
            fid = b["file_id"]
            if isinstance(fid, bytes):
                fid = fid.hex()

            btype = b["behavior_type"]
            weight = b.get("behavior_weight", BEHAVIOR_WEIGHTS.get(btype, 1.0))

            # 时间衰减
            if b.get("created_at"):
                try:
                    from datetime import datetime, timezone
                    created = b["created_at"]
                    if isinstance(created, str):
                        created = datetime.fromisoformat(created)
                    now = datetime.now(timezone.utc)
                    days_ago = (now - created.replace(tzinfo=timezone.utc)).days
                    decay = math.pow(0.5, days_ago / self.DECAY_HALF_LIFE_DAYS)
                    weight *= decay
                except Exception:
                    pass

            user_prefs[uid][fid] = user_prefs[uid].get(fid, 0.0) + weight

        return dict(user_prefs)

    def _collaborative_filtering(
        self,
        user_id: str,
        user_prefs: dict[str, dict[str, float]],
        exclude_file_ids: set[str],
        top_k: int = 20,
    ) -> list[dict]:
        """
        基于用户的协同过滤 (User-Based CF)

        1. 计算用户相似度 (余弦相似度)
        2. 找到最相似的 Top-N 用户
        3. 聚合相似用户的偏好文件
        4. 排除用户已有文件
        """
        if user_id not in user_prefs:
            return []

        target_prefs = user_prefs[user_id]
        target_files = set(target_prefs.keys())
        target_vec = self._dict_to_sorted_vec(target_prefs, target_files)

        # 计算所有用户与目标用户的相似度
        similarities = []
        for other_uid, other_prefs in user_prefs.items():
            if other_uid == user_id:
                continue

            other_files = set(other_prefs.keys())
            common_files = target_files & other_files

            if len(common_files) < 2:  # 至少需要2个共同文件
                continue

            # 构建向量
            vec1 = []
            vec2 = []
            for f in common_files:
                vec1.append(target_prefs.get(f, 0.0))
                vec2.append(other_prefs.get(f, 0.0))

            similarity = self._cosine_similarity(vec1, vec2)
            if similarity > 0:
                similarities.append((other_uid, similarity))

        if not similarities:
            return []

        # 取 Top-N 相似用户
        similarities.sort(key=lambda x: x[1], reverse=True)
        top_users = similarities[:min(20, len(similarities))]

        # 聚合推荐文件
        recommendations: dict[str, float] = defaultdict(float)
        for other_uid, sim in top_users:
            other_prefs = user_prefs[other_uid]
            for fid, score in other_prefs.items():
                if fid not in exclude_file_ids:
                    recommendations[fid] += score * sim

        # 排序
        return self._sort_and_format(recommendations, "collaborative", top_k)

    def _content_based_recommend(
        self,
        user_id: str,
        user_tags: list[dict],
        user_prefs: dict[str, dict[str, float]],
        exclude_file_ids: set[str],
        top_k: int = 20,
    ) -> list[dict]:
        """
        基于内容的推荐 (Content-Based)

        1. 构建用户兴趣标签向量
        2. 计算每个文件与用户兴趣的标签相似度
        3. 推荐标签相似度最高的文件
        """
        if not user_tags:
            return []

        user_tag_prefs = user_prefs.get(user_id, {})

        # 构建用户兴趣标签权重
        user_tag_weights: dict[str, float] = defaultdict(float)
        for tag in user_tags:
            fid = tag["file_id"]
            if isinstance(fid, bytes):
                fid = fid.hex()

            tag_name = tag["tag_name"]
            confidence = tag.get("confidence", 0.5)

            # 用户交互过的文件标签权重更高
            if fid in user_tag_prefs:
                confidence *= (1.0 + math.log(1 + user_tag_prefs[fid]))

            user_tag_weights[tag_name] = max(
                user_tag_weights[tag_name],
                confidence,
            )

        if not user_tag_weights:
            return []

        # 计算每个文件的标签相似度
        file_tag_scores: dict[str, float] = defaultdict(float)
        processed_files = set()

        for tag in user_tags:
            fid = tag["file_id"]
            if isinstance(fid, bytes):
                fid = fid.hex()

            if fid in exclude_file_ids or fid in processed_files:
                continue
            processed_files.add(fid)

            tag_name = tag["tag_name"]
            confidence = tag.get("confidence", 0.5)

            if tag_name in user_tag_weights:
                file_tag_scores[fid] += user_tag_weights[tag_name] * confidence

        return self._sort_and_format(file_tag_scores, "tag_match", top_k)

    def _trending_recommend(
        self,
        behaviors: list[dict],
        exclude_file_ids: set[str],
        top_k: int = 10,
    ) -> list[dict]:
        """
        基于热门趋势的推荐

        1. 统计近期热门文件 (按交互频率)
        2. 时间衰减加权
        3. 排除用户已有文件
        """
        if not behaviors:
            return []

        file_scores: dict[str, float] = defaultdict(float)

        for b in behaviors:
            fid = b["file_id"]
            if isinstance(fid, bytes):
                fid = fid.hex()

            if fid in exclude_file_ids:
                continue

            weight = b.get("behavior_weight", 1.0)

            # 时间衰减
            if b.get("created_at"):
                try:
                    from datetime import datetime, timezone
                    created = b["created_at"]
                    if isinstance(created, str):
                        created = datetime.fromisoformat(created)
                    now = datetime.now(timezone.utc)
                    days_ago = (now - created.replace(tzinfo=timezone.utc)).days
                    decay = math.pow(0.5, days_ago / self.DECAY_HALF_LIFE_DAYS)
                    weight *= decay
                except Exception:
                    pass

            file_scores[fid] += weight

        return self._sort_and_format(file_scores, "recent_trend", top_k)

    def _hybrid_merge(
        self,
        cf_recs: list[dict],
        content_recs: list[dict],
        trending_recs: list[dict],
        top_k: int = 20,
    ) -> list[dict]:
        """
        混合推荐融合

        加权融合:
        - CF: 0.4
        - Content: 0.4
        - Trending: 0.2

        归一化各来源分数后加权求和。
        """
        merged: dict[str, dict] = {}

        # 归一化函数
        def normalize(recs: list[dict]) -> dict[str, float]:
            if not recs:
                return {}
            scores = [r["score"] for r in recs]
            max_s = max(scores) if scores else 1.0
            min_s = min(scores) if scores else 0.0
            if max_s == min_s:
                return {r["file_id"]: 1.0 for r in recs}
            return {
                r["file_id"]: (r["score"] - min_s) / (max_s - min_s)
                for r in recs
            }

        cf_norm = normalize(cf_recs)
        content_norm = normalize(content_recs)
        trending_norm = normalize(trending_recs)

        all_file_ids = set(cf_norm.keys()) | set(content_norm.keys()) | set(trending_norm.keys())

        for fid in all_file_ids:
            score = (
                self.CF_WEIGHT * cf_norm.get(fid, 0.0)
                + self.CONTENT_WEIGHT * content_norm.get(fid, 0.0)
                + self.TRENDING_WEIGHT * trending_norm.get(fid, 0.0)
            )

            # 确定推荐理由
            reasons = []
            if fid in cf_norm and cf_norm[fid] > 0.3:
                reasons.append(("collaborative", "与你相似的用户也喜欢"))
            if fid in content_norm and content_norm[fid] > 0.3:
                reasons.append(("tag_match", "标签匹配"))
            if fid in trending_norm and trending_norm[fid] > 0.3:
                reasons.append(("recent_trend", "近期热门"))

            reason_type = reasons[0][0] if reasons else "similar_content"
            reason_text = reasons[0][1] if reasons else "相似内容"

            merged[fid] = {
                "file_id": fid,
                "score": round(score, 4),
                "reason": reason_text,
                "reason_type": reason_type,
            }

        # 排序
        sorted_recs = sorted(merged.values(), key=lambda x: x["score"], reverse=True)
        return sorted_recs[:top_k]

    def _cosine_similarity(self, a: list[float], b: list[float]) -> float:
        """余弦相似度"""
        a = np.array(a)
        b = np.array(b)
        norm_a = np.linalg.norm(a)
        norm_b = np.linalg.norm(b)
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return float(np.dot(a, b) / (norm_a * norm_b))

    def _dict_to_sorted_vec(self, d: dict[str, float], keys: set[str]) -> list[float]:
        """将字典转换为排序向量"""
        return [d.get(k, 0.0) for k in sorted(keys)]

    def _sort_and_format(
        self,
        scores: dict[str, float],
        reason_type: str,
        top_k: int,
    ) -> list[dict]:
        """排序并格式化推荐结果"""
        sorted_items = sorted(scores.items(), key=lambda x: x[1], reverse=True)
        reason_text = REASON_TYPES.get(reason_type, reason_type)

        return [
            {
                "file_id": fid,
                "score": round(score, 4),
                "reason": reason_text,
                "reason_type": reason_type,
            }
            for fid, score in sorted_items[:top_k]
        ]


# =============================================================================
# 全局单例
# =============================================================================
recommendation_engine = RecommendationEngine()