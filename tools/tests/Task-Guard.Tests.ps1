$ErrorActionPreference='Stop'
$source=Split-Path (Split-Path $PSScriptRoot)
if(!(Test-Path -LiteralPath (Join-Path $source 'tools/Commit-Task.ps1'))){throw 'RED: 提交检查入口尚不存在'}
$tempRoot=Join-Path ([IO.Path]::GetTempPath()) ('pry-guard-test-'+[guid]::NewGuid().ToString('N'))
$repo=Join-Path $tempRoot 'repo';$remote=Join-Path $tempRoot 'remote.git'
[IO.Directory]::CreateDirectory($repo)|Out-Null
$oldLocation=Get-Location;$oldAllowed=$env:PRY_TASK_FILES;$oldCommits=$env:PRY_PUSH_COMMITS;$oldBranch=$env:PRY_TASK_BRANCH
function Invoke-TestGit([string[]]$Arguments){
 $output=@(& git @Arguments 2>&1);if($LASTEXITCODE){throw ($output -join "`n")};return $output
}
function Expect-Blocked([string]$Name,[scriptblock]$Action,[string]$Pattern){
 $caught=$null
 try{& $Action|Out-Null}catch{$caught=$_.Exception.Message}
 if(!$caught -or $caught -notmatch $Pattern){throw ('检查未正确拦截: '+$Name+' '+$caught)}
 Write-Output ('PASS '+$Name)
}
function Put([string]$Name,[string]$Content){$path=Join-Path $repo $Name;[IO.Directory]::CreateDirectory((Split-Path $path))|Out-Null;[IO.File]::WriteAllText($path,$Content,[Text.UTF8Encoding]::new($false))}
try{
 Invoke-TestGit @('init','-b','master',$repo)|Out-Null
 Invoke-TestGit @('init','--bare',$remote)|Out-Null
 Set-Location -LiteralPath $repo
 Invoke-TestGit @('config','user.name','Local Test')|Out-Null
 Invoke-TestGit @('config','user.email','test@localhost')|Out-Null
 Invoke-TestGit @('config','core.autocrlf','false')|Out-Null
 Put 'README.md' "test`n"
 Put '.gitignore' "run/`n"
 Copy-Item -LiteralPath (Join-Path $source 'tools') -Destination (Join-Path $repo 'tools') -Recurse
 Copy-Item -LiteralPath (Join-Path $source '.githooks') -Destination (Join-Path $repo '.githooks') -Recurse
 Invoke-TestGit @('add','--','README.md','.gitignore','tools','.githooks')|Out-Null
 Invoke-TestGit @('commit','-m','chore: 初始化检查环境')|Out-Null
 Invoke-TestGit @('remote','add','origin',$remote)|Out-Null
 Invoke-TestGit @('push','origin','master')|Out-Null
 & (Join-Path $repo 'tools/Install-Checks.ps1')|Out-Null
 $commit=Join-Path $repo 'tools/Commit-Task.ps1'
 $guard=Join-Path $repo 'tools/Task-Guard.ps1'
 Put 'change.txt' "one`n"
 & $commit -Files @('change.txt') -Message 'fix: 修正单项设置' -Push|Out-Null
 if(@(Invoke-TestGit @('status','--porcelain')).Count){throw '正常提交后未干净'}
 Write-Output 'PASS 单事项提交与本地推送'

 Invoke-TestGit @('switch','-c','task/parallel')|Out-Null
 Put 'parallel.txt' 'parallel'
 & $commit -Files @('parallel.txt') -Message 'fix: 并行任务过程' -Branch 'task/parallel'|Out-Null
 Expect-Blocked '任务分支推送入口' {& $commit -Files @('parallel.txt') -Message 'fix: 不应上传' -Branch 'task/parallel' -Push} '只允许推送master'
 $env:PRY_TASK_BRANCH='task/parallel';$env:PRY_PUSH_COMMITS=ConvertTo-Json -InputObject @((Invoke-TestGit @('rev-parse','HEAD'))) -Compress
 Expect-Blocked '钩子拒绝远端任务分支' {Invoke-TestGit @('push','origin','task/parallel:refs/heads/task/parallel')} 'GitHub只保留master'
 $env:PRY_TASK_BRANCH=$null;$env:PRY_PUSH_COMMITS=$null
 Invoke-TestGit @('switch','master')|Out-Null
 $before=Invoke-TestGit @('rev-parse','HEAD')
 Invoke-TestGit @('merge','--squash','task/parallel')|Out-Null
 & $commit -Files @('parallel.txt') -Message 'fix: 接纳一个并行任务' -Push|Out-Null
 if((Invoke-TestGit @('rev-list','--count',($before+'..HEAD'))) -ne '1'){throw '主线应只有一次正式提交'}
 if(@(Invoke-TestGit @('ls-remote','--heads','origin','task/parallel')).Count){throw '远端不应出现任务分支'}
 Write-Output 'PASS 本地任务压入master后单独提交推送'

 Put 'unrelated.txt' 'other';Invoke-TestGit @('add','--','unrelated.txt')|Out-Null
 Put 'selected.txt' 'selected'
 Expect-Blocked '暂存范围不符' {& $commit -Files @('selected.txt') -Message 'fix: 指定文件'} '暂存.*范围|无关暂存'
 Invoke-TestGit @('reset','--','unrelated.txt')|Out-Null
 [IO.File]::Delete((Join-Path $repo 'unrelated.txt'));[IO.File]::Delete((Join-Path $repo 'selected.txt'))

 Put 'boot-error.log' 'runtime'
 Expect-Blocked '运行产物' {& $commit -Files @('boot-error.log') -Message 'fix: 测试'} '运行产物|禁止提交'
 [IO.File]::Delete((Join-Path $repo 'boot-error.log'))

 Put 'selected.txt' 'selected';Invoke-TestGit @('add','--','selected.txt')|Out-Null
 $env:PRY_TASK_FILES=$null
 Expect-Blocked '真实钩子拒绝无文件范围提交' {Invoke-TestGit @('commit','-m','fix: 未声明范围')} '未声明本次文件范围'
 $env:PRY_TASK_FILES='["selected.txt"]'
 $message=Join-Path $tempRoot 'message.txt'
 [IO.File]::WriteAllText($message,"fix: 测试`n`nCo-Authored-By: Example <example@localhost>`n")
 Expect-Blocked '提交署名' {& $guard -Mode Message -MessageFile $message} '署名|尾注'
 $before=Invoke-TestGit @('rev-parse','HEAD')
 Expect-Blocked '真实提交拒绝协作尾注' {Invoke-TestGit @('commit','-F',$message)} '署名|尾注'
 if((Invoke-TestGit @('rev-parse','HEAD')) -ne $before){throw '带署名提交不应产生'}
 Put 'selected.txt' "<<<<<<< HEAD`nwrong`n>>>>>>> other`n";Invoke-TestGit @('add','--','selected.txt')|Out-Null
 Expect-Blocked '冲突标记' {& $guard -Mode Staged} '冲突标记'
 Invoke-TestGit @('reset','--','selected.txt')|Out-Null;[IO.File]::Delete((Join-Path $repo 'selected.txt'))

 Put 'src/main/resources/assets/test/lang/zh_cn.json' '{"a":"one","a":"two"}'
 Invoke-TestGit @('add','--','src/main/resources/assets/test/lang/zh_cn.json')|Out-Null
 $env:PRY_TASK_FILES='["src/main/resources/assets/test/lang/zh_cn.json"]'
 Expect-Blocked '语言文件重复键' {& $guard -Mode Staged} '重复'
 Invoke-TestGit @('reset','--','src')|Out-Null;[IO.File]::Delete((Join-Path $repo 'src/main/resources/assets/test/lang/zh_cn.json'))

 Put 'launcher.cmd' "@echo off`npause`n";Invoke-TestGit @('add','--','launcher.cmd')|Out-Null;$env:PRY_TASK_FILES='["launcher.cmd"]'
 Expect-Blocked '批处理换行' {& $guard -Mode Staged} 'CRLF'
 Invoke-TestGit @('reset','--','launcher.cmd')|Out-Null;[IO.File]::Delete((Join-Path $repo 'launcher.cmd'))

 $settings='src/main/java/me/primaryuan/carpet/CarpetPrimaryuanSettings.java'
 $languageFiles=@('zh_cn','zh_tw','en_us'|ForEach-Object {'src/main/resources/assets/carpet-pry-addition/lang/'+$_.ToString()+'.json'})
 foreach($file in $languageFiles){Put $file '{}'}
 Put $settings '@Rule(categories = {FEATURE}) public static boolean BadRule = false;'
 Invoke-TestGit (@('add','--',$settings)+$languageFiles)|Out-Null
 $env:PRY_TASK_FILES=ConvertTo-Json -InputObject (@($settings)+$languageFiles) -Compress
 Expect-Blocked '规则命名' {& $guard -Mode Staged} 'lowerCamelCase'
 Put $settings '@Rule(categories = {FEATURE}) public static boolean newRule = false;'
 Invoke-TestGit @('add','--',$settings)|Out-Null
 Expect-Blocked '规则三语缺失' {& $guard -Mode Staged} '三语name/desc'
 Invoke-TestGit (@('reset','--',$settings)+$languageFiles)|Out-Null
 foreach($file in @($settings)+$languageFiles){[IO.File]::Delete((Join-Path $repo $file))}

 $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0);$listener.Start()
 try{
  $port=$listener.LocalEndpoint.Port
  Put 'run/server.properties' "server-port=$port`nenable-rcon=false`n"
  Put 'server-test.txt' 'server test';Invoke-TestGit @('add','--','server-test.txt')|Out-Null;$env:PRY_TASK_FILES='["server-test.txt"]'
  Expect-Blocked '测试服未收尾' {& $guard -Mode Staged} '测试服端口.*仍在监听'
 }finally{$listener.Stop()}
 Invoke-TestGit @('reset','--','server-test.txt')|Out-Null;[IO.File]::Delete((Join-Path $repo 'server-test.txt'));[IO.File]::Delete((Join-Path $repo 'run/server.properties'))

 Invoke-TestGit @('checkout','--detach','HEAD')|Out-Null
 Expect-Blocked '工作分支不符' {& $guard -Mode Paths -Paths @('README.md')} 'master|分支'
 Invoke-TestGit @('checkout','master')|Out-Null

 Put 'old.txt' 'old';& $commit -Files @('old.txt') -Message 'fix: 先前事项'|Out-Null
 Put 'new.txt' 'new'
 Expect-Blocked '旧提交混推' {& $commit -Files @('new.txt') -Message 'fix: 新事项' -Push} '未授权|旧提交|提交范围'
 $local=Invoke-TestGit @('rev-parse','HEAD');$remoteHead=Invoke-TestGit @('rev-parse','origin/master')
 if($local -eq $remoteHead){throw '混推检查不应已更新远端'}
 Write-Output 'All commit guard checks passed.'
}finally{
 Set-Location $oldLocation
 $env:PRY_TASK_FILES=$oldAllowed;$env:PRY_PUSH_COMMITS=$oldCommits
 $env:PRY_TASK_BRANCH=$oldBranch
 # 只删除本次在系统临时目录建立的唯一测试目录。
 $full=[IO.Path]::GetFullPath($tempRoot);$base=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
 if($full.StartsWith($base+'\pry-guard-test-',[StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $full)){Remove-Item -LiteralPath $full -Recurse -Force}
}
