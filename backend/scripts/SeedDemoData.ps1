param(
  [string]$BaseUrl='http://localhost:8080',
  [string]$CatalogSnapshotPath,
  [string]$ExportCatalogSnapshot,
  [string]$StatePath=(Join-Path $PSScriptRoot '../.ringlab-demo-state.json'),
  [ValidateRange(1,500)][int]$BuildCount=60,
  [int]$RandomSeed=20260920,
  [datetime]$ReferenceTime=[datetime]::Parse('2026-09-20T12:00:00Z'),
  [ValidateRange(0,1)][double]$BoardRatio=.20,
  [switch]$Preview,
  [switch]$ValidateOnly,
  [switch]$Refresh,
  [switch]$Apply
)
$ErrorActionPreference='Stop'
$DemoPassword='RingLabDemo!2026'
. "$PSScriptRoot/CommunityDemoPlan.ps1"

function Assert-LoopbackUrl([string]$Url) {
  try {$uri=[uri]$Url}catch{throw 'BaseUrl must be a valid absolute URL.'}
  if(!$uri.IsAbsoluteUri -or !$uri.IsLoopback -or $uri.Scheme -notin @('http','https')){throw 'Demo tooling accepts only a loopback HTTP(S) URL.'}
}

function Get-StatusCode($Failure) {
  if($Failure.Exception.Response -and $Failure.Exception.Response.StatusCode){return [int]$Failure.Exception.Response.StatusCode}
  $null
}

function Invoke-RingLabApi {
  param([string]$Method,[string]$Path,$Body,[string]$Token,[int]$MaxRetries=3)
  $headers=@{};if($Token){$headers.Authorization="Bearer $Token"}
  $request=@{Uri="$($BaseUrl.TrimEnd('/'))/api$Path";Method=$Method;Headers=$headers}
  if($null -ne $Body){$request.ContentType='application/json';$request.Body=$Body|ConvertTo-Json -Depth 20}
  for($attempt=0;;$attempt++){
    try{return Invoke-RestMethod @request}catch{
      if((Get-StatusCode $_) -ne 429 -or $attempt -ge $MaxRetries){throw}
      $retryAfter=1
      if($_.Exception.Response.Headers.RetryAfter.Delta){$retryAfter=[Math]::Ceiling($_.Exception.Response.Headers.RetryAfter.Delta.TotalSeconds)}
      elseif($_.Exception.Response.Headers.Contains('Retry-After')){[void][int]::TryParse([string]$_.Exception.Response.Headers.GetValues('Retry-After')[0],[ref]$retryAfter)}
      Start-Sleep -Seconds ([Math]::Min(60,[Math]::Max(1,$retryAfter)))
    }
  }
}

function Get-LiveCatalog {
  [pscustomobject]@{
    racers=Invoke-RingLabApi GET '/racers'
    machines=Invoke-RingLabApi GET '/machines'
    parts=Invoke-RingLabApi GET '/machine-parts'
    gadgets=Invoke-RingLabApi GET '/gadgets'
    versions=Invoke-RingLabApi GET '/game-versions'
  }
}

function Assert-LocalDevelopmentDatabase {
  $root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
  if($IsWindows){
    $port=([uri]$BaseUrl).Port
    $listener=Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1
    if(!$listener){throw "No local development application is listening on port $port."}
    $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $expected=(Join-Path $root 'backend\target\ringlab-dev.jar')
    if(!$process.CommandLine -or $process.CommandLine -notlike "*$expected*"){throw "Port $port is not served by this checkout's Quarkus development application."}
  }
  $compose=Join-Path $root 'compose.yaml'
  if(!(Test-Path $compose)){throw 'Local compose.yaml was not found.'}
  $json=& docker compose -f $compose ps postgres --format json 2>&1
  if($LASTEXITCODE -ne 0 -or !$json){throw 'The RingLab PostgreSQL Compose service is not running.'}
  $service=$json|ConvertFrom-Json
  if($service.State -ne 'running' -or $service.Health -ne 'healthy' -or @($service.Publishers|Where-Object {$_.PublishedPort -eq 5432 -and $_.URL -in @('127.0.0.1','::1')}).Count -ne 1){throw 'PostgreSQL is not the healthy RingLab service bound to loopback port 5432.'}
  $required=[int](Get-ChildItem (Join-Path $root 'backend/src/main/resources/db/migration') -Filter 'V*__*.sql'|ForEach-Object{if($_.Name-match'^V(\d+)__'){[int]$Matches[1]}}|Measure-Object -Maximum).Maximum
  $applied=& docker compose -f $compose exec -T postgres psql -U ringlab -d ringlab -Atc 'select coalesce(max(version::integer),0) from flyway_schema_history where success' 2>&1
  if($LASTEXITCODE -ne 0 -or [int]$applied -lt $required){throw "Local database migrations are stale: required V$required, applied V$applied."}
  [pscustomobject]@{compose=$compose;requiredMigration=$required;appliedMigration=[int]$applied}
}

function Assert-Plan($Plan,$Catalog) {
  $errors=@();$buildKeys=@{};$partById=@{};$versionIds=@{};$gadgetById=@{}
  foreach($p in (ConvertTo-DemoArray $Catalog.parts)){$partById[[string]$p.id]=$p};foreach($v in (ConvertTo-DemoArray $Catalog.versions)){$versionIds[[string]$v.id]=$true};foreach($g in (ConvertTo-DemoArray $Catalog.gadgets)){$gadgetById[[string]$g.id]=$g}
  foreach($b in $Plan.builds){
    if($buildKeys.ContainsKey($b.key)){$errors+="$($b.key): duplicate fixture key"}else{$buildKeys[$b.key]=$b}
    $front=$partById[[string]$b.frontPartId];$rear=$partById[[string]$b.rearPartId];$tire=if($b.tirePartId){$partById[[string]$b.tirePartId]}else{$null}
    if(!$front -or $front.type -ne 'FRONT' -or !$rear -or $rear.type -ne 'REAR'){$errors+="$($b.key): missing FRONT or REAR part"}
    if($front.sourceMachineFamily -ne $b.family -or $rear.sourceMachineFamily -ne $b.family){$errors+="$($b.key): cross-family part combination"}
    if($b.family -eq 'BOARD' -and $tire){$errors+="$($b.key): Board has a tire"};if($b.family -eq 'STANDARD' -and (!$tire -or $tire.sourceMachineFamily -ne 'STANDARD')){$errors+="$($b.key): Standard build lacks a Standard tire"}
    if(!$versionIds.ContainsKey([string]$b.gameVersionId)){$errors+="$($b.key): unknown version"}
    $costs=@();$seen=@{};foreach($id in $b.gadgetIds){if($seen.ContainsKey([string]$id)){$errors+="$($b.key): duplicate gadget"};$seen[[string]$id]=$true;$g=$gadgetById[[string]$id];if(!$g -or $null -eq $g.slotCost){$errors+="$($b.key): unknown gadget cost"}else{$costs+=[int]$g.slotCost}}
    if(!(Test-GadgetPlateFit $costs)){$errors+="$($b.key): gadgets do not fit the 2x3 plate"}
    if($b.family -eq 'BOARD' -and $b.description -match '(?i)tires?'){$errors+="$($b.key): Board description mentions a tire"}
    if($b.stock -and $b.description -match '(?i)mixed'){$errors+="$($b.key): stock description says mixed"}
    if($b.remixedFromKey -and !$buildKeys.ContainsKey($b.remixedFromKey)){$errors+="$($b.key): remix parent does not precede child"}
  }
  if($errors.Count){throw "Demo plan validation failed:`n - $($errors-join"`n - ")"}
}

function Get-Request($Build,$BuildIds) {
  @{title=$Build.title;description=$Build.description;racerId=$Build.racerId;frontPartId=$Build.frontPartId;rearPartId=$Build.rearPartId;tirePartId=$Build.tirePartId;gameVersionId=$Build.gameVersionId;remixedFromBuildId=if($Build.remixedFromKey){$BuildIds[$Build.remixedFromKey]}else{$null};gadgetIds=@($Build.gadgetIds)}
}

function Get-Fingerprint($Value) {
  $json=$Value|ConvertTo-Json -Depth 15 -Compress
  $bytes=[Text.Encoding]::UTF8.GetBytes($json);[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Get-ActualRequest($Build) {
  @{title=$Build.title;description=$Build.description;racerId=$Build.racer.id;frontPartId=$Build.frontPart.id;rearPartId=$Build.rearPart.id;tirePartId=if($Build.tirePart){$Build.tirePart.id}else{$null};gameVersionId=if($Build.gameVersion){$Build.gameVersion.id}else{$null};remixedFromBuildId=if($Build.remixedFrom){$Build.remixedFrom.id}else{$null};gadgetIds=@($Build.gadgets.id)}
}

function Read-State {
  if(Test-Path $StatePath){return Get-Content -Raw -LiteralPath $StatePath|ConvertFrom-Json}
  [pscustomobject]@{schemaVersion=1;users=@();builds=@();votes=@();comments=@()}
}

function Save-State($State) {
  $directory=Split-Path -Parent $StatePath;if($directory -and !(Test-Path $directory)){New-Item -ItemType Directory -Path $directory|Out-Null}
  $temporary="$StatePath.tmp";$State|ConvertTo-Json -Depth 20|Set-Content -LiteralPath $temporary -Encoding utf8;Move-Item -Force -LiteralPath $temporary -Destination $StatePath
}

function Get-AllBuilds {
  $items=@();$page=0;do{$result=Invoke-RingLabApi GET "/builds?sort=newest&page=$page&size=50";$items+=@(ConvertTo-DemoArray $result.items);$page++}while($page*50 -lt $result.total);$items
}

function Show-Summary($Plan,$Actions,$WholeBuilds) {
  $latest=$Plan.newestVersion.version
  Write-Host "Plan: $($Plan.users.Count) users, $($Plan.builds.Count) builds, $($Plan.votes.Count) votes, $($Plan.comments.Count) comments."
  Write-Host "Patches: $(@($Plan.builds|Where-Object version -eq $latest).Count) newest ($latest), $(@($Plan.builds|Where-Object version -ne $latest).Count) older."
  Write-Host "Families: $(@($Plan.builds|Where-Object family -eq 'STANDARD').Count) Standard, $(@($Plan.builds|Where-Object family -eq 'BOARD').Count) Extreme Gear. Setups: $(@($Plan.builds|Where-Object stock).Count) stock, $(@($Plan.builds|Where-Object {!$_.stock}).Count) mixed, $(@($Plan.builds|Where-Object remixedFromKey).Count) remixes."
  if($Actions){foreach($group in ($Actions|Group-Object action|Sort-Object Name)){$count=if($group.Name -eq 'retain-manual'){($group.Group|Measure-Object count -Sum).Sum}else{$group.Count};Write-Host "Refresh $($group.Name): $count"}}
  if($WholeBuilds){$known=@($WholeBuilds|Where-Object gameVersion);$wholeLatest=@($known|Where-Object {$_.gameVersion.version -eq $latest}).Count;Write-Host "Whole database: $($WholeBuilds.Count) builds; $wholeLatest newest and $($known.Count-$wholeLatest) older among versioned builds."}
}

Assert-LoopbackUrl $BaseUrl
if($Preview){
  if(!$CatalogSnapshotPath){throw 'Offline preview requires -CatalogSnapshotPath. Use -ExportCatalogSnapshot with live read-only validation first.'}
  $catalog=Get-Content -Raw -LiteralPath $CatalogSnapshotPath|ConvertFrom-Json
  $plan=Get-CommunityDemoPlan $catalog $BuildCount $RandomSeed $ReferenceTime $BoardRatio;Assert-Plan $plan $catalog;Show-Summary $plan $null $null;return $plan
}

$database=Assert-LocalDevelopmentDatabase
$catalog=Get-LiveCatalog
if($ExportCatalogSnapshot){$catalog|ConvertTo-Json -Depth 12|Set-Content -LiteralPath $ExportCatalogSnapshot -Encoding utf8;Write-Host "Wrote explicit live catalog snapshot to $ExportCatalogSnapshot";return}
$plan=Get-CommunityDemoPlan $catalog $BuildCount $RandomSeed $ReferenceTime $BoardRatio
Assert-Plan $plan $catalog
$families=@(ConvertTo-DemoArray $catalog.machines|Select-Object -Expand family -Unique)
if('BOARD' -notin $families){throw 'Running backend catalog does not expose BOARD / Extreme Gear support.'}
Write-Host "Preflight passed: local Compose PostgreSQL, Flyway V$($database.appliedMigration), current machine-family catalog."
if($ValidateOnly){Show-Summary $plan $null $null;Write-Host 'Live validation completed with zero writes.';return $plan}

$state=Read-State;$allBuilds=@(Get-AllBuilds);$stateByKey=@{};foreach($record in @($state.builds)){$stateByKey[$record.key]=$record}
$legacyMatches=@{}
foreach($b in $plan.builds){$legacyMatches[$b.key]=@($allBuilds|Where-Object {$_.title -ceq $b.title -and $_.author.username -ceq "ringlab_demo_$($b.owner)"})}
$legacyDatasetPresent=@($legacyMatches.Values|Where-Object {$_.Count -eq 1}).Count -ge [Math]::Min(10,$plan.builds.Count)
$actions=@();$buildIds=@{}
foreach($b in $plan.builds){
  $record=$stateByKey[$b.key];$actual=$null
  if($record){try{$actual=Invoke-RingLabApi GET "/builds/$($record.id)"}catch{}}
  if(!$actual){
    $matches=@($legacyMatches[$b.key])
    if($matches.Count -eq 1){
      $actual=$matches[0];$buildIds[$b.key]=[string]$actual.id
      if(Test-DemoLegacyAdoptable $matches.Count $actual.createdAt $actual.updatedAt){$actions+=[pscustomobject]@{key=$b.key;action='update';reason='unambiguous unedited legacy fixture';id=$actual.id;legacy=$true}}
      else{$actions+=[pscustomobject]@{key=$b.key;action='conflict';reason='legacy fixture was edited after creation';id=$actual.id}}
      continue
    }
  }
  if(!$actual){
    if($legacyDatasetPresent){$actions+=[pscustomobject]@{key=$b.key;action='conflict';reason='legacy dataset exists but this key may have been renamed or removed';id=$null}}
    else{$actions+=[pscustomobject]@{key=$b.key;action='add';reason='missing';id=$null}}
    continue
  }
  $buildIds[$b.key]=[string]$actual.id;$actualFingerprint=Get-Fingerprint (Get-ActualRequest $actual)
  if($actualFingerprint -ne $record.fingerprint){$actions+=[pscustomobject]@{key=$b.key;action='conflict';reason='managed fields were edited';id=$actual.id};continue}
  $desired=Get-Request $b $buildIds;$desiredFingerprint=Get-Fingerprint $desired
  $actions+=[pscustomobject]@{key=$b.key;action=if($desiredFingerprint -eq $actualFingerprint){'retain'}else{'update'};reason='trusted managed record';id=$actual.id}
}
$manualCount=@($allBuilds|Where-Object {$id=[string]$_.id;$id -notin @($actions.id|ForEach-Object{[string]$_})}).Count
$actions+=[pscustomobject]@{key='(unrelated/manual builds)';action='retain-manual';reason='outside fixture ownership';id=$null;count=$manualCount}
Show-Summary $plan $actions $allBuilds
foreach($conflict in @($actions|Where-Object action -eq 'conflict')){Write-Warning "$($conflict.key): $($conflict.reason)"}
if(!$Apply){Write-Host 'Preview only. Add -Apply to perform the displayed additions/updates; use -Refresh -Apply for an explicit refresh.';return [pscustomobject]@{plan=$plan;actions=$actions}}

$bootstrap=Invoke-RingLabApi POST '/dev-fixtures/demo-accounts' @{accounts=@($plan.users|Select-Object key,username,email);password=$DemoPassword}
$sessions=@{};foreach($entry in (ConvertTo-DemoArray $bootstrap)){$sessions[$entry.key]=$entry.session}
foreach($b in $plan.builds){
  $action=@($actions|Where-Object key -eq $b.key)[0];if($action.action -in @('conflict','retain')){continue}
  $request=Get-Request $b $buildIds
  if($action.action -eq 'add'){$actual=Invoke-RingLabApi POST '/builds' $request $sessions[$b.owner].token}else{if(!$Refresh){continue};$actual=Invoke-RingLabApi PUT "/builds/$($action.id)" $request $sessions[$b.owner].token}
  $buildIds[$b.key]=[string]$actual.id;$fingerprint=Get-Fingerprint (Get-ActualRequest $actual)
  $state.builds=@($state.builds|Where-Object key -ne $b.key)+[pscustomobject]@{key=$b.key;id=[string]$actual.id;fingerprint=$fingerprint}
  if($action.legacy){
    $state.votes=@($state.votes)+@($plan.votes|Where-Object build -eq $b.key|Select-Object -Expand key)
    $state.comments=@($state.comments)+@($plan.comments|Where-Object build -eq $b.key|Select-Object -Expand key)
  }
  Save-State $state
}
foreach($vote in $plan.votes){
  if($vote.key -in @($state.votes)){continue};if(!$buildIds.ContainsKey($vote.build)){continue}
  $current=Invoke-RingLabApi GET "/builds/$($buildIds[$vote.build])/vote" $null $sessions[$vote.user].token
  if($current.myVote -eq 0){Invoke-RingLabApi PUT "/builds/$($buildIds[$vote.build])/vote" @{value=$vote.value} $sessions[$vote.user].token|Out-Null;$state.votes=@($state.votes)+$vote.key;Save-State $state}
}
foreach($comment in $plan.comments){
  if($comment.key -in @($state.comments)){continue};if(!$buildIds.ContainsKey($comment.build)){continue}
  $existing=Invoke-RingLabApi GET "/builds/$($buildIds[$comment.build])/comments?page=0&size=50"
  if(!(@(ConvertTo-DemoArray $existing.items)|Where-Object {$_.authorId -eq $sessions[$comment.user].user.id -and $_.text -ceq $comment.text})){Invoke-RingLabApi POST "/builds/$($buildIds[$comment.build])/comments" @{text=$comment.text} $sessions[$comment.user].token|Out-Null}
  $state.comments=@($state.comments)+$comment.key;Save-State $state
}
Write-Host "Demo fixture apply completed. State: $StatePath"
