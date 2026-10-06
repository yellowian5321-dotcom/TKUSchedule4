param(
    [string]$ProjectDir = (Get-Location).Path,
    [string]$AdbPath = "",
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"
$tkuPackage = "com.example.tkuschedule"
$tkuRoot = (Resolve-Path -LiteralPath $ProjectDir).Path
$tkuWrapper = Join-Path $tkuRoot "gradlew.bat"
if (!(Test-Path -LiteralPath $tkuWrapper)) {
    throw "請在 Android Studio 專案的 Terminal 執行；目前資料夾找不到 gradlew.bat。"
}

if ([string]::IsNullOrWhiteSpace($AdbPath)) {
    $tkuDefaultAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -LiteralPath $tkuDefaultAdb) {
        $AdbPath = $tkuDefaultAdb
    } else {
        $tkuAdbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
        if ($null -eq $tkuAdbCommand) {
            throw "找不到 adb.exe。請用 -AdbPath 指定 Android SDK 的 platform-tools\adb.exe。"
        }
        $AdbPath = $tkuAdbCommand.Source
    }
}
if (!(Test-Path -LiteralPath $AdbPath)) { throw "adb.exe 路徑不存在：$AdbPath" }

function Invoke-TkuAdb {
    param([string[]]$AdbArguments)
    # Windows PowerShell 5.1 treats redirected native stderr as ErrorRecords.
    $ErrorActionPreference = "Continue"
    $tkuResult = & $AdbPath @AdbArguments 2>&1
    $tkuExit = $LASTEXITCODE
    $tkuText = ($tkuResult | ForEach-Object { "$_" }) -join "`n"
    if ($tkuExit -ne 0) { throw "adb 執行失敗：$tkuText" }
    return $tkuText
}

if ([string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $tkuDevicesText = Invoke-TkuAdb -AdbArguments @("devices")
    $tkuReady = @([regex]::Matches($tkuDevicesText, '(?m)^([^\s]+)\s+device\s*$') |
        ForEach-Object { $_.Groups[1].Value })
    if ($tkuReady.Count -ne 1) {
        throw "需要一台已授權的手機（目前 $($tkuReady.Count) 台）。請開 USB 偵錯並允許連線；多台時用 -DeviceSerial 指定。`n$tkuDevicesText"
    }
    $DeviceSerial = $tkuReady[0]
}
$tkuPrefix = @("-s", $DeviceSerial)
if ((Invoke-TkuAdb -AdbArguments ($tkuPrefix + @("get-state"))).Trim() -ne "device") {
    throw "指定裝置尚未就緒。"
}

Write-Host "手機：$DeviceSerial"
$tkuBefore = Invoke-TkuAdb -AdbArguments ($tkuPrefix + @("shell", "dumpsys", "package", $tkuPackage))
if ($tkuBefore -match '(?m)^\s*(?:pkgFlags|flags)=\[[^\]\r\n]*\bDEBUGGABLE\b') {
    Write-Host "目前手機上的 App 是可偵錯版本。將改裝 performance。" -ForegroundColor Yellow
}

Write-Host "建置 performance APK；保留目前專案的 SDK、相依套件與程式碼。"
Push-Location -LiteralPath $tkuRoot
try {
    & $tkuWrapper ":app:assemblePerformance"
    if ($LASTEXITCODE -ne 0) {
        throw "performance 建置失敗，尚未安裝。若找不到 assemblePerformance，請照分析說明新增或修正 performance buildType。"
    }
} finally {
    Pop-Location
}

$tkuApk = Join-Path $tkuRoot "app\build\outputs\apk\performance\app-performance.apk"
if (!(Test-Path -LiteralPath $tkuApk)) {
    throw "找不到 $tkuApk。若專案有 productFlavors，需依實際 variant 調整建置任務與 APK 路徑。"
}

# Fresh APK replacement, rather than Android Studio Apply Changes.
Write-Host "完整安裝 APK，保留既有 App 資料。"
$tkuInstall = Invoke-TkuAdb -AdbArguments ($tkuPrefix + @("install", "-r", "-t", $tkuApk))
Write-Host $tkuInstall
if ($tkuInstall -notmatch '(?m)^Success\s*$') {
    throw "安裝未回報 Success。請保留 App；不要先解除安裝。"
}

$tkuAfter = Invoke-TkuAdb -AdbArguments ($tkuPrefix + @("shell", "dumpsys", "package", $tkuPackage))
$tkuVersion = [regex]::Match($tkuAfter, '(?m)^\s*versionName=([^\r\n]+)').Groups[1].Value.Trim()
$tkuFlags = @([regex]::Matches($tkuAfter, '(?m)^\s*(?:pkgFlags|flags)=\[([^\]\r\n]*)\]'))
if ($tkuFlags.Count -eq 0) { throw "無法讀取 App flags，不能確認安裝版本。" }
foreach ($tkuFlag in $tkuFlags) {
    if ($tkuFlag.Groups[1].Value -match '\bDEBUGGABLE\b') {
        throw "手機仍是可偵錯版本。請確認 performance 的 isDebuggable = false，並檢查主 Manifest 是否覆寫 debuggable。"
    }
}
if (!$tkuVersion.EndsWith("-performance")) {
    throw "版本名稱是 $tkuVersion，未包含 -performance 結尾。請確認 performance 的 versionNameSuffix 設定。"
}

Invoke-TkuAdb -AdbArguments ($tkuPrefix + @("shell", "am", "force-stop", $tkuPackage)) | Out-Null
$tkuStart = Invoke-TkuAdb -AdbArguments ($tkuPrefix + @("shell", "am", "start", "-W", "-n", "$tkuPackage/.MainActivity"))
if ($tkuStart -notmatch '(?m)^Status:\s+ok\s*$') { throw "App 啟動失敗：$tkuStart" }
Write-Host "確認完成：$tkuVersion / DEBUGGABLE=false" -ForegroundColor Green
Write-Host "先各開主頁和課表一次，再測第二次滑動。若仍卡，請再用原本的錄製腳本取得新 trace。"
