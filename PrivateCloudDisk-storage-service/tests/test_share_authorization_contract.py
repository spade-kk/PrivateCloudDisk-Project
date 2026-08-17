"""分享授权与空间边界的静态契约测试。

不连接外部 Redis/MySQL，保证在最小开发环境也能验证高风险路由未回退到
旧的 storage_path/公开下载实现；真实令牌和 Range 联调在集成环境执行。
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class ShareAuthorizationContractTests(unittest.TestCase):
    def test_share_grant_routes_are_present_and_bound_to_resource_id(self):
        source = (ROOT / "app/api/v1/endpoints/share_grants.py").read_text()
        self.assertIn('"/{share_token}/preview-grants"', source)
        self.assertIn('"/{share_token}/download-grants"', source)
        self.assertIn('verify_preview_grant_for_share', source)
        self.assertIn('verify_download_grant_for_share', source)

    def test_specialized_share_preview_routes_do_not_return_internal_path(self):
        source = (ROOT / "app/api/v1/endpoints/share_preview_resources.py").read_text()
        self.assertIn("_public_resource", source)
        self.assertIn("storage_path", source)  # only used for internal FileResponse lookup
        self.assertIn("share_resource_id", source)
        self.assertIn("serve_preview_resource", source)

    def test_share_download_never_uses_legacy_platform_file_entity_route(self):
        controller = (ROOT.parent / "PrivateCloudDisk-platform-service/src/main/java/org/project/control/ShareController.java").read_text()
        self.assertNotIn("resources/{share_resource_id}/download", controller)
        self.assertIn("download-permission", controller)

    def test_hls_share_token_contains_share_boundary(self):
        source = (ROOT / "app/api/v1/endpoints/share_video_stream.py").read_text()
        token_source = (ROOT / "app/core/share_hls_token.py").read_text()
        self.assertIn("share_resource_id", source)
        self.assertIn("分享视频流令牌与资源不匹配", token_source)
        self.assertIn("_resolve_hls_child", source)
        self.assertIn("issue_share_hls_token", source)
        self.assertNotIn("_generate_hls_token", source)

    def test_share_specialized_media_endpoints_keep_virtual_boundary(self):
        preview = (ROOT / "app/api/v1/endpoints/share_preview_resources.py").read_text()
        progress = (ROOT / "app/api/v1/endpoints/share_video_progress.py").read_text()
        self.assertIn("/thumbnail", preview)
        self.assertIn("/video/progress", progress)
        self.assertIn("share_resource_id", progress)
        self.assertIn("result.pop(\"file_id\", None)", progress)

    def test_share_and_normal_content_paths_use_shared_core(self):
        """分享入口只能改变授权边界，文件读取核心必须与普通接口复用。"""
        share = (ROOT / "app/api/v1/endpoints/share_grants.py").read_text()
        normal_preview = (ROOT / "app/api/v1/endpoints/preview_resources.py").read_text()
        normal_download = (ROOT / "app/api/v1/endpoints/files.py").read_text()
        self.assertIn("resolve_share_file", share)
        self.assertIn("serve_authorized_file", share)
        self.assertIn("serve_authorized_file", normal_preview)
        self.assertIn("serve_authorized_file", normal_download)
        self.assertNotIn("def _parse_range", share)
        self.assertNotIn("def _range_response", share)

    def test_all_share_media_adapters_use_single_virtual_resolver(self):
        for name in (
            "share_preview_resources.py",
            "share_video_stream.py",
            "share_video_progress.py",
        ):
            source = (ROOT / "app/api/v1/endpoints" / name).read_text()
            self.assertIn("resolve_share_file", source, name)
            self.assertNotIn("business_service_client.resolve_share_resource", source, name)

    def test_share_and_normal_grants_use_common_limiter(self):
        limiter = (ROOT / "app/core/grant_limiter.py").read_text()
        share = (ROOT / "app/api/v1/endpoints/share_grants.py").read_text()
        normal_preview = (ROOT / "app/core/preview_grant_limiter.py").read_text()
        normal_download = (ROOT / "app/core/download_grant_limiter.py").read_text()
        self.assertIn("async def enforce_grant_limits", limiter)
        for source in (share, normal_preview, normal_download):
            self.assertIn("enforce_grant_limits", source)

    def test_platform_share_children_use_owner_space_parent_queries(self):
        mapper = (ROOT.parent / "PrivateCloudDisk-platform-service/src/main/java/org/project/mapper/ShareResourceMapper.java").read_text()
        service = (ROOT.parent / "PrivateCloudDisk-platform-service/src/main/java/org/project/service/impl/ShareServiceImpl.java").read_text()
        self.assertIn("countNodeInShare", mapper)
        self.assertIn("findShareFolderNodesByParentId", service)
        self.assertIn("findShareActiveFilesByNodeId", service)


if __name__ == "__main__":
    unittest.main()
