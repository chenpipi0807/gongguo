# 连上手机后，一键把微信/红果当前界面 dump 下来，打印所有可读到的控件
# 用法：pwsh -File tools\dump-ui.ps1
param(
    [string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe",
    [string]$Out = "C:\Users\PIP\Downloads\nx\tools\ui.xml"
)

$ErrorActionPreference = "Stop"

$devices = & $Adb devices | Select-String "`tdevice$"
if (-not $devices) {
    Write-Host "没有已授权的设备。先跑：$Adb devices 看看状态" -ForegroundColor Yellow
    exit 1
}

& $Adb shell uiautomator dump /sdcard/ui.xml | Out-Null
& $Adb pull /sdcard/ui.xml $Out | Out-Null
Write-Host "已保存：$Out"

[xml]$xml = Get-Content -Path $Out -Encoding UTF8
$nodes = $xml.SelectNodes("//node")
Write-Host ("共 {0} 个节点，下面只列有文字或描述的：" -f $nodes.Count)

$i = 0
foreach ($n in $nodes) {
    $text = $n.text
    $desc = $n.'content-desc'
    if ([string]::IsNullOrEmpty($text) -and [string]::IsNullOrEmpty($desc)) { continue }
    $i++
    "{0,3} cls={1,-28} text=[{2}] desc=[{3}] bounds={4} clickable={5}" -f `
        $i, ($n.class -replace '^.*\.', ''), $text, $desc, $n.bounds, $n.clickable
}
