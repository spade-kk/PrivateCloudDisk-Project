# PrivateCloudDisk-ai-service

可选 AI/异步推理服务目录。提供 FastAPI 入口、Worker 和模型目录占位，用于在明确配置后承载 AI 扩展；当前官网不把自然语言搜索、OCR 或模型效果写成默认产品承诺。

## 技术栈

- FastAPI、Python
- 独立 Worker
- 可选模型与异步任务依赖

## 职责边界

- 承载可选 AI 推理 API 和异步任务
- 模型、权重、资源和外部服务由部署方配置
- 不替代 Platform 的文件元数据、不替代 Storage 的文件 I/O
- 实际能力以 app/ 代码、requirements 和 Compose 配置为准

## 快速开始

    pip install -r requirements.txt
    uvicorn app.main:app --reload
