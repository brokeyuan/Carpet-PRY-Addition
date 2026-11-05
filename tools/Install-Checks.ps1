$ErrorActionPreference='Stop'
if($PSVersionTable.PSVersion.Major -lt 7){throw '请使用PowerShell 7安装项目检查。'}
$root=Split-Path $PSScriptRoot
$previous=(& git -C $root config --get core.hooksPath)
if($previous -and $previous -ne '.githooks'){throw ('已有其它Git钩子配置，请先核对: '+$previous)}
$runtime=(Get-Process -Id $PID).Path
& git -C $root config core.hooksPath .githooks
if($LASTEXITCODE){throw '无法安装Git钩子'}
& git -C $root config pry.powershell ($runtime.Replace('\','/'))
if($LASTEXITCODE){throw '无法记录PowerShell入口'}
Write-Output '提交与推送检查已安装。'
