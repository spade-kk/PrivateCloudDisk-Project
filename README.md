# 私有云盘系统

## 后端

1. ### 文件贮存 采用 关系型数据库 + 本地文件系统 方式管理云托管文件

关系型数据库 --->  放置文件的信息 例如：文件名 文件大小 文件实际存放路径 作者信息 上传时间 分享链接 权限等级等等

本地文件系统 ---> 存放实际的文件内容 可以转换为二进制数据 改文件后缀名放置 （注意安全问题防止木马病毒的上传运行）



#### Graph1

```mermaid
graph LR
A[用户上传] --> B[后端服务]
B --> C{文件处理}
C -->|流程1| D[将文件信息保存到关系型数据库]
C -->|流程2| E[把文件实际二进制内容保存到本地文件系统]
D & E --> F[生成分享链接]
F --> G[返回结果]
```

#### Graph2

```mermaid
graph LR
  A[客户端] -->|上传/下载请求| B[HTTP API服务]
  B -->|元数据操作| C[(关系数据库)]
  B -->|文件操作指令| D[文件存储服务]
  D -->|物理存储| E[本地文件系统]
  D -->|缓存| F[Redis]
```

#### Graph3

```mermaid
sequenceDiagram
  participant Client as 客户端
  participant API as API网关
  participant Storage as 文件存储服务
  participant FS as 文件系统
  participant MQ as 消息队列
  participant Worker as 异步工作器
  participant DB as 数据库

  Client->>API: 1. POST /uploads (初始化)
  API->>DB: 创建上传记录(status=init)
  DB-->>API: 返回uploadId
  API-->>Client: 返回uploadId
  
  loop 分片上传
    Client->>Storage: 2. 直接上传分片到存储服务
    Storage->>FS: 保存分片到临时目录
    Storage-->>Client: 204 No Content
  end
  
  Client->>API: 3. POST /uploads/{id}/complete
  API->>MQ: 发送合并任务
  MQ-->>API: 确认接收
  API-->>Client: 202 Accepted (异步处理中)
  
  MQ->>Worker: 4. 消费合并任务
  Worker->>Storage: 读取所有分片
  Worker->>Storage: 合并文件并校验
  Worker->>FS: 移动至正式存储
  Worker->>DB: 更新记录(status=ready)
```

#### Graph4

```mermaid
flowchart TD
    subgraph Upload[切片上传 - 有状态]
        direction TB
        U1[客户端] --> U2[将文件切分为多个切片]
        U2 --> U3[发送切片数据 + 上传会话标识]
        U3 --> U4[服务器接收切片]
        U4 --> U5{会话是否存在?}
        U5 -- 否 --> U6[创建上传会话<br/>记录文件信息、切片大小<br/>总片数、过期时间]
        U6 --> U7[保存切片并更新已上传计数]
        U5 -- 是 --> U7
        U7 --> U8{已上传切片 = 总片数?}
        U8 -- 否 --> U9[返回成功，等待剩余切片]
        U8 -- 是 --> U10[合并切片，标记上传完成<br/>清理会话]
        U10 --> U11[返回上传完成响应]
    end

    subgraph Download[切片下载 - 无状态 + JWT限流]
        direction TB
        D1[客户端] --> D2[生成 operation_token JWT<br/>包含 jti 等声明]
        D2 --> D3[发起 GET 请求<br/>携带 Range 头和<br/>Authorization: Bearer token]
        D3 --> D4[服务器验证 JWT]
        D4 -- 无效 --> D5[返回 401 Unauthorized]
        D4 -- 有效 --> D6[提取 jti]
        D6 --> D7[Redis INCR jti 键<br/>键自动设置 TTL 过期]
        D7 --> D8{当前计数 > 并发阈值?}
        D8 -- 是 --> D9[返回 429 Too Many Requests]
        D8 -- 否 --> D10[根据 Range 头读取文件片段]
        D10 --> D11[返回 206 Partial Content]
        D11 --> D12[请求结束]
        D12 --> D13[Redis DECR jti 键]
        D9 --> D14[结束]
        D13 --> D14
    end
```



客户端向API网关服务发起请求 /uploads 通过参数告诉服务端上传文件总大小 上传文件分多少个切片 上传文件名字 文件类型 服务端处理请求在数据库创建一条上传操作信息 保存客户端提供的参数信息与上传操作记录关联 返回一个uploads_id 上传链接 客户端把原始上传的文件数据分成若干个文件数据部分 客户端拿着这个上传链接直接向文件储存服务直传切片文件数据 文件储存服务向API网关提供的内部接口核实 客户端的uploads_id是否有效 上传链接是否有效 检查客户端提交的文件切片大小是否与申请的一致是否超出范围 在临时文件夹下保存客户端上传的文件切片 通过内部接口通知API网关服务创建文件切片记录到数据库中保存 返回客户端成功应答 客户端由此循环上传文件切片 最终向API网关发起请求/uploads/{uploads_id}/complete API网关通过内部接口通知文件储存服务 开始合并uploads_id关联的所有文件切片 合并文件完成后服务核实文件检验码成功后 把文件移入正式文件夹储存 发送文件元数据给API网关 API网关在关联性数据库创建文件记录



我现在设计有一个私有云系统 文件上传采用切片上传的方式 后端架构分为两个服务 API网关服务 文件储存服务 客户端上传一个单文件需要通过api接口 http请求传递参数文件切片个数 切片最大大小 文件名 文件类型等 api网关服务在数据库中创建一条上传会话用来管理本次文件上传 拿着返回的uploads_id客户端直接把文件切片通过h t tp请求携带这次上传的切片索引和uploads_id参数传递到文件储存服务 文件储存服务通过内部暴露的api接口向api网关查询上传会话信息 进行核实后在本地文件系统临时保存切片文件 再通过内部接口让api网关在数据库创建一条切片文件的记录用来管理跟踪 最后所有切片上传完成客户端通知文件储存服务合并文件 正确合并文件后 通过内部接口让api网关服务创建一条文件记录到数据库并删除这次文件上传会话和切片的记录 这是我设计上传一个文件的所有流程 

