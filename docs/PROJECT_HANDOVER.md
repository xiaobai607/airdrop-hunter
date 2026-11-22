# Airdrop Hunter 项目交接说明

> 交接快照：2026-11-22  
> 适用对象：项目接手开发者、技术买家、维护团队  
> 当前状态：多语言系统代码已完成，最终 Android 单元测试与 Debug APK 构建已通过；尚未完成真实设备验收，也不是可直接上架的正式 Release。

## 1. 项目概况

**Airdrop Hunter（空投猎人）** 是一款 Android 优先的 Web3 空投管理应用，目标是把空投发现、项目与任务管理、多钱包追踪、Gas / 收益记账、社区公开统计和 DApp 浏览器集中在一个手机端工具中。

- 仓库：<https://github.com/xiaobai607/airdrop-hunter>
- 应用包名：`com.airdrophunter.app`
- 当前应用版本：`0.10.0`（versionCode `10`）
- 当前用户指定的开发主线：`feature/home-direct-manual-accounting-ui-base`
- 当前代码快照：`8d72e5bfb6e919b668478944db3933573cfc9002`
- 最终构建触发提交：`30d3411c2e2457702fc90ed503eee9aa367fdd70`
- GitHub Actions：<https://github.com/xiaobai607/airdrop-hunter/actions>
- 产品需求台账：<https://github.com/xiaobai607/airdrop-hunter/blob/feature/home-direct-manual-accounting-ui-base/docs/PRODUCT_REQUIREMENTS.md>
- UI 规范与重构台账：<https://github.com/xiaobai607/airdrop-hunter/blob/feature/home-direct-manual-accounting-ui-base/docs/UI_REFACTOR_PROGRESS.md>

**分支特别说明：** 上述 feature 分支是用户指定的当前开发主线；它不是 GitHub 默认 `main`。2026-11-22 检查时，默认 `main` 仍停留在 `ce846e226a5064c7e5d04c1bde252e9c0bdaf62d`，不能把默认分支误认为当前最新版本。未经项目所有者明确同意，不要自动合并到 `main`，也不要重新创建功能分支。旧的 `feature/app-language-system` 与当前开发主线已经分叉，不要整分支合并或用它覆盖当前代码。

PR #135 当前为 closed / draft 状态，不是当前交付入口：<https://github.com/xiaobai607/airdrop-hunter/pull/135>。

## 2. 当前功能范围

### Android 应用

1. **首页与行情**
   - 首页仪表盘、市场概览、快捷入口。
   - 行情搜索与币种详情，带本地缓存和状态提示。

2. **空投发现与空投库**
   - 空投活动目录、官方公告入口、项目线索与详情。
   - 数据源健康状态面板，可查看启用状态、最近成功/失败、记录数量和简要错误。
   - Binance 官方公告详情展示原始文章，不自动编造奖励、时间或资格条件。

3. **项目与待办**
   - 个人项目管理、项目详情、任务与进度。
   - 项目数据、活动信息与用户自己的记录分层处理。

4. **钱包管理**
   - 多钱包地址管理、钱包详情、交易活动、发送/接收记录和资产信息。
   - WalletConnect / Reown AppKit 外部钱包连接流程。
   - 应用不托管钱包，不收集助记词或私钥，不代替用户签名。

5. **浏览器与 DApp**
   - Android WebView 与腾讯 X5 浏览器页面。
   - X5 不可用时保留 Android WebView 路径。
   - DApp 来源权限、WalletConnect 请求和安全提示。
   - 浏览器记账工具栏可将浏览器交互与用户账单关联。

6. **记账与收益**
   - 手动记账、Gas 费用、收益、账单历史、待确认账单。
   - 预估 Gas / 预估收益与实际 Gas / 实际收益分开记录。
   - 支持交易哈希核验流程；不要把预估值冒充实际收益。

7. **微社区**
   - 社区项目目录、项目公开统计、收藏和贡献等级展示。
   - 公共贡献同步与私人个人账本不是同一数据层，不能混为一谈。

