# 触发手机上的「抓取当前界面」，打印最后一条 manual 记录（控件树）
# 用法：pwsh -File tools\dump.ps1
param(
    [string]$Adb = "C:\Android\Sdk\platform-tools\adb.exe",
    [int]$Lines = 120
)

& $Adb shell am broadcast -n com.nx.adcleaner/.DumpReceiver -a com.nx.adcleaner.DUMP | Out-Null
Start-Sleep -Milliseconds 900

$raw = & $Adb shell run-as com.nx.adcleaner cat files/events.jsonl
$last = $raw | Select-Object -Last 1
if (-not $last) { Write-Host "没有日志"; exit 1 }

$obj = $last | ConvertFrom-Json
if ($obj.k -ne "manual") {
    Write-Host "最后一条不是 manual（k=$($obj.k)），可能没抓到"
}
$text = $obj.d -replace '\\n', "`n" -replace '\\t', "`t"
$text.Split("`n") | Select-Object -First $Lines
