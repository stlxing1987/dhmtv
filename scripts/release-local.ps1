# 本地发版脚本（GitHub 网络不稳定时使用）
# 用法: .\scripts\release-local.ps1 -Changelog "修复某某问题"

param(
    [string]$Changelog = "本地构建发布"
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $ParentMyInvocation.MyCommand.Path -Parent) | Out-Null
Set-Location ..

$versionCode = (Select-String -Path "app\build.gradle" -Pattern "versionCode (\d+)" | ForEach-Object { $_.Matches.Groups[1].Value })
$versionName = (Select-String -Path "app\build.gradle" -Pattern "versionName '([^']+)'" | ForEach-Object { $_.Matches.Groups[1].Value })
$tag = "v$versionName"
$repo = "stlxing1987/dhmtv"

Write-Host "Building $tag (code=$versionCode)..."
.\gradlew.bat assembleDebug --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

Write-Host "Uploading release $tag..."
gh release upload $tag $apk --repo $repo --clobber 2>$null
if ($LASTEXITCODE -ne 0) {
    gh release create $tag $apk --repo $repo --title "Release $tag" --notes $Changelog
}

$apkUrl = "https://gh-proxy.com/https://github.com/$repo/releases/download/$tag/app-debug.apk"
$updateJson = @{
    versionCode = [int]$versionCode
    versionName = $versionName
    apkUrl = $apkUrl
    forceUpdate = $false
    changelog = $Changelog
} | ConvertTo-Json -Depth 3
$updateJson | Set-Content -Path "docs\update.json" -Encoding UTF8

Write-Host "Done."
Write-Host "update.json: https://gh-proxy.com/https://raw.githubusercontent.com/$repo/master/docs/update.json"
Write-Host "apk: $apkUrl"
Write-Host "Run: git add docs/update.json && git commit -m `"chore: sync update.json for $tag`""