下面是我设计的文件夹上传流程 首先我数据库有几张表 上传会话表 文件表 目录树结构表（每个用户都有在我的私有云系统中都有一个网盘 每个用户上的这个网盘的目录结构的记录保存在这张表 用户id <----> 目录结构）上传文件夹时客户端遍历递归这个文件夹的结构 最后生成一个文件夹目录树清单 通过API网关接口（接口作用 通过请求提供的文件夹树清单 在此用户的网盘上都某个位置创建插入这个文件夹树目录）客户端提供文件夹目录清单 已经要创建在网盘上的相对路径 API网关更新用户的网盘目录清单 客户端递归这个文件夹下所有的文件 一个一个通过切片上传的形式上传单个文件 并把它在网盘的路径参数加上（客户端自行计算 根据上传文件夹的相对路径加上这个文件在上传文件夹的路径） 如果在上传这个文件夹的所有文件时候有文件未能正确上传 客户端统计下来 写逻辑再次申请上传会话重传文件 几次后依旧失败记录下来 最后的时候在客户端显示提示询问用户是选择跳过这些文件 还是撤销这次上传文件夹的操作回滚操作 回滚其实就是把之前在在网盘创建的文件夹目录通过接口删除 和把文件也删除 上传的文件实际在服务器上保存的路径结构由服务器管理 所以上传文件夹根本不需要重建文件的保存结构在本地文件系统 只需要将它和网盘的目录清单关联一下即可 后续在用户网盘管理界面查看网盘中的文件和文件夹时 API网关只需要查两张表一张是文件表（通过网盘相对路径过滤符合条件的文件记录 就可以知道在这个网盘路径下有哪些文件了） 一张是用户的目录树结构表（通过代码逻辑根据当前的网盘路径在 用户的目录结构中搜索就可以知道这个路径下有哪些文件夹了）用户的目录树结构表可以采用特殊的文件格式以文件形式保存在系统 保存一份记录在数据库进行管理 当然为了内存节省文件夹目录树结构清单可以采用特殊的文件格式可以不是json保存 此外还要考虑读取解析效率速度的问题 所以到底是保存在数据库记录还是文件形式要权衡 请问还可以怎么优化

数据库有两张表一张文件表  文件表 把文件与指定的目录结构位置关联不需要通过网盘路径而是用 node_id关联目录表 解偶 一张目录结构表 目录结构表每个文件夹节点是一条记录 关联的user_id 父节点的parent_id 节点的状态 自己的node_id标识 上传文件夹时候 客户端递归遍历目录结构生成清单递交服务 服务在数据库的目录结构表 指定的用户目录结构相应的位置预创建文件夹节点 节点状态预创建 把其父节点状态改为锁定 实现三重逻辑锁 服务返回所有创建的节点的node_id 以及用上传文件夹的相对路径与node_id关联 以便客户端使用区分 客户端上传每个文件时候提供node_id上传到指定的节点 文件上传采用切片上传的形式 首先向服务申请文件上传的 上传会话 提供文件切片总个数 文件切片最大大小 文件名 文件类型信息 做好约定服务更新记录到数据库并且做会话过期管理 客户端通过uploads_id向文件存储服务直接传递切片文件 通过内部接口验证后 完成切片上传保存到临时目录 内部接口通知API服务更新切片记录到数据库 关联性管理 上传完毕所有切片 客户端发送文件合并请求到文件储存服务 如果文件夹上传的时候遇到了一些问题比如说文件夹里面有些文件没有上传成功 重试上传几次之后还是不行 那么客户端或者网页就应该跟用户交互是跳过失败的文件还是撤销这次文件夹上传的操作 如果文件夹上传操作要撤销 需要发送撤销请求到接口 撤销这些目录结构的节点 API网关检查提供的node_id列表 如果这些节点的状态都是预创建非锁定非活动 那么就把对应的文件夹节点记录从数据库删除 并且查询与这个节点关联的所有上传过的文件标记为待删除状态 等待文件储存服务的定时任务去清理这些待删除的文件 这样就达成了一次上传文件夹操作的回滚 如果用户选择跳过失败的文件 那么可以直接发送请求去激活这些目录结构清单中的文件夹节点了

文件上传采用切片上传模式 一个文件的数据不会直接在一个HTTP请求里面全部一次性上传提交给服务器 而是分切片形式控流上传 服务器会跟踪每个切片的上传状况 还有此次上传操作的状态 比方说这次文件上传是否完成 操作过期时间 上传完成了多少个切片 上传文件的信息 每个切片最大大小 总共分了多少切片 上传业务是有状态的 而且切片上传模式是自定义的 文件下载也是采用切片下载模式 但是使用了HTTP Range标准 无状态操作 服务器不跟踪下载操作 不关心此次下载操作是否完成 完成的进度 也不跟踪下载的切片状态 客户端想读取哪个文件哪个部分就GET读取哪个部分 但是服务器做出了并发限流逻辑 引入了JWT operation_token令牌 + redis缓存计数器逻辑 每次客户端携带operation_token在HTTP头部 发起操作请求到接口的时候 服务端在请求前对JWT进行验证 提取信息把其中的jti作为key放入redis 计算自动加一 如果超过阈值返回Code 429 请求后结束计算自动减一 key设置有TTL过期自动清理(redis计数器无状态) 使用Reids INCR/DECR

2. ### API接口服务 包括：

​	文件上传 

​	文件下载 

​	文件分享链接生成

​	用户管理

​		用户注册

​		用户登录

​		权限等级

​	.............

​	

#### API服务分层 Spring服务

1. 接口层
2. 业务层
3. DTO层
4. 实体层
5. 缓存层
6. 持久层

