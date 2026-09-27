# Goaltree

从主目标逐层拆解到可执行任务的目标规划应用。每个主目标拥有独立的目标树画布。

## Windows 应用

运行 `./scripts/build-windows-app.ps1` 可生成 Windows 便携版。双击生成目录中的 `Goaltree.exe` 启动；移动应用时请复制整个 `Goaltree-win32-x64` 文件夹。应用自带 Electron 窗口和 Java 运行环境，无需安装或打开浏览器；本机 MySQL 仍需运行。生成的安装包和压缩包保留在本机 `release/`，不提交到源码仓库。

删除目标后可在页面底部提示中立即“撤销”；关闭提示或重启应用后，仍可从侧边栏或画布工具栏打开“回收站”恢复。整棵树按一次删除操作恢复，先前单独删除的子目标仍留在回收站；如果父目标也已删除，需要先恢复父目标。

主目标在左侧列表中使用上下箭头排序；子目标打开卡片详情后使用“上移”“下移”调整同级顺序。排序保存到数据库，刷新或重启后仍保留，各主目标的目标树互不影响。

目标树节点采用轻量导线标题样式，默认只显示标题、完成状态和子目标折叠数量。悬停或聚焦节点时可操作，触屏可点击节点后在详情弹窗中添加、编辑、完成或删除目标。新建和编辑目标不再提交截止日期；旧记录的内部日期字段仍保留用于数据兼容。

目标树工具栏的“下一步”按钮打开当前主目标的行动抽屉。抽屉根据目标树实时列出未完成的末级目标；点击一项会自动展开其上级分支并在画布中定位，切换主目标后列表随之更新。

应用继续使用本机 `goalflow` 数据库。启动前请设置 `DB_PASSWORD`；如果使用其他账号或地址，还需设置 `DB_USERNAME` 和 `DB_URL`。数据库凭据不应提交到仓库。应用运行日志位于 `%APPDATA%\Goaltree\server.log`。

重新打包：在项目根目录运行 `./scripts/build-windows-app.ps1`。脚本会构建前端和后端，并生成包含 Electron、Java 和项目文件的 Goaltree 应用目录与 ZIP。

## 技术栈

- 前端：Vue 3、Vite、TypeScript、Pinia、Vue Router、Tailwind CSS；
- 后端：Spring Boot 4.1、Java 17、Spring Data JPA、Flyway、OpenAPI；
- 数据库：本机 MySQL 8.0+；
- 测试：Vitest、Playwright、JUnit 5。

## 本地启动

```powershell
# 1. 启动本机 MySQL 服务（默认监听 3306）
Start-Service MySQL80

# 按本机数据库账号设置 DB_USERNAME 和 DB_PASSWORD 环境变量（不要提交密码）。
# 连接地址默认为 jdbc:mysql://localhost:3306/goalflow，可用 DB_URL 覆盖。
# 首次运行需确保 goalflow 数据库已存在，Flyway 会执行现有迁移。

# 2. 后端
cd backend
.\mvnw.cmd spring-boot:run

# 3. 新终端启动前端
cd frontend
pnpm install
pnpm dev
```

访问地址：

- 前端：<http://localhost:5173>
- 本机也可使用 <http://127.0.0.1:5173/goals>；两种地址都允许目标的保存和删除。
- 后端健康检查：<http://localhost:8080/api/system/health>
- Swagger UI：<http://localhost:8080/docs>

目标页使用可隐藏的主目标侧边栏和全窗口目标树画布。侧边栏用于新建、切换和删除主目标；节点可添加子目标、编辑、完成或删除。画布顶部可按标题搜索所有主目标及子目标，选择结果后会切换到所属目标树、展开其上级分支、居中并高亮目标；`Ctrl+K` 可聚焦搜索框。分组节点可展开或收起所有后代，各主目标分别保存收起状态和画布视图，进度始终按完整目标树计算。删除主目标或分组节点会在确认后软删除整棵子树。已有详情、里程碑、完成标准、乐观锁和修改历史继续保留。目标层级最多四层；有子节点的目标作为分组，末级目标作为可执行目标。内部目录、数据库和 API 标识沿用 Goalflow 以保持兼容。

点击目标卡片的非操作区域可打开目标详情弹窗，在其中添加、编辑、删除该目标的多个关联链接。链接只接受 `http` 或 `https` 地址，桌面版会用系统默认浏览器打开。备注输入及显示已移除；旧备注数据保留在数据库中，不会影响已有目标。

主目标只能通过侧边栏“新建主目标”创建，对应 `POST /api/goals/roots`，请求不能包含 `parentId`。子目标只能从树节点或详情页“添加子目标”创建，对应 `POST /api/goals`，必须指定有效的 `parentId`。编辑子目标时仅能在所属主目标树内调整父节点。

本机 MySQL 与原 Docker 数据卷是独立存储；切换连接不会自动迁移 Docker 数据。首次创建空库时，Flyway 会按现有迁移初始化结构和示例记录。

## 验证命令

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
pnpm lint
pnpm type-check
pnpm test:unit --run
pnpm build
pnpm test:e2e
```

开发阶段与验收门槛见 `docs/IMPLEMENTATION_PLAN.md`，当前状态见 `docs/IMPLEMENTATION_STATUS.md`。
