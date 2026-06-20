"""
AI Processing Service - AI 摘要生成器

基于 HuggingFace Transformers 的文本摘要生成。
支持:
- 文档内容摘要 (中文)
- 英文摘要备选
- 关键词提取
- 文档分类
- 阅读时间估算

模型:
- 主模型: fnlp/bart-base-chinese (中文 BART 摘要)
- 备选: 基于 TextRank 的抽取式摘要
- 分类: bert-base-chinese
"""
from __future__ import annotations
import logging
import re
import time
import math
from typing import Optional

import numpy as np

from app.core.config import settings, AITaskType, FailureReason
from app.core.services.model_manager import model_manager, ModelInfo
from app.core.services.file_reader import file_reader
from app.core.events.ai_process_event import AIProcessResult

logger = logging.getLogger("ai_service.summarizer")

# 文档分类提示词
CATEGORY_KEYWORDS = {
    "tech": ["技术", "代码", "API", "算法", "系统", "架构", "编程", "开发", "部署", "运维"],
    "finance": ["财务", "收入", "支出", "利润", "预算", "资产", "负债", "投资", "税务", "审计"],
    "legal": ["合同", "法律", "条款", "甲方", "乙方", "仲裁", "诉讼", "知识产权", "保密", "违约"],
    "education": ["教学", "课程", "学生", "教师", "考试", "论文", "研究", "学术", "实验", "教材"],
    "medical": ["患者", "诊断", "治疗", "手术", "药物", "临床", "病例", "体检", "疫苗", "康复"],
    "business": ["市场", "营销", "客户", "产品", "销售", "战略", "竞争", "渠道", "品牌", "运营"],
    "personal": ["日记", "备忘", "简历", "日常", "计划", "个人", "生活", "旅行", "健身", "家庭"],
    "news": ["报道", "新闻", "记者", "发布", "宣布", "事件", "政策", "社会", "国际", "民生"],
    "academic": ["研究", "论文", "实验", "数据", "方法", "结论", "引用", "文献", "假设", "变量"],
    "manual": ["使用", "操作", "说明", "安装", "配置", "设置", "步骤", "功能", "提示", "注意"],
    "creative": ["故事", "小说", "诗歌", "创意", "艺术", "设计", "灵感", "创作", "作品", "表达"],
}