8. **其他**
   - TRON 能量助手。
   - 个人中心、设置、安全与通知相关入口。

### 本轮完成的多语言系统

- 设置页新增语言选择：**跟随系统 / 简体中文 / English**。
- Android 13 及以上使用系统 per-app locale（`LocaleManager.applicationLocales`），也能读取用户在 Android 系统设置中单独指定的应用语言。
- Android 8–12 使用应用偏好保存语言，并通过 `AppLocale.wrap()` 包装应用和 Activity Context。
- 已覆盖 Application、MainActivity、普通浏览器、X5 浏览器、WalletConnect 和 WalletConnect Redirect Activity。
- 系统语言配置文件：`android/app/src/main/res/xml/locales_config.xml`。
- 英文资源：`android/app/src/main/res/values/strings.xml`。
- 简体中文资源：`android/app/src/main/res/values-zh/strings.xml`。
- 英文与简体中文各有 1,239 个字符串资源键；已检查键名配对、重复项和本轮修改页面的代码引用，没有发现缺失或重复。
- 仅声明 English 与 Simplified Chinese；没有宣称其他语言已支持。

## 3. 技术架构

### Android

| 项目 | 当前配置 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose、Material 3 |
| 最低 Android | API 26 / Android 8.0 |
| compileSdk / targetSdk | 35 / 35 |
| Java | 17 |
| Gradle | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| 本地数据 | Room、DataStore / SharedPreferences |
| 后台任务 | WorkManager |
| 网络 | Retrofit、OkHttp、Gson |
| 钱包连接 | Reown Android AppKit 1.3.3 |
| 浏览器 | AndroidX WebKit、Tencent TBS SDK 44286 |

Android 模块主要位于 `android/app/src/main/java/com/airdrophunter/app/`。应用依赖通过 `AppContainer` 组合，不使用大型 DI 框架。

### Backend

- Python 3.11+
- FastAPI、Uvicorn
- SQLAlchemy Async、Alembic
- PostgreSQL 16
- JWT、pwdlib / Argon2
- slowapi 请求限流
- boto3（对象存储相关能力）

后端入口：`backend/app/main.py`。应用启动时执行 `alembic upgrade head`。数据库迁移位于 `backend/app/migrations/versions/`，当前仓库包含 `0001_bootstrap` 至 `0026_discovery_observations` 等迁移；已部署迁移不得直接改写，应新增版本。

### 数据源

当前公开空投导入器按四类来源隔离：

- Binance 官方 CMS 公告；
- CoinGecko Demo Airdrop 分类（需要可选 Demo API Key）；
- 经过人工审核、固定完整 commit SHA 且记录许可证与署名信息的 GitHub Raw 名单；
- DexScreener 新池市场线索。

CoinGecko、GitHub Raw 和 DexScreener 只能作为线索，不能直接当作已确认空投。Binance 公告虽为官方来源，应用也不推断未公开的奖励或参与规则。详细规则见 `backend/AIRDROP_HUB.md`。

## 4. 构建与交付

### 本地 Android 构建

需要 JDK 17、Android SDK 和 Gradle 8.9。进入 `android/` 后执行：

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

APK 输出路径：

```
android/app/build/outputs/apk/debug/app-debug.apk
```

当前工作流只构建 Debug APK；在已检查的 Gradle 配置中没有正式 Release 签名配置，也没有 Play Store 发布流水线。正式发行前必须由接手方建立并安全保管 Release keystore。

### GitHub Actions

工作流：`.github/workflows/android-build.yml`

- Android 文件变化：运行 Android 单元测试。
- 后端文件变化：运行 PostgreSQL 服务下的后端 pytest。
- 推送变更 `android/BUILD_APK_NOW`：运行 Android 单元测试和 `assembleDebug`，并上传 APK artifact。
- Artifact 名称：`airdrop-hunter-debug-apk`。
- APK 文件：`app-debug.apk`。

