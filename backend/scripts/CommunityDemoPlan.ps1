# Pure demo planning: no network, database, or filesystem writes.
function Test-GadgetPlateFit([int[]]$Costs) {
  if (@($Costs | Where-Object { $_ -lt 1 -or $_ -gt 3 }).Count) { return $false }
  $ordered = @($Costs | Sort-Object -Descending)
  function Test-Rows([int]$Index,[int]$First,[int]$Second) {
    if ($Index -eq $ordered.Count) { return $true }
    $cost=$ordered[$Index]
    if ($First+$cost -le 3 -and (Test-Rows ($Index+1) ($First+$cost) $Second)) { return $true }
    return $Second+$cost -le 3 -and (Test-Rows ($Index+1) $First ($Second+$cost))
  }
  Test-Rows 0 0 0
}

function Invoke-DemoShuffle([object[]]$Items,[System.Random]$Random) {
  $copy=@($Items)
  for($i=$copy.Count-1;$i -gt 0;$i--){$j=$Random.Next($i+1);$copy[$i],$copy[$j]=$copy[$j],$copy[$i]}
  $copy
}

function ConvertTo-DemoArray($Value) {
  $result=@()
  foreach($item in @($Value)) {
    if($item -is [System.Array]) { foreach($nested in $item) { $result += $nested } }
    else { $result += $item }
  }
  $result
}

function Get-DemoRefreshDecision {
  param([string]$CurrentFingerprint,[string]$PreviousFingerprint,[string]$DesiredFingerprint,[switch]$LegacyIdentityFound)
  if(!$CurrentFingerprint){return 'add'}
  if(!$PreviousFingerprint){return 'conflict'}
  if($CurrentFingerprint -ne $PreviousFingerprint){return 'conflict'}
  if($CurrentFingerprint -eq $DesiredFingerprint){return 'retain'}
  'update'
}

function Test-DemoLegacyAdoptable([int]$ExactIdentityMatches,$CreatedAt,$UpdatedAt) {
  $ExactIdentityMatches -eq 1 -and $null -ne $CreatedAt -and $CreatedAt -eq $UpdatedAt
}