| API接口路径                                                  | API接口请求类型 |                       描述                        |
| :----------------------------------------------------------- | :-------------: | :-----------------------------------------------: |
| /api/act/uploads                                             |      POST       |               指定文件上传操作申请                |
| /api/act/download                                            |      POST       |                 指定文件下载操作                  |
| /api/user/login                                              |      POST       |                     用户登录                      |
| /api/user/register                                           |      POST       |                     用户注册                      |
| /api/act/detele                                              |      POST       |                指定文件 文件夹删除                |
| /api/act/share                                               |      POST       |               指定文件生成分享链接                |
| /api/act/uploads/folder                                      |      POST       |                    上传文件夹                     |
| /api/act/uploads/{id}                                        |      POST       |                   上传文件切片                    |
| /internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete |      POST       |        内部接口 文件储存服务 切片上传完成         |
| /internal/storage/uploads/{uploads_id}/query                 |      POST       |   内部接口 文件储存服务 查询上传uploads_id记录    |
| /internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/query |      POST       | 内部接口 文件储存服务 查询上传切片chunk_index记录 |
| {STORAGE_SERVICE_URL}/api/uploads/{uploads_id}/video.mp4?index=3 |      POST       |             文件储存服务 文件切片上传             |
| /api/act/uploads/{uploads_id}/complete                       |      POST       |             文件切片上传完毕 文件合并             |
| {STORAGE_SERVICE_URL}/api/operation/init                     |      POST       |             申请操作上传文件资源Token             |
| {STORAGE_SERVICE_URL}/api/download/{node_id}/{file_name}     |       GET       |           文件储存服务 文件资源获取下载           |



| /api/user/login |
| --------------- |
| account         |
| phone_number    |
| password        |

| /api/user/login Result |
| ---------------------- |
| token                  |
| code                   |
| message                |

```json
Request
{
  account: "pcd_19715660327",
  phone_number: "1897756671",
  password: "348922200XiaoXi"
}

Result
{
  token: "0197f0cb-4ac4-7124-b0a0-34c6199ec020",
  code: 200,
  message: "OK"
}
```



| /api/user/register |
| ------------------ |
| phone_number       |
| password           |
| code               |
| user_name          |

| /api/user/register Result |
| ------------------------- |
| user_account              |
| code                      |
| message                   |

```json
Request
{
  phone_number: "1897756671",
  password: "348922200XiaoXi",
  code: "XD89F3C",
  user_name: "小西"
}

Result
{
  user_account: "pcd_19715660327",
  code: 200,
  message: "OK"
}
```



| /api/act/uploads |
| ---------------- |
| file_name        |
| total_chunks     |
| file_size        |

| /api/act/uploads Result |
| ----------------------- |
| uploads_id              |
| code                    |
| message                 |

```json
Request
{
  file_name: "IMG_6477.jpg",
  total_chunks: 7,
  file_size: 6679
}

Result
{
  uploads_id: "0197f0d0636772bfa0a95df727c12e78",
  code: 200,
  message: "OK"
}
```



| /api/act/uploads/{id} |
| --------------------- |
| chunk_index           |
| chunk_size            |

| /api/act/uploads/{id} Result |
| ---------------------------- |
| uploads_presigned_url        |
| code                         |
| message                      |

```json
Request
{
  chunk_index: 1,
  chunk_size: 4487,
}

Result
{
  uploads_presigned_url: "https://storage.yourcloud.com/bucket/user123/video.mp4?X-Amz-		Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20230719%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20230719T120000Z&X-Amz-Expires=3600&X-AmzSignedHeaders=host&X-Amz-Signature=fe5f80f77d5fa3beca038a248ff027d4a799",
  code: 200,
  message: "OK"
}
```

文件上传HTTP Request报文

