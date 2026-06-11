"""
OpenSearch 索引映射定义

两个索引:
1. pcd_file_basic   - 文件基本信息索引 (用于精确/模糊搜索元数据)
2. pcd_file_content - 文件内容索引 (用于全文搜索文件内容)
"""

# ========== 文件基本信息索引 ==========
FILE_BASIC_INDEX_BODY = {
    "settings": {
        "number_of_shards": 3,
        "number_of_replicas": 1,
        "analysis": {
            "analyzer": {
                "pcd_text_analyzer": {
                    "type": "standard",
                }
            }
        },
    },
    "mappings": {
        "dynamic": "strict",
        "properties": {
            "file_id": {"type": "keyword"},
            "user_id": {"type": "keyword"},
            "node_id": {"type": "keyword"},

            "filename": {
                "type": "text",
                "analyzer": "pcd_text_analyzer",
                "fields": {
                    "keyword": {"type": "keyword", "ignore_above": 256},
                    "suggest": {"type": "completion"},
                },
            },
            "file_ext": {"type": "keyword"},
            "file_type": {"type": "keyword"},
            "file_category": {"type": "keyword"},

            "size_bytes": {"type": "long"},
            "status": {"type": "keyword"},

            "created_at": {"type": "date"},
            "updated_at": {"type": "date"},
            "indexed_at": {"type": "date"},

            "tags": {"type": "keyword"},
            "summary": {
                "type": "text",
                "analyzer": "pcd_text_analyzer",
            },

            "extraction": {
                "properties": {
                    "extractor": {"type": "keyword"},
                    "char_count": {"type": "integer"},
                    "chunk_count": {"type": "integer"},
                    "has_ocr": {"type": "boolean"},
                    "has_image_tags": {"type": "boolean"},
                    "warnings": {"type": "keyword"},
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
        "analysis": {
            "analyzer": {
                "pcd_text_analyzer": {
                    "type": "standard",
                }
            }
        },
    },
    "mappings": {
        "dynamic": "strict",
        "properties": {
            "file_id": {"type": "keyword"},
            "user_id": {"type": "keyword"},
            "filename": {
                "type": "text",
                "analyzer": "pcd_text_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                },
            },
            "file_ext": {"type": "keyword"},
            "created_at": {"type": "date"},
            "indexed_at": {"type": "date"},

            "content_text": {
                "type": "text",
                "analyzer": "pcd_text_analyzer",
            },

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
                        "analyzer": "pcd_text_analyzer",
                    },
                },
            },

            "ocr_text": {
                "type": "text",
                "analyzer": "pcd_text_analyzer",
            },
            "image_labels": {"type": "keyword"},

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