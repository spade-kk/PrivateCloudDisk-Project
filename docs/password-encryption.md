# 密码加密流程详解

## 1. 概述

PrivateCloudDisk 采用 **前端 PBKDF2 预哈希 + 后端 BCrypt 二次加密** 的双层密码架构，确保密码明文永不离开客户端，且即使数据库泄露也无法直接暴力破解。

## 2. 加密流程

```
┌─────────────────────────────────────────────────────────┐
│                    原始密码（示例占位符）                 │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│  前端 PBKDF2-SHA256 预哈希                                │
│  ┌───────────────────────────────────────────────────┐ │
│  │ 算法:     PBKDF2-HMAC-SHA256                       │ │
│  │ 迭代次数: 600,000                                   │ │
│  │ Pepper:  "clouddrive-pbkdf2-v1-pepper" (固定值)     │ │
│  │ 输出长度: 32 字节 → 64 位 hex 字符串                  │ │
│  └───────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│  传输层: HTTPS 加密传输                                   │
│  发送 PBKDF2 的 hex 结果到后端                             │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│  后端 BCrypt 二次加密                                     │
│  ┌───────────────────────────────────────────────────┐ │
│  │ 算法: BCrypt                                       │ │
│  │ 轮数: 12 rounds                                   │ │
│  │ 输入: 前端 PBKDF2 的 hex 字符串                     │ │
│  │ 输出: $2b$12$[salt][hash] (60 字符)               │ │
│  └───────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              存入数据库 pcd_user_info_table               │
│              user_password = $2b$12$...                  │
└─────────────────────────────────────────────────────────┘
```

## 3. 前端实现

### 3.1 Web 端 (TypeScript)

文件: `PrivateCloudDisk-web/src/utils/crypto.ts`

```typescript
const PBKDF2_ITERATIONS = 600000;
const PBKDF2_KEY_LENGTH = 32;
const PBKDF2_HASH = 'SHA-256';
const PEPPER = new TextEncoder().encode('clouddrive-pbkdf2-v1-pepper');

async function pbkdf2Hash(password: string): Promise<string> {
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    { name: 'PBKDF2' },
    false,
    ['deriveBits']
  );

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: PEPPER,
      iterations: PBKDF2_ITERATIONS,
      hash: PBKDF2_HASH,
    },
    keyMaterial,
    PBKDF2_KEY_LENGTH * 8
  );

  return Array.from(new Uint8Array(derivedBits))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
```

### 3.2 桌面端 (Electron/React)

使用 `crypto-js` 实现相同的 PBKDF2 哈希。

### 3.3 移动端

| 平台 | 实现方式 |
|------|----------|
| uni-app | `src/utils/crypto.js` — Web Crypto API |
| iOS | `CryptoService.swift` — CommonCrypto |
| Android | `CryptoManager.kt` — javax.crypto |

## 4. 后端实现

### 4.1 密码验证 (Java)

文件: `PrivateCloudDisk-platform-service/src/main/java/org/project/service/impl/UserServiceImpl.java`

```java
public boolean passwordMatches(String rawPassword, String storedHash) {
    // rawPassword 已经是前端 PBKDF2 后的 hex 字符串
    // storedHash 是数据库中 BCrypt 哈希值
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    return encoder.matches(rawPassword, storedHash);
}
```

### 4.2 密码存储

```java
public String encodePassword(String pbkdf2Hex) {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    return encoder.encode(pbkdf2Hex);
}
```

## 5. 测试账号生成

### 5.1 工具脚本

项目提供 `scripts/generate_admin_password.py` 用于生成符合加密流程的密码哈希：

```bash
# 安装依赖
pip3 install bcrypt

# 生成密码哈希
python3 scripts/generate_admin_password.py your-test-password

# 输出示例:
# 原始密码: your-test-password（仅作命令示例）
# PBKDF2 哈希 (hex): a1b2c3d4e5f6...
# BCrypt 哈希: $2b$12$jKWWnXEsIn7Be2RHkpsTb.wk252WlxUfAy3bYyjjsJgF/P0FIP9Hi
# SQL INSERT:
# INSERT INTO pcd_admin_user_table (..., admin_password, ...)
# VALUES (..., '$2b$12$jKWWnXEsIn7Be2RHkpsTb.wk252WlxUfAy3bYyjjsJgF/P0FIP9Hi', ...);
```

### 5.2 为什么不能直接使用 BCrypt

如果直接对原始密码执行 BCrypt：
```bash
# 错误做法
htpasswd -bnBC 12 "" your-test-password | tr -d ':\n'
# 生成的哈希无法通过后端验证！

# 原因: 后端验证的是 BCrypt.matches(PBKDF2(原始密码), stored_hash)
# 而不是 BCrypt.matches(原始密码, stored_hash)
```

## 6. 安全设计理由

### 6.1 为什么使用 PBKDF2

| 特性 | PBKDF2 | 优势 |
|------|--------|------|
| 内存密集型 | 是 | 抵抗 GPU 暴力破解 |
| 可配置迭代 | 600,000 次 | 随硬件提升可增加 |
| 标准化 | RFC 2898 | 跨平台兼容 |
| Pepper 支持 | 固定 salt | 与数据库分离 |

### 6.2 为什么使用 BCrypt

| 特性 | BCrypt | 优势 |
|------|--------|------|
| 自适应盐值 | 内置随机 salt | 无需手动管理 |
| 可配置轮数 | 12 rounds | 安全性与性能平衡 |
| 抗彩虹表 | 内置盐值 | 即使相同密码也不同哈希 |
| 成熟稳定 | 广泛使用 | 经过充分安全审计 |

### 6.3 双层哈希的优势

1. **密码明文不离开客户端**: 即使 HTTPS 被中间人攻击，截获的也是 PBKDF2 结果
2. **数据库泄露防护**: 攻击者需要反向 PBKDF2 + BCrypt 两重哈希
3. **Pepper 分离**: pepper 存储在代码中，与数据库不在同一位置
4. **前端可更换算法**: 可以在不影响数据库的情况下升级前端哈希参数

## 7. 参数配置

| 参数 | 值 | 说明 |
|------|-----|------|
| PBKDF2 算法 | SHA-256 | 哈希算法 |
| PBKDF2 迭代 | 600,000 | 迭代次数 |
| PBKDF2 输出 | 32 字节 | 密钥长度 |
| Pepper | `clouddrive-pbkdf2-v1-pepper` | 固定 pepper |
| BCrypt 轮数 | 12 | 工作因子 |
| BCrypt 版本 | $2b$ | 兼容所有平台 |