```http
POST /upload-multiple-files HTTP/1.1
Host: storage.yourcloud.com
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW
Authorization: Bearer <upload_token>
Content-Length: [总长度]

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="metadata"
Content-Type: application/json

{"user_id": "12345", "description": "项目文档"}
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="document.pdf"
Content-Type: application/pdf

[PDF文件的二进制数据]
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="screenshot.png"
Content-Type: image/png

[PNG图片的二进制数据]
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="files"; filename="data.csv"
Content-Type: text/csv

[CSV文件的二进制数据]
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

# 1. 私有云系统后端 API 接口文档

## 1.1. 目录

1. [上传相关接口](#1-上传相关接口)

1. [用户相关接口](#2-用户相关接口)

1. [节点相关接口](#3-节点相关接口)

1. [内部存储相关接口](#4-内部存储相关接口)

## 1.2. 接口概览

| API 接口路径                                                 | 请求类型 |    作用描述    | 需要的参数                                                   |
| ------------------------------------------------------------ | :------: | :------------: | ------------------------------------------------------------ |
| /api/v1/business/uploads/                                    |   POST   |  创建上传会话  | JSON: total_chunks, file_size, file_checksum, chunks_max_size, file_name, file_type, node_id；Header: Authorization |
| /api/v1/files/uploads/{uploads_id}/chunks/                   |   POST   |  上传文件分块  | Path: uploads_id；Form: chunk_index, upload_file_chunk；Header: Authorization |
| /api/v1/files/uploads/{uploads_id}/merge                     |   POST   |  合并上传分块  | Path: uploads_id；Header: Authorization                      |
| /api/v1/business/users/login                                 |   POST   |    用户登录    | JSON: account 或 phone_number, password, captcha_token        |
| /api/v1/business/users/                                      |   POST   |    用户注册    | JSON: phone_number, password, code, name, captcha_token       |
| /api/v1/business/users/me                                    | GET/PATCH/DELETE | 当前用户资料 | Header: Authorization；PATCH JSON: new_email, new_phone_number, new_name |
| /api/v1/business/users/me/password                           |   POST   |  修改用户密码  | JSON: old_password, new_password；Header: Authorization       |
| /api/v1/business/users/me/online-devices                     |   GET    | 查询登录设备列表 | Header: Authorization                                      |
| /api/v1/business/nodes/root                                  |   GET    |   查询根节点   | Header: Authorization                                        |
| /api/v1/business/nodes/{node_id}/children                    |   GET    |   查询子节点   | Path: node_id；Header: Authorization                         |
| /api/v1/business/nodes/                                      |   POST   | 创建文件夹节点 | JSON: node_id, folder_name；Header: Authorization             |
| /api/v1/business/nodes/{node_id}/position                    |   PATCH  |  移动文件夹节点 | Path: node_id；JSON: target_position；Header: Authorization  |
| /api/v1/business/nodes/{node_id}/name                        |   PATCH  | 重命名文件夹节点 | Path: node_id；JSON: new_node_name；Header: Authorization   |
| /api/v1/business/nodes/{node_id}/files/{file_name}/          |   GET    | 查询文件信息   | Path: node_id, file_name；Header: Authorization              |
| /api/v1/business/files/{file_id}                             | GET/DELETE | 查询或删除文件 | Path: file_id；Header: Authorization                       |
| /api/v1/business/files/{file_id}/name                        |   PATCH  |   文件重命名   | Path: file_id；JSON: file_new_name；Header: Authorization     |
| /api/v1/business/files/{file_id}/position                    |   PATCH  |    文件移动    | Path: file_id；JSON: target_node_id；Header: Authorization    |
| /api/v1/business/quotas/me                                   |   GET    | 查询用户配额   | Header: Authorization                                        |
| /api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/complete |   POST   | 完成分块上传 | Path: uploads_id, chunk_index；Query/Form: storage_path       |
| /api/v1/business/internal/storage/uploads/{uploads_id}/      |   GET    | 查询上传会话   | Path: uploads_id                                             |
| /api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/ |   GET    | 查询分块信息 | Path: uploads_id, chunk_index                                |
| /api/v1/business/internal/storage/uploads/{uploads_id}/merge |   POST   |  通知分块合并  | Path: uploads_id                                             |
| /api/v1/business/internal/storage/files                      |   POST   |  完成文件上传  | Query/Form: uploads_id, file_storage_path                    |
| /api/v1/business/internal/storage/files/{node_id}/{file_name} |   GET    | 查询内部文件元数据 | Path: node_id, file_name；Query: uid                    |
| /api/v1/files/operation-tokens                               |   POST   |  操作凭证申请  | JSON: node_id, file_name, operation_type；Header: Authorization |
| /api/v1/files/operation-tokens/                              |  DELETE  |  销毁操作凭证  | JSON: operation_token；Header: Authorization                  |
| /api/v1/files/nodes/{node_id}/files/{file_name}/content      |   GET    |  文件获取下载  | Path: node_id, file_name；Header: Authorization, X-Operation-Token |

## 1.3. 上传相关接口

### 1.3.1. 创建上传会话

- **API 接口路径**：/api/v1/business/uploads/

- **请求类型**：POST

- **参数位置**：请求头JSON

- **参数说明**：

| 参数名          | 类型   | 描述                  |
| --------------- | ------ | --------------------- |
| total_chunks    | int    | 总分块数              |
| file_size       | long   | 文件大小              |
| file_checksum   | String | 文件校验和            |
| chunks_max_size | int    | 分块最大大小          |
| file_name       | String | 文件名称              |
| file_type       | String | 文件类型              |
| node_id         | String | 目录节点 ID           |
| session.uid     | String | 从会话中获取的用户 ID |

- **请求实例**：

```http
POST /api/act/uploads/create HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

total_chunks=5&file_size=1024000&file_checksum=abc123&chunks_max_size=204800&file_name=test.txt&file_type=text/plain&node_id=123
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":"550e8400-e29b-41d4-a716-446655440000"}
```

### 1.3.2. 上传文件分块

- **API 接口路径**：/api/v1/files/uploads/{uploads_id}/chunks/

- **请求类型**：POST

- **参数位置**：请求体JSON

- **参数说明**：uploads_id file chunk_index

### 1.3.3. 上传文件分块合并

- **API 接口路径**：/api/v1/files/uploads/{uploads_id}/merge

- **请求类型**：POST

- **参数位置**：请求体JSON

- **参数说明**：uploads_id

## 1.4. 用户相关接口

### 1.4.1. 用户登录

- **API 接口路径**：/api/v1/business/users/login

- **请求类型**：POST

- **参数位置**：请求体JSON

- **参数说明**：

| 参数名       | 类型   | 描述                                                         |
| ------------ | ------ | ------------------------------------------------------------ |
| account      | String | 账号（可选）                                                 |
| phone_number | String | 手机号（可选，格式：^1 [3-9]\d {9}$）                        |
| password     | String | 密码（必填，格式：^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$） |

- **请求实例**：

```http
POST /api/user/login HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

phone_number=13800138000&password=Test1234
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":null}
```

### 1.4.2. 用户注册

- **API 接口路径**：/api/v1/business/users/
- **请求类型**：POST
- **参数位置**：请求体JSON
- **参数说明**：

| 参数名       | 类型   | 描述                                                   |
| ------------ | ------ | ------------------------------------------------------ |
| phone_number | String | 手机号（格式：^1 [3-9]\d {9}$）                        |
| password     | String | 密码（格式：^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$） |
| code         | String | 验证码（格式：^[a-zA-Z0-9]{6}$）                       |
| name         | String | 用户名（格式：^[a-zA-Z0-9]{2,10}$）                    |

- **请求实例**：

```http
POST /api/user/register HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

