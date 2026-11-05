param([Parameter(Mandatory=$true)][string[]]$Files,[Parameter(Mandatory=$true)][string]$Message,[switch]$Push,[string]$Branch='master')
$ErrorActionPreference='Stop'
if($Push -and $Branch -ne 'master'){throw '只允许推送master；任务分支只在本地提交、合并。'}
$guard=Join-Path $PSScriptRoot 'Task-Guard.ps1'
$oldFiles=$env:PRY_TASK_FILES;$oldBranch=$env:PRY_TASK_BRANCH;$oldCommits=$env:PRY_PUSH_COMMITS
try{
 $env:PRY_TASK_BRANCH=$Branch
 $files=@($Files|ForEach-Object {$_.Replace('\','/')}|Select-Object -Unique)
 $env:PRY_TASK_FILES=ConvertTo-Json -InputObject $files -Compress
 & $guard -Mode Paths -Paths $files
 & $guard -Mode Message -MessageText $Message
 $staged=@(& git -c core.quotepath=false diff --cached --no-renames --name-only)
 if($LASTEXITCODE){throw '无法读取暂存区'}
 if(@($staged|Where-Object {$_ -notin $files}).Count){throw '已有无关暂存文件不在本次范围，保留原状，请先核对。'}
 foreach($file in $files){
  # 首次纳入规范时只对AGENTS.md覆盖旧的本地忽略设置。
  if($file -eq 'AGENTS.md'){& git --literal-pathspecs add --force -- $file}else{& git --literal-pathspecs add -- $file}
  if($LASTEXITCODE){throw ('暂存失败: '+$file)}
 }
 & $guard -Mode Staged
 & git commit -m $Message
 if($LASTEXITCODE){throw '提交被检查拒绝，请查看上方原因；不自动回退或清空暂存区。'}
 $commit=(& git rev-parse HEAD)
 Write-Output ('Committed: '+$commit)
 if($Push){
  $env:PRY_PUSH_COMMITS=ConvertTo-Json -InputObject @($commit) -Compress
  & git push origin ($Branch+':refs/heads/'+$Branch)
  if($LASTEXITCODE){throw '推送未完成；本次本地提交已保留。请核对旧提交或上方远端错误，不强推。'}
 }
}finally{$env:PRY_TASK_FILES=$oldFiles;$env:PRY_TASK_BRANCH=$oldBranch;$env:PRY_PUSH_COMMITS=$oldCommits}
