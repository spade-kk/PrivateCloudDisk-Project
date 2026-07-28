"""原始内容预览授权 API。"""
from fastapi import APIRouter, Header, Request, status
from fastapi.responses import JSONResponse

from app.core.preview_grant import issue_preview_grant, release_preview_grant
from app.models.schemas import InitPreviewGrantRequest, PreviewGrantReleaseRequest
from app.utils.helpers import get_client_ip

router = APIRouter(tags=["预览授权管理"])


@router.post("/files/preview-grants", status_code=status.HTTP_201_CREATED, summary="申请原始内容预览授权")
async def create_preview_grant(
    body: InitPreviewGrantRequest,
    request: Request,
    user_id: str = Header(..., alias="X-User-Id"),
):
    """
    需求三-2：颁发两分钟有效的有状态 Preview Token。

    文件权限、白名单与大小限制在颁发阶段一次完成；接口不会写下载行为或最近访问记录。
    """
    token, grant = await issue_preview_grant(user_id, body.file_id, get_client_ip(request))
    return JSONResponse({
        "code": 200,
        "data": {
            "preview_grant": token,
            "expires_at": int(grant["expiresAt"]),
            "file_name": grant["fileName"],
            "file_size": int(grant["fileSize"]),
            "preview_kind": grant["previewKind"],
        },
        "message": None,
    })


@router.post("/files/preview-grants/release", summary="释放原始内容预览授权")
async def release_preview_grant_endpoint(
    body: PreviewGrantReleaseRequest,
    user_id: str = Header(..., alias="X-User-Id"),
):
    await release_preview_grant(body.preview_grant, user_id)
    return {"code": 200, "data": None, "message": "Preview grant released"}