phone_number=13800138000&password=Test1234&code=123456&name=testuser
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":"user123456"}
```

### 1.4.3. 用户注销

- **API 接口路径**：/api/v1/business/users/me

- **请求类型**：DELETE

- **参数位置**：NULL

- **参数说明**：NULL

### 1.4.4. 编辑用户信息

- **API 接口路径**：/api/v1/business/users/me

- **请求类型**：PATCH

- **参数位置**：请求体JSON

- **参数说明**：new_email new_phone_number new_username

### 1.4.5. 查询用户信息

- **API 接口路径**：/api/v1/business/users/me

- **请求类型**：GET

- **参数位置**：NULL

- **参数说明**：NULL

### 1.4.6. 修改用户密码

- **API 接口路径**：/api/v1/business/users/me/password

- **请求类型**：POST

- **参数位置**：请求体JSON

- **参数说明**：old_password new_password

### 1.4.7. 上传用户头像

- **API 接口路径**：/api/v1/business/users/me/avatar

- **请求类型**：PUT

- **参数位置**：请求体表单数据

- **参数说明**：avatar_file

### 1.4.8. 查询登陆用户设备列表

- **API 接口路径**：/api/v1/business/users/me/online-devices

- **请求类型**：GET

- **参数位置**：NULL

- **参数说明**：NULL

## 1.5. 节点相关接口

### 1.5.1. 查询当前用户云盘根节点

- **API 接口路径**：api/v1/business/nodes/root

- **请求类型**：GET

- **参数位置**：NULL

- **参数说明**：NULL

| 参数名      | 类型   | 描述                  |
| ----------- | ------ | --------------------- |
| session.uid | String | 从会话中获取的用户 ID |

- **请求实例**：

```http
GET /node/query_root HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=ABC1234567890
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":{"id":"root123","name":"根目录","parent_id":null,"create_time":"2023-01-01 00:00:00","update_time":"2023-01-01 00:00:00","status":null,"user_id":null}}
```

### 1.5.2. 查询目标节点子节点列表

- **API 接口路径**：api/v1/business/nodes/{node_id}/children

- **请求类型**：GET

- **参数位置**：路径参数

- **参数说明**：node_id

| 参数名      | 类型   | 描述                  |
| ----------- | ------ | --------------------- |
| node_id     | String | 节点 ID（路径参数）   |
| session.uid | String | 从会话中获取的用户 ID |

- **请求实例**：

```http
GET /node/root123/child_nodes/query HTTP/1.1
Host: localhost:8080
Cookie: JSESSIONID=ABC1234567890
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":[{"id":"node1","name":"文档","type":"folder","parent_id":"root123"},{"id":"node2","name":"图片","type":"folder","parent_id":"root123"}]}
```

### 1.5.3. 创建文件夹节点

- **API 接口路径**：api/v1/business/nodes

- **请求类型**：POST

- **参数位置**：请求体JSON

- **参数说明**：folder_name position

| 参数名      | 类型   | 描述                  |
| ----------- | ------ | --------------------- |
| folder_name | String | 文件夹名称            |
| position    | String | 父节点 ID             |
| session.uid | String | 从会话中获取的用户 ID |

- **请求实例**：

```http
POST /node/create_folder HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded
Cookie: JSESSIONID=ABC1234567890

folder_name=新文件夹&position=root123
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":null}
```

### 1.5.4. 文件夹节点删除

- **API 接口路径**：/api/v1/business/nodes/{node_id}/
- **请求类型**：DELETE
- **参数位置**：路径参数
- **参数说明**：node_id

### 1.5.5. 文件夹节点移动

- **API 接口路径**：/api/v1/business/nodes/{node_id}/position

- **请求类型**：PATCH

- **参数说明**：target_position node_id

- **参数位置**：路径参数 请求体JSON

### 1.5.6. 文件夹节点重命名

- **API 接口路径**：/api/v1/business/nodes/{node_id}/name
- **请求类型**：PATCH
- **参数位置**：路径参数 请求头JSON
- **参数说明**：new_node_name node_id

## 1.6. 内部存储相关接口

### 1.6.1. 完成分块上传

- **API 接口路径**：/api/v1/business/internal/storage/uploads/{uploads_id}/chunks/

- **请求类型**：POST

- **参数位置**：路径参数 请求头JSON

- **参数说明**：

| 参数名         | 类型   | 描述                                |
| -------------- | ------ | ----------------------------------- |
| uploads_id     | String | 上传会话 ID（路径参数，格式：UUID） |
| chunk_index    | String | 分块索引（格式：非负正整数）        |
| chunk_checksum | String | 分块校验和（16 进制字符串）         |
| storage_path   | String | 分块存储路径（16 进制字符串）       |

- **请求实例**：

```http
POST /internal/storage/uploads/550e8400-e29b-41d4-a716-446655440000/chunks/1/complete HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

chunk_checksum=def456&storage_path=789abc
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":null}
```

### 1.6.2. 查询上传会话

- **API 接口路径**：/api/v1/business/internal/storage/uploads/{uploads_id}/

- **请求类型**：GET

- **参数位置**：路径参数

- **参数说明**：

| 参数名     | 类型   | 描述                                |
| ---------- | ------ | ----------------------------------- |
| uploads_id | String | 上传会话 ID（路径参数，格式：UUID） |

- **请求实例**：

```http
POST /internal/storage/uploads/550e8400-e29b-41d4-a716-446655440000/query HTTP/1.1
Host: localhost:8080
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":{"uploads_id":"550e8400-e29b-41d4-a716-446655440000","total_chunks":5,"file_size":1024000,"file_checksum":"abc123","status":"uploading"}}
```

### 1.6.3. 查询分块信息

- **API 接口路径**：/api/v1/business/internal/storage/uploads/{uploads_id}/chunks/{chunk_index}/

- **请求类型**：GET

- **参数位置**：路径参数

- **参数说明**：

| 参数名      | 类型   | 描述                                   |
| ----------- | ------ | -------------------------------------- |
| uploads_id  | String | 上传会话 ID（路径参数，格式：UUID）    |
| chunk_index | int    | 分块索引（路径参数，格式：非负正整数） |

- **请求实例**：

```http
POST /internal/storage/uploads/550e8400-e29b-41d4-a716-446655440000/chunks/1/query HTTP/1.1
Host: localhost:8080
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":{"uploads_id":"550e8400-e29b-41d4-a716-446655440000","chunk_index":1,"chunk_checksum":"def456","storage_path":"789abc","status":"completed"}}
```

### 1.6.4. 通知分块合并

- **API 接口路径**：/api/v1/business/internal/storage/uploads/{uploads_id}/merge

- **请求类型**：POST

- **参数位置**：路径参数

- **参数说明**：

| 参数名     | 类型   | 描述                                |
| ---------- | ------ | ----------------------------------- |
| uploads_id | String | 上传会话 ID（路径参数，格式：UUID） |

- **请求实例**：

```http
POST /internal/storage/uploads/550e8400-e29b-41d4-a716-446655440000/merging HTTP/1.1
Host: localhost:8080
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":null}
```

### 1.6.5. 完成文件上传

- **API 接口路径**：/api/v1/business/internal/storage/files/

- **请求类型**：POST

- **参数位置**：请求头JSON

- **参数说明**：

| 参数名             | 类型   | 描述                              |
| ------------------ | ------ | --------------------------------- |
| uploads_id         | String | 上传会话 ID（格式：UUID）         |
| file_shortage_path | String | 文件物理存储路径（16 进制字符串） |

- **请求实例**：

```http
POST /internal/storage/file/complete HTTP/1.1
Host: localhost:8080
Content-Type: application/x-www-form-urlencoded

