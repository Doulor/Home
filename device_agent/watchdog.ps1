<#
    设备状态 Agent 看门狗
    ─────────────────────────────────────────────────────────────
    作用：确保 device_agent/agent.py 一直在正常运行。

    为什么需要它：agent 用 pythonw 无窗口运行。一旦它卡在某个阻塞调用上
    （跨进程窗口标题读取、COM 音频会话枚举、PowerShell 子进程收尾等），
    进程仍然"活着"，但循环整个停住、不再推送，而且没有任何报错。

    判定依据：agent 每轮循环都会更新 agent.heartbeat，本脚本只看这个文件的
    修改时间，以及里面记录的 pid。

    ⚠ 不要改回用 CPU 占用判断（v1 的教训）：
      网络不通时 agent 的 CPU 会掉到接近 0，和"真的卡死"完全无法区分 ——
      实测健康但推不出去约 0.001、真卡死约 0.0018 CPU秒/秒，区间重叠。
      v1 用 CPU 判定，结果每 15 分钟误杀一次健康进程（两天里误杀 59 次）。

    用法：
          powershell -NoProfile -ExecutionPolicy Bypass -File watchdog.ps1
          powershell ... -File watchdog.ps1 -DryRun       # 只检查不动作
          powershell ... -File watchdog.ps1 -HeartbeatTimeoutSeconds 30
#>
[CmdletBinding()]
param(
    # 心跳文件多久没更新就认为卡死（秒）。agent 循环间隔是 5 秒，
    # 默认 60 秒 = 连续漏掉 12 轮，留足了 PowerShell 超时（各 10 秒）的余量
    [int]$HeartbeatTimeoutSeconds = 60,

    # 心跳正常、但超过这么多分钟没能成功推送，就提醒可能是网络/代理问题
    [int]$PushStaleWarnMinutes = 10,

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
$heartbeatFile = Join-Path $AgentDir 'agent.heartbeat'

Write-Log "── 看门狗检查开始（心跳超时 ${HeartbeatTimeoutSeconds}s）──"

$procs = @(Get-AgentProcess)

if ($procs.Count -eq 0) {
    Write-Log 'agent 未在运行'
    if ($DryRun) { Write-Log '[DryRun] 本应启动 agent' }
    elseif ($NoStart) { Write-Log 'NoStart 已指定，不启动' }
    else { Start-Agent }
    return
}

Write-Log ("发现 agent 进程: " + (($procs | ForEach-Object { $_.ProcessId }) -join ', '))

# ── 读心跳 ────────────────────────────────────────────────────
$hbPid = $null
$hbAge = [double]::MaxValue
$hbLastPush = ''
$hbLastError = ''

if (Test-Path $heartbeatFile) {
    $hbAge = ((Get-Date) - (Get-Item $heartbeatFile).LastWriteTime).TotalSeconds
    foreach ($line in ((Get-Content $heartbeatFile -ErrorAction SilentlyContinue) -split "`r?`n")) {
        if ($line -match '^pid=(\d+)') { $hbPid = [int]$Matches[1] }
        elseif ($line -match '^last_push_ok=(.*)$') { $hbLastPush = $Matches[1].Trim() }
        elseif ($line -match '^last_error=(.*)$') { $hbLastError = $Matches[1].Trim() }
    }
} else {
    Write-Log "没有心跳文件（$heartbeatFile）—— agent 可能是旧版本，本次不判定、不动作"
    return
}

# 心跳的持有者必须正好是还在运行的某个 agent 进程
$ownerAlive = $false
if ($hbPid -and ($procs | Where-Object { $_.ProcessId -eq $hbPid })) {
    $ownerAlive = $hbAge -le $HeartbeatTimeoutSeconds
}

# ── 心跳正常：清理多余实例 + 检查推送健康度 ────────────────────
if ($ownerAlive) {
    Write-Log ("健康：PID {0} 心跳 {1:N1} 秒前更新，上次成功推送 {2}" -f $hbPid, $hbAge, $hbLastPush)

    # 去重：心跳持有者才是"正主"，其余一律终止。
    # 这一步必须无条件执行 —— v1 把它写在"卡死处理"之后，结果全健康时
    # 直接 return，重复实例永远清不掉（实测攒到过 3 个）。
    foreach ($p in $procs) {
        if ($p.ProcessId -eq $hbPid) { continue }
        if ($DryRun) { Write-Log "[DryRun] 本应终止重复实例 PID $($p.ProcessId)"; continue }
        try {
            Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
            Write-Log "发现重复实例，已终止 PID $($p.ProcessId)（保留 $hbPid）"
        } catch {
            Write-Log "终止重复实例 PID $($p.ProcessId) 失败：$($_.Exception.Message)"
        }
    }

    # 推送健康度：心跳在跑、但长时间推不出去 —— 这正是"代理被切回直连"的典型表现
    if ($hbLastPush -eq 'never') {
        Write-Log "⚠ 从未成功推送过。最后一次错误：$hbLastError"
    } elseif ($hbLastPush -match '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$') {
        $pushAge = ((Get-Date) - [datetime]::ParseExact($hbLastPush, 'yyyy-MM-dd HH:mm:ss', $null)).TotalMinutes
        if ($pushAge -gt $PushStaleWarnMinutes) {
            Write-Log ("⚠ 心跳正常，但已 {0:N1} 分钟没能成功推送 —— 大概率是网络/代理问题：" -f $pushAge)
            Write-Log "  去 Clash Verge 看看「代理模式」是不是又被切成「直连」了。最后一次错误：$hbLastError"
        }
    }
    return
}

# ── 心跳过期 / 持有者已不在：全部重启 ──────────────────────────
if ($hbPid) {
    Write-Log ("心跳异常：文件 {0:N1} 秒未更新（超时 {1}s），持有者 PID {2}" -f $hbAge, $HeartbeatTimeoutSeconds, $hbPid)
} else {
    Write-Log '心跳文件里没有 pid，按异常处理'
}

if ($DryRun) {
    Write-Log ("[DryRun] 本应终止 {0} 并重启" -f (($procs | ForEach-Object { $_.ProcessId }) -join ', '))
    return
}

foreach ($p in $procs) {
    try {
        Stop-Process -Id $p.ProcessId -Force -ErrorAction Stop
        Write-Log "已终止 PID $($p.ProcessId)"
    } catch {
        Write-Log "终止 PID $($p.ProcessId) 失败：$($_.Exception.Message)"
    }
}

if ($NoStart) { Write-Log 'NoStart 已指定，不重启' }
else { Start-Agent }
