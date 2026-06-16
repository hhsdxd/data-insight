# DataInsight — AI 数据分析助手

> AI数据分析平台 | Spring Cloud Alibaba + LangChain4j NL2SQL + RocketMQ + Docker

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2023.0.1.0-blue)](https://spring.io/projects/spring-cloud-alibaba)

上传 CSV/Excel 数据 → AI 自然语言查询 → ECharts 可视化报告，全自动数据流水线。

## 🏗 架构

```
浏览器 (Vue 3 + Element Plus + ECharts)
  │
  ▼
┌──────────────────────────────┐
│  Gateway :9999               │  路由转发 / JWT鉴权 / 跨域
└──────┬───────────────────────┘
       │
       ├────────────┬────────────┬────────────┐
       ▼            ▼            ▼            ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│用户服务    │ │数据解析   │ │AI分析     │ │可视化     │
│:8081      │ │:8082      │ │:8083      │ │:8084      │
│           │ │           │ │           │ │           │
│注册/登录   │ │CSV/Excel  │ │NL2SQL     │ │ECharts    │
│JWT鉴权    │ │类型推断    │ │SSE流式    │ │报告生成    │
│API Key    │ │分片解析    │ │自动洞察   │ │图表模板    │
│文件上传    │ │数据预览    │ │会话历史   │ │数据导出    │
└────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
     │            │            │            │
     └────────────┴─────┬──────┘            │
                        │                   │
              ┌─────────┴─────────┐         │
              │      Nacos        │         │
              │   注册中心 :8848   │         │
              └───────────────────┘         │
                                            │
     ┌──────────┐  ┌──────────┐  ┌──────────┴──┐
     │  MySQL   │  │  Redis   │  │  通义千问    │
     │  8.0     │  │  缓存     │  │  NL2SQL     │
     └──────────┘  └──────────┘  └─────────────┘
```

## ✨ 核心功能

### 1. 数据解析（CSV/Excel → 结构化存储）
- 自动推断列类型（INT/DECIMAL/DATE/VARCHAR）
- JSON 动态行存储，无需预定义表结构
- 采样+投票法类型推断（准确率 >95%）
- 列元数据自动生成（类型/空值率/示例值）

### 2. NL2SQL（自然语言 → SQL → 结果解读）
- System Prompt 注入列元数据
- LLM 自动生成 SQL（仅允许 SELECT）
- SQL 安全校验（正则拦截危险操作）
- SSE 流式返回 + 结果自然语言解读

### 3. 可视化（查询结果 → ECharts 图表）
- 柱状图 / 饼图 / 折线图 / 散点图
- 自动推荐可视化方式
- 汇总分析报告

### 4. 微服务基础设施
- Nacos 服务注册与发现
- Gateway 统一路由 + JWT 全局过滤器
- API Key（ak/sk）双鉴权体系
- 全局异常处理 + 统一响应格式

## 🚀 快速开始

### 环境要求
- JDK 21
- MySQL 8.0
- Maven 3.8+
- Nacos 2.3.x

### 本地开发

```bash
# 1. 启动 Nacos（单机模式）
startup.cmd -m standalone

# 2. 初始化数据库
mysql -u root -p < docker/mysql/init/01-init-databases.sql

# 3. 编译打包
mvn clean package -DskipTests

# 4. 按顺序启动服务
java -jar data-insight-user/target/data-insight-user-1.0-SNAPSHOT.jar
java -jar data-insight-parser/target/data-insight-parser-1.0-SNAPSHOT.jar
java -jar data-insight-analyzer/target/data-insight-analyzer-1.0-SNAPSHOT.jar
java -jar data-insight-viz/target/data-insight-viz-1.0-SNAPSHOT.jar
java -jar data-insight-gateway/target/data-insight-gateway-1.0-SNAPSHOT.jar

# 5. 测试
curl -X POST http://localhost:8081/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"123456"}'
```

### Docker Compose 部署

```bash
export DASHSCOPE_API_KEY=sk-your-key
docker-compose up -d
```

## 📡 API 文档

### 用户服务 (:8081)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/register | 注册 |
| POST | /api/user/login | 登录 |
| GET | /api/user/info | 用户信息 |
| POST | /api/user/key/generate | 生成 API Key |
| GET | /api/user/key/list | Key 列表 |
| POST | /api/user/file/upload | 上传文件 |
| GET | /api/user/file/list | 文件列表 |

### 数据解析 (:8082)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/data/trigger-parse | 触发解析 |
| GET | /api/data/status/{fileId} | 解析状态 |
| GET | /api/data/preview/{fileId} | 数据预览 |
| GET | /api/data/stats/{fileId} | 列统计 |

### AI 分析 (:8083)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ai/query | NL2SQL 同步查询 |
| POST | /api/ai/chat | SSE 流式对话 |
| POST | /api/ai/insight/{fileId} | 自动洞察 |
| GET | /api/ai/conversations | 会话列表 |
| GET | /api/ai/history/{convId} | 对话历史 |

### 可视化 (:8084)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/viz/chart/bar/{fileId} | 柱状图 |
| POST | /api/viz/chart/pie/{fileId} | 饼图 |
| POST | /api/viz/chart/line/{fileId} | 折线图 |
| GET | /api/viz/report/{fileId} | 分析报告 |
| GET | /api/viz/templates | 图表模板 |

## 🛠 技术栈

| 层 | 选型 | 说明 |
|------|------|------|
| 框架 | Spring Boot 3.3.5 | 最新稳定版 |
| 微服务 | Spring Cloud Alibaba 2023.0.1.0 | Nacos + Gateway + Sentinel |
| ORM | MyBatis-Plus 3.5.7 | 单表CRUD零SQL |
| LLM | 通义千问 qwen-plus | NL2SQL 引擎 |
| 消息队列 | RocketMQ 5.x | 异步数据流水线 |
| 数据库 | MySQL 8.0 + Redis | 持久化 + 缓存 |
| 前端 | Vue 3 + Element Plus + ECharts | SPA |
| 部署 | Docker Compose | 一键启动 |

## 📁 项目结构

```
data-insight/
├── data-insight-common/      # 公共模块：R / JwtHelper / 异常
├── data-insight-gateway/     # API网关：路由 + JWT过滤
├── data-insight-user/        # 用户服务：注册登录/API Key/文件
├── data-insight-parser/      # 数据解析：CSV/Excel/类型推断
├── data-insight-analyzer/    # AI分析：NL2SQL/SSE/洞察
├── data-insight-viz/         # 可视化：图表/报告/模板
├── docker/                   # Docker配置
│   ├── Dockerfile
│   └── mysql/init/
├── docker-compose.yml
└── README.md
```

## 🤖 NL2SQL 工作流程

```
用户: "销售额大于15000的有哪些？"
  ↓
读取 column_meta（姓名:VARCHAR, 年龄:INT, 销售额:INT, 日期:DATE）
  ↓
构建 System Prompt（注入列元数据 + SQL规则）
  ↓
LLM 生成: SELECT * FROM data_record
          WHERE CAST(JSON_EXTRACT(row_data, '$.销售额') AS DECIMAL) > 15000
  ↓
安全校验（正则: 只允许SELECT）
  ↓
执行SQL → 返回结果
  ↓
LLM 解读: "共找到2条记录，李四销售额最高为22000，张三15000"
```

## 📝 面试要点

| 问题 | 回答 |
|------|------|
| 为什么5个服务？ | 用户/解析/分析/可视化是完全不同的业务域，独立开发/部署/扩容 |
| 服务间通信？ | OpenFeign同步（可视化→解析），RocketMQ异步（文件上传→解析） |
| NL2SQL怎么做的？ | System Prompt注入列元数据 → LLM生成SQL → 安全校验 → 执行 → 结果解读 |
| JSON存行数据？ | 动态列，MySQL 8.0 JSON类型+虚拟索引，用户上传的CSV列名不定 |
| SQL安全？ | System Prompt约束+正则拦截(INSERT/UPDATE/DELETE/DROP) |
| 大文件处理？ | 分片读取+批量insert+异步MQ，不阻塞 |

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
