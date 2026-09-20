param(
  [string]$BaseUrl='http://127.0.0.1:8080',
  [string]$CatalogSnapshotPath,
  [string]$ExportCatalogSnapshot,
  [string]$StatePath=(Join-Path $PSScriptRoot '../.ringlab-demo-state.json'),
  [ValidateRange(1,500)][int]$BuildCount=60,
  [int]$RandomSeed=20260920,
  [datetime]$ReferenceTime=[datetime]::Parse('2026-09-20T12:00:00Z'),
  [ValidateRange(0,1)][double]$BoostMachineRatio=.20,
  [switch]$Preview,
  [switch]$ValidateOnly,
  [switch]$Refresh,
  [switch]$PromoteFeatured,
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
      if($retryAfter -gt 120){throw 'API rate limit requires more than two minutes; stop and rerun later.'}
      $remaining=[Math]::Max(1,$retryAfter)
      while($remaining -gt 0){$wait=[Math]::Min(60,$remaining);Start-Sleep -Seconds $wait;$remaining-=$wait}
    }
  }
}

function Get-LiveCatalog {
  $catalog=[pscustomobject]@{
    racers=Invoke-RingLabApi GET '/racers'
    machines=Invoke-RingLabApi GET '/machines'
    parts=Invoke-RingLabApi GET '/machine-parts'
    gadgets=Invoke-RingLabApi GET '/gadgets'
    versions=Invoke-RingLabApi GET '/game-versions'
    stats=@{}
  }
  foreach($version in $catalog.versions){$catalog.stats[[string]$version.id]=Invoke-RingLabApi GET "/stats/catalog?gameVersionId=$($version.id)"}
  $catalog
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
    $connections=@(Get-NetTCPConnection -OwningProcess $listener.OwningProcess -RemotePort 5432 -State Established -ErrorAction SilentlyContinue)
    if(!$connections.Count -or @($connections|Where-Object RemoteAddress -notin @('127.0.0.1','::1')).Count){throw 'Cannot verify a loopback database connection from the running backend.'}
  }
  $dockerHost=& docker context inspect --format '{{.Endpoints.docker.Host}}'
  if($LASTEXITCODE -ne 0 -or $dockerHost -notlike 'npipe://*'){throw 'Demo writes require the local Windows Docker engine.'}
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
    if($b.machineType -notin @('SPEED','ACCELERATION','HANDLING','POWER','BOOST')){$errors+="$($b.key): unknown machine type"}
    foreach($part in @($front,$rear,$tire)|Where-Object{$_}) {
      $source=@($Catalog.machines|Where-Object id -eq $part.sourceMachineId)
      if($source.Count -ne 1 -or !$part.racingType -or $source[0].racingType -ne $b.machineType -or $part.racingType -ne $b.machineType){$errors+="$($b.key): incompatible source machine type"}
    }
    if($b.machineType -eq 'BOOST' -and $tire){$errors+="$($b.key): Boost has a tire"};if($b.machineType -ne 'BOOST' -and (!$tire -or $tire.type -ne 'TIRE')){$errors+="$($b.key): non-Boost build lacks a tire"}
    if(!$versionIds.ContainsKey([string]$b.gameVersionId)){$errors+="$($b.key): unknown version"}
    $costs=@();$seen=@{};foreach($id in $b.gadgetIds){if($seen.ContainsKey([string]$id)){$errors+="$($b.key): duplicate gadget"};$seen[[string]$id]=$true;$g=$gadgetById[[string]$id];if(!$g -or $null -eq $g.slotCost){$errors+="$($b.key): unknown gadget cost"}else{$costs+=[int]$g.slotCost}}
    if(!(Test-GadgetPlateFit $costs)){$errors+="$($b.key): gadgets do not fit the 2x3 plate"}
    if($b.machineType -eq 'BOOST' -and $b.description -match '(?i)tires?'){$errors+="$($b.key): Boost description mentions a tire"}
    if($b.stock -and $b.description -match '(?i)mixed'){$errors+="$($b.key): stock description says mixed"}
    if($b.remixedFromKey -and !$buildKeys.ContainsKey($b.remixedFromKey)){$errors+="$($b.key): remix parent does not precede child"}
    if($Catalog.stats){
      $versionStats=if($Catalog.stats -is [System.Collections.IDictionary]){$Catalog.stats[[string]$b.gameVersionId]}else{$Catalog.stats.PSObject.Properties[[string]$b.gameVersionId].Value}
      $values=@($versionStats.racers.PSObject.Properties[[string]$b.racerId].Value)
      foreach($id in @($b.frontPartId,$b.rearPartId,$b.tirePartId)|Where-Object{$_}){$values+= $versionStats.machineParts.PSObject.Properties[[string]$id].Value}
      foreach($value in $values){foreach($name in @('speed','acceleration','handling','power','boost')){if($null -eq $value -or $null -eq $value.$name){$errors+="$($b.key): catalog stats are unavailable for a selected component ($name)";break}}}
    }
  }
  if($errors.Count){throw "Demo plan validation failed:`n - $($errors-join"`n - ")"}
}

function Get-Request($Build,$BuildIds) {
  [ordered]@{title=$Build.title;description=$Build.description;racerId=$Build.racerId;frontPartId=$Build.frontPartId;rearPartId=$Build.rearPartId;tirePartId=$Build.tirePartId;gameVersionId=$Build.gameVersionId;remixedFromBuildId=if($Build.remixedFromKey){$BuildIds[$Build.remixedFromKey]}else{$null};gadgetIds=@($Build.gadgetIds)}
}

function Get-Fingerprint($Value) {
  $json=$Value|ConvertTo-Json -Depth 15 -Compress
  $bytes=[Text.Encoding]::UTF8.GetBytes($json);[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}

function Get-ActualRequest($Build) {
  [ordered]@{title=$Build.title;description=$Build.description;racerId=$Build.racer.id;frontPartId=$Build.frontPart.id;rearPartId=$Build.rearPart.id;tirePartId=if($Build.tirePart){$Build.tirePart.id}else{$null};gameVersionId=if($Build.gameVersion){$Build.gameVersion.id}else{$null};remixedFromBuildId=if($Build.remixedFrom){$Build.remixedFrom.id}else{$null};gadgetIds=@($Build.gadgets.id)}
}

function Read-State {
  if(Test-Path $StatePath){
    $state=Get-Content -Raw -LiteralPath $StatePath|ConvertFrom-Json
    if($state.schemaVersion -ne 1){throw 'Unsupported seed-state schema; preserve the manifest and review it before continuing.'}
    # V1 fingerprints cover request fields only, so family removal does not invalidate ownership.
    return $state
  }
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
  Write-Host "Machine types: $(($Plan.builds|Group-Object machineType|ForEach-Object {"$($_.Name): $($_.Count)"}) -join ', '). Setups: $(@($Plan.builds|Where-Object stock).Count) stock, $(@($Plan.builds|Where-Object {!$_.stock}).Count) mixed."
  Write-Host "Fresh-plan remix origins: $(@($Plan.builds|Where-Object remixedFromKey).Count). Refresh preserves each existing build's immutable origin."
  if($Actions){foreach($group in ($Actions|Group-Object action|Sort-Object Name)){$count=if($group.Name -eq 'retain-manual'){($group.Group|Measure-Object count -Sum).Sum}else{$group.Count};Write-Host "Refresh $($group.Name): $count"}}
  if($WholeBuilds){$known=@($WholeBuilds|Where-Object gameVersion);$wholeLatest=@($known|Where-Object {$_.gameVersion.version -eq $latest}).Count;Write-Host "Whole database: $($WholeBuilds.Count) builds; $wholeLatest newest and $($known.Count-$wholeLatest) older among versioned builds."}
}

Assert-LoopbackUrl $BaseUrl
if($Preview){
  if(!$CatalogSnapshotPath){throw 'Offline preview requires -CatalogSnapshotPath. Use -ExportCatalogSnapshot with live read-only validation first.'}
  $catalog=Get-Content -Raw -LiteralPath $CatalogSnapshotPath|ConvertFrom-Json
  $plan=Get-CommunityDemoPlan $catalog $BuildCount $RandomSeed $ReferenceTime $BoostMachineRatio;Assert-Plan $plan $catalog;Show-Summary $plan $null $null;return $plan
}

$database=Assert-LocalDevelopmentDatabase
$catalog=Get-LiveCatalog
if($ExportCatalogSnapshot){$catalog|ConvertTo-Json -Depth 12|Set-Content -LiteralPath $ExportCatalogSnapshot -Encoding utf8;Write-Host "Wrote explicit live catalog snapshot to $ExportCatalogSnapshot";return}
$plan=Get-CommunityDemoPlan $catalog $BuildCount $RandomSeed $ReferenceTime $BoostMachineRatio
Assert-Plan $plan $catalog
$types=@(ConvertTo-DemoArray $catalog.machines|Select-Object -Expand racingType -Unique)
if('BOOST' -notin $types){throw 'Running backend catalog does not expose Boost / Extreme Gear support.'}
Write-Host "Preflight passed: local Compose PostgreSQL, Flyway V$($database.appliedMigration), current machine-type catalog."
if($ValidateOnly){Show-Summary $plan $null $null;Write-Host 'Live validation completed with zero writes.';return $plan}

$state=Read-State;$allBuilds=@(Get-AllBuilds);$stateByKey=@{};foreach($record in @($state.builds)){$stateByKey[$record.key]=$record}
$legacyMatches=@{}
foreach($b in $plan.builds){$legacyMatches[$b.key]=@($allBuilds|Where-Object {$_.title -ceq $b.legacyTitle -and $_.author.username -ceq "ringlab_demo_$($b.owner)"})}
$legacyDatasetPresent=@($legacyMatches.Values|Where-Object {$_.Count -eq 1}).Count -ge [Math]::Min(10,$plan.builds.Count)
$actions=@();$buildIds=@{}
foreach($b in $plan.builds){
  $record=$stateByKey[$b.key];$actual=$null
  if($record){$actual=@($allBuilds|Where-Object id -eq $record.id)|Select-Object -First 1}
  if(!$actual){
    $matches=@($legacyMatches[$b.key])
    if($matches.Count -eq 1){
      $actual=$matches[0];$buildIds[$b.key]=[string]$actual.id
      if(Test-DemoLegacyAdoptable $matches.Count $actual.createdAt $actual.updatedAt){$actions+=[pscustomobject]@{key=$b.key;action='update';reason='legacy candidate; exact account and content rechecked before apply';id=$actual.id;legacy=$true;before=(Get-Fingerprint (Get-ActualRequest $actual))}}
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
  if($actual.author.username -cne "ringlab_demo_$($b.owner)") {throw "Fixture owner mismatch: $($b.key)"}
  if($actualFingerprint -ne $record.fingerprint -and $actualFingerprint -ne $record.pendingFingerprint){$actions+=[pscustomobject]@{key=$b.key;action='conflict';reason='managed fields were edited';id=$actual.id};continue}
  $desired=Get-Request $b $buildIds;$desiredFingerprint=Get-Fingerprint $desired
  # Remix origin is immutable in BuildService.edit. Never claim that refresh adds provenance.
  $desired.remixedFromBuildId=if($actual.remixedFrom){$actual.remixedFrom.id}else{$null}
  $desiredFingerprint=Get-Fingerprint $desired
  $actions+=[pscustomobject]@{key=$b.key;action=if($desiredFingerprint -eq $actualFingerprint){'retain'}else{'update'};reason='trusted managed record';id=$actual.id;before=$actualFingerprint}
}
$manualCount=@($allBuilds|Where-Object {$id=[string]$_.id;$id -notin @($actions.id|ForEach-Object{[string]$_})}).Count
$actions+=[pscustomobject]@{key='(unrelated/manual builds)';action='retain-manual';reason='outside fixture ownership';id=$null;count=$manualCount}
Show-Summary $plan $actions $allBuilds
foreach($conflict in @($actions|Where-Object action -eq 'conflict')){Write-Warning "$($conflict.key): $($conflict.reason)"}
if($PromoteFeatured){Write-Host 'Featured Sonic: add missing votes from 65 reserved fixture voters; preserve all existing votes. This is fictional demo engagement.'}
if(!$Apply){Write-Host 'Preview only. Add -Apply to perform the displayed additions/updates; use -Refresh -Apply for an explicit refresh.';return [pscustomobject]@{plan=$plan;actions=$actions}}

# Compare the API identities with the proven local database before any mutation.
$sql="select json_build_object('builds',(select coalesce(json_agg(b),'[]') from builds b),'gadgets',(select coalesce(json_agg(g),'[]') from build_gadgets g),'votes',(select coalesce(json_agg(v),'[]') from votes v),'comments',(select coalesce(json_agg(c),'[]') from comments c));"
$beforeJson=& docker compose -f $database.compose exec -T postgres psql -U ringlab -d ringlab -Atc $sql
if($LASTEXITCODE -ne 0){throw 'Could not read the verified local database before apply.'}
$before=$beforeJson | ConvertFrom-Json
if(Compare-Object @($before.builds.id|Sort-Object) @($allBuilds.id|Sort-Object)){throw 'API builds do not match the verified local database. Refusing writes.'}
foreach($action in $actions|Where-Object action -eq 'update'){
  $row=@($before.builds|Where-Object id -eq $action.id)[0]
  $api=@($allBuilds|Where-Object id -eq $action.id)[0]
  if($row.author_id -ne $api.author.id -or $row.title -cne $api.title -or $row.description -cne $api.description){throw "API/database content mismatch for $($action.key)."}
}
$backupPath="$StatePath.before-$([datetime]::UtcNow.ToString('yyyyMMddTHHmmssfff')).json"
$beforeJson | Set-Content -LiteralPath $backupPath -Encoding utf8
Write-Host "Saved local community snapshot: $backupPath"
$bootstrap=Invoke-RingLabApi POST '/dev-fixtures/demo-accounts' @{accounts=@($plan.users|Select-Object key,username,email);password=$DemoPassword}
$sessions=@{};foreach($entry in (ConvertTo-DemoArray $bootstrap)){$sessions[$entry.key]=$entry.session}
foreach($b in $plan.builds){
  $action=@($actions|Where-Object key -eq $b.key)[0];if($action.action -in @('conflict','retain')){continue}
  $request=Get-Request $b $buildIds
  if($action.action -eq 'update'){
    if(!$Refresh){continue}
    $current=Invoke-RingLabApi GET "/builds/$($action.id)"
    if($current.author.id -ne $sessions[$b.owner].user.id -or (Get-Fingerprint (Get-ActualRequest $current)) -ne $action.before){throw "Concurrent edit or ownership conflict for $($b.key); stopping without overwriting it."}
    $request.remixedFromBuildId=if($current.remixedFrom){$current.remixedFrom.id}else{$null}
    $previous=@($state.builds|Where-Object key -eq $b.key)|Select-Object -First 1
    $preserveEngagement=[bool]($action.legacy -or $previous.preserveEngagement)
    $state.builds=@($state.builds|Where-Object key -ne $b.key)+[pscustomobject]@{
      key=$b.key;id=[string]$current.id;fingerprint=$action.before
      pendingFingerprint=(Get-Fingerprint $request);preserveEngagement=$preserveEngagement
    }
    Save-State $state
  } else {$preserveEngagement=$false}
  if($action.action -eq 'add'){$actual=Invoke-RingLabApi POST '/builds' $request $sessions[$b.owner].token}else{if(!$Refresh){continue};$actual=Invoke-RingLabApi PUT "/builds/$($action.id)" $request $sessions[$b.owner].token}
  $buildIds[$b.key]=[string]$actual.id;$fingerprint=Get-Fingerprint (Get-ActualRequest $actual)
  $state.builds=@($state.builds|Where-Object key -ne $b.key)+[pscustomobject]@{key=$b.key;id=[string]$actual.id;fingerprint=$fingerprint;preserveEngagement=$preserveEngagement}
  Save-State $state
  Write-Host "Saved fixture $($b.key)."
}
foreach($vote in $plan.votes){
  if($vote.key -in @($state.votes)){continue};if(!$buildIds.ContainsKey($vote.build)){continue}
  if(@($actions|Where-Object {$_.key -eq $vote.build -and $_.action -eq 'conflict'}).Count){continue}
  $record=@($state.builds|Where-Object key -eq $vote.build)|Select-Object -First 1
  if(!$record -or ($record.preserveEngagement -and !($PromoteFeatured -and $vote.build -eq 'sonic-speed'))){continue}
  $current=Invoke-RingLabApi GET "/builds/$($buildIds[$vote.build])/vote" $null $sessions[$vote.user].token
  if($current.myVote -eq 0){Invoke-RingLabApi PUT "/builds/$($buildIds[$vote.build])/vote" @{value=$vote.value} $sessions[$vote.user].token|Out-Null}
  $state.votes=@($state.votes)+$vote.key;Save-State $state
}
foreach($comment in $plan.comments){
  if($comment.key -in @($state.comments)){continue};if(!$buildIds.ContainsKey($comment.build)){continue}
  $record=@($state.builds|Where-Object key -eq $comment.build)|Select-Object -First 1
  if(!$record -or $record.preserveEngagement -or @($actions|Where-Object {$_.key -eq $comment.build -and $_.action -eq 'conflict'}).Count){continue}
  $existing=Invoke-RingLabApi GET "/builds/$($buildIds[$comment.build])/comments?page=0&size=50"
  if(!(@(ConvertTo-DemoArray $existing.items)|Where-Object {$_.authorId -eq $sessions[$comment.user].user.id -and $_.text -ceq $comment.text})){Invoke-RingLabApi POST "/builds/$($buildIds[$comment.build])/comments" @{text=$comment.text} $sessions[$comment.user].token|Out-Null}
  $state.comments=@($state.comments)+$comment.key;Save-State $state
}
Write-Host "Demo fixture apply completed. State: $StatePath"
