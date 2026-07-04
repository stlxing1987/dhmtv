#### 分支结构
| 分支 | 用途 | 应用名 | applicationId |
|------|------|--------|---------------|
| master | 电视专版（主分支） | 大花喵TV | com.github.tvbox.osc |
| phone | 手机专版 | 大花喵手机 | com.github.tvbox.osc.phone |
| tablet | 平板专版 | 大花喵平板 | com.github.tvbox.osc.tablet |

#### 远程仓库
仅使用 **GitHub**：https://github.com/stlxing1987/dhmtv

#### 打包
```bash
git checkout master   # TV 版
git checkout phone    # 手机版
git checkout tablet   # 平板版

./gradlew assembleDebug
```

#### 推送到 GitHub
国内网络直连 GitHub 可能失败，可用 GitHub CLI 令牌经镜像推送：

```powershell
$token = gh auth token
git push "https://oauth2:${token}@ghfast.top/https://github.com/stlxing1987/dhmtv.git" master phone tablet
```

#### 热更新
| 端 | update.json |
|----|-------------|
| TV | docs/update-tv.json |
| 手机 | docs/update-phone.json |
| 平板 | docs/update-tablet.json |

修改对应分支 `app/build.gradle` 版本号后 push，GitHub Actions 会自动发版。
