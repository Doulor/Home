<#
    设备状态 Agent 看门狗
    ─────────────────────────────────────────────────────────────
    作用：确保 device_agent/agent.py 一直在正常运行。

    为什么需要它：agent 用 pythonw 无窗口运行。一旦它卡在某个阻塞调用上
    （跨进程窗口标题读取、COM 音频会话枚举、PowerShell 子进程收尾等），
    进程仍然"活着"，但循环整个停住、不再推送，而且没有任何报错。
    实测卡死时 60 秒 CPU 只增长 0.11 秒。

    判定依据（本机实测标定）：
          健康 agent : 每 1 秒墙钟消耗约 0.0135s CPU（60 秒 +0.81s）
          卡死 agent : 每 1 秒墙钟仅约 0.0018s CPU（60 秒 +0.11s）
          默认阈值 0.003 CPU秒/秒：健康值高出 4.5 倍，卡死值低 1.6 倍。

    用法：
          powershell -NoProfile -ExecutionPolicy Bypass -File watchdog.ps1
          powershell ... -File watchdog.ps1 -DryRun              # 只检查不动作
          powershell ... -File watchdog.ps1 -ObservationSeconds 10
#>
[CmdletBinding()]
param(
    # 观察窗口（秒）
    [int]$ObservationSeconds = 90,

    # 判定阈值，单位「CPU 秒 / 墙钟秒」。低于它即认为进程没有在推进
    [double]$MinCpuRate = 0.003,

    # 匹配 agent 进程命令行的正则
    [string]$MatchPattern = 'agent\.py',

    # agent 所在目录
    [string]$AgentDir = 'D:\build\GitLocal\Home\device_agent',

    # 日志文件，默认写在 agent 目录下（.gitignore 已忽略 *.log）
    [string]$LogFile = '',

    # 只报告，不杀不启
    [switch]$DryRun,

    # 允许杀进程但不负责拉起（手动排查用）
    [switch]$NoStart
)

if (-not $LogFile) { $LogFile = Join-Path $AgentDir 'watchdog.log' }

# ── 日志（超过 512KB 自动瘦身，只留最近 200 行）────────────────
function Write-Log {
    param([string]$Message)
    $line = '[{0}] {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    try {
        if ((Test-Path $LogFile) -and ((Get-Item $LogFile).Length -gt 512KB)) {
            $keep = Get-Content $LogFile -Tail 200
            Set-Content -Path $LogFile -Value $keep -Encoding UTF8
        }
        Add-Content -Path $LogFile -Value $line -Encoding UTF8
    } catch { }
    Write-Output $line
}

# ── 找 agent 进程 ─────────────────────────────────────────────
function Get-AgentProcess {
    Get-CimInstance Win32_Process -Filter "Name='pythonw.exe' or Name='python.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -match $MatchPattern }
}

# ── 找 pythonw.exe ────────────────────────────────────────────
function Get-PythonwPath {
    $cmd = Get-Command pythonw.exe -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }
    $fallback = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python313\pythonw.exe'
    if (Test-Path $fallback) { return $fallback }
    return $null
}

# ── 启动失败时做一次有界的自诊断 ──────────────────────────────
function Get-StartFailureReason {
    $pyw = Get-PythonwPath
    if (-not $pyw) { return '找不到 pythonw.exe' }
    $py = $pyw -replace 'pythonw\.exe$', 'python.exe'
    if (-not (Test-Path $py)) { return "找不到 $py" }

    $diagPy = Join-Path $env:TEMP 'watchdog-diag.py'
    $diagOut = Join-Path $env:TEMP 'watchdog-diag.out.txt'
    $code = @"
import os, sys, traceback
sys.path.insert(0, r'$AgentDir')
try:
    import supabase, psutil, win32gui, pycaw
    from config import SUPABASE_URL, SERVICE_ROLE_KEY
    print('imports ok')
    print('url = ' + str(SUPABASE_URL))
    print('key set = ' + str(bool(SERVICE_ROLE_KEY)))
    print('no_proxy = ' + repr(os.environ.get('no_proxy')))
except Exception:
    traceback.print_exc()
"@
    Set-Content -Path $diagPy -Value $code -Encoding UTF8
    Remove-Item $diagOut -ErrorAction SilentlyContinue

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = '/c ""' + $py + '" "' + $diagPy + '" > "' + $diagOut + '" 2>&1"'
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    if (-not $p.WaitForExit(20000)) {
        try { $p.Kill() } catch { }
        return '诊断超时（20 秒未结束）'
    }
    if (Test-Path $diagOut) {
        return ((Get-Content $diagOut -Raw) -replace '\s+$', '')
    }
    return '诊断没有产生输出'
}

