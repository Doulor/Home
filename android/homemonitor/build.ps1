# 手工构建设备监控 App（不依赖 Gradle）
#
#   powershell -ExecutionPolicy Bypass -File build.ps1
#
# 需要：JDK 17+ 和 Android SDK 的 build-tools + platform（用 sdkmanager 装）。
param(
    [string]$Sdk = 'D:\build\android-sdk',
    [switch]$KeepBuildDir
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$bt = Join-Path $Sdk 'build-tools\35.0.0'
$aj = Join-Path $Sdk 'platforms\android-34\android.jar'

foreach ($p in @($bt, $aj)) {
    if (-not (Test-Path $p)) { throw "找不到 $p（先用 sdkmanager 安装 build-tools;35.0.0 和 platforms;android-34）" }
}

$build = Join-Path $root 'build'
if (Test-Path $build) { Remove-Item $build -Recurse -Force }
$classes = Join-Path $build 'classes'
$dexDir = Join-Path $build 'dex'
New-Item -ItemType Directory -Force -Path $classes, $dexDir | Out-Null

function Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# 1. 链接资源，生成未签名的 APK 骨架
Step '1/6 aapt2 link'
$unsigned = Join-Path $build 'unsigned.apk'
& (Join-Path $bt 'aapt2.exe') link `
    -o $unsigned `
    --manifest (Join-Path $root 'AndroidManifest.xml') `
    -I $aj `
    --min-sdk-version 26 `
    --target-sdk-version 33 `
    --version-code 1 `
    --version-name '1.0'
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

# 2. 编译 Java
Step '2/6 javac'
$src = Get-ChildItem (Join-Path $root 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 11 -encoding UTF-8 -cp $aj -d $classes @src 2>&1 | ForEach-Object { "  $_" }
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }
Write-Host ("  编译成功: " + (Get-ChildItem $classes -Recurse -Filter *.class).Count + " 个 class")

# 3. dex
Step '3/6 d8'
$classFiles = Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& (Join-Path $bt 'd8.bat') --lib $aj --min-api 26 --output $dexDir @classFiles 2>&1 |
    Where-Object { $_ -notmatch '^Warning: |missing class' } | ForEach-Object { "  $_" }
$dex = Join-Path $dexDir 'classes.dex'
if (-not (Test-Path $dex)) { throw "d8 没有生成 classes.dex" }
Write-Host ("  classes.dex: " + [math]::Round((Get-Item $dex).Length/1KB, 1) + " KB")

# 4. 把 classes.dex 塞进 APK
Step '4/6 打包 classes.dex'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
$existing = $zip.Entries | Where-Object { $_.FullName -eq 'classes.dex' }
if ($existing) { $existing.Delete() }
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dex, 'classes.dex', 'Optimal') | Out-Null
$zip.Dispose()
Write-Host '  已加入 classes.dex'

# 5. zipalign
Step '5/6 zipalign'
$aligned = Join-Path $build 'aligned.apk'
& (Join-Path $bt 'zipalign.exe') -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

# 6. 签名（keystore 不存在就生成一个，务必自己备份）
Step '6/6 apksigner'
$ks = Join-Path $root 'monitor.keystore'
if (-not (Test-Path $ks)) {
    Write-Host '  生成新 keystore（请妥善备份，丢了就只能卸载重装）'
    & keytool -genkeypair -keystore $ks -alias monitor -keyalg RSA -keysize 2048 `
        -validity 10950 -storepass monitor123 -keypass monitor123 `
        -dname 'CN=HomeMonitor, OU=Personal, O=Doulor, L=Jingzhou, ST=Hubei, C=CN' 2>&1 |
        Select-Object -Last 2 | ForEach-Object { "  $_" }
}
$out = Join-Path $root 'homemonitor.apk'
& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-pass pass:monitor123 --key-pass pass:monitor123 --out $out $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner 失败" }
& (Join-Path $bt 'apksigner.bat') verify --print-certs $out 2>&1 | Select-Object -First 4 | ForEach-Object { "  $_" }

if (-not $KeepBuildDir) { Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue }

Write-Host "`n✅ 构建完成: $out" -ForegroundColor Green
Write-Host ("   大小: " + [math]::Round((Get-Item $out).Length/1KB, 1) + " KB")
