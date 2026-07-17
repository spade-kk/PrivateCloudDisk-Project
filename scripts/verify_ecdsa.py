#!/usr/bin/env python3
"""
ECDSA 签名验证脚本
用法示例：
    python verify_ecdsa.py \
        --public-key "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEmnSL6Txp/68RKrWgMLRHJ6DR9q9yz024OPY2vdaS+3HiAWG730oJnZdfpF+ER00DnAyia+uaP3XfTTb7Gdz/Q==" \
        --payload "POST|/api/v1/business/users/login|66a814c1-ddd7-4da8-8732-3316c4dc7c79|1783936559066|5F81180F-B59F-4D72-BD13-443DCADCFC55|3155d418167b14cc4f6918820f07969929ca957b315b77e5f6643f8fb0d76a7b" \
        --signature "MEUCIFJCDEjmgeNoZkDMPntBOKPZVM6V3USqnSWJzcr4BVwoAiEAwiL7gICfakNk8Ro43VSTNZGctZp9B1xJItRiruIQx1Q="
"""

import base64
import argparse
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.backends import default_backend


def verify_ecdsa(public_key_b64: str, payload: str, signature_b64: str) -> bool:
    """
    验证 ECDSA 签名

    :param public_key_b64: Base64 编码的 X.509 SPKI 公钥 (SubjectPublicKeyInfo)
    :param payload: 签名负载（UTF-8 字符串）
    :param signature_b64: Base64 编码的签名（ASN.1 DER 格式）
    :return: True 表示签名有效，False 表示无效或出错
    """
    try:
        # 1. 解码公钥 -> DER 字节
        pub_key_der = base64.b64decode(public_key_b64)

        # 2. 加载公钥对象
        public_key = serialization.load_der_public_key(
            pub_key_der, backend=default_backend()
        )

        # 3. 解码签名
        signature_bytes = base64.b64decode(signature_b64)

        # 4. 准备数据
        data = payload.encode("utf-8")

        # 5. 验证签名 (SHA256withECDSA, 签名使用 DER 编码)
        public_key.verify(
            signature_bytes,
            data,
            ec.ECDSA(hashes.SHA256()),
        )
        return True
    except Exception:
        return False


def main():
    parser = argparse.ArgumentParser(
        description="验证 ECDSA 签名 (SHA256withECDSA, DER 签名)"
    )
    parser.add_argument(
        "--public-key",
        required=True,
        help="Base64 编码的 X.509 SPKI 公钥",
    )
    parser.add_argument(
        "--payload",
        required=True,
        help="签名负载 (UTF-8 字符串)",
    )
    parser.add_argument(
        "--signature",
        required=True,
        help="Base64 编码的签名 (ASN.1 DER 格式)",
    )
    args = parser.parse_args()

    success = verify_ecdsa(args.public_key, args.payload, args.signature)
    if success:
        print("签名验证成功")
    else:
        print("签名验证失败")


if __name__ == "__main__":
    main()