最终多语言 APK 验证运行 #2790：<https://github.com/xiaobai607/airdrop-hunter/actions/runs/45023235501>。该运行已成功完成 Android 单元测试与 Debug APK 构建，并成功上传 Artifact。

### Backend 本地运行

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
alembic upgrade head
pytest
uvicorn app.main:app --reload
```

健康检查：`GET /health`；就绪检查：`GET /health/ready`。

## 5. 配置、部署与密钥交接

### Android / GitHub Actions

- `API_BASE_URL`：Android API 地址覆盖项；未设置时 Gradle 默认使用 `https://airdrop-hunter-z6qn.onrender.com/`。
- `COMMUNITY_SNAPSHOT_BASE_URL`：可选社区快照 CDN 地址，必须是 HTTPS。
- `REOWN_PROJECT_ID`：当前写在 `android/app/build.gradle.kts` 的 BuildConfig 字段中。它不是钱包私钥，但正式接手时建议使用买家/新维护者自己拥有的 Reown 项目配置。

### Backend 环境变量

参考 `backend/.env.example`：

- 必需：`DATABASE_URL`、`JWT_SECRET_KEY`。
- 常用：`JWT_EXPIRE_MINUTES`。
- 可选：`ETHERSCAN_API_KEY`、`COINGECKO_DEMO_API_KEY`、`DEXSCREENER_SEARCH_QUERIES`、`AIRDROP_GITHUB_RAW_SOURCE_APPROVALS`。
- `AIRDROP_DISCOVERY_SYNC_ENABLED` 示例默认是 `false`；公开发现定时同步未启用前，不要声称它已在生产环境自动运行。
- Render 配置文件：`render.yaml`，服务计划为 Free，健康检查路径为 `/health/ready`。

**不要把任何真实密钥、数据库密码、JWT Secret、访问令牌或钱包信息写进本文件、README 或 Git 仓库。** 转交时应在 GitHub / Render 的权限系统中单独转移并轮换凭据。

## 6. 重要已知限制与产品缺口

### A. 私人个人账本云同步尚未完成（最高优先级交接事项）

当前代码已存在后端用户级 `/api/personal-ledger` 查询、写入和删除接口，但 Android 当前版本明确关闭了私人云同步：

- `CurrentReleaseDataPolicy.PRIVATE_PERSONAL_CLOUD_SYNC_ENABLED = false`；
- `ApiClient` 在此开关关闭时移除 Authorization 请求头；
- `PersonalLedgerSyncWorker` 会提前退出；
- `PersonalLedgerSyncScheduler.enqueue/ensurePeriodic` 当前是空操作；
- 因此当前应用不能承诺私人账本云端备份、跨设备同步或恢复。

**产品方向必须与旧文档区分：** 用户后续明确要求详细个人花费/收益账本上传云端并仅本人可见，以支持跨设备访问与恢复；游客和登录用户的合格 Gas / 收益 / 项目参与数据则进入匿名化公共社区统计。两者必须分层实现。最新更正已写入 `docs/PRODUCT_REQUIREMENTS.md` 顶部的 2026-11-22 addendum。旧的 local-only 段落属于历史实现说明，不能当成最终产品方向。

公共贡献同步是独立机制：`CommunityContributionSyncWorker` 会为待同步记录使用匿名 contributor ID，并提交符合条件的网络、Gas、收益、资产和交易核验信息；这不等于私人账本云同步已完成。接手者需要用游客和登录场景分别做端到端验证。

### B. 账号与恢复

- 后端 `app.state.allow_registration = False`，注册接口当前关闭。
- 登录接口代码存在，但当前私人云同步开关关闭，不能据此宣称账户级数据同步已经可用。
- 私人云端恢复、设备迁移和完整备份恢复仍是待完成工作。

### C. 空投数据与生产运行

