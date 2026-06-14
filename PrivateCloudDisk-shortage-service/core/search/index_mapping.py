"""
OpenSearch 索引映射定义

两个索引:
1. pcd_file_basic   - 文件基本信息索引 (用于精确/模糊搜索元数据)
2. pcd_file_content - 文件内容索引 (用于全文搜索文件内容)

优化要点:
- 中文分词: ik_max_word (索引) / ik_smart (搜索)
- ngram 前缀匹配: 支持文件名部分匹配 (如输入 "报告" 匹配 "2024年度报告.pdf")
- 丰富字段: file_ext_text, file_category_text, keyword_hints 等
- 多字段加权: 文件名 > 标签 > 文件类型 > 内容
"""

# ========== 文件基本信息索引 ==========
FILE_BASIC_INDEX_BODY = {
    "settings": {
        "number_of_shards": 3,
        "number_of_replicas": 1,
        "index": {
            "max_ngram_diff": 8          # 允许差值为 8
        },
        "analysis": {
            "analyzer": {
                # 中文分词分析器 (索引时用)
                "pcd_index_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase"],
                },
                # 中文分词分析器 (搜索时用)
                "pcd_search_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase"],
                },
                # 文件名 ngram 分析器 (前缀匹配)
                "pcd_filename_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": [
                        "lowercase",
                        "pcd_ngram_filter",
                    ],
                },
                # 文件扩展名分析器 (不需要分词)
                "pcd_keyword_lower": {
                    "type": "custom",
                    "tokenizer": "keyword",
                    "filter": ["lowercase"],
                },
            },
            "filter": {
                "pcd_ngram_filter": {
                    "type": "ngram",
                    "min_gram": 2,
                    "max_gram": 10,
                },
            },
        },
    },
    "mappings": {
        "dynamic": "strict",
        "properties": {
            # === 核心标识 ===
            "file_id": {"type": "keyword"},
            "user_id": {"type": "keyword"},
            "node_id": {"type": "keyword"},
            "tenant_id": {"type": "keyword"},

            # === 文件名（多字段索引，支持多种搜索方式） ===
            "filename": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    # 精确匹配
                    "keyword": {"type": "keyword", "ignore_above": 256},
                    # ngram 前缀/部分匹配（文件名部分搜索）
                    "ngram": {
                        "type": "text",
                        "analyzer": "pcd_filename_analyzer",
                    },
                    # 补全建议
                    "suggest": {"type": "completion"},
                },
            },

            # === 文件类型（多字段，支持精确过滤和模糊搜索） ===
            "file_ext": {
                "type": "keyword",
                "fields": {
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_keyword_lower",
                    },
                },
            },
            "file_type": {
                "type": "keyword",
                "fields": {
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_keyword_lower",
                    },
                },
            },
            "file_category": {
                "type": "keyword",
                "fields": {
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_index_analyzer",
                        "search_analyzer": "pcd_search_analyzer",
                    },
                },
            },

            # === 文件元信息 ===
            "size_bytes": {"type": "long"},
            "status": {"type": "keyword"},

            "created_at": {"type": "date"},
            "updated_at": {"type": "date"},
            "indexed_at": {"type": "date"},

            # === 标签与关键词（增强搜索匹配） ===
            "tags": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },
            "keyword_hints": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },
            "summary": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
            },

            # === 图片标签（独立字段，高权重搜索） ===
            "image_labels": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },

            # === 简短内容摘要（从content_text截取前2000字符，用于快速搜索） ===
            "content_snippet": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
            },

            # === 提取器元信息 ===
            "extraction": {
                "properties": {
                    "extractor": {"type": "keyword"},
                    "char_count": {"type": "integer"},
                    "chunk_count": {"type": "integer"},
                    "has_ocr": {"type": "boolean"},
                    "has_image_tags": {"type": "boolean"},
                    "warnings": {"type": "keyword"},
                    "language": {"type": "keyword"},
                },
            },
        },
    },
}

# ========== 文件内容索引 ==========
FILE_CONTENT_INDEX_BODY = {
    "settings": {
        "number_of_shards": 3,
        "number_of_replicas": 1,
        "index": {
            "max_ngram_diff": 8          # 允许差值为 8
        },
        "analysis": {
            "analyzer": {
                "pcd_index_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase"],
                },
                "pcd_search_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase"],
                },
                "pcd_filename_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": [
                        "lowercase",
                        "pcd_ngram_filter",
                    ],
                },
                "pcd_keyword_lower": {
                    "type": "custom",
                    "tokenizer": "keyword",
                    "filter": ["lowercase"],
                },
            },
            "filter": {
                "pcd_ngram_filter": {
                    "type": "ngram",
                    "min_gram": 2,
                    "max_gram": 10,
                },
            },
        },
    },
    "mappings": {
        "dynamic": "strict",
        "properties": {
            "file_id": {"type": "keyword"},
            "user_id": {"type": "keyword"},
            "node_id": {"type": "keyword"},
            "tenant_id": {"type": "keyword"},

            "filename": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword", "ignore_above": 256},
                    "ngram": {
                        "type": "text",
                        "analyzer": "pcd_filename_analyzer",
                    },
                },
            },

            "file_ext": {
                "type": "keyword",
                "fields": {
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_keyword_lower",
                    },
                },
            },
            "file_type": {
                "type": "keyword",
                "fields": {
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_keyword_lower",
                    },
                },
            },
            "file_category": {
                "type": "keyword",
                "fields": {
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_index_analyzer",
                        "search_analyzer": "pcd_search_analyzer",
                    },
                },
            },

            "size_bytes": {"type": "long"},
            "status": {"type": "keyword"},
            "created_at": {"type": "date"},
            "updated_at": {"type": "date"},
            "indexed_at": {"type": "date"},

            "tags": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },
            "keyword_hints": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },
            "summary": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
            },
            "image_labels": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },

            "content_snippet": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
            },

            # === 全文内容（大字段） ===
            "content_text": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
            },

            # === 内容分块（nested，支持精确命中定位） ===
            "content_chunks": {
                "type": "nested",
                "properties": {
                    "chunk_id": {"type": "keyword"},
                    "source_type": {"type": "keyword"},
                    "page": {"type": "integer"},
                    "sheet": {"type": "keyword"},
                    "slide": {"type": "integer"},
                    "row": {"type": "integer"},
                    "text": {
                        "type": "text",
                        "analyzer": "pcd_index_analyzer",
                        "search_analyzer": "pcd_search_analyzer",
                    },
                },
            },

            # === OCR 文字 ===
            "ocr_text": {
                "type": "text",
                "analyzer": "pcd_index_analyzer",
                "search_analyzer": "pcd_search_analyzer",
            },

            "extraction": {
                "properties": {
                    "extractor": {"type": "keyword"},
                    "char_count": {"type": "integer"},
                    "chunk_count": {"type": "integer"},
                    "has_ocr": {"type": "boolean"},
                    "has_image_tags": {"type": "boolean"},
                    "language": {"type": "keyword"},
                },
            },
        },
    },
}