class AISummarizer:
    """
    AI 摘要生成器

    流程:
    1. 文本预处理 (清洗、分段)
    2. 文档分类 (基于关键词匹配)
    3. 抽取式摘要 (TextRank)
    4. 生成式摘要 (BART, 如果模型可用)
    5. 关键词润色与去重
    6. 阅读时间估算
    """

    MODEL_NAME = "summarizer"
    MODEL_VERSION = "1.0.0"
    MAX_INPUT_LENGTH = 1024      # BART 最大输入长度
    MAX_SUMMARY_LENGTH = 256     # 最大摘要长度
    MIN_SUMMARY_LENGTH = 30      # 最小摘要长度
    READING_SPEED_CN = 400       # 中文阅读速度 (字/分钟)
    READING_SPEED_EN = 200       # 英文阅读速度 (词/分钟)

    # 中文句子分割
    SENTENCE_SPLITTER = re.compile(r'[。！？；\n](?![）\)"\'」』])')

    def __init__(self):
        self._registered = False

    def _ensure_model_registered(self):
        if self._registered:
            return
        model_manager.register_model(ModelInfo(
            name=self.MODEL_NAME,
            version=self.MODEL_VERSION,
            backend="transformers",
            model_path="fnlp/bart-base-chinese",
            model_size_mb=580.0,
        ))
        self._registered = True

    async def summarize(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
        content_text: str = "",
    ) -> AIProcessResult:
        """
        生成文件摘要

        Args:
            content_text: 如果已提取文本内容 (如 OCR 结果)，直接传入

        Returns:
            AIProcessResult:
                data.summary: "中文摘要"
                data.summary_en: "English summary"
                data.keywords: ["关键词1", "关键词2", ...]
                data.category: "tech"
                data.category_label_zh: "技术文档"
                data.reading_time_min: 5
        """
        t_start = time.monotonic()
        logger.info(f"AI 摘要生成开始: file_id={file_id}")

        try:
            # 1. 获取文本内容
            if not content_text:
                content_text = await self._extract_text(storage_path, file_name)

            if not content_text or len(content_text) < 50:
                return AIProcessResult(
                    file_id=file_id,
                    task_type=AITaskType.SUMMARIZATION,
                    success=True,
                    skipped=True,
                    skipped_reason="文本内容过短 (< 50 字符)",
                )

            # 2. 文本清洗
            cleaned_text = self._clean_text(content_text)

            # 3. 文档分类
            category, category_score = self._classify_document(cleaned_text)

            # 4. 抽取式摘要 (TextRank，始终可用)
            extractive_summary = self._extractive_summarize(cleaned_text)

            # 5. 生成式摘要 (BART，如果模型可用)
            abstractive_summary = ""
            try:
                self._ensure_model_registered()
                abstractive_summary = self._abstractive_summarize(cleaned_text)
            except Exception as e:
                logger.warning(f"BART 摘要不可用，使用抽取式摘要: {e}")
                abstractive_summary = extractive_summary

            final_summary = abstractive_summary or extractive_summary

            # 6. 关键词提取
            keywords = self._extract_keywords(cleaned_text, final_summary)

            # 7. 阅读时间估算
            reading_time = self._estimate_reading_time(cleaned_text)

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"AI 摘要生成完成: file_id={file_id}, "
                f"summary_len={len(final_summary)}, "
                f"keywords={len(keywords)}, "
                f"category={category}, "
                f"elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.SUMMARIZATION,
                success=True,
                data={
                    "summary": final_summary,
                    "summary_en": "",  # 暂不生成英文摘要
                    "extractive_summary": extractive_summary,
                    "keywords": keywords,
                    "category": category,
                    "category_label_zh": CATEGORY_KEYWORDS.get(category, {}).get(
                        "label", "其他"
                    ) if category in CATEGORY_KEYWORDS else "其他",
                    "category_score": category_score,
                    "reading_time_min": reading_time,
                    "text_length": len(cleaned_text),
                    "model_name": self.MODEL_NAME,
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"AI 摘要生成失败: file_id={file_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.SUMMARIZATION,
                success=False,
                failure_reason=FailureReason.INFERENCE_ERROR,
                error=str(e),
                processing_time_ms=elapsed_ms,
            )

    async def _extract_text(self, storage_path: str, file_name: str) -> str:
        """从文件中提取文本内容"""
        try:
            data = await file_reader.read_file_bytes(storage_path, file_name)
            text = data.decode("utf-8", errors="ignore")
            # 限制文本长度以避免内存溢出
            max_bytes = settings.ai_max_file_size_mb * 1024 * 1024
            return text[:max_bytes]
        except Exception:
            return ""

    def _clean_text(self, text: str) -> str:
        """文本清洗"""
        # 移除多余空白
        text = re.sub(r'\s+', ' ', text)
        # 移除特殊控制字符
        text = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', text)
        # 标准化换行
        text = re.sub(r'[\r\n]{3,}', '\n\n', text)
        return text.strip()

    def _split_sentences(self, text: str) -> list[str]:
        """分割句子"""
        sentences = self.SENTENCE_SPLITTER.split(text)
        return [s.strip() for s in sentences if s.strip() and len(s.strip()) >= 5]

    def _classify_document(self, text: str) -> tuple[str, float]:
        """基于关键词匹配的文档分类"""
        text_lower = text.lower()
        scores = {}

        for category, keywords in CATEGORY_KEYWORDS.items():
            if isinstance(keywords, dict):
                kw_list = keywords.get("keywords", [])
            else:
                kw_list = keywords

            if not kw_list:
                continue

            matches = sum(1 for kw in kw_list if kw in text_lower)
            total = len(kw_list)
            scores[category] = matches / total if total > 0 else 0.0

        if not scores:
            return "other", 0.0

        best_category = max(scores, key=scores.get)
        best_score = scores[best_category]

        if best_score < 0.05:
            return "other", best_score

        return best_category, round(best_score, 4)

    def _extractive_summarize(self, text: str) -> str:
        """
        基于 TextRank 的抽取式摘要

        算法:
        1. 分句
        2. 构建句子相似度矩阵 (基于 TF-IDF)
        3. PageRank 迭代计算句子重要性
        4. 选取 Top-N 重要句子
        """
        sentences = self._split_sentences(text)

        if len(sentences) <= 1:
            return text[:self.MAX_SUMMARY_LENGTH]

        try:
            from sklearn.feature_extraction.text import TfidfVectorizer
            from sklearn.metrics.pairwise import cosine_similarity

            # TF-IDF 向量化
            vectorizer = TfidfVectorizer(
                analyzer="char",
                ngram_range=(2, 3),
                max_features=500,
            )
            tfidf_matrix = vectorizer.fit_transform(sentences)

            # 相似度矩阵
            sim_matrix = cosine_similarity(tfidf_matrix)

            # TextRank (简化版 PageRank)
            scores = self._textrank(sim_matrix)

            # 选取 Top-N 句子
            summary_sent_count = max(3, min(8, len(sentences) // 5))
            top_indices = np.argsort(scores)[-summary_sent_count:]

            # 按原文顺序排列
            top_indices = sorted(top_indices)

            summary_sentences = [sentences[i] for i in top_indices]
            return "。".join(summary_sentences) + "。"

        except ImportError:
            # 无 sklearn 时的降级方案: 选取首尾段落
            if len(sentences) >= 5:
                return "。".join(sentences[:3] + sentences[-2:]) + "。"
            return "。".join(sentences) + "。"

    def _textrank(self, sim_matrix: np.ndarray, damping: float = 0.85,
                   max_iter: int = 100, tol: float = 1e-6) -> np.ndarray:
        """TextRank / PageRank 算法"""
        n = sim_matrix.shape[0]
        if n == 0:
            return np.array([])

        # 初始化分数
        scores = np.ones(n) / n

        # 归一化相似度矩阵
        sim_sum = sim_matrix.sum(axis=1, keepdims=True)
        sim_sum[sim_sum == 0] = 1
        transition = sim_matrix / sim_sum

        for _ in range(max_iter):
            prev_scores = scores.copy()
            scores = (1 - damping) / n + damping * transition.T @ scores

            if np.abs(scores - prev_scores).sum() < tol:
                break

        return scores

    def _abstractive_summarize(self, text: str) -> str:
        """
        基于 BART 的生成式摘要

        使用 fnlp/bart-base-chinese 模型。
        """
        try:
            model_data = model_manager.get_model(self.MODEL_NAME)
            model = model_data["model"]
            tokenizer = model_data.get("tokenizer")

            if tokenizer is None:
                from transformers import AutoTokenizer
                tokenizer = AutoTokenizer.from_pretrained(
                    "fnlp/bart-base-chinese",
                    cache_dir=settings.model_cache_dir,
                )
                model_data["tokenizer"] = tokenizer

            # 截断文本
            inputs = tokenizer(
                text[:self.MAX_INPUT_LENGTH],
                max_length=self.MAX_INPUT_LENGTH,
                truncation=True,
                return_tensors="pt",
            )
            inputs = {k: v.to(model_manager.get_torch_device()) for k, v in inputs.items()}

            with __import__("torch").no_grad():
                summary_ids = model.generate(
                    **inputs,
                    max_length=self.MAX_SUMMARY_LENGTH,
                    min_length=self.MIN_SUMMARY_LENGTH,
                    num_beams=4,
                    length_penalty=2.0,
                    early_stopping=True,
                    no_repeat_ngram_size=3,
                )

            summary = tokenizer.decode(summary_ids[0], skip_special_tokens=True)
            return summary.replace(" ", "")

        except Exception as e:
            logger.debug(f"BART 摘要生成失败: {e}")
            return ""

    def _extract_keywords(self, text: str, summary: str,
                          top_k: int = 10) -> list[str]:
        """关键词提取 (基于 TF-IDF + 摘要增强)"""
        try:
            from sklearn.feature_extraction.text import TfidfVectorizer

            # 分句
            sentences = self._split_sentences(text)
            if not sentences:
                return []

            # 合并摘要作为一个额外"文档"
            if summary:
                sentences.append(summary)

            vectorizer = TfidfVectorizer(
                analyzer="char",
                ngram_range=(2, 4),
                max_features=100,
            )

            vectorizer.fit(sentences)
            feature_names = vectorizer.get_feature_names_out()

            # 对摘要句加权
            if summary:
                summary_vec = vectorizer.transform([summary])
                scores = summary_vec.toarray()[0]
            else:
                tfidf_matrix = vectorizer.transform(sentences)
                scores = tfidf_matrix.mean(axis=0).A1

            top_indices = scores.argsort()[-top_k * 2:][::-1]

            # 过滤单字和无意义词
            stop_chars = set("的了在是我有和就不人都一上个也到说要去会这那")
            keywords = []
            for idx in top_indices:
                kw = feature_names[idx]
                if len(kw) >= 2 and not all(c in stop_chars for c in kw):
                    keywords.append(kw)
                if len(keywords) >= top_k:
                    break

            return keywords

        except ImportError:
            return []

    def _estimate_reading_time(self, text: str) -> int:
        """估算阅读时间 (分钟)"""
        # 检测语言 (简单判断: 中文字符比例)
        chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', text))
        total_chars = len(text.replace(" ", "").replace("\n", ""))

        if total_chars > 0 and chinese_chars / total_chars > 0.3:
            # 中文为主
            minutes = chinese_chars / self.READING_SPEED_CN
        else:
            # 英文为主
            words = len(text.split())
            minutes = words / self.READING_SPEED_EN

        return max(1, math.ceil(minutes))


# =============================================================================
# 全局单例
# =============================================================================
ai_summarizer = AISummarizer()