function Get-CommunityDemoPlan {
  param(
    [Parameter(Mandatory=$true)]$Catalog,
    [ValidateRange(1,500)][int]$BuildCount=60,
    [int]$RandomSeed=20260920,
    [datetime]$ReferenceTime=[datetime]::Parse('2026-09-20T12:00:00Z'),
    [ValidateRange(0,1)][double]$BoostMachineRatio=.20
  )
  $random=[System.Random]::new($RandomSeed)
  $racers=@(ConvertTo-DemoArray $Catalog.racers | Sort-Object name,id)
  $machines=@(ConvertTo-DemoArray $Catalog.machines | Sort-Object racingType,name,id)
  $parts=@(ConvertTo-DemoArray $Catalog.parts | Sort-Object sourceMachineName,type,id)
  $gadgets=@(ConvertTo-DemoArray $Catalog.gadgets | Where-Object { $null -ne $_.slotCost -and $_.slotCost -ge 1 -and $_.slotCost -le 3 } | Sort-Object name,id)
  $versions=@(ConvertTo-DemoArray $Catalog.versions | Where-Object { $_.releasedAt -and [datetime]::Parse(([string]($_.releasedAt))) -le $ReferenceTime } | Sort-Object @{Expression={[datetime]::Parse(([string]($_.releasedAt)))};Descending=$true},version,id)
  if(!$racers.Count){throw 'Catalog has no racers.'};if(!$gadgets.Count){throw 'Catalog has no gadgets with verified slot costs.'};if($versions.Count -lt 2){throw 'At least one newest and one older released game version are required.'}
  $byMachine=@{};foreach($m in $machines){$byMachine[[string]$m.id]=@($parts | Where-Object sourceMachineId -eq $m.id)}
  $standards=@($machines | Where-Object racingType -in @('SPEED','ACCELERATION','HANDLING','POWER') | Where-Object {$t=@($byMachine[[string]$_.id].type);$t.Count -eq 3 -and 'FRONT' -in $t -and 'REAR' -in $t -and 'TIRE' -in $t})
  $boards=@($machines | Where-Object racingType -eq 'BOOST' | Where-Object {$t=@($byMachine[[string]$_.id].type);$t.Count -eq 2 -and 'FRONT' -in $t -and 'REAR' -in $t})
  if(!$standards.Count){throw 'Catalog has no complete STANDARD machine.'};if($BoostMachineRatio -gt 0 -and !$boards.Count){throw 'Catalog has no complete BOARD / Extreme Gear machine.'}

  $mainNames=@('Sonic the Hedgehog','Shadow the Hedgehog','Miles "Tails" Prower','Amy Rose','Knuckles the Echidna')
  $guestNames=@('Joker','Mega Man','SpongeBob SquarePants','PAC-MAN','Ichiban Kasuga','Steve','Alex','Creeper',
    'AiAi','Arle','Axel','Blinky','Donatello','Goro Majima (Captain Majima)','Hatsune Miku','Leonardo',
    'Michelangelo','NiGHTS','Patrick Star','Proto Man','Raphael','Red')
  $main=@($racers | Where-Object name -in $mainNames);$other=@($racers | Where-Object {$_.name -notin $mainNames -and $_.name -notin $guestNames});$guests=@($racers | Where-Object name -in $guestNames)
  if(!$main.Count){$main=$racers};if(!$other.Count){$other=$main};if(!$guests.Count){$guests=$other}
  $authors=@('amy','tails','shadow','sonic','knuckles','rouge','cream','blaze','silver','vector','blueblur','ultimatefan','rosegrid','metalhead','chaotix','eggman','bigfan','phantom','megafan','crossover')
  $favorites=@{amy=@('Amy Rose','Cream & Cheese');tails=@('Miles "Tails" Prower','Sonic the Hedgehog');shadow=@('Shadow the Hedgehog','Rouge the Bat');sonic=@('Sonic the Hedgehog','Miles "Tails" Prower');knuckles=@('Knuckles the Echidna','Big the Cat');rouge=@('Rouge the Bat','Shadow the Hedgehog');cream=@('Cream & Cheese','Amy Rose');blaze=@('Blaze the Cat','Silver the Hedgehog');silver=@('Silver the Hedgehog','Blaze the Cat');vector=@('Vector the Crocodile','Espio the Chameleon');blueblur=@('Sonic the Hedgehog','Classic Sonic');ultimatefan=@('Shadow the Hedgehog','Sonic the Hedgehog');rosegrid=@('Amy Rose','Cream & Cheese');metalhead=@('Metal Sonic','Dr. Eggman');chaotix=@('Espio the Chameleon','Vector the Crocodile');eggman=@('Dr. Eggman','Metal Sonic');bigfan=@('Big the Cat');phantom=@('Joker','Shadow the Hedgehog');megafan=@('Mega Man','Sonic the Hedgehog');crossover=@('SpongeBob SquarePants','Joker')}
  $users=@();foreach($key in $authors){$users+=[pscustomobject]@{key=$key;username="ringlab_demo_$key";email="ringlab_demo_$key@example.test";author=$true}}
  foreach($n in 1..80){$s=$n.ToString('00');$users+=[pscustomobject]@{key="member$s";username="ringlab_demo_member_$s";email="ringlab_demo_member_$s@example.test";author=$false}}

  $legacyKeys=@('cornering','items','route','acceleration','recovery','boost','shadow','finish','amy-drift','tails-grid','shadow-laps','sonic-speed','sonic-boost','sonic-route','knuckles-power','knuckles-endurance','knuckles-ring')
  $legacyTitles=@('[DEMO] Cornering Showcase','[DEMO] Item Control Practice','[DEMO] Ring Route Session','[DEMO] Acceleration Lab','[DEMO] Power Recovery Run','[DEMO] Boost Timing Notes','[DEMO] Dark Reaper Sprint','[DEMO] Consistent Finish Plan','[DEMO] Drift Line Routine','[DEMO] Starting Grid Notes','[DEMO] Night Circuit Notes','[DEMO] Speedster Session','[DEMO] Boost Route Notes','[DEMO] Balanced Start','[DEMO] Power Lineup','[DEMO] Steady Lap Plan','[DEMO] Ring Collection Notes')
  $legacyOwners=@('amy','amy','amy','tails','tails','tails','shadow','shadow','amy','tails','shadow','sonic','sonic','sonic','knuckles','knuckles','knuckles')
  $communityTitles=[ordered]@{
    amy=@('Amy is still my first pick','Another evening with Amy','Borrowing a Tails setup')
    tails=@('Tails and a notebook full of ideas','Keeping it simple with Tails')
    shadow=@('The Shadow setup I keep returning to','Shadow with a different machine mix','Fine, one evening as Sonic')
    sonic=@('Sonic for the weekend','One more Sonic experiment','Team Sonic, different driver','Back to the blue blur')
    knuckles=@('Knuckles stays on the character select','My other Knuckles loadout')
    rouge=@('A little more Rouge on the grid','Rouge, take two')
    cream=@('A spot on the grid for Cream')
    blaze=@('Blaze deserves more builds','My second saved Blaze setup')
    silver=@('Silver fans checking in','Still experimenting with Silver')
    vector=@('Someone has to bring Vector')
    blueblur=@('My usual Sonic pick','Taking Tails out for a change','Sonic, but a different garage visit')
    ultimatefan=@('Another Shadow main reporting in','Shadow and my spare machine setup')
    rosegrid=@('Pink on the starting grid','Amy with my alternate loadout')
    metalhead=@('Metal Sonic gets a garage slot','A second idea for Metal','Back to my original Metal pick')
    chaotix=@('Espio, finally on my saved list','Switching detectives for a night')
    eggman=@('The doctor joins the grid','Eggman has another idea')
    bigfan=@('A quiet evening with Big')
    phantom=@('A guest in the Sonic garage','Keeping a second Joker setup')
    megafan=@('The other blue character','Mega Man with a new combination')
    crossover=@('A very different kind of weekend pick','Trying another guest character')
  }
  $communityBlueprints=@()
  foreach($profile in $communityTitles.GetEnumerator()){
    for($index=0;$index -lt $profile.Value.Count;$index++){
      $communityBlueprints += [pscustomobject]@{owner=$profile.Key;key="community-$($profile.Key)-$($index+1)";title=$profile.Value[$index]}
    }
  }
  $activity=@(6,5,5,4,4,4,4,3,3,3,3,3,3,2,2,2,2,1,1,1);$ownerList=@();for($i=0;$i -lt $authors.Count;$i++){1..$activity[$i]|ForEach-Object{$ownerList+=$authors[$i]}}
  $ownerList=@($ownerList|Select-Object -First $BuildCount);while($ownerList.Count -lt $BuildCount){$ownerList+=$authors[$ownerList.Count%$authors.Count]}
  $blueprints=@();$ownerCounts=@{};foreach($a in $authors){$ownerCounts[$a]=0}
  for($i=0;$i -lt $BuildCount;$i++){
    if($i -lt $legacyKeys.Count){
      $owner=$legacyOwners[$i];$key=$legacyKeys[$i];$title=$legacyTitles[$i]
    } elseif(($i - $legacyKeys.Count) -lt $communityBlueprints.Count) {
      $legacy=$communityBlueprints[$i - $legacyKeys.Count];$owner=$legacy.owner;$key=$legacy.key;$title=$legacy.title
    } else {
      $owner=$authors[($i-$legacyKeys.Count)%$authors.Count];$ownerCounts[$owner]++;$key="generated-$owner-$($ownerCounts[$owner])";$title=@('Regular setup','Weeknight notes','Small variation','Trying something new','Stock run','Alternate route','Quiet experiment','Garage remix')[$random.Next(8)]+" — $owner $($ownerCounts[$owner])"
    }
    $blueprints+=[pscustomobject]@{key=$key;owner=$owner;title=$title;ordinal=$i}
  }
  $boardCount=if($boards.Count){[Math]::Round($BuildCount*$BoostMachineRatio,[MidpointRounding]::AwayFromZero)}else{0}
  $boardIndexes=@((Invoke-DemoShuffle (0..($BuildCount-1)) $random)|Select-Object -First $boardCount)
  $olderCount=[Math]::Round($BuildCount*.10,[MidpointRounding]::AwayFromZero);if($olderCount -lt 1){$olderCount=1}
  $olderIndexes=@((Invoke-DemoShuffle @(0..($BuildCount-1) | Where-Object { $blueprints[$_].key -ne 'sonic-speed' }) $random)|Select-Object -First $olderCount);$latest=$versions[0];$older=@($versions|Select-Object -Skip 1)
  # These themes use catalog names and costs, never assumed effects or stat bonuses.
  $themes=@(
    @{ label='ring route'; names=@('Ring Engine','Ring Doubler','Route Planner Bounty','Ring Mercy','130 Ring Limit','Ring Evolution') },
    @{ label='drift practice'; names=@('Ultimate Charge','Perfect Charge Boost','Drift Charge Kit','Friction Drift','Spin Drift','Drift Spinner Kit') },
    @{ label='item options'; names=@('Inventory Swap','Item Keeper','Item Stock Plus','Lucky Pair','Attack Item Chance UP','Defense Item Chance UP') },
    @{ label='recovery notes'; names=@('Quick Recovery','Damage Mercy','Crash Pads','Item Mercy','Substitute Item','Damage Evolution') },
    @{ label='start and finish'; names=@('Starting Boost Bounty','Invincible Start','Quick Starter','Strong Finish','Slow Starter','Invincible Finish') },
    @{ label='air tricks'; names=@('Ultimate Air Trick','Perfect Landing','Ace Pilot Kit','Extended Slipstream','Sea Dog Kit','All-Rounder Kit') }
  )
  $profiles=@{}
  for($i=0; $i -lt $authors.Count; $i++) {
    $profiles[$authors[$i]]=@{
      theme=$themes[$i % $themes.Count]
      machines=@(Invoke-DemoShuffle $machines $random | Select-Object -First 3)
    }
  }
  $builds=@()
  foreach($b in $blueprints){
    $availableTypes=@($standards.racingType | Sort-Object -Unique)
    $machineType=if($b.ordinal -in $boardIndexes){'BOOST'}else{$availableTypes[$random.Next($availableTypes.Count)]}
    $profile=$profiles[$b.owner]
    $pool=@(@($standards)+@($boards) | Where-Object racingType -eq $machineType)
    $preferredMachines=@($pool | Where-Object id -in @($profile.machines.id))
    $frontPool=if($preferredMachines.Count -and $random.NextDouble() -lt .78){$preferredMachines}else{$pool}
    $fm=$frontPool[$random.Next($frontPool.Count)];$mixed=$random.NextDouble() -lt .38;$rm=if($mixed -and $pool.Count -gt 1){@($pool | Where-Object id -ne $fm.id)[$random.Next($pool.Count-1)]}else{$fm};$tm=if($machineType -ne 'BOOST' -and $mixed -and $pool.Count -gt 1 -and $random.NextDouble() -lt .5){$pool[$random.Next($pool.Count)]}else{$fm}
    $front=@($byMachine[[string]$fm.id] | Where-Object type -eq 'FRONT')[0];$rear=@($byMachine[[string]$rm.id] | Where-Object type -eq 'REAR')[0];$tire=if($machineType -ne 'BOOST'){@($byMachine[[string]$tm.id] | Where-Object type -eq 'TIRE')[0]}else{$null}
    $group=if($b.ordinal -lt [Math]::Round($BuildCount*.60)){$main}elseif($b.ordinal -lt [Math]::Round($BuildCount*.90)){$other}else{$guests}
    $fav=@($favorites[$b.owner] | ForEach-Object{$wanted=$_;$group | Where-Object name -ceq $wanted} | Where-Object{$_});$racer=if($fav.Count -and $random.NextDouble() -lt .82){$fav[$random.Next($fav.Count)]}else{$group[$random.Next($group.Count)]}
    if($b.key -eq 'sonic-speed') {
      $sonic=@($racers | Where-Object name -CEQ 'Sonic the Hedgehog')
      if($sonic.Count -ne 1){throw 'Featured fixture sonic-speed requires one canonical Sonic the Hedgehog racer.'}
      $racer=$sonic[0]
    }
    $preferred=@($gadgets | Where-Object name -in $profile.theme.names)
    $candidates=@(Invoke-DemoShuffle $preferred $random)+@(Invoke-DemoShuffle @($gadgets | Where-Object name -notin $profile.theme.names) $random)
    $selected=@();foreach($g in $candidates){$trial=@($selected+$g);if($trial.Count -le 4 -and (Test-GadgetPlateFit @($trial.slotCost))){$selected=$trial};if($selected.Count -ge 2 -and $random.NextDouble() -lt .45){break}}
    $version=if($b.ordinal -in $olderIndexes){$older[$random.Next($older.Count)]}else{$latest};$stock=$fm.id -eq $rm.id -and ($machineType -eq 'BOOST' -or $fm.id -eq $tm.id);$parent=if($b.ordinal -ge 8 -and $b.ordinal%11 -eq 0){$builds[$b.ordinal-5].key}else{$null}
    $setup=if($machineType -eq 'BOOST'){if($stock){"A straightforward $($fm.name) Extreme Gear setup."}else{"An Extreme Gear mix using the $($fm.name) front and $($rm.name) rear."}}else{if($stock){"A straightforward stock $($fm.name) setup."}else{"A mixed $machineType setup with $($fm.name), $($rm.name), and $($tm.name) parts."}}
    $tone=@('I keep coming back to this one.','Still deciding whether this becomes my regular setup.','A small experiment I wanted to save.','Any thoughts on the gadget order?','Keeping a spare setup for next time.','Nothing ambitious today; just saving my current picks.')[$random.Next(6)]
    $title=@("$($racer.name): $($profile.theme.label)","An evening with $($racer.name)","$($fm.name), take two","$($racer.name) and a garage experiment","A spare $($machineType.ToLowerInvariant()) setup","Trying $($selected[0].name)")[$random.Next(6)]
    if($b.key -eq 'sonic-speed'){$title='Sonic — my regular garage pick'}
    $description="$setup $tone"
    if($b.ordinal % 3 -ne 0){$description+=" Keeping $($selected[0].name) with $($selected[1].name) for this version of the loadout."}
    $builds+=[pscustomobject]@{key=$b.key;owner=$b.owner;title=$title;legacyTitle=$b.title;description=$description;racerId=$racer.id;racerName=$racer.name;machineType=$machineType;frontPartId=$front.id;frontMachine=$fm.name;rearPartId=$rear.id;rearMachine=$rm.name;tirePartId=if($tire){$tire.id}else{$null};tireMachine=if($tire){$tm.name}else{$null};gameVersionId=$version.id;version=$version.version;releasedAt=$version.releasedAt;gadgetIds=@($selected.id);gadgetNames=@($selected.name);stock=$stock;remixedFromKey=$parent}
  }
  $votes=@();$comments=@()
  foreach($b in $builds){
    $roll=$random.Next(100)
    $attention=if($roll -lt 22){0}elseif($roll -lt 62){$random.Next(1,5)}elseif($roll -lt 90){$random.Next(5,14)}else{$random.Next(18,36)}
    $candidates=@(Invoke-DemoShuffle @($users | Where-Object key -ne $b.owner) $random)
    # A visible Sonic entry is an explicit editorial fixture, not a ranking rule.
    if($b.key -eq 'sonic-speed'){$attention=65}
    for($i=0;$i -lt [Math]::Min($attention,$candidates.Count);$i++){
      $down=if($roll -ge 84 -and $roll -lt 92){.45}else{.12}
      if($b.key -eq 'sonic-speed'){$down=0}
      $votes+=[pscustomobject]@{key="vote/$($candidates[$i].key)/$($b.key)";user=$candidates[$i].key;build=$b.key;value=if($random.NextDouble() -lt $down){-1}else{1}}
    }
    $cc=if(!$attention){0}elseif($random.NextDouble() -lt .5){0}else{[Math]::Min(4,1+$random.Next([Math]::Max(1,[Math]::Min(4,$attention))))}
    for($i=0;$i -lt $cc;$i++){
      $speaker=$candidates[($i+2)%$candidates.Count]
      $texts=@("Nice to see another $($b.racerName) setup.","What made you choose the $($b.rearMachine) rear here?",'I would probably change one gadget, but the setup is interesting.',"How has $($b.gadgetNames[0]) felt with this combination?")
      $comments+=[pscustomobject]@{key="comment/$($b.key)/$i";user=$speaker.key;build=$b.key;text=$texts[$i%4]}
    }
    if($cc -ge 2 -and $random.NextDouble() -lt .55){$comments+=[pscustomobject]@{key="comment/$($b.key)/author";user=$b.owner;build=$b.key;text='Mostly curiosity. I am comparing it with my other saved setup.'}}
  }
  [pscustomobject]@{schemaVersion=2;seed=$RandomSeed;referenceTime=$ReferenceTime.ToUniversalTime().ToString('o');newestVersion=$latest;users=$users;builds=$builds;votes=$votes;comments=$comments}
}
