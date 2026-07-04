# 大花喵 dhmtv

TV / 手机 / 平板三分支独立维护，GitHub 发版 + 热更新 OTA。

---

## 分支结构

| 分支 | 用途 | 应用名 | applicationId |
|------|------|--------|---------------|
| **master** | 电视专版（主分支） | 大花喵TV | `com.github.tvbox.osc` |
| **phone** | 手机专版 | 大花喵手机 | `com.github.tvbox.osc.phone` |
| **tablet** | 平板专版 | 大花喵平板 | `com.github.tvbox.osc.tablet` |

远程仓库（唯一）：https://github.com/stlxing1987/dhmtv

---

## 一、本地打包

### 1. 切换分支

```bash
git checkout master   # TV 版
git checkout phone    # 手机版
git checkout tablet   # 平板版
```

### 2. 执行构建

```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

### 3. 输出位置

| 文件 | 路径 |
|------|------|
| 安装包 | `app/build/outputs/apk/debug/app-debug.apk` |

> 所有正式发版 APK 均使用 `app/dhmtv.jks` 固定签名（debug / release 共用），保证热更新覆盖安装时签名一致。

---

## 二、发布新版本（推荐：GitHub Actions 自动发版）

### 整体流程

```
修改 app/build.gradle 版本号
        ↓
git commit + push 到对应分支
        ↓
GitHub Actions 自动构建 APK
        ↓
创建 GitHub Release（上传 APK）
        ↓
自动生成 update-*.json 并 commit 回仓库
        ↓
已安装 App 拉取 update.json → 提示更新 → 下载安装
```

### 1. 修改版本号

编辑 `app/build.gradle`：

```gradle
versionCode 12        // 必须递增，App 靠它判断是否有新版本
versionName '1.0.12'  // 显示给用户的版本名
```

| 分支 | Release Tag 示例 | APK 文件名 |
|------|------------------|------------|
| master | `v1.0.12-tv` | `app-tv.apk` |
| phone | `v1.0.12-phone` | `app-phone.apk` |
| tablet | `v1.0.12-tablet` | `app-tablet.apk` |

### 2. 提交并推送

```bash
git add app/build.gradle
git commit -m "release: TV 1.0.12 某某更新说明"
git push origin master
```

> **触发条件**：只有 `app/build.gradle` 发生变更并 push 到 `master` / `phone` / `tablet` 时，才会触发 `.github/workflows/release.yml`。

### 3. CI 自动做了什么

Workflow 文件：`.github/workflows/release.yml`

| 步骤 | 说明 |
|------|------|
| 识别分支 | master→TV，phone→手机，tablet→平板 |
| 构建 | `./gradlew assembleDebug`（固定签名） |
| 上传 Release | 创建/更新 Tag，上传 APK 到 [Releases](https://github.com/stlxing1987/dhmtv/releases) |
| 生成热更新配置 | 写入 `docs/update-tv.json` 等 |
| 回写仓库 | 自动 commit update json（带 `[skip ci]` 避免循环触发） |

### 4. 查看发版结果

- **Actions 日志**：https://github.com/stlxing1987/dhmtv/actions
- **Releases 页面**：https://github.com/stlxing1987/dhmtv/releases
- **热更新配置**（国内加速）：
  - TV：https://gh-proxy.com/https://raw.githubusercontent.com/stlxing1987/dhmtv/master/docs/update-tv.json
  - 手机：https://gh-proxy.com/https://raw.githubusercontent.com/stlxing1987/dhmtv/phone/docs/update-phone.json
  - 平板：https://gh-proxy.com/https://raw.githubusercontent.com/stlxing1987/dhmtv/tablet/docs/update-tablet.json

### 5. 手动触发（不改版本号时）

GitHub 网页 → **Actions** → **Release APK** → **Run workflow** → 选择分支 → Run。

---

## 三、推送到 GitHub

本地 remote 名称为 `origin`，指向 GitHub。

```bash
# 查看远程
git remote -v
```

国内直连 GitHub 可能失败，可用 **gh CLI 令牌 + 镜像** 推送：

```powershell
# 需先安装 GitHub CLI 并登录：gh auth login
$token = gh auth token
git push "https://oauth2:${token}@ghfast.top/https://github.com/stlxing1987/dhmtv.git" master
```

推送多个分支：

```powershell
$token = gh auth token
git push "https://oauth2:${token}@ghfast.top/https://github.com/stlxing1987/dhmtv.git" master phone tablet
```

---

## 四、热更新（OTA）

App 启动或设置里「检查更新」时，会拉取 update json，比较 `versionCode`，有新版本则下载 APK 安装。

| 端 | 配置文件 | App 内默认地址（strings.xml） |
|----|----------|-------------------------------|
| TV | `docs/update-tv.json` | `.../master/docs/update-tv.json` |
| 手机 | `docs/update-phone.json` | `.../phone/docs/update-phone.json` |
| 平板 | `docs/update-tablet.json` | `.../tablet/docs/update-tablet.json` |

`docs/update.json` 保留给 **1.0.9 及更早版本** 兼容，新包请读 `update-tv.json`。

### update json 格式

```json
{
  "versionCode": 12,
  "versionName": "1.0.12",
  "apkUrl": "https://gh-proxy.com/https://github.com/stlxing1987/dhmtv/releases/download/v1.0.12-tv/app-tv.apk",
  "forceUpdate": false,
  "changelog": "更新说明"
}
```

---

## 五、本地手动发版（CI 不可用时）

脚本：`scripts/release-local.ps1`（需已安装 `gh` 并登录）

```powershell
# 1. 先在 app/build.gradle 改好 versionCode / versionName
# 2. 运行脚本
.\scripts\release-local.ps1 -Changelog "修复某某问题"

# 3. 提交 update json
git add docs/update-tv.json docs/update.json
git commit -m "chore: sync update json for v1.0.12"
git push origin master
```

脚本会：本地构建 → 上传/创建 GitHub Release → 生成 `docs/update.json`。

---

## 六、常见问题

### 热更新下载后「安装失败 / 未知错误」

**原因**：旧版 APK 签名与新版不一致（例如 CI 每次用不同 debug 签名）。

**处理**：
1. 已在项目中固定 `app/dhmtv.jks` 签名，**1.0.11 起** 各版本签名一致，可正常覆盖更新。
2. **首次升级**：若电视上是 1.0.10 及更早 CI 包，需 **先卸载旧版**，再安装 1.0.11+，之后 OTA 即可正常。

### GitHub 网页看不到 phone / tablet 分支

说明该分支尚未 push 到 GitHub，执行第三节推送命令即可。

### 更新了 versionName 但没触发 CI

Workflow 仅在 `app/build.gradle` **文件内容变更** 时触发，请确认已 commit 并 push 到正确分支。

---

## 七、日常开发速查

```bash
# TV 版本地打包
git checkout master
.\gradlew.bat assembleDebug

# 发 TV 1.0.12
# 1. 改 app/build.gradle → versionCode 12, versionName '1.0.12'
# 2. git add app/build.gradle && git commit -m "release: TV 1.0.12"
# 3. git push origin master
# 4. 等 Actions 跑完，去 Releases 查看
```
