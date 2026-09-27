param()

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stage = Join-Path $repo "release\stage-electron-$stamp"
$runtimeDir = Join-Path $stage 'runtime'
$appSource = Join-Path $stage 'app-source'
$outputDir = Join-Path $repo "release\build-electron-$stamp"
New-Item -ItemType Directory -Path $stage, $appSource, $outputDir -Force | Out-Null

Push-Location (Join-Path $repo 'frontend')
try {
    & pnpm.cmd build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败。' }
} finally { Pop-Location }

Push-Location (Join-Path $repo 'backend')
try {
    & .\mvnw.cmd -Pdesktop -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw '后端打包失败。' }
} finally { Pop-Location }

$backendJar = Join-Path $repo 'backend\target\goalflow-backend-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $backendJar)) { throw "找不到后端包：$backendJar" }
$icon = Join-Path $repo 'frontend\public\favicon.ico'
$electronVersion = '44.4.5'
$electronZipName = "electron-v$electronVersion-win32-x64.zip"
$electronZipDir = Join-Path $repo 'release\cache\electron'
$electronZip = Join-Path $electronZipDir $electronZipName
New-Item -ItemType Directory -Path $electronZipDir -Force | Out-Null
if (!(Test-Path -LiteralPath $electronZip)) {
    $download = Join-Path $electronZipDir "$electronZipName.download-$stamp"
    Invoke-WebRequest -Uri "https://github.com/electron/electron/releases/download/v$electronVersion/$electronZipName" `
        -OutFile $download -TimeoutSec 600
    $checksums = [System.Text.Encoding]::UTF8.GetString((Invoke-WebRequest `
        -Uri "https://github.com/electron/electron/releases/download/v$electronVersion/SHASUMS256.txt" `
        -TimeoutSec 30).Content)
    $expected = ($checksums -split "`n" | Where-Object { $_ -match [regex]::Escape($electronZipName) } | Select-Object -First 1) -split '\s+'
    if ($expected.Count -lt 2 -or (Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $expected[0].ToUpperInvariant()) {
        throw 'Electron 下载文件校验失败。'
    }
    Move-Item -LiteralPath $download -Destination $electronZip
}

& jlink.exe --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.management `
    --strip-debug --no-header-files --no-man-pages --output $runtimeDir
if ($LASTEXITCODE -ne 0) { throw 'Java 运行环境打包失败。' }

Push-Location (Join-Path $repo 'desktop\electron')
try {
    & pnpm.cmd install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw '桌面依赖安装失败。' }
    & pnpm.cmd test
    if ($LASTEXITCODE -ne 0) { throw '桌面启动器测试失败。' }
    Copy-Item -LiteralPath (Join-Path $repo 'desktop\electron\main.cjs') -Destination $appSource
    Copy-Item -LiteralPath (Join-Path $repo 'desktop\electron\server.cjs') -Destination $appSource
    Copy-Item -LiteralPath (Join-Path $repo 'desktop\electron\package.json') -Destination $appSource
    & pnpm.cmd exec electron-packager $appSource Goaltree --platform=win32 --arch=x64 `
        --electron-version=$electronVersion --out=$outputDir --icon=$icon --asar `
        --electron-zip-dir=$electronZipDir
    if ($LASTEXITCODE -ne 0) { throw 'Windows 应用打包失败。' }
} finally { Pop-Location }

$appDir = Join-Path $outputDir 'Goaltree-win32-x64'
$resourcesDir = Join-Path $appDir 'resources'
if (!(Test-Path -LiteralPath (Join-Path $appDir 'Goaltree.exe'))) {
    throw "找不到桌面程序：$appDir"
}
Copy-Item -LiteralPath $runtimeDir -Destination (Join-Path $resourcesDir 'runtime') -Recurse
Copy-Item -LiteralPath $backendJar -Destination (Join-Path $resourcesDir 'goaltree-server.jar')
Copy-Item -LiteralPath (Join-Path $repo 'frontend\public\favicon.ico') `
    -Destination (Join-Path $resourcesDir 'app.ico')

@'
Goaltree Windows 桌面应用

双击 Goaltree.exe 启动。请保留此文件夹中的全部文件。
程序自带界面运行环境和 Java，不需要安装或打开 Edge 等浏览器。
应用使用本机 MySQL 的 goalflow 数据库，不会迁移或重建已有目标。
MySQL 必须先启动；默认数据库账号为 goalflow。
如使用其他账号，请在启动应用前设置 DB_USERNAME、DB_PASSWORD 和 DB_URL 环境变量。
启动异常时查看 %APPDATA%\Goaltree\server.log。
'@ | Set-Content -LiteralPath (Join-Path $appDir '使用说明.txt') -Encoding UTF8

$zipPath = Join-Path $repo "release\Goaltree-Windows-Desktop-$stamp.zip"
Compress-Archive -LiteralPath $appDir -DestinationPath $zipPath -CompressionLevel Optimal
Write-Output "Goaltree 桌面程序：$(Join-Path $appDir 'Goaltree.exe')"
Write-Output "便携压缩包：$zipPath"