- `AIRDROP_DISCOVERY_SYNC_ENABLED` 示例默认关闭。
- CoinGecko Demo Key、GitHub Raw 人工审核名单均为可选配置。
- GitHub Raw 许可证/署名的合法性仍需运营者人工审查；代码只校验配置形状和固定 commit，不替代法律审核。
- `TRUSTED_AIRDROP_PROVIDER_LABELS` 当前为空；不能把第三方市场线索自动升级成确认活动。
- Render Free 服务、PostgreSQL、备份恢复、定时任务、日志监控和生产数据质量仍需接手方实测。仓库里有配置文件不代表线上服务当前一定可用。

### D. 发布与设备验收

- CI 构建成功不等于真实手机验收完成。
- 必须至少测试 Android 8–12 的旧版 Context 语言切换、Android 13+ 系统应用语言切换，以及首页、待办、钱包、普通浏览器、X5、WalletConnect、社区和记账页面。
- 还需检查中英文下弹窗、Toast、错误状态、空状态、无障碍描述、长文本、窄屏和深色主题。
- 正式上架前需完成 Release 签名、隐私政策、数据处理说明、第三方 SDK 合规审查和恢复演练。

## 7. 最终验收状态（生成交接时更新）

- 多语言代码：已提交到用户指定开发主线。
- 中英文资源：已做键名配对、重复项和修改页面引用检查。
- 最终 Android 单元测试 + Debug APK：**已通过**（Run #2790，Gradle `:app:testDebugUnitTest :app:assembleDebug` 成功）。
- Artifact：`airdrop-hunter-debug-apk`，约 45.3 MB；SHA-256：`4c7e326ea92b4d658381ab56800089796c835dba952b388636314223d107d866`。
- Artifact 下载：<https://github.com/xiaobai607/airdrop-hunter/actions/runs/45023235501>（进入 Artifacts 区域下载）；Artifact ID：`14754780724`，有效期至 2027-02-20。
- 本次后端测试：未触发（本轮仅修改 Android 与文档，不代表后端测试通过）。
- 真实 Android 设备验收：未完成。
- Release 签名 / 商店发布：未完成。
- 私人账本云同步与跨设备恢复：未完成，见第 6A 节。

最终 CI 结论以 Run #2790 为准：Android 单元测试、Debug APK 构建、Artifact 上传均通过。真实设备验收和 Release 发布仍未完成。

## 8. 接手方建议顺序

1. 先在 GitHub Actions 打开最终 Run，确认 Android 单元测试、Debug APK 构建和 Artifact 上传全部成功。
2. 下载 `airdrop-hunter-debug-apk`，在真实 Android 手机上检查中英文切换与所有核心页面。
3. 阅读 `docs/PRODUCT_REQUIREMENTS.md` 顶部最新 addendum，先解决私人账本云同步与公共匿名统计的数据边界。
4. 检查注册/登录、用户隔离、Token 传递、云端备份/恢复和游客贡献路径。
5. 验证 Render 服务、PostgreSQL、Alembic 迁移、环境变量、数据源授权和备份恢复。
6. 建立 Release 签名和正式发布流水线，再做隐私、安全和第三方 SDK 审查。

## 9. 交接清单

- [ ] GitHub 仓库所有权或维护权限已转移。
- [ ] 当前用户指定开发主线已交接；默认 `main` 是否合并由项目所有者另行批准。
- [ ] GitHub Actions Variables / Secrets 已安全转移并检查。
- [ ] Render 服务、PostgreSQL 数据库、备份和部署权限已转移。
- [ ] JWT Secret、API Keys、Reown 项目配置已轮换或转移。
- [ ] Release keystore 由新所有者创建并离线安全保管。
- [ ] 私人账本云同步、匿名公共贡献、注册登录和数据恢复的差异已由接手方确认。
- [ ] 中英文真机验收完成。
- [ ] 隐私政策、数据来源许可、SDK 合规和正式发布流程已完成。

---

**交接原则：** 本文记录的是仓库代码与配置的核验结果，不把计划、接口存在、CI 触发或本地缓存描述成已经上线的产品能力。所有未实测的生产能力都应继续标记为待验证。