uploads_id=550e8400-e29b-41d4-a716-446655440000&file_shortage_path=def789
```

- **响应实例**：

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"code":200,"message":null,"data":null}
```

## 1.7. 下载相关接口

### 1.7.1. 下载文件操作凭证申请

- **API 接口路径**：/api/v1/files/operation-tokens
- **请求类型**：POST
- **参数位置**：请求体JSON
- **参数说明**：

| 参数名         | 类型   | 描述                               |
| :------------- | ------ | ---------------------------------- |
| operation_type | String | 操作类型 Download Steaming Preview |
| node_id        | String | 目标文件位于的目录节点ID           |
| file_name      | String | 目标文件名字                       |

- **请求实例**：

```http
POST http://127.0.0.1:8000/api/operations/init?node_id=d8785081-e2bf-4ced-97fa-3b785de3abc6&file_name=java_error_in_idea.hprof&operation_type=download HTTP/1.1
Host: 127.0.0.1:8080
```

- **响应实例**：

```http
HTTP/1.1 200 OK
X-process-time: 401.35 ms
Content-Type: application/json

{"code":200,"data":{"ticket":"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyX2lkIiwibm9kZV9pZCI6ImQ4Nzg1MDgxLWUyYmYtNGNlZC05N2ZhLTNiNzg1ZGUzYWJjNiIsImZpbGVfbmFtZSI6ImphdmFfZXJyb3JfaW5faWRlYS5ocHJvZiIsIm9wZXJhdGlvbl90eXBlIjoiZG93bmxvYWQiLCJqdGkiOiJiYmExMWFhOC00ZTU1LTRhZTAtYTYzNS1kYjQ4MDA1N2IwMDMiLCJpYXQiOjE3Nzk3MzgyNjcsImV4cCI6MTc3OTczODg2NywicmxpbWl0IjozMDB9.bzeMXu_Mi_Co4Qky8_C1fEIvFomX3lpsHGnMtgd8jq7yF-6aQkBM2T1U6iWyjujh6Kh_7-HEXyroRL8wb-XVXskYbZNcukZrjpao8FziYyy8-wy8yLY605zZ0panrovZhcMgzbTLdxN8jDnqZGsfFJutp4u98IeqeptrZIA9K5JIXMf7I_giIjb-Yr5dTspgXQ08_Qng2zU__J8-w8htaVJPPZhERR_Y2lKxOlaRzJRXvSlXiaowiz2SizucBtPQoGROrttwNgKcLCs-uSghx7acibg9CpUNVYB_D-_QwVkq-RKW1nmE5bJdL0n9XZMrFHdY1e1MhCjJ0J4gomvbwQ"},"message":null}
```

### 1.7.2. 操作凭证吊销

- **API 接口路径**：/api/v1/files/operation-tokens/
- **请求类型**：DELETE
- **参数位置**：请求体JSON
- **参数说明**：operation_token

### 1.7.3. 文件下载

- **API 接口路径**：/api/v1/files/nodes/{node_id}/files/{file_name}/content
- **请求类型**：GET
- **参数位置**：路径参数
- **参数说明**：

| 参数名    | 类型   | 描述                     |
| :-------- | ------ | ------------------------ |
| file_name | String | 目标文件的文件名         |
| node_id   | String | 目标文件位于的目录节点ID |

- **请求实例**：

```http
GET http://127.0.0.1:8000/api/download/d8785081-e2bf-4ced-97fa-3b785de3abc6/IMG_0949.jpeg HTTP/1.1
Host: 127.0.0.1:8080
X-Operation-Ticket: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyX2lkIiwibm9kZV9pZCI6ImQ4Nzg1MDgxLWUyYmYtNGNlZC05N2ZhLTNiNzg1ZGUzYWJjNiIsImZpbGVfbmFtZSI6ImphdmFfZXJyb3JfaW5faWRlYS5ocHJvZiIsIm9wZXJhdGlvbl90eXBlIjoiZG93bmxvYWQiLCJqdGkiOiJiYmExMWFhOC00ZTU1LTRhZTAtYTYzNS1kYjQ4MDA1N2IwMDMiLCJpYXQiOjE3Nzk3MzgyNjcsImV4cCI6MTc3OTczODg2NywicmxpbWl0IjozMDB9.bzeMXu_Mi_Co4Qky8_C1fEIvFomX3lpsHGnMtgd8jq7yF-6aQkBM2T1U6iWyjujh6Kh_7-HEXyroRL8wb-XVXskYbZNcukZrjpao8FziYyy8-wy8yLY605zZ0panrovZhcMgzbTLdxN8jDnqZGsfFJutp4u98IeqeptrZIA9K5JIXMf7I_giIjb-Yr5dTspgXQ08_Qng2zU__J8-w8htaVJPPZhERR_Y2lKxOlaRzJRXvSlXiaowiz2SizucBtPQoGROrttwNgKcLCs-uSghx7acibg9CpUNVYB_D-_QwVkq-RKW1nmE5bJdL0n9XZMrFHdY1e1MhCjJ0J4gomvbwQ
Range: bytes=0-1200
```

