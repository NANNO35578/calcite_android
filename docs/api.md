# REST API 设计（前后端对接核心）

## API 设计原则

* RESTful 风格
* JSON 数据格式
* Token 鉴权
* 统一返回格式

**统一响应格式：**
```json
{
  "code": 0,        // 0 表示成功，非0表示失败
  "message": "success",
  "data": {}
}
```

**鉴权方式：**
- Header: `Authorization: Bearer {token}`
- URL 参数: `?token={token}`

### API 总览

| 接口                   | 方法     | 说明                                                     |
| -------------------- | ------ | ------------------------------------------------------ |
| /api/auth/register   | POST   | [用户注册](#11-用户注册-post-apiauthregister)                  |
| /api/auth/login      | POST   | [用户登录](#12-用户登录-post-apiauthlogin)                     |
| /api/auth/logout     | POST   | [退出登录](#13-退出登录-post-apiauthlogout)                    |
| /api/user/profile    | GET    | [获取用户信息](#14-获取用户信息-get-apiuserprofile)                |
|                      |        |                                                        |
| /api/note/create     | POST   | [新建笔记](#21-创建笔记-post-apinotecreate)                    |
| /api/note/update     | POST   | [更新笔记](#22-更新笔记-post-apinoteupdate)                    |
| /api/note/delete     | POST   | [删除笔记](#23-删除笔记-post-apinotedelete)                    |
| /api/note/list       | GET    | [获取笔记列表](#24-获取笔记列表-get-apinotelist)                   |
| /api/note/detail     | GET    | [获取笔记详情](#25-获取笔记详情-get-apinotedetail)                 |
| /api/note/search     | GET    | [全文搜索](#26-全文搜索笔记-get-apinotesearch)                   |
|                      |        |                                                        |
| /api/note/view       | POST   | [浏览笔记](#29-浏览笔记-post-apinoteview)                      |
| /api/note/like       | POST   | [点赞笔记](#210-点赞笔记-post-apinotelike)                     |
| /api/note/collect    | POST   | [收藏笔记](#211-收藏笔记-post-apinotecollect)                  |
| /api/notes/like      | DELETE | [取消点赞](#212-取消点赞-delete-apinoteslike)                  |
| /api/notes/collect   | DELETE | [取消收藏](#213-取消收藏-delete-apinotescollect)               |
|                      |        |                                                        |
| /api/recommend/notes | GET    | [推荐笔记](#214-推荐笔记-get-apirecommendnotes)                |
|                      |        |                                                        |
| /api/tags/hot        | GET    | [获取热门标签](#31-获取热门标签-get-apitagshot)                    |
| /api/notes/tags      | GET    | [获取笔记标签](#27-获取笔记标签列表-get-apinotesidtags)              |
| /api/notes/tags/ai   | POST   | [AI生成标签](#28-ai生成笔记标签-post-apinotesidtagsai)           |
|                      |        |                                                        |
| /api/folder/create   | POST   | [创建文件夹](#36-创建文件夹-post-apifoldercreate)                |
| /api/folder/list     | GET    | [获取文件夹列表](#39-获取文件夹列表-get-apifolderlist)               |
| /api/folder/update   | POST   | [更新文件夹](#37-更新文件夹-post-apifolderupdate)                |
| /api/folder/delete   | POST   | [删除文件夹](#38-删除文件夹-post-apifolderdelete)                |
|                      |        |                                                        |
| /api/file/upload     | POST   | [上传文件到MinIO](#41-上传文件-post-apifileupload)              |
| /api/file/list       | GET    | [获取文件列表](#42-获取文件列表-get-apifilelist)                   |
| /api/file/delete     | POST   | [删除文件(MinIO+数据库)](#43-删除文件-post-apifiledelete)         |
| /api/file/status     | GET    | [查询文件上传状态](#44-查询文件上传状态-get-apifilestatus)             |
| /api/file/info       | GET    | [获取单个文件详情](#45-获取单个文件详情-get-apifileinfo)               |
|                      |        |                                                        |
| /api/ocr/recognize   | POST   | [上传文件进行OCR识别,生成新笔记](#461-提交ocr任务-post-apiocrrecognize) |
| /api/ocr/status      | GET    | [查询OCR处理状态](#462-查询ocr状态-get-apiocrstatus)             |

---

## 1. 用户模块 API

| 接口               | 方法   | 说明       |
| ------------------ | ---- | -------- |
| /api/auth/register | POST | 用户注册   |
| /api/auth/login    | POST | 用户登录   |
| /api/auth/logout   | POST | 退出登录   |
| /api/user/profile  | GET  | 获取用户信息 |

### 1.1 用户注册 POST /api/auth/register

**请求示例：**
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "password123"
}
```

**请求参数：**

| 参数     | 类型   | 必填 | 说明           |
| -------- | ------ | ------ | -------------- |
| username | string | 是     | 用户名       |
| email    | string | 否     | 邮箱         |
| password | string | 是     | 密码         |

**响应示例：**
```json
{
  "code": 0,
  "message": "注册成功",
  "data": {
    "user_id": 1,
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### 1.2 用户登录 POST /api/auth/login

**请求示例：**
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**请求参数：**

| 参数     | 类型   | 必填 | 说明     |
| -------- | ------ | ------ | -------- |
| username | string | 是     | 用户名   |
| password | string | 是     | 密码     |

**响应示例：**
```json
{
  "code": 0,
  "message": "登录成功",
  "data": {
    "user_id": 1,
    "username": "testuser",
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### 1.3 退出登录 POST /api/auth/logout

**请求方式：**
- Header: `Authorization: Bearer {token}`
- 或 Body 参数: `{"token": "your_jwt_token"}`

**请求示例：**
```json
{
  "token": "your_jwt_token"
}
```

**响应示例：**
```json
{
  "code": 0,
  "message": "退出登录成功",
  "data": {}
}
```

### 1.4 获取用户信息 GET /api/user/profile

**请求方式：**
Header: `Authorization: Bearer {token}`

**响应示例：**
```json
{
  "code": 0,
  "message": "获取用户信息成功",
  "data": {
    "user_id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "created_at": "2025-01-01 12:00:00"
  }
}
```

---

## 2. 笔记管理 API（核心）

| 接口                 | 方法     | 说明     |
| ------------------ | ------ | ------ |
| /api/note/create   | POST   | 新建笔记   |
| /api/note/update   | POST   | 更新笔记   |
| /api/note/delete   | POST   | 删除笔记   |
| /api/note/list     | GET    | 获取笔记列表 |
| /api/note/detail   | GET    | 获取笔记详情 |
| /api/note/search   | GET    | 全文搜索   |
|                    |        |        |
| /api/notes/tags    | GET    | 获取笔记标签 |
| /api/notes/tags/ai | POST   | AI生成标签 |
|                    |        |        |
| /api/note/view     | POST   | 浏览笔记   |
| /api/note/like     | POST   | 点赞笔记   |
| /api/note/collect  | POST   | 收藏笔记   |
| /api/notes/like    | DELETE | 取消点赞   |
| /api/notes/collect | DELETE | 取消收藏   |

**鉴权要求：** 所有笔记接口均需通过 Token 鉴权

### 2.1 创建笔记 POST /api/note/create

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "title": "我的第一篇笔记",
  "folder_id": 1
}
```

**请求参数：**

| 参数      | 类型   | 必填 | 说明                     |
| --------- | ------ | ------ | ------------------------ |
| title     | string | 是     | 笔记标题                 |
| folder_id | int64  | 否     | 所属文件夹ID，0表示未分类 |

**响应示例：**
```json
{
  "code": 0,
  "message": "创建笔记成功",
  "data": {
    "note_id": 1
  }
}
```

### 2.2 更新笔记 POST /api/note/update

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "note_id": 1,
  "title": "更新后的标题",
  "content": "更新后的内容",
  "summary": "更新后的摘要",
  "folder_id": 2,
  "is_public": true
}
```

**请求参数：**

| 参数      | 类型   | 必填 | 说明                       |
| --------- | ------ | ------ | -------------------------- |
| note_id   | int64  | 是     | 笔记ID                     |
| title     | string | 否     | 更新标题                   |
| content   | string | 否     | 更新内容                   |
| summary   | string | 否     | 更新摘要                   |
| folder_id | int64  | 否     | 更新文件夹ID，>=0表示更新 |
| is_public | bool   | 否     | 更新笔记是否公开         |

**响应示例：**
```json
{
  "code": 0,
  "message": "更新笔记成功",
  "data": {}
}
```

### 2.3 删除笔记 POST /api/note/delete

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "note_id": 1
}
```

**请求参数：**

| 参数    | 类型  | 必填 | 说明     |
| ------- | ----- | ------ | -------- |
| note_id | int64 | 是     | 笔记ID   |

**响应示例：**
```json
{
  "code": 0,
  "message": "删除笔记成功",
  "data": {}
}
```

> 注：删除为软删除，仅标记 `is_deleted` 字段，实际数据仍保留

### 2.4 获取笔记列表 GET /api/note/list

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数        | 类型    | 必填  | 说明                 |
| --------- | ----- | --- | ------------------ |
| folder_id | int64 | 否   | 文件夹ID，0/不传 表示根目录笔记 |

**响应示例：**
```json
{
  "code": 0,
  "message": "获取笔记列表成功",
  "data": [
    {
      "id": 1,
      "title": "我的第一篇笔记",
      "summary": "笔记摘要",
      "folder_id": 1,
      "created_at": "2025-01-01 12:00:00",
      "updated_at": "2025-01-01 12:00:00"
    }
  ]
}
```

> 注：列表响应不包含 `content` 字段以减少网络流量，完整内容需调用 `/api/note/detail` 获取

### 2.5 获取笔记详情 GET /api/note/detail

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数    | 类型   | 必填 | 说明     |
| ------- | ------ | ------ | -------- |
| note_id | string | 是     | 笔记ID   |

**响应示例：**
- 以下是`GET http://localhost:8888/api/note/detail?note_id=1`的响应
```json
{
    "code": 0,
    "data": {
        "author_id": 1,
        "collect_count": 0,
        "content": "更新后的内容",
        "created_at": "2026-01-25 14:25:18",
        "folder_id": 1,
        "has_collected": false,
        "has_liked": false,
        "id": 1,
        "is_public": 0,
        "like_count": 0,
        "summary": "更新后的摘要",
        "title": "更新后的标题",
        "updated_at": "2026-01-25 14:28:46"
    },
    "message": "获取笔记详情成功"
}
```

### 2.6 全文搜索笔记 GET /api/note/search

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数       | 类型     | 必填  | 说明              |
| -------- | ------ | --- | --------------- |
| keyword  | string | 是   | 搜索关键词           |
| isPublic | int    | 否   | 有就搜索公开的         |
| from     | int    | 否   | 分页起始位置，默认0      |
| size     | int    | 否   | 每页数量，默认20，最大100 |

**响应示例：**
- 以下是`GET http://localhost:8888/api/note/search?keyword=C++&is_public=0`的部分响应
```json
{
    "code": 0,
    "data": [
        {
            "author_id": 7,
            "created_at": "2026-04-19T13:42:35",
            "highlight_content": "## 笔记2：<mark>C</mark>语言数据类型与变量\n### ... **变量定义与使用**\n```<mark>c</mark>\n// 格式",
            "highlight_title": "<mark>C</mark>语言数据类型与变量",
            "id": 125,
            "score": 4.7718439102172852,
            "summary": "",
            "title": "C语言数据类型与变量",
            "updated_at": "2026-04-19T14:32:35"
        },
        {
            "author_id": 7,
            "created_at": "2026-04-19T13:42:14",
            "highlight_content": "## 笔记1：<mark>C</mark>语言基础入门与环境搭建\n# ... **<mark>C</mark>语言概述**：编译型高级语言，执行效率高 ... **第一个<mark>C</mark>程序（固定模板）**\n\n哦,",
            "highlight_title": "<mark>C</mark>语言基础入门与环境搭建",
            "id": 124,
            "score": 4.7718439102172852,
            "summary": "",
            "title": "C语言基础入门与环境搭建",
            "updated_at": "2026-04-20T17:49:06"
        }
    ],
    "message": "搜索成功"
}
```

**说明：**
- 搜索基于 Elasticsearch 实现，支持全文检索
- 搜索匹配字段（按权重排序）：title(3x)、tags(2x)、summary(2x)、content(1x)
- 支持模糊匹配（fuzziness: AUTO）
- 返回结果按相关度分数降序排列，分数相同按更新时间降序
- `highlight_title`: 标题中的匹配高亮片段（使用 `<mark></mark>` 标签）
- `highlight_content`: 内容中的匹配高亮片段
- `score`: 匹配相关度分数
- 响应不包含完整 `content` 字段，需要详情请调用 `/api/note/detail`

### 2.7 获取笔记标签列表 GET /api/notes/tags

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数 | 类型  | 必填 | 说明             |
| ---- | ----- | ---- | ---------------- |
| id   | int64 | 是   | 笔记ID（路径参数）|

**响应示例：**
```json
{
  "code": 0,
  "message": "获取标签列表成功",
  "data": [
    {
      "id": 1,
      "name": "Java"
    },
    {
      "id": 2,
      "name": "SpringBoot"
    }
  ]
}
```

### 2.8 AI 生成笔记标签 POST /api/notes/tags/ai

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数 | 类型  | 必填 | 说明             |
| ---- | ----- | ---- | ---------------- |
| id   | int64 | 是   | 笔记ID（路径参数）|

**响应示例：**
```json
{
  "code": 0,
  "message": "AI标签生成并保存成功",
  "data": [
    {
      "name": "Java"
    },
    {
      "name": "SpringBoot"
    },
    {
      "name": "微服务"
    }
  ]
}
```

> 注：该接口会调用 DsService（DeepSeek API）根据笔记内容推荐标签，自动替换该笔记原有标签，并同步更新 Elasticsearch。

### 2.9 浏览笔记 POST /api/note/view

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "note_id": 1
}
```

**请求参数：**

| 参数    | 类型  | 必填 | 说明   |
| ------- | ----- | ---- | ------ |
| note_id | int64 | 是   | 笔记ID |

**响应示例：**
```json
{
  "code": 0,
  "message": "浏览笔记成功",
  "data": {}
}
```

**说明：**
- 记录用户浏览行为到 `user_action` 表
- 笔记 `view_count` +1
- 更新 `user_tag_stat` 中对应标签的浏览统计

### 2.10 点赞笔记 POST /api/note/like

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "note_id": 1
}
```

**请求参数：**

| 参数    | 类型  | 必填 | 说明   |
| ------- | ----- | ---- | ------ |
| note_id | int64 | 是   | 笔记ID |

**响应示例（成功）：**
```json
{
  "code": 0,
  "message": "点赞笔记成功",
  "data": {}
}
```

**响应示例（已点赞）：**
```json
{
  "code": 1,
  "message": "已点赞过该笔记"
}
```

**说明：**
- 写入 `note_like` 表，重复点赞会返回错误
- 记录用户点赞行为到 `user_action` 表
- 笔记 `like_count` +1
- 更新 `user_tag_stat` 中对应标签的点赞统计

### 2.11 收藏笔记 POST /api/note/collect

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "note_id": 1
}
```

**请求参数：**

| 参数    | 类型  | 必填 | 说明   |
| ------- | ----- | ---- | ------ |
| note_id | int64 | 是   | 笔记ID |

**响应示例（成功）：**
```json
{
  "code": 0,
  "message": "收藏笔记成功",
  "data": {}
}
```

**响应示例（已收藏）：**
```json
{
  "code": 1,
  "message": "已收藏过该笔记"
}
```

**说明：**
- 写入 `note_collect` 表，重复收藏会返回错误
- 记录用户收藏行为到 `user_action` 表
- 笔记 `collect_count` +1
- 更新 `user_tag_stat` 中对应标签的收藏统计
- 收藏行为在推荐算法中权重最高

### 2.12 取消点赞 DELETE /api/notes/like

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数    | 类型  | 必填 | 说明   |
| ------- | ----- | ---- | ------ |
| note_id | int64 | 是   | 笔记ID |

> 参数可通过 Query String (`?note_id=1`) 或 JSON Body 传递

**响应示例（成功）：**
```json
{
  "code": 0,
  "message": "取消点赞成功",
  "data": {}
}
```

**响应示例（未点赞）：**
```json
{
  "code": 1,
  "message": "未点赞过该笔记"
}
```

**说明：**
- 删除 `note_like` 表中对应记录
- 笔记 `like_count` -1（最小为0）
- 更新 `user_tag_stat` 中对应标签的点赞统计

### 2.13 取消收藏 DELETE /api/notes/collect

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数    | 类型  | 必填 | 说明   |
| ------- | ----- | ---- | ------ |
| note_id | int64 | 是   | 笔记ID |

> 参数可通过 Query String (`?note_id=1`) 或 JSON Body 传递

**响应示例（成功）：**
```json
{
  "code": 0,
  "message": "取消收藏成功",
  "data": {}
}
```

**响应示例（未收藏）：**
```json
{
  "code": 1,
  "message": "未收藏过该笔记"
}
```

**说明：**
- 删除 `note_collect` 表中对应记录
- 笔记 `collect_count` -1（最小为0）
- 更新 `user_tag_stat` 中对应标签的收藏统计

### 2.14 推荐笔记 GET /api/recommend/notes

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数       | 类型  | 必填 | 说明                                |
| ---------- | ----- | ---- | ----------------------------------- |
| page       | int   | 否   | 页码，默认1                         |
| page_size  | int   | 否   | 每页数量，默认10，最大50            |

**响应示例：**
```json
{
  "code": 0,
  "message": "获取推荐成功",
  "data": [
    {
      "id": 1,
      "title": "推荐笔记标题",
      "summary": "笔记摘要",
      "created_at": "2025-01-01T12:00:00",
      "updated_at": "2025-01-01T12:00:00"
    }
  ]
}
```

**说明：**
- 基于用户画像的个性化推荐，返回公开笔记列表
- **新用户**（最近30天行为数 < 20）：冷启动推荐
    - 综合用户笔记标签（Top3）、最新标签（Top2）、搜索历史（Top2）、全局热门标签（Top3）
- **老用户**（最近30天行为数 ≥ 20）：兴趣模型推荐
    - 基于 `user_tag_stat` 行为分数：`(1×view + 3×like + 7×collect) × e^(-0.1×Δt)`
    - 综合行为标签（Top5）、搜索历史（Top2）、全局热门标签（Top3）
- 兜底逻辑：当标签集合为空或ES无匹配结果时，返回最近创建的公开笔记
- 分页参数：`from = (page-1) * page_size`，`size = page_size`

---

## Elasticsearch 集成说明

### 数据同步机制

系统采用 **"先写数据库，后异步同步ES"** 的策略：

| 操作   | MariaDB                     | Elasticsearch              |
| ------ | --------------------------- | -------------------------- |
| 创建   | 先插入记录，获取主键ID       | 异步索引文档，ID与数据库一致 |
| 更新   | 先更新记录                   | 异步部分更新文档            |
| 删除   | 标记 `is_deleted=1`（软删除）| 异步删除文档                |
| 搜索   | 不参与查询                   | 执行全文检索，返回匹配ID列表 |

### ES索引结构

索引名称：`notes`

文档字段映射：

| 字段        | 类型       | 说明                    |
| ----------- | ---------- | ---------------------- |
| user_id     | long       | 用户ID，用于权限过滤     |
| title       | text       | 标题，带keyword子字段    |
| content     | text       | 内容                    |
| summary     | text       | 摘要                    |
| tags        | keyword    | 标签数组                |
| updated_at  | date       | 更新时间                |

### 搜索流程

1. 客户端发起搜索请求（带关键词）
2. 服务端在ES中执行多字段匹配查询（multi_match）
3. 应用用户ID过滤，确保只能搜索自己的笔记
4. 获取匹配的文档ID列表及高亮片段
5. 根据ID列表从MariaDB批量查询完整记录
6. 按ES返回顺序排序，合并高亮信息
7. 返回给客户端

### 容错处理

- ES连接失败不影响数据库写入操作
- ES操作失败会记录错误日志，不会阻塞主流程
- 搜索时如果ES不可用，返回空结果

---

## 3. 标签与分类 API

| 接口                 | 方法   | 说明      |
| ------------------ | ---- | ------- |
| /api/tags/hot      | GET  | 获取热门标签  |
|                    |      |         |
| /api/folder/create | POST | 创建文件夹   |
| /api/folder/list   | GET  | 获取文件夹列表 |
| /api/folder/update | POST | 更新文件夹   |
| /api/folder/delete | POST | 删除文件夹   |

> **废弃说明：** 以下旧版标签接口已废弃，请使用笔记标签相关接口替代：
> - ~~POST /api/tag/create~~
> - ~~GET /api/tag/list~~
> - ~~POST /api/tag/bind~~
> - ~~POST /api/tag/update~~
> - ~~POST /api/tag/delete~~

**鉴权要求：** 所有接口均需通过 Token 鉴权

### 3.1 获取热门标签 GET /api/tags/hot

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：** 无

**响应示例：**
```json
{
  "code": 0,
  "message": "获取热门标签成功",
  "data": [
    {
      "tag": "Java",
      "count": 42
    },
    {
      "tag": "SpringBoot",
      "count": 35
    }
  ]
}
```

**说明：**
- 基于 Elasticsearch 聚合查询，统计近 7 天内公开笔记的标签使用情况
- 返回 Top 10 热门标签及其出现次数
- 数据来源：`notes` 索引，`tags` 字段（`keyword` 类型）

### 3.6 创建文件夹 POST /api/folder/create

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "name": "技术文档",
  "parent_id": 0
}
```

**请求参数：**

| 参数       | 类型  | 必填 | 说明                     |
| ---------- | ----- | ------ | ------------------------ |
| name       | string | 是     | 文件夹名称               |
| parent_id | int64  | 否     | 父文件夹ID，0表示根文件夹 |

**响应示例：**
```json
{
  "code": 0,
  "message": "创建文件夹成功",
  "data": {
    "folder_id": 1
  }
}
```

### 3.7 更新文件夹 POST /api/folder/update

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "folder_id": 1,
  "name": "新文件夹名",
  "parent_id": 2
}
```

**请求参数：**

| 参数       | 类型   | 必填 | 说明                                 |
| ---------- | ------ | ------ | ------------------------------------ |
| folder_id  | int64  | 是     | 待更新的文件夹ID                        |
| name       | string | 否     | 新的文件夹名称（不更新则不传或为空）       |
| parent_id  | int64  | 否     | 新的父文件夹ID，0表示根文件夹（不更新则不传） |

**响应示例：**
```json
{
  "code": 0,
  "message": "更新文件夹成功",
  "data": {}
}
```

> 注：更新 parent_id 时会防止循环引用（不能将文件夹设为自己或自己的子文件夹的子文件夹）

### 3.8 删除文件夹 POST /api/folder/delete

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "folder_id": 1
}
```

**请求参数：**

| 参数      | 类型  | 必填 | 说明       |
| --------- | ----- | ------ | ---------- |
| folder_id | int64 | 是     | 待删除的文件夹ID |

**响应示例：**
```json
{
  "code": 0,
  "message": "删除文件夹成功",
  "data": {}
}
```

> 注：删除文件夹时会递归删除所有子文件夹，并将这些文件夹下的笔记标记为软删除（is_deleted = 1）

### 3.9 获取文件夹列表 GET /api/folder/list

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数       | 类型   | 必填 | 说明                                   |
| ---------- | ------ | ------ | -------------------------------------- |
| folder_id | int64  | 否     | 父文件夹ID，0表示获取根级文件夹（不传默认为0） |

**响应示例：**
```json
{
  "code": 0,
  "message": "获取文件夹列表成功",
  "data": [
    {
      "id": 1,
      "name": "技术文档",
      "parent_id": 0,
      "created_at": "2025-01-01 12:00:00"
    },
    {
      "id": 2,
      "name": "前端",
      "parent_id": 1,
      "created_at": "2025-01-02 10:00:00"
    }
  ]
}
```

> 注：该接口仅返回指定父文件夹的直接子文件夹，不会递归返回所有子文件夹

---


## 4. 文件管理 API

| 接口                | 方法   | 说明                    | 附加参数           |
| ------------------ | ---- | ---------------------- | ----------------- |
| /api/file/upload   | POST | 上传文件到 MinIO        | 支持 multipart      |
| /api/file/list     | GET  | 获取文件列表             | user_id, note_id    |
| /api/file/delete   | POST | 删除文件（MinIO+数据库） |                    |
| /api/file/status   | GET  | 查询文件上传状态          | file_id             |
| /api/file/info     | GET  | 获取单个文件详情          | file_id             |

**鉴权要求：** 所有文件接口均需通过 Token 鉴权

### 文件存储架构

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   前端上传   │ ──▶  │   Drogon    │ ──▶  │   MariaDB   │
│  (multipart)│      │  (异步处理)  │      │ (元数据记录)  │
└─────────────┘      └──────┬──────┘      └─────────────┘
                            │
                            ▼
                     ┌─────────────┐
                     │    MinIO    │
                     │(对象存储S3)  │
                     │ notes-files │
                     └─────────────┘
```

**上传流程：**
1. 前端通过 multipart/form-data 上传文件
2. 后端接收文件，创建数据库记录（status = processing）
3. 返回 file_id，后台异步上传文件到 MinIO
4. 上传成功：更新 url、status = done
5. 上传失败：更新 status = failed

**MinIO 配置：**
- endpoint: `http://127.0.0.1:9000`
- bucket: `notes-files`
- object key 规则: `userId/yyyy/mm/dd/uuid.ext`

### 4.1 上传文件 POST /api/file/upload

**请求方式：**
Header: `Authorization: Bearer {token}`
Content-Type: `multipart/form-data`

**请求参数：**

| 参数     | 类型   | 必填 | 说明                      |
| -------- | ------ | ---- | ------------------------- |
| file     | file   | 是   | 要上传的文件               |
| note_id  | int64  | 否   | 关联的笔记ID（可选）       |

**请求示例（curl）：**
```bash
curl -X POST http://localhost:8080/api/file/upload \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -F "file=@/path/to/document.pdf" \
  -F "note_id=123"
```

**响应示例（成功，异步上传模式）：**
```json
{
  "code": 0,
  "message": "文件上传已提交",
  "data": {
    "file_id": 1001,
    "status": "processing"
  }
}
```

**说明：**
- 接口采用异步上传模式，立即返回 file_id 和 processing 状态
- 后台自动完成 MinIO 上传，完成后更新状态为 done
- 可通过 `/api/file/status` 接口轮询查询上传状态

### 4.2 获取文件列表 GET /api/file/list

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数     | 类型   | 必填 | 说明                              |
| -------- | ------ | ---- | --------------------------------- |
| user_id  | int64  | 否   | 用户ID（不传则使用当前登录用户）   |
| note_id  | int64  | 否   | 笔记ID过滤（0表示未关联笔记的文件）|
| status   | string | 否   | 状态过滤：processing/done/failed   |

**响应示例：**
```json
{
  "code": 0,
  "message": "获取文件列表成功",
  "data": [
    {
      "id": 1001,
      "user_id": 1,
      "note_id": 123,
      "file_name": "document.pdf",
      "file_type": "application/pdf",
      "file_size": 1048576,
      "file_size_formatted": "1.00 MB",
      "object_key": "1/2026/04/08/a1b2c3d4.pdf",
      "url": "http://127.0.0.1:9000/notes-files/1/2026/04/08/a1b2c3d4.pdf",
      "status": "done",
      "created_at": "2026-04-08 10:30:00",
      "updated_at": "2026-04-08 10:30:05"
    },
    {
      "id": 1002,
      "user_id": 1,
      "note_id": 0,
      "file_name": "image.png",
      "file_type": "image/png",
      "file_size": 204800,
      "file_size_formatted": "200.00 KB",
      "object_key": "1/2026/04/08/e5f6g7h8.png",
      "url": "",
      "status": "processing",
      "created_at": "2026-04-08 10:35:00",
      "updated_at": "2026-04-08 10:35:00"
    }
  ]
}
```

**说明：**
- 默认按 created_at 降序排列
- 返回结果中 file_size_formatted 为格式化后的大小（如 "1.00 MB"）
- status 为 processing 时 url 可能为空，需等待上传完成

### 4.3 删除文件 POST /api/file/delete

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求示例：**
```json
{
  "file_id": 1001
}
```

**请求参数：**

| 参数    | 类型  | 必填 | 说明     |
| ------- | ----- | ---- | -------- |
| file_id | int64 | 是   | 文件ID   |

**响应示例（成功）：**
```json
{
  "code": 0,
  "message": "删除文件成功",
  "data": {
    "minio_deleted": true
  }
}
```

**响应示例（MinIO删除失败但数据库已清理）：**
```json
{
  "code": 0,
  "message": "删除文件成功",
  "data": {
    "minio_deleted": false,
    "minio_error": "Object not found"
  }
}
```

**说明：**
- 删除操作会同时清理 MinIO 存储和数据库记录
- 即使 MinIO 删除失败，数据库记录也会被清理
- 只能删除自己上传的文件

### 4.4 查询文件上传状态 GET /api/file/status

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数    | 类型   | 必填 | 说明   |
| ------- | ------ | ---- | ------ |
| file_id | string | 是   | 文件ID |

**响应示例（processing 状态）：**
```json
{
  "code": 0,
  "message": "获取文件状态成功",
  "data": {
    "file_id": 1002,
    "file_name": "image.png",
    "status": "processing",
    "file_size": 204800
  }
}
```

**响应示例（done 状态）：**
```json
{
  "code": 0,
  "message": "获取文件状态成功",
  "data": {
    "file_id": 1001,
    "file_name": "document.pdf",
    "status": "done",
    "url": "http://127.0.0.1:9000/notes-files/1/2026/04/08/a1b2c3d4.pdf",
    "file_size": 1048576
  }
}
```

**响应示例（failed 状态）：**
```json
{
  "code": 0,
  "message": "获取文件状态成功",
  "data": {
    "file_id": 1003,
    "file_name": "large.zip",
    "status": "failed",
    "file_size": 0
  }
}
```

**说明：**
- 可用于轮询检查异步上传的状态
- status 为 done 时 url 字段才包含有效值
- 只能查询自己上传的文件状态

### 4.5 获取单个文件详情 GET /api/file/info

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数    | 类型   | 必填 | 说明   |
| ------- | ------ | ---- | ------ |
| file_id | string | 是   | 文件ID |

**响应示例：**
```json
{
  "code": 0,
  "message": "获取文件详情成功",
  "data": {
    "id": 1001,
    "user_id": 1,
    "note_id": 123,
    "file_name": "document.pdf",
    "file_type": "application/pdf",
    "file_size": 1048576,
    "file_size_formatted": "1.00 MB",
    "object_key": "1/2026/04/08/a1b2c3d4.pdf",
    "url": "http://127.0.0.1:9000/notes-files/1/2026/04/08/a1b2c3d4.pdf",
    "status": "done",
    "created_at": "2026-04-08 10:30:00",
    "updated_at": "2026-04-08 10:30:05"
  }
}
```

### 4.6 OCR 图片转文本 API

| 接口                 | 方法   | 说明                   |
| -------------------- | ---- | ---------------------- |
| /api/ocr/recognize   | POST | 上传文件进行OCR识别     |
| /api/ocr/status      | GET  | 查询OCR处理状态         |

**OCR处理流程：**
1. 前端通过 multipart/form-data 上传图片/PDF文件
2. 后端接收文件，创建 file_resource 记录（status = processing）
3. 调用第三方OCR API进行文字识别
4. 根据识别结果自动创建笔记（标题使用文件名，内容为识别的markdown文本）
5. 异步上传原始文件到 MinIO
6. 上传成功：更新 file_resource 的 note_id、url、status = done
7. 上传失败：更新 status = failed

**支持的文件格式：**
- 图片：jpg, jpeg, png, bmp, webp
- 文档：pdf

#### 4.6.1 提交OCR任务 POST /api/ocr/recognize

**请求方式：**
Header: `Authorization: Bearer {token}`
Content-Type: `multipart/form-data`

**请求参数：**

| 参数   | 类型   | 必填 | 说明                      |
| ------ | ------ | ---- | ------------------------- |
| file   | file   | 是   | 要识别的文件（图片或PDF） |

**请求示例（curl）：**
```bash
curl -X POST http://localhost:8080/api/ocr/recognize \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..." \
  -F "file=@/path/to/document.pdf"
```

**响应示例（成功，异步处理模式）：**
```json
{
  "code": 0,
  "message": "OCR任务已提交",
  "data": {
    "file_id": 1001,
    "status": "processing"
  }
}
```

**响应示例（不支持的文件类型）：**
```json
{
  "code": 1,
  "message": "不支持的文件类型，仅支持: JPG, PNG, BMP, WEBP, PDF"
}
```

**说明：**
- 接口采用异步处理模式，立即返回 file_id 和 processing 状态
- 后台自动完成 OCR 识别、笔记创建和 MinIO 上传
- 可通过 `/api/ocr/status` 接口轮询查询处理状态
- 识别成功后，可通过返回的 note_id 获取创建的笔记

#### 4.6.2 查询OCR状态 GET /api/ocr/status

**请求方式：**
Header: `Authorization: Bearer {token}`

**请求参数：**

| 参数    | 类型   | 必填 | 说明   |
| ------- | ------ | ---- | ------ |
| file_id | string | 是   | 文件ID |

**响应示例（processing 状态）：**
```json
{
  "code": 0,
  "message": "获取OCR状态成功",
  "data": {
    "file_id": 1001,
    "file_name": "document.pdf",
    "status": "processing",
    "file_size": 1048576,
    "file_size_formatted": "1.00 MB",
    "created_at": "2026-04-08 10:30:00",
    "updated_at": "2026-04-08 10:30:00"
  }
}
```

**响应示例（done 状态，处理完成）：**
```json
{
  "code": 0,
  "message": "获取OCR状态成功",
  "data": {
    "file_id": 1001,
    "file_name": "document.pdf",
    "status": "done",
    "note_id": 123,
    "url": "http://127.0.0.1:9000/notes-files/1/2026/04/08/a1b2c3d4.pdf",
    "file_size": 1048576,
    "file_size_formatted": "1.00 MB",
    "created_at": "2026-04-08 10:30:00",
    "updated_at": "2026-04-08 10:30:15"
  }
}
```

**响应示例（failed 状态，处理失败）：**
```json
{
  "code": 0,
  "message": "获取OCR状态成功",
  "data": {
    "file_id": 1002,
    "file_name": "corrupted.pdf",
    "status": "failed",
    "file_size": 0,
    "file_size_formatted": "0.00 B",
    "created_at": "2026-04-08 10:35:00",
    "updated_at": "2026-04-08 10:35:05"
  }
}
```

**说明：**
- 可用于轮询检查异步OCR处理的状态
- `status` 为 `done` 时，响应中包含 `note_id`，表示笔记已成功创建
- `status` 为 `failed` 时，表示OCR识别或后续处理失败
- 只有文件所有者可以查询该文件的状态


## 5. 同步 API（双端重点）

| 接口             | 方法   | 说明     |
| -------------- | ---- | ------ |
| /api/sync/pull | GET  | 拉取最新数据 |
| /api/sync/push | POST | 上传变更   |

参数示例：

```json
{
  "last_sync_time": "2025-01-01 12:00:00"
}
```

---


## 错误码说明

| code | message             | 说明               |
| ----- | ------------------- | ------------------ |
| 0     | success            | 请求成功           |
| 1     | Token无效或已过期    | 鉴权失败           |
| 1     | 请求参数错误         | 参数格式或缺失       |
| 1     | 标题和内容不能为空     | 创建/更新笔记参数错误 |
| 1     | 笔记ID无效         | 笔记ID不存在       |
| 1     | 无权访问该笔记      | 用户无权操作该笔记   |
| 1     | 标签名称不能为空     | 创建标签参数错误     |
| 1     | 部分标签不存在或无权访问 | 绑定标签时验证失败 |
| 1     | 文件夹名称不能为空     | 创建文件夹参数错误   |

> 注意：所有非0的 code 均为失败情况，具体错误信息通过 `message` 字段返回
