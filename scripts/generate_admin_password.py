#!/usr/bin/env python3
"""
PrivateCloudDisk - 密码哈希生成工具
=====================================

用途:
  生成管理员/用户测试账号的密码哈希值，可直接写入 SQL INSERT 语句。

密码安全架构（企业级双层哈希）:
  第1层: 客户端预哈希 — PBKDF2-SHA256（60万次迭代，pepper 作为 salt）
  第2层: 服务端二次哈希 — BCrypt(12 rounds)

  数据库存储格式: BCrypt( PBKDF2-SHA256( raw_password, pepper ) )

  之所以需要这个脚本，是因为密码经过两次哈希：
    - 前端 Web 使用 PBKDF2-SHA256 预哈希
    - 后端再对预哈希结果做 BCrypt 二次哈希
  直接往数据库写 BCrypt('admin123') 是错误的，因为后端会用
  BCrypt.matches(前端PBKDF2哈希, 数据库值) 来验证，而不是直接验证原始密码。

使用方式:
  python3 generate_admin_password.py                # 交互式输入密码
  python3 generate_admin_password.py admin123       # 命令行传参
  python3 generate_admin_password.py --batch admin123 test123456  # 批量生成

依赖:
  pip3 install bcrypt

输出:
  生成的 BCrypt 哈希值可直接用于:
    INSERT INTO pcd_admin_user_table (..., admin_password, ...) VALUES (..., '$2a$12$...', ...)
    INSERT INTO pcd_user_info_table (..., user_password, ...) VALUES (..., '$2a$12$...', ...)
"""

import hashlib
import sys
import os

# ============================================================
# PBKDF2 配置参数（与前端 crypto.ts 完全一致）
# ============================================================
PBKDF2_ITERATIONS = 600_000
PBKDF2_HASH = 'sha256'
PBKDF2_KEY_LENGTH = 32  # 256 bits = 32 bytes

# Pepper 值（与前端 crypto.ts 完全一致）
PEPPER = b"clouddrive-pbkdf2-v1-pepper"


def pbkdf2_hash(password: str) -> str:
    """
    模拟前端 PBKDF2-SHA256 预哈希。
    
    使用固定的 application-level pepper 作为 salt，
    完全匹配前端 crypto.ts 中的 hashPasswordForTransport() 输出。
    
    返回: 64 位十六进制字符串（与前端完全一致）
    """
    derived_key = hashlib.pbkdf2_hmac(
        hash_name=PBKDF2_HASH,
        password=password.encode('utf-8'),
        salt=PEPPER,
        iterations=PBKDF2_ITERATIONS,
        dklen=PBKDF2_KEY_LENGTH,
    )
    return derived_key.hex()


def bcrypt_hash(pbkdf2_hex: str) -> str:
    """
    模拟后端 BCrypt 二次哈希。
    
    对前端传来的 PBKDF2 十六进制字符串做 BCrypt(12 rounds) 哈希。
    返回: BCrypt 哈希字符串，格式为 $2b$12$...
    """
    import bcrypt
    # BCrypt 要求输入是 bytes
    return bcrypt.hashpw(
        pbkdf2_hex.encode('utf-8'),
        bcrypt.gensalt(rounds=12),
    ).decode('utf-8')


def generate_password_hash(raw_password: str) -> dict:
    """
    完整的双层哈希流程。
    
    参数:
      raw_password: 原始明文密码（如 'admin123'）
    
    返回:
      {
        'raw_password': 'admin123',
        'pbkdf2_hex': '64位十六进制...',
        'bcrypt_hash': '$2b$12$...',
        'sql_value': '$2b$12$...'  # 可直接用于 SQL
      }
    """
    pbkdf2_hex = pbkdf2_hash(raw_password)
    bcrypt_result = bcrypt_hash(pbkdf2_hex)
    
    return {
        'raw_password': raw_password,
        'pbkdf2_hex': pbkdf2_hex,
        'bcrypt_hash': bcrypt_result,
        'sql_value': bcrypt_result,
    }


def print_result(result: dict):
    """格式化输出单个密码的哈希结果。"""
    print(f"\n{'='*60}")
    print(f"  原始密码:    {result['raw_password']}")
    print(f"  PBKDF2(SHA256): {result['pbkdf2_hex']}")
    print(f"  BCrypt(12):  {result['bcrypt_hash']}")
    print(f"{'='*60}")
    print(f"\n  SQL INSERT 示例:")
    print(f"  -- 管理员账号")
    print(f"  INSERT INTO pcd_admin_user_table (..., admin_password, ...)")
    print(f"  VALUES (..., '{result['sql_value']}', ...);")
    print(f"\n  -- 普通用户账号")
    print(f"  INSERT INTO pcd_user_info_table (..., user_password, ...)")
    print(f"  VALUES (..., '{result['sql_value']}', ...);")


def main():
    # 检查 bcrypt 依赖
    try:
        import bcrypt
    except ImportError:
        print("错误: 缺少 bcrypt 依赖，请先安装:")
        print("  pip3 install bcrypt")
        sys.exit(1)

    # 解析参数
    passwords = []
    
    if len(sys.argv) > 1:
        if sys.argv[1] == '--batch':
            passwords = sys.argv[2:]
        elif sys.argv[1] in ('-h', '--help'):
            print(__doc__)
            sys.exit(0)
        else:
            passwords = sys.argv[1:]
    
    if not passwords:
        # 交互式输入
        print("PrivateCloudDisk - 密码哈希生成工具")
        print("-" * 40)
        try:
            raw_password = input("请输入原始密码: ").strip()
            if not raw_password:
                print("错误: 密码不能为空")
                sys.exit(1)
            passwords = [raw_password]
        except (EOFError, KeyboardInterrupt):
            print("\n已取消")
            sys.exit(0)
    
    for pwd in passwords:
        try:
            result = generate_password_hash(pwd)
            print_result(result)
        except Exception as e:
            print(f"\n错误: 生成密码 '{pwd}' 的哈希时出错: {e}")
            sys.exit(1)


if __name__ == '__main__':
    main()