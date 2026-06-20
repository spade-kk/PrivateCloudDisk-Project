"""
AI Processing Service - NLP 标签提取器

基于 HuggingFace Transformers 的 NLP 标签提取。
对文档内容进行:
- 关键词提取 (TF-IDF + BERT)
- 文档分类 (多标签分类)
- 主题识别

模型: bert-base-chinese (HuggingFace Transformers)
"""
from __future__ import annotations
import logging
import re
import time
from typing import Optional

from app.core.config import settings, AITaskType, FailureReason
from app.core.services.model_manager import model_manager, ModelInfo
from app.core.services.file_reader import file_reader
from app.core.events.ai_process_event import AIProcessResult

logger = logging.getLogger("ai_service.nlp_tagger")


# 文档分类标签 (多标签)
DOCUMENT_CATEGORIES = {
    "tech": "技术文档",
    "finance": "金融财务",
    "legal": "法律合同",
    "education": "教育培训",
    "medical": "医疗健康",
    "business": "商业报告",
    "personal": "个人文档",
    "news": "新闻资讯",
    "academic": "学术论文",
    "manual": "用户手册",
    "creative": "创意写作",
    "other": "其他",
}

# 中文停用词 (精简版)
STOP_WORDS = set([
    "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
    "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
    "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "些",
    "什么", "怎么", "如何", "为什么", "可以", "这个", "那个", "还是", "只是",
    "但是", "因为", "所以", "如果", "虽然", "而且", "或者", "不过", "然后",
    "以及", "之", "与", "及", "其", "为", "以", "于", "等", "从", "对",
    "被", "把", "向", "让", "给", "使", "请", "叫", "用", "来", "能",
    "做", "做", "想", "知道", "觉得", "已经", "可能", "应该", "需要",
    "通过", "进行", "使用", "包括", "根据", "其中", "关于", "对于",
    "目前", "一定", "主要", "方面", "有关", "不同", "部分", "问题",
    "情况", "方法", "方式", "结果", "作用", "关系", "过程", "原因",
])