- **响应实例**：

```http
HTTP/1.1 200 OK
X-process-time: 401.35 ms
Content-disposition: attachment; filename="IMG_0949.jpeg"
Content-type: application/octet-stream
Accept-ranges: bytes

l0IjozMDB9.bzeMXu_Mi_Co4Qky8_C1fEIvFomX3lpsHGnMtgd8jq7yF-6aQkBM2T1U6iWyjujh6Kh_7-HEXyroRL8wb-XVXskYbZNcukZrjpao8FziYyy8-wy8yLY605zZ0panrovZhcMgzbTLdxN8jDnqZGsfFJutp4u98IeqeptrZIA9K5JIXMf7I_giIjb-Yr5dTspgXQ08_Qng2zU__J8-w8htaVJPPZhERR_Y2lKxOlaRzJRXvSlXiaowiz2SizucBtPQoGROrttwNgKcLCs-uSghx7acibg9CpUNVYB_D-
```

- **请求测试**:

```cmd
aria2c -s4 -x4 --header="X-Operation-Ticket:eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyX2lkIiwibm9kZV9pZCI6ImQ4Nzg1MDgxLWUyYmYtNGNlZC05N2ZhLTNiNzg1ZGUzYWJjNiIsImZpbGVfbmFtZSI6ImphdmFfZXJyb3JfaW5faWRlYS5ocHJvZiIsIm9wZXJhdGlvbl90eXBlIjoiZG93bmxvYWQiLCJqdGkiOiJiYmExMWFhOC00ZTU1LTRhZTAtYTYzNS1kYjQ4MDA1N2IwMDMiLCJpYXQiOjE3Nzk3MzgyNjcsImV4cCI6MTc3OTczODg2NywicmxpbWl0IjozMDB9.bzeMXu_Mi_Co4Qky8_C1fEIvFomX3lpsHGnMtgd8jq7yF-6aQkBM2T1U6iWyjujh6Kh_7-HEXyroRL8wb-XVXskYbZNcukZrjpao8FziYyy8-wy8yLY605zZ0panrovZhcMgzbTLdxN8jDnqZGsfFJutp4u98IeqeptrZIA9K5JIXMf7I_giIjb-Yr5dTspgXQ08_Qng2zU__J8-w8htaVJPPZhERR_Y2lKxOlaRzJRXvSlXiaowiz2SizucBtPQoGROrttwNgKcLCs-uSghx7acibg9CpUNVYB_D-_QwVkq-RKW1nmE5bJdL0n9XZMrFHdY1e1MhCjJ0J4gomvbwQ" --check-certificate=false -o java_error_in_idea.hprof "http://127.0.0.1:8000/api/download/d8785081-e2bf-4ced-97fa-3b785de3abc6/java_error_in_idea.hprof"
```

## 1.8. 文件相关接口

### 1.8.1. 查询文件信息

- **API 接口路径**：/api/v1/business/nodes/{node_id}/files/{file_name}/
- **请求类型**：GET
- **参数位置**：路径参数
- **参数说明**:  file_name node_id

### 1.8.2. 根据ID查询文件信息

- **API 接口路径**：/api/v1/business/files/{file_id}
- **请求类型**：GET
- **参数位置**：路径参数
- **参数说明**:  file_id

### 1.8.2. 文件重命名

- **API 接口路径**：/api/v1/business/files/{file_id}/name

- **请求类型**：PATCH

- **参数位置**：请求头JSON 路径参数

- **参数说明**：file_new_name file_id

### 1.8.3. 文件移动

- **API 接口路径**：/api/v1/business/files/{file_id}/position
- **请求类型**：PATCH
- **参数位置**：请求体JSON 路径参数
- **参数说明**：new_position file_id

### 1.8.4. 删除文件

- **API 接口路径**：/api/v1/business/files/{file_id}

- **请求类型**：DELETE

- **参数位置**：请求头JSON 路径参数

- **参数说明**：file_id

## 1.9. 分享相关接口

### 1.9.1. 分享文件夹

- **API 接口路径**：
- **请求类型**：
- **参数位置**：
- **参数说明**：

### 1.9.2. 分享文件

- **API 接口路径**：
- **请求类型**：
- **参数位置**：
- **参数说明**：

### 1.9.3. 取消分享

- **API 接口路径**：
- **请求类型**：
- **参数位置**：
- **参数说明**：

### 1.9.4. 查询分享链接的节点

- **API 接口路径**：
- **请求类型**：
- **参数位置**：
- **参数说明**：

## 1.10. 用户配额相关接口

### 1.10.1 查询用户网盘配额信息

- **API 接口路径**：/api/v1/business/quotas/me
- **请求类型**：GET
- **参数位置**：NULL
- **参数说明**：NULL

## 1.11. 补充说明

- 所有接口的成功响应状态码均为 200，对应JsonResult中的code字段为 200。

- 当接口发生异常时，会返回对应的错误状态码和错误信息，具体错误码可参考BaseController中的异常处理部分。

- 涉及用户身份验证的接口均通过登录 JWT 完成。公网请求必须携带 `Authorization: Bearer <token>`，网关校验后向内部服务注入可信 `X-User-Id`；业务服务和文件服务不信任客户端自行传入的内部身份头。

- 接口参数中带有格式限制的，如手机号、密码、UUID 等，需严格按照指定格式传递，否则会返回参数校验错误。

3. ### 数据库 和  本地文件系统

   数据库做好初始化 建多张表格

   文件系统做好统一化管理 存放有规律规章

   |      Private_Cloud_Disk      |
   | :--------------------------: |
   |     PCD_User_Info_Table      |
   |     PCD_File_Info_Table      |
   | PCD_Sharing_Link_Mange_Table |
   |    PCD_Folder_Info_Table     |
   | PCD_Login_Token_Mange_Table  |
   |  PCD_Uploads_Session_Table   |
   |   PCD_Upload_Chunks_Table    |
   |   PCD_Directory_Tree_Table   |
   
   #### Table1：PCD_User_Info_Table
   
   |      属性名       |   属性类型   |
   | :---------------: | :----------: |
   |     user_name     | VARCHAR(120) |
   |      user_id      | VARCHAR(36)  |
   | user_phone_number | VARCHAR(50)  |
   |  user_image_path  | VARCHAR(512) |
   |   user_password   | VARCHAR(70)  |
   |   user_account    | VARCHAR(70)  |
   |    user_email     | VARCHAR(70)  |
   
   #### Table2：PCD_File_Info_Table
   
   |       属性名       |   属性类型   |
   | :----------------: | :----------: |
   |     file_name      | VARCHAR(150) |
   | file_uploaded_time |  TIMESTAMP   |
   |     file_size      |    BIGINT    |
   |     file_type      | VARCHAR(60)  |
   |    file_user_id    | VARCHAR(36)  |
   |      file_id       | VARCHAR(36)  |
   | file_content_path  | VARCHAR(512) |
   |   file_checksum    | VARCHAR(256) |
   |    file_node_id    | VARCHAR(36)  |
   
   #### Table3：PCD_Sharing_Link_Mange_Table
   
   |              属性名              |   属性类型   |
   | :------------------------------: | :----------: |
   |         sharing_link_id          | VARCHAR(36)  |
   |        sharing_link_path         | VARCHAR(512) |
   |       sharing_link_file_id       | VARCHAR(36)  |
   | sharing_link_valid_starting_time |  TIMESTAMP   |
   | sharing_link_valid_endding_time  |  TIMESTAMP   |
   |      sharing_link_password       | VARCHAR(60)  |
   
   #### Table4： PCD_Folder_Info_Table
   
   |        属性名        |   属性类型   |
   | :------------------: | :----------: |
   | folder_uploaded_time |  TIMESTAMP   |
   |    folder_user_id    | VARCHAR(36)  |
   |      folder_id       | VARCHAR(36)  |
   |  folder_lists_info   |     JSON     |
   |     folder_name      | VARCHAR(200) |
   
   #### Table5：PCD_Login_Token_Mange_Table
   
   |          属性名           |  属性类型   |
   | :-----------------------: | :---------: |
   |      login_token_id       | VARCHAR(36) |
   | login_token_starting_time |  TIMESTAMP  |
   | login_token_endding_time  |  TIMESTAMP  |
   |    login_token_user_id    | VARCHAR(36) |
   
   #### Table6：PCD_Uploads_Session_Table
   
   |         属性名          |   属性类型   |
   | :---------------------: | :----------: |
   |       uploads_id        | VARCHAR(36)  |
   |     uploads_user_id     | VARCHAR(36)  |
   |  uploads_total_chunks   |     INT      |
   |  uploads_starting_time  |  TIMESTAMP   |
   |  uploads_endding_time   |  TIMESTAMP   |
   |    uploads_file_size    |    BIGINT    |
   |  uploads_file_checksum  | VARCHAR(256) |
   | uploads_chunks_max_size |     INT      |
   |    uploads_file_name    | VARCHAR(150) |
   |    uploads_file_type    | VARCHAR(60)  |
   |     uploads_node_id     | VARCHAR(36)  |
   |     uploads_status      |     ENUM     |
   
   #### Table7: PCD_Upload_Chunks_Table
   
   |       属性名        |   属性类型   |
   | :-----------------: | :----------: |
   |  chunk_uploads_id   | VARCHAR(36)  |
   |     chunk_index     |     INT      |
   |    chunk_status     |     ENUM     |
   | chunk_storage_path  | VARCHAR(512) |
   | chunk_uploaded_time |  TIMESTAMP   |
   |   chunk_checksum    | VARCHAR(256) |
   
   #### Table9：PCD_Directory_Tree_Table
   
   |      属性名      |   属性类型   |
   | :--------------: | :----------: |
   |     node_id      | VARCHAR(36)  |
   |   node_user_id   | VARCHAR(36)  |
   |  node_parent_id  | VARCHAR(36)  |
   |    node_name     | VARCHAR(200) |
   | node_create_time |  TIMESTAMP   |
   |   node_status    |     ENUM     |
   
   

| 上传文件储存路径规范示例                                     | 描述           |
| ------------------------------------------------------------ | -------------- |
| /Uploads/{user_id}/2007/03/15/{file_name}-{file_id}.cloud    | 上传的文件     |
| /Uploads/temp/{user_id}/2005/02/19/{uploads_id}/{file_name}-{chunk_index}.part | 上传的文件切片 |



## 前端交互

### 开发网站页面：

​	用户登录Page

​	用户注册Page

​	文件上传Page

​	文件分享页面Page

​	.........

​	

| 页面URL        | 页面名称             |
| -------------- | -------------------- |
| /page/login    | 用户登录Page         |
| /page/register | 用户注册Page         |
| /page/upload   | 文件或文件夹上传Page |
| /page/share    | 分享链接Page         |



## 客户端程序

### 多平台开发 对接后端服务业务接口

​	安卓

​	苹果

​	Windows

​	微信小程序

​	.......

## 项目结构

```
├── PrivateCloudDisk-project/
│   ├── PrivateCloudDisk-shortage-service/
│   ├── PrivateClundDisk-web/
│   ├── PrivateCloudDisk-clients/
│   │   ├── PrivateCloudDisk-android-app/
│   │   ├──	PrivateCloudDisk-windows-app/
│   │   └── PrivateCloudDisk-ios-app/
│   ├── PrivateCloudDisk-db/
│   ├── Uploads/
│   ├── LICENSE
│   └── README.md
└── 
```



```mermaid
graph LR
    A[前端应用] --> B[API网关]
    B --> C[文件存储服务]
    B --> D[元数据服务]
    D --> E[数据库]
    D --> F[缓存]
    B --> F
    C --> G[对象存储/本地存储]
    B --> H[消息队列]
    H --> D
```