# ── 启动 agent ────────────────────────────────────────────────
function Start-Agent {
    $py = Get-PythonwPath
    if (-not $py) { Write-Log '错误：找不到 pythonw.exe，无法拉起 agent'; return }
    if (-not (Test-Path (Join-Path $AgentDir 'agent.py'))) {
        Write-Log "错误：$AgentDir\agent.py 不存在"; return
    }

    # 用 ProcessStartInfo 精确控制子进程的环境块：只在 no_proxy 被污染时
    # 修这一个变量，http_proxy / HTTPS_PROXY 等原样保留。
    #
    # 不用 Start-Process -UseNewEnvironment：实测在 PowerShell 5.1 下重建的
    # 环境块会让 Winsock 初始化失败（OSError [WinError 10106]），agent 在
    # import asyncio 阶段就崩，比原问题更难查。
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $py
    $psi.Arguments = 'agent.py'
    $psi.WorkingDirectory = $AgentDir
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $np = $psi.EnvironmentVariables['no_proxy']
    if ($np -and $np -match '\[') {
        $psi.EnvironmentVariables['no_proxy'] = 'localhost,127.0.0.1'
        Write-Log "子进程 no_proxy 含非法条目，已修正为 localhost,127.0.0.1（原值：$np）"
    }

    $proc = [System.Diagnostics.Process]::Start($psi)

    # 存活确认：启动后立刻退出是最难发现的一种故障，这里主动抓出来
    Start-Sleep -Seconds 5
    if (-not $proc.HasExited) {
        Write-Log "已拉起 agent，PID = $($proc.Id)（5 秒后仍存活）"
        return
    }

    Write-Log "警告：agent PID $($proc.Id) 启动后 5 秒内即退出（退出码 $($proc.ExitCode)），开始诊断"
    $reason = Get-StartFailureReason
    if ($reason) { Write-Log "诊断结果：$reason" }
}

# ── 主流程 ────────────────────────────────────────────────────
Write-Log "── 看门狗检查开始（观察 ${ObservationSeconds}s，阈值 ${MinCpuRate} CPU秒/秒）──"

$procs = @(Get-AgentProcess)

if ($procs.Count -eq 0) {
    Write-Log 'agent 未在运行'
    if ($DryRun) { Write-Log '[DryRun] 本应启动 agent' }
    elseif ($NoStart) { Write-Log 'NoStart 已指定，不启动' }
    else { Start-Agent }
    return
}

Write-Log ("发现 agent 进程: " + (($procs | ForEach-Object { $_.ProcessId }) -join ', '))

# 采样 CPU 基线
$before = @{}
foreach ($p in $procs) {
    $proc = Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue
    if ($proc) { $before[$p.ProcessId] = $proc.CPU }
}

if ($before.Count -eq 0) {
    Write-Log '错误：找到了 agent 进程但无法读取其 CPU 时间，本次检查视为失败（不动作）'
    exit 2
}

Start-Sleep -Seconds $ObservationSeconds

$alive = @()
$stuck = @()
foreach ($procId in $before.Keys) {
    $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if (-not $proc) { Write-Log "PID $procId 在观察期内退出"; continue }
    $delta = $proc.CPU - $before[$procId]
    $rate = $delta / $ObservationSeconds
    if ($rate -ge $MinCpuRate) {
        $alive += $procId
    } else {
        $stuck += $procId
        Write-Log ("PID {0} 判定卡死：{1}s 内 CPU 仅 +{2:N3}s（{3:N4} CPU秒/秒，阈值 {4}）" -f $procId, $ObservationSeconds, $delta, $rate, $MinCpuRate)
    }
}

# 兜底：一个都没判定出来说明检查本身出错了，绝不能当成"健康"放过
if (($alive.Count + $stuck.Count) -eq 0) {
    Write-Log '错误：观察期结束后没有取得任何进程的判定结果，本次检查视为失败（不动作）'
    exit 2
}

if ($stuck.Count -eq 0) {
    Write-Log ("健康：PID {0} 正常推进，无需处理" -f ($alive -join ', '))
    return
}

if ($DryRun) {
    Write-Log ("[DryRun] 本应终止 PID {0} 并重启" -f ($stuck -join ', '))
    return
}

# 杀掉卡死实例
foreach ($sid in $stuck) {
    try {
        Stop-Process -Id $sid -Force -ErrorAction Stop
        Write-Log "已终止卡死进程 PID $sid"
    } catch {
        Write-Log "终止 PID $sid 失败：$($_.Exception.Message)"
    }
}

# 去重：存活实例多于一个时，保留最早启动的那个
if ($alive.Count -gt 1) {
    $sorted = $alive | ForEach-Object {
        $pr = Get-Process -Id $_ -ErrorAction SilentlyContinue
        if ($pr) { [pscustomobject]@{ Id = $_; Start = $pr.StartTime } }
    } | Sort-Object Start
    $keep = $sorted[0].Id
    foreach ($dup in ($sorted | Select-Object -Skip 1)) {
        try {
            Stop-Process -Id $dup.Id -Force -ErrorAction Stop
            Write-Log "发现重复实例，已终止 PID $($dup.Id)（保留 $keep）"
        } catch { }
    }
    $alive = @($keep)
}

if ($alive.Count -eq 0) {
    if ($NoStart) { Write-Log 'NoStart 已指定，不重启' }
    else { Start-Agent }
} else {
    Write-Log ("仍有健康实例 {0}，不重复启动" -f ($alive -join ', '))
}