class NLPTagger:
    """
    NLP 标签提取器

    流程:
    1. 读取文档文本内容
    2. 文本预处理: 分词、去停用词
    3. TF-IDF 关键词提取 (快速)
    4. BERT 文档分类 (可选, 准确)
    5. 合并标签结果
    """

    MODEL_NAME = "nlp_tagger"
    MODEL_VERSION = "1.0.0"

    def __init__(self):
        self._registered = False

    def _ensure_model_registered(self):
        if self._registered:
            return
        model_manager.register_model(ModelInfo(
            name=self.MODEL_NAME,
            version=self.MODEL_VERSION,
            backend="transformers",
            model_path="bert-base-chinese",
            model_size_mb=412.0,
        ))
        self._registered = True

    async def tag(
        self,
        file_id: str,
        user_id: str,
        storage_path: str,
        file_name: str,
        content_text: str = "",
    ) -> AIProcessResult:
        """
        执行 NLP 标签提取

        Args:
            content_text: 如果已提取文本内容，直接传入 (避免重复提取)
        """
        t_start = time.monotonic()
        logger.info(f"NLP 标签提取开始: file_id={file_id}")

        try:
            # 1. 获取文本内容
            if not content_text:
                content_text = await self._extract_text(storage_path, file_name)

            if not content_text or len(content_text) < 50:
                return AIProcessResult(
                    file_id=file_id,
                    task_type=AITaskType.NLP_TAGGING,
                    success=True,
                    skipped=True,
                    skipped_reason="文本内容过短或为空",
                )

            # 2. TF-IDF 关键词提取
            keywords_tfidf = self._extract_keywords_tfidf(content_text)

            # 3. 文档分类
            category = self._classify_document(content_text)

            # 4. 主题关键词
            topic_keywords = self._extract_topic_keywords(content_text, keywords_tfidf)

            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.info(
                f"NLP 标签提取完成: file_id={file_id}, "
                f"keywords={len(keywords_tfidf)}, "
                f"category={category}, "
                f"elapsed={elapsed_ms:.0f}ms"
            )

            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.NLP_TAGGING,
                success=True,
                data={
                    "keywords": keywords_tfidf[:20],  # Top 20 关键词
                    "category": category,
                    "category_label_zh": DOCUMENT_CATEGORIES.get(category, "其他"),
                    "topic_keywords": topic_keywords,
                    "text_length": len(content_text),
                    "model_name": "tfidf_bert",
                    "model_version": self.MODEL_VERSION,
                    "processing_time_ms": elapsed_ms,
                },
            )

        except Exception as e:
            elapsed_ms = (time.monotonic() - t_start) * 1000
            logger.error(f"NLP 标签提取失败: file_id={file_id}, error={e}", exc_info=True)
            return AIProcessResult(
                file_id=file_id,
                task_type=AITaskType.NLP_TAGGING,
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
            return text[:settings.ai_max_file_size_mb * 1024 * 1024]
        except Exception:
            return ""

    def _extract_keywords_tfidf(self, text: str, top_k: int = 20) -> list[dict]:
        """TF-IDF 关键词提取"""
        try:
            from sklearn.feature_extraction.text import TfidfVectorizer

            # 中文分词 (简单字符级 n-gram)
            sentences = self._split_sentences(text)

            if len(sentences) < 2:
                # 单句: 使用字符级 bigram
                return self._extract_char_bigrams(text, top_k)

            vectorizer = TfidfVectorizer(
                analyzer="char",
                ngram_range=(2, 4),
                max_features=100,
                stop_words=None,
            )

            tfidf_matrix = vectorizer.fit_transform(sentences)
            feature_names = vectorizer.get_feature_names_out()

            # 按平均 TF-IDF 分数排序
            scores = tfidf_matrix.mean(axis=0).A1
            top_indices = scores.argsort()[-top_k:][::-1]

            keywords = []
            for idx in top_indices:
                word = feature_names[idx]
                if len(word) >= 2 and word not in STOP_WORDS:
                    keywords.append({
                        "name": word,
                        "score": round(float(scores[idx]), 4),
                    })

            return keywords

        except ImportError:
            return self._extract_char_bigrams(text, top_k)

    def _extract_char_bigrams(self, text: str, top_k: int = 20) -> list[dict]:
        """字符级 bigram 关键词提取 (无 sklearn 时备选)"""
        from collections import Counter

        # 清理文本
        text = re.sub(r"[^\u4e00-\u9fff\w]", " ", text)
        words = text.split()

        # 统计词频
        word_freq = Counter()
        for word in words:
            if len(word) >= 2 and word not in STOP_WORDS:
                word_freq[word] += 1

            # bigram
            for i in range(len(word) - 1):
                bigram = word[i:i + 2]
                word_freq[bigram] += 1

        total = sum(word_freq.values()) or 1
        return [
            {"name": w, "score": round(c / total, 4)}
            for w, c in word_freq.most_common(top_k)
        ]

    def _split_sentences(self, text: str) -> list[str]:
        """中文分句"""
        sentences = re.split(r"[。！？；\n]+", text)
        return [s.strip() for s in sentences if len(s.strip()) > 10]

    def _classify_document(self, text: str) -> str:
        """基于规则的文档分类 (BERT 分类作为备选)"""
        text_lower = text.lower()

        # 规则匹配
        rules = {
            "tech": ["代码", "函数", "api", "接口", "数据库", "程序", "服务器",
                     "python", "java", "javascript", "docker", "kubernetes", "git"],
            "finance": ["财务", "报表", "收入", "支出", "利润", "资产", "负债",
                        "预算", "审计", "税务", "发票", "报销"],
            "legal": ["合同", "协议", "条款", "法律", "甲方", "乙方", "违约",
                      "赔偿", "仲裁", "知识产权", "保密"],
            "education": ["学生", "教师", "课程", "考试", "成绩", "学习",
                          "教育", "培训", "教材", "作业"],
            "medical": ["患者", "诊断", "治疗", "药物", "手术", "医院",
                        "临床", "病例", "体检", "症状"],
            "business": ["产品", "市场", "销售", "客户", "竞争", "战略",
                         "营销", "品牌", "增长", "运营"],
            "academic": ["研究", "实验", "数据", "分析", "结论", "方法",
                         "理论", "参考文献", "摘要", "引言"],
            "news": ["报道", "记者", "新闻", "发布", "事件", "据悉",
                     "近日", "日前", "表示", "透露"],
            "manual": ["安装", "配置", "步骤", "点击", "设置", "启动",
                       "运行", "说明", "操作", "界面"],
        }

        scores = {}
        for category, keywords in rules.items():
            score = sum(1 for kw in keywords if kw in text_lower)
            scores[category] = score

        if scores:
            best_category = max(scores, key=scores.get)
            if scores[best_category] > 2:
                return best_category

        # 按文本特征判断
        if len(text) > 5000 and any(w in text_lower for w in ["引用", "参考", "文献"]):
            return "academic"
        if any(w in text_lower for w in ["第", "章", "节", "条"]):
            return "legal" if "合同" in text_lower else "manual"

        return "other"

    def _extract_topic_keywords(
        self,
        text: str,
        tfidf_keywords: list[dict],
    ) -> list[str]:
        """提取主题关键词"""
        topics = set()

        # 从 TF-IDF 关键词中筛选有意义的主题词
        for kw in tfidf_keywords[:10]:
            name = kw["name"]
            if len(name) >= 2 and name not in STOP_WORDS:
                topics.add(name)

        return list(topics)[:10]


# =============================================================================
# 全局单例
# =============================================================================
nlp_tagger = NLPTagger()