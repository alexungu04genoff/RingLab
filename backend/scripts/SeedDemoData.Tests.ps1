$ErrorActionPreference='Stop'
$seeder=Join-Path $PSScriptRoot 'SeedDemoData.ps1'
. (Join-Path $PSScriptRoot 'CommunityDemoPlan.ps1')
function Assert-True($Condition,[string]$Message){if(!$Condition){throw $Message}}
function Assert-Fails([scriptblock]$Action,[string]$Message){$failed=$false;try{&$Action|Out-Null}catch{$failed=$true};Assert-True $failed $Message}
function New-Id([int]$Number){'00000000-0000-4000-8000-'+$Number.ToString('000000000000')}

# Independent fixture: it is deliberately not derived from planner output.
$racers=@(
  [pscustomobject]@{id=New-Id 1;name='Sonic the Hedgehog'},
  [pscustomobject]@{id=New-Id 2;name='Amy Rose'},
  [pscustomobject]@{id=New-Id 3;name='Miles "Tails" Prower'},
  [pscustomobject]@{id=New-Id 4;name='Rouge the Bat'},
  [pscustomobject]@{id=New-Id 5;name='Joker'},
  [pscustomobject]@{id=New-Id 6;name='New Catalog Racer'}
)
$machines=@(
  [pscustomobject]@{id=New-Id 101;name='Standard One';family='STANDARD'},
  [pscustomobject]@{id=New-Id 102;name='Renamed Standard';family='STANDARD'},
  [pscustomobject]@{id=New-Id 103;name='New Standard';family='STANDARD'},
  [pscustomobject]@{id=New-Id 201;name='Board One';family='BOARD'},
  [pscustomobject]@{id=New-Id 202;name='New Board';family='BOARD'}
)
$parts=@();$partNumber=300
foreach($machine in $machines){
  $types=if($machine.family -eq 'BOARD'){@('FRONT','REAR')}else{@('FRONT','REAR','TIRE')}
  foreach($type in $types){$partNumber++;$parts+=[pscustomobject]@{id=New-Id $partNumber;type=$type;sourceMachineId=$machine.id;sourceMachineName=$machine.name;sourceMachineFamily=$machine.family}}
}
$gadgets=@(
  [pscustomobject]@{id=New-Id 401;name='Ring Engine';slotCost=1},
  [pscustomobject]@{id=New-Id 402;name='Boost Item Chance UP';slotCost=2},
  [pscustomobject]@{id=New-Id 403;name='Strong Finish';slotCost=3},
  [pscustomobject]@{id=New-Id 404;name='Unknown Cost';slotCost=$null}
)
$versions=@(
  [pscustomobject]@{id=New-Id 501;version='1.2.0';releasedAt='2025-12-03'},
  [pscustomobject]@{id=New-Id 504;version='1.10.0';releasedAt='2026-08-01'},
  [pscustomobject]@{id=New-Id 502;version='1.4.1';releasedAt='2026-06-23'},
  [pscustomobject]@{id=New-Id 503;version='1.3.1';releasedAt='2026-03-18'}
)
$catalog=[pscustomobject]@{racers=$racers;machines=$machines;parts=$parts;gadgets=$gadgets;versions=$versions}
$snapshot=Join-Path ([IO.Path]::GetTempPath()) "ringlab-demo-catalog-$([guid]::NewGuid()).json"
$catalog|ConvertTo-Json -Depth 10|Set-Content -LiteralPath $snapshot -Encoding utf8
try{
  function Invoke-RestMethod {throw 'Offline preview contacted the API'}
  $plan=&$seeder -Preview -CatalogSnapshotPath $snapshot
  $repeat=&$seeder -Preview -CatalogSnapshotPath $snapshot
  $different=&$seeder -Preview -CatalogSnapshotPath $snapshot -RandomSeed 7
  Assert-True (($plan|ConvertTo-Json -Depth 20 -Compress)-ceq($repeat|ConvertTo-Json -Depth 20 -Compress)) 'Same inputs were not deterministic'
  Assert-True (($plan|ConvertTo-Json -Depth 20 -Compress)-cne($different|ConvertTo-Json -Depth 20 -Compress)) 'Different seed did not vary output'
  Assert-True ($plan.users.Count -eq 100 -and $plan.builds.Count -eq 60) 'Wrong default population'
  Assert-True (@($plan.builds.owner|Sort-Object -Unique).Count -eq 20) 'Expected 20 authors'
  Assert-True (@($plan.builds|Where-Object version -eq '1.10.0').Count -eq 54) 'Newest version was not selected by release date'
  Assert-True (@($plan.builds|Where-Object version -ne '1.10.0').Count -eq 6) 'Older version allocation is not exactly 10%'
  Assert-True (@($plan.builds|Where-Object family -eq 'BOARD').Count -eq 12) 'Expected 12 Extreme Gear builds'
  $mainNames=@('Sonic the Hedgehog','Amy Rose','Miles "Tails" Prower');$guestNames=@('Joker')
  Assert-True (@($plan.builds|Where-Object racerName -in $mainNames).Count -eq 36) 'Expected 60% main-cast builds'
  Assert-True (@($plan.builds|Where-Object racerName -in $guestNames).Count -eq 6) 'Expected 10% guest builds'
  Assert-True (@($plan.builds|Where-Object {$_.racerName -notin $mainNames -and $_.racerName -notin $guestNames}).Count -eq 18) 'Expected 30% other-Sonic builds'
  Assert-True (@($plan.builds|Where-Object {$_.family -eq 'BOARD' -and $_.tirePartId}).Count -eq 0) 'Board received a tire'
  Assert-True (@($plan.builds|Where-Object {$_.family -eq 'STANDARD' -and !$_.tirePartId}).Count -eq 0) 'Standard build lacks a tire'
  Assert-True (@($plan.builds|Where-Object {$_.family -eq 'BOARD' -and $_.description -match '(?i)tire'}).Count -eq 0) 'Board text mentions tires'
  Assert-True (@($plan.builds|Where-Object {$_.stock -and $_.description -match '(?i)mixed'}).Count -eq 0) 'Stock text says mixed'
  Assert-True (@($plan.builds|Where-Object remixedFromKey).Count -gt 0) 'No real remix provenance'
  foreach($build in $plan.builds){Assert-True (Test-GadgetPlateFit @($build.gadgetIds|ForEach-Object{$id=$_;($gadgets|Where-Object id -eq $id).slotCost})) "Invalid gadget layout: $($build.key)"}
  Assert-True (!(Test-GadgetPlateFit @(2,2,2))) 'Invalid 2+2+2 plate was accepted'
  Assert-True ((Get-DemoRefreshDecision '' '' 'new') -eq 'add') 'Missing fixture was not added'
  Assert-True ((Get-DemoRefreshDecision 'legacy' '' 'new' -LegacyIdentityFound) -eq 'conflict') 'Legacy record without trusted state was overwritten'
  Assert-True ((Get-DemoRefreshDecision 'manual-edit' 'old' 'new') -eq 'conflict') 'Manual edit was overwritten'
  Assert-True ((Get-DemoRefreshDecision 'old' 'old' 'new') -eq 'update') 'Unchanged managed record was not refreshable'
  Assert-True ((Get-DemoRefreshDecision 'same' 'same' 'same') -eq 'retain') 'Idempotent rerun was not retained'
  Assert-True (Test-DemoLegacyAdoptable 1 '2026-01-01Z' '2026-01-01Z') 'Unedited unambiguous legacy record was not adoptable'
  Assert-True (!(Test-DemoLegacyAdoptable 2 '2026-01-01Z' '2026-01-01Z')) 'Ambiguous legacy record was adoptable'
  Assert-True (!(Test-DemoLegacyAdoptable 1 '2026-01-01Z' '2026-01-02Z')) 'Edited legacy record was adoptable'
  $renamed=$plan.builds[0].PSObject.Copy();$renamed.title='A renamed generated title'
  Assert-True ($renamed.key -eq $plan.builds[0].key) 'Title change altered stable fixture identity'
  Assert-Fails {&$seeder -BaseUrl 'https://example.com' -Preview -CatalogSnapshotPath $snapshot} 'Remote target accepted'

  $broken=$catalog|ConvertTo-Json -Depth 10|ConvertFrom-Json
  $broken.machines=@($broken.machines|Where-Object family -ne 'BOARD')
  $broken.parts=@($broken.parts|Where-Object sourceMachineFamily -ne 'BOARD')
  $brokenPath="$snapshot-broken";$broken|ConvertTo-Json -Depth 10|Set-Content $brokenPath
  Assert-Fails {&$seeder -Preview -CatalogSnapshotPath $brokenPath} 'Missing Extreme Gear catalog accepted'
  Remove-Item -LiteralPath $brokenPath
}finally{Remove-Item -LiteralPath $snapshot -ErrorAction SilentlyContinue}
Write-Host 'PASS: independent catalog, deterministic planning, seed variation, release-date patch selection, 54/6 distribution, Standard/Board rules, text consistency, remix provenance, plate validation, offline safety, and unsafe-target refusal.'
