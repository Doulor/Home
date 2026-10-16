' 设备状态 Agent 看门狗 - 无窗口启动
' 由任务计划程序调用，避免每次运行时闪出控制台窗口
Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -File ""D:\build\GitLocal\Home\device_agent\watchdog.ps1""", 0, False
