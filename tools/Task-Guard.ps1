param(
 [ValidateSet('Paths','Staged','Message','Push')][string]$Mode='Staged',
 [string[]]$Paths=@(),[string]$MessageFile,[string]$MessageText,[string]$Remote='origin'
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
function Git-Lines([string[]]$GitArguments){
 $result=@(& git -c core.quotepath=false @GitArguments 2>&1)
 if($LASTEXITCODE){throw ($result -join "`n")}
 return @($result|ForEach-Object {$_.ToString()})
}
$root=(Git-Lines @('rev-parse','--show-toplevel')|Select-Object -First 1)
function Assert-Branch {
 $branch=(Git-Lines @('branch','--show-current')|Select-Object -First 1)
 $expected=if($env:PRY_TASK_BRANCH){$env:PRY_TASK_BRANCH}else{'master'}
 if($branch -ne $expected){throw ('工作分支应为 '+$expected+'，当前为 '+$branch+'；不自动建分支或切分支。')}
 return $branch
}
function Assert-Path([string]$Path){
 if(!$Path -or [IO.Path]::IsPathRooted($Path) -or $Path -match '(^|[\\/])\.\.([\\/]|$)'){throw ('必须明确指定仓库内相对路径: '+$Path)}
 $p=$Path.Replace('\','/')
 if($p -eq 'gradle/wrapper/gradle-wrapper.jar'){return}
 if($p -match '(^|/)(\.git|\.gradle|\.gradle-user|\.zcode|\.codex|\.agents|\.claude|\.trae|build|out|classes|logs|run(?:\.[^/]*)?|libraries|libs|__pycache__)(/|$)' -or
    $p -match '\.(log|class|pyc|jar|zip|lck|part|bak|swp|pem|key|p12)$' -or
    $p -match '(^|/)(server\.properties|eula\.txt|\.env(?:\..*)?|.*\.pid|.*-pids\.json|hs_err_pid.*)$' -or
    $p -match '^(REVIEW|IMPLEMENTATION-PLAN|FOLLOWUP-REVIEW[^/]*|CLIENT-EXPERIENCE-REVIEW)\.md$'){
  throw ('禁止提交运行产物、私有配置或临时审查资料: '+$p)
 }
}
function Has-Signature([string]$Text){
 return $Text -match '(?im)^\s*(?:co-authored-by\s*:|(?:[^a-z\r\n]{0,8})?generated\s+(?:with|by)\s+.*(?:chatgpt|codex|claude|gemini))'
}
function Assert-TestsStopped {
 $configs=@();$project=Split-Path $root;$inventory=Join-Path $project 'workspace.json'
 if(Test-Path -LiteralPath $inventory){
  $data=Get-Content -LiteralPath $inventory -Raw|ConvertFrom-Json
  foreach($server in $data.servers){$configs+=Join-Path $server.directory 'server.properties'}
 }
 $local=Join-Path $root 'run/server.properties'
 if((Test-Path -LiteralPath $local) -and $local -notin $configs){$configs+=$local}
 if(!$configs.Count){return}
 $listeners=@(& netstat.exe -ano -p tcp 2>&1)
 if($LASTEXITCODE -or !$listeners.Count){throw '无法确认测试服监听状态，先处理查询错误。'}
 foreach($config in $configs){
  if(!(Test-Path -LiteralPath $config)){throw ('测试服登记路径失效: '+$config)}
  $props=@{};foreach($line in Get-Content -LiteralPath $config){if($line -match '^([^#=]+)=(.*)$'){$props[$matches[1]]=$matches[2]}}
  $ports=@($props['server-port']);if($props['enable-rcon'] -eq 'true'){$ports+=$props['rcon.port']}
  foreach($port in $ports){if($port -and @($listeners|Where-Object {$_ -match (':'+[regex]::Escape($port)+'\s+.*LISTENING\s+')})){
   throw ('测试服端口 '+$port+' 仍在监听，请先正常保存停服或核对归属: '+$config)
  }}
 }
}

if($Mode -eq 'Message'){
 $message=if($MessageFile){[IO.File]::ReadAllText($MessageFile)}else{$MessageText}
 if(Has-Signature $message){throw '提交信息包含署名、生成声明或协作尾注。'}
 $subject=($message -split '\r?\n')[0]
 if($subject.Length -gt 100 -or $subject -notmatch '^(feat|fix|docs|chore|test|refactor|build|ci|perf|style|revert)(\([^\r\n()]+\))?: .*[\u4e00-\u9fff]'){
  throw '提交信息须采用简短中文 conventional commits 格式。'
 }
 return
}
$branch=Assert-Branch
if($Mode -eq 'Paths'){foreach($path in $Paths){Assert-Path $path};return}
if($Mode -eq 'Staged'){
 if(!$env:PRY_TASK_FILES){throw '未声明本次文件范围，请使用 tools/Commit-Task.ps1 -Files 明确文件列表。'}
 $allowed=@($env:PRY_TASK_FILES|ConvertFrom-Json)|ForEach-Object {$_.Replace('\','/')}
 $files=@(Git-Lines @('diff','--cached','--no-renames','--name-only','--diff-filter=ACMD'))
 if(!$files.Count){throw '没有本次暂存改动。'}
 $deleted=@(Git-Lines @('diff','--cached','--name-only','--diff-filter=D'))
 foreach($file in $files){
  if($file -notin $allowed){throw ('无关暂存文件超出本次范围: '+$file)}
  Assert-Path $file
  if($file -in $deleted){continue}
  if($file -match '\.(java|json|md|ps1|gradle|yml|yaml|cmd|bat|txt)$' -or $file -match '(^|/)AGENTS\.md$'){
   $text=(Git-Lines @('show',(':'+$file))) -join "`n"
   if($text -match '(?m)^(<<<<<<< |>>>>>>> )'){throw ('存在冲突标记: '+$file)}
   if(Has-Signature $text){throw ('文件含工具署名或生成声明: '+$file)}
   if($file -like '*.json'){
    $null=$text|ConvertFrom-Json -ErrorAction Stop
    if($file -match '/lang/[^/]+\.json$'){
     $keys=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
     $document=[Text.Json.JsonDocument]::Parse($text,[Text.Json.JsonDocumentOptions]::new())
     try{foreach($property in $document.RootElement.EnumerateObject()){if(!$keys.Add($property.Name)){throw ('语言文件重复键: '+$file+' '+$property.Name)}}}finally{$document.Dispose()}
    }
   }
  }
  if($file -match '\.(cmd|bat)$'){
   $bytes=[IO.File]::ReadAllBytes((Join-Path $root $file))
   for($i=0;$i -lt $bytes.Length;$i++){if($bytes[$i] -eq 10 -and ($i -eq 0 -or $bytes[$i-1] -ne 13)){throw ('批处理文件必须使用CRLF换行: '+$file)}}
  }
 }
 $settings='src/main/java/me/primaryuan/carpet/CarpetPrimaryuanSettings.java'
 if($settings -in $files -and $settings -notin $deleted){
  $text=(Git-Lines @('show',(':'+$settings))) -join "`n"
  $rules=@([regex]::Matches($text,'@Rule\([\s\S]*?\)\s*public static \w+ (\w+)')|ForEach-Object {$_.Groups[1].Value})
  foreach($rule in $rules){if($rule -cnotmatch '^[a-z][A-Za-z0-9]*$'){throw ('规则名必须使用lowerCamelCase: '+$rule)}}
  foreach($language in @('zh_cn','zh_tw','en_us')){
   $path='src/main/resources/assets/carpet-pry-addition/lang/'+$language+'.json'
   try{$translation=((Git-Lines @('show',(':'+$path))) -join "`n")|ConvertFrom-Json}catch{throw ('规则缺少三语文件: '+$language)}
   foreach($rule in $rules){foreach($suffix in @('name','desc')){$key='carpet.rule.'+$rule+'.'+$suffix;$entry=$translation.PSObject.Properties[$key];if(!$entry -or ![string]$entry.Value){throw ('规则缺少三语name/desc: '+$key+' '+$language)}}}
  }
 }
 Assert-TestsStopped
 return
}
if($Mode -eq 'Push'){
 if($branch -ne 'master'){throw 'GitHub只保留master，禁止推送本地任务分支。'}
 if($Remote -ne 'origin'){throw '请先核对非origin远端，不自动推送。'}
 if(@(Git-Lines @('status','--porcelain=v1','--untracked-files=all')).Count){throw '工作区未干净，不能宣称本次已收尾。'}
 Assert-TestsStopped
 if(!$env:PRY_PUSH_COMMITS){throw '未声明推送提交范围，请先核对未推送的旧提交。'}
 $allowed=@($env:PRY_PUSH_COMMITS|ConvertFrom-Json)
 $updates=[Console]::In.ReadToEnd() -split '\r?\n'|Where-Object {$_}
 foreach($update in $updates){
  $parts=$update -split ' '
  if($parts.Count -ne 4 -or $parts[0] -ne ('refs/heads/'+$branch) -or $parts[2] -ne ('refs/heads/'+$branch) -or $parts[1] -match '^0+$' -or $parts[3] -match '^0+$'){
   throw '推送分支范围不符，不自动建分支、删分支或推标签。'
  }
  & git merge-base --is-ancestor $parts[3] $parts[1]
  if($LASTEXITCODE){throw '远端历史不是本次提交的祖先，禁止覆盖或强推。'}
  $outgoing=@(Git-Lines @('rev-list',($parts[3]+'..'+$parts[1])))
  if(@($outgoing|Where-Object {$_ -notin $allowed}).Count -or @($allowed|Where-Object {$_ -notin $outgoing}).Count){
   throw ('推送包含未授权的旧提交或提交范围不符；实际待推送 '+$outgoing.Count+' 个，声明 '+$allowed.Count+' 个。')
  }
 }
}
