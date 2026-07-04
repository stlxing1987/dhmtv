#### 分支结构
分支	用途	应用名	applicationId
master  电视专版（主分支）  大花喵TV    com.github.tvbox.osc
phone   手机专版    大花喵手机  com.github.tvbox.osc.phone
tablet  平板专版    大花喵平板  com.github.tvbox.osc.tablet

```bash
git checkout master   # 打包 TV 版
git checkout phone    # 打包手机版
git checkout tablet   # 打包平板版

./gradlew assembleDebug
```
