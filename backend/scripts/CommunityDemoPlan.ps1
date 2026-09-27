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
    [ValidateRange(0,1)][double]$BoostMachineRatio=.20,
    [switch]$ExpandedCommunity
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
  if($ExpandedCommunity){$authors+=@('moonlit','coffeelaps','lastcorner','mintgear','weekendgrid','raincheck','nightowl','copperline','cloudnine','secondlap','peachpit','pocketboost','smallturns','arcadehour','spareparts','sundaydriver','neonroute','quietgarage','driftjournal','latebraker')}
  $favorites=@{amy=@('Amy Rose','Cream & Cheese');tails=@('Miles "Tails" Prower','Sonic the Hedgehog');shadow=@('Shadow the Hedgehog','Rouge the Bat');sonic=@('Sonic the Hedgehog','Miles "Tails" Prower');knuckles=@('Knuckles the Echidna','Big the Cat');rouge=@('Rouge the Bat','Shadow the Hedgehog');cream=@('Cream & Cheese','Amy Rose');blaze=@('Blaze the Cat','Silver the Hedgehog');silver=@('Silver the Hedgehog','Blaze the Cat');vector=@('Vector the Crocodile','Espio the Chameleon');blueblur=@('Sonic the Hedgehog','Classic Sonic');ultimatefan=@('Shadow the Hedgehog','Sonic the Hedgehog');rosegrid=@('Amy Rose','Cream & Cheese');metalhead=@('Metal Sonic','Dr. Eggman');chaotix=@('Espio the Chameleon','Vector the Crocodile');eggman=@('Dr. Eggman','Metal Sonic');bigfan=@('Big the Cat');phantom=@('Joker','Shadow the Hedgehog');megafan=@('Mega Man','Sonic the Hedgehog');crossover=@('SpongeBob SquarePants','Joker')}
  $users=@();foreach($key in $authors){$users+=[pscustomobject]@{key=$key;username="ringlab_demo_$key";email="ringlab_demo_$key@example.test";author=$true}}
  $memberCount=if($ExpandedCommunity){120}else{80}
  foreach($n in 1..$memberCount){$s=$n.ToString('00');$users+=[pscustomobject]@{key="member$s";username="ringlab_demo_member_$s";email="ringlab_demo_member_$s@example.test";author=$false}}
  $maps=@(ConvertTo-DemoArray $Catalog.maps | Where-Object id | Sort-Object catalogOrder,id)
  if($ExpandedCommunity -and $maps.Count -lt 3){throw 'Expanded community requires the verified map catalog.'}

  $legacyKeys=@('cornering','items','route','acceleration','recovery','boost','shadow','finish','amy-drift','tails-grid','shadow-laps','sonic-speed','sonic-boost','sonic-route','knuckles-power','knuckles-endurance','knuckles-ring')
  $legacyTitles=@('[DEMO] Cornering Showcase','[DEMO] Item Control Practice','[DEMO] Ring Route Session','[DEMO] Acceleration Lab','[DEMO] Power Recovery Run','[DEMO] Boost Timing Notes','[DEMO] Dark Reaper Sprint','[DEMO] Consistent Finish Plan','[DEMO] Drift Line Routine','[DEMO] Starting Grid Notes','[DEMO] Night Circuit Notes','[DEMO] Speedster Session','[DEMO] Boost Route Notes','[DEMO] Balanced Start','[DEMO] Power Lineup','[DEMO] Steady Lap Plan','[DEMO] Ring Collection Notes')
  $legacyOwners=@('amy','amy','amy','tails','tails','tails','shadow','shadow','amy','tails','shadow','sonic','sonic','sonic','knuckles','knuckles','knuckles')
  $wilsonFixtures=[ordered]@{
    cornering=[pscustomobject]@{title='[Wilson Demo] Small sample, perfect approval';upvotes=3;downvotes=0;description='Three upvotes, no downvotes. Compare with "Large sample, strong approval": Best rated also considers how many people voted.'}
    items=[pscustomobject]@{title='[Wilson Demo] Large sample, strong approval';upvotes=40;downvotes=1;description='Forty upvotes and one downvote. More feedback gives this build stronger ranking confidence than "Small sample, perfect approval".'}
    'community-shadow-2'=[pscustomobject]@{title='[Wilson Demo] Same score, unanimous approval';upvotes=15;downvotes=0;description='Fifteen upvotes, no downvotes. It has the same net score as "Same score, divided opinion", with stronger approval.'}
    acceleration=[pscustomobject]@{title='[Wilson Demo] Same score, divided opinion';upvotes=25;downvotes=10;description='Twenty-five upvotes and ten downvotes. Compare with "Same score, unanimous approval": the same net score does not mean the same confidence.'}
    recovery=[pscustomobject]@{title='[Wilson Demo] Close confidence scores: sixteen clean votes';upvotes=16;downvotes=0;description='Sixteen upvotes, no downvotes. Its Wilson confidence is about 0.80639, just above "Close confidence scores: thirty-eight approvals" even though both round to 0.81.'}
    boost=[pscustomobject]@{title='[Wilson Demo] Close confidence scores: thirty-eight approvals';upvotes=38;downvotes=3;description='Thirty-eight upvotes and three downvotes. Its Wilson confidence is about 0.80572, just below "Close confidence scores: sixteen clean votes"; ranking keeps the full precision.'}
    shadow=[pscustomobject]@{title='[Wilson Demo] Unrated build';upvotes=0;downvotes=0;description='No votes yet, so there is no approval evidence for Best rated.'}
    finish=[pscustomobject]@{title='[Wilson Demo] Evenly split feedback';upvotes=5;downvotes=5;description='Five upvotes and five downvotes. Evenly divided feedback still gives more approval evidence than no votes.'}
    'amy-drift'=[pscustomobject]@{title='[Wilson Demo] Negative score with positive votes';upvotes=20;downvotes=40;description='Twenty upvotes and forty downvotes. Despite a negative net score, the positive votes can place it above an unrated build in Best rated.'}
    'tails-grid'=[pscustomobject]@{title='[Wilson Demo] Downvotes only';upvotes=0;downvotes=8;description='Eight downvotes and no upvotes. Without positive votes, Wilson confidence stays at zero.'}
  }
  $rankingFixtures=[ordered]@{
    'community-amy-1'=[pscustomobject]@{title='[Wilson Demo] Zero-confidence tie: no votes';description='No votes yet. Compare with "Zero-confidence tie: one downvote" and "Zero-confidence tie: five downvotes": all share a patch and posting time, so fewer downvotes break the tie.';upvotes=0;downvotes=0;caseTime='2026-09-18T12:00:00Z';patch='latest'}
    'community-amy-2'=[pscustomobject]@{title='[Wilson Demo] Zero-confidence tie: one downvote';description='One downvote, no upvotes. This sits between "Zero-confidence tie: no votes" and "Zero-confidence tie: five downvotes" when other ranking factors tie.';upvotes=0;downvotes=1;caseTime='2026-09-18T12:00:00Z';patch='latest'}
    'community-amy-3'=[pscustomobject]@{title='[Wilson Demo] Zero-confidence tie: five downvotes';description='Five downvotes, no upvotes. Compare with the other two garage-review examples to see how zero-confidence ties are ordered.';upvotes=0;downvotes=5;caseTime='2026-09-18T12:00:00Z';patch='latest'}
    'community-tails-1'=[pscustomobject]@{title='[Version Demo] Newer patch wins a confidence tie';description='One downvote on the latest patch. It ranks above "Older patch in a confidence tie" because patch recency breaks this confidence tie first.';upvotes=0;downvotes=1;caseTime='2026-09-18T12:00:00Z';patch='latest'}
    'community-tails-2'=[pscustomobject]@{title='[Version Demo] Older patch in a confidence tie';description='No votes on an older patch. Compare with "Newer patch wins a confidence tie" to see the patch-recency tie-break.';upvotes=0;downvotes=0;caseTime='2026-09-18T12:00:00Z';patch='oldest'}
    'community-shadow-1'=[pscustomobject]@{title='[DEMO] Last week''s garage pick';description='Same patch and votes as "This week''s garage pick", but posted earlier. The newer build wins this ranking tie.';upvotes=8;downvotes=2;caseTime='2026-09-17T12:00:00Z';patch='latest'}
    'community-shadow-3'=[pscustomobject]@{title='[DEMO] This week''s garage pick';description='Same patch and votes as "Last week''s garage pick", but posted later. This demonstrates the creation-time tie-break.';upvotes=8;downvotes=2;caseTime='2026-09-19T12:00:00Z';patch='latest'}
    'community-sonic-1'=[pscustomobject]@{title='[DEMO] A morning garage experiment';description='Same votes, patch, and posting time as "Another morning garage experiment". Build IDs provide a stable final order.';upvotes=3;downvotes=1;caseTime='2026-09-18T12:00:00Z';patch='latest'}
    'community-sonic-2'=[pscustomobject]@{title='[DEMO] Another morning garage experiment';description='Same votes, patch, and posting time as "A morning garage experiment". This pair demonstrates a stable order when all visible ranking factors tie.';upvotes=3;downvotes=1;caseTime='2026-09-18T12:00:00Z';patch='latest'}
  }
  # The Explore UI requests 20 comments per page. These totals expose exact page boundaries.
  $commentFixtures=[ordered]@{
    'shadow-laps'=[pscustomobject]@{title='[Comment Demo] 21 comments — first overflow';comments=21;description='This discussion has 21 comments so the final comment appears on page 2.'}
    'sonic-boost'=[pscustomobject]@{title='[Comment Demo] 40 comments — two full pages';comments=40;description='This discussion has 40 comments and fills exactly two pages.'}
    'sonic-route'=[pscustomobject]@{title='[Comment Demo] 41 comments — third-page overflow';comments=41;description='This discussion has 41 comments so the final comment appears on page 3.'}
    'knuckles-power'=[pscustomobject]@{title='[Comment Demo] 61 comments — four-page example';comments=61;description='This discussion has 61 comments so the final comment appears on page 4.'}
    'knuckles-endurance'=[pscustomobject]@{title='[Comment Demo] 81 comments — five-page example';comments=81;description='This discussion has 81 comments so the final comment appears on page 5.'}
    'knuckles-ring'=[pscustomobject]@{title='[Comment Demo] 101 comments — long discussion';comments=101;description='This longer discussion has 101 comments across six pages.'}
  }
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
  $builds=@();$demonstratedGadgetScenarios=@{}
  $racerRotation=@(Invoke-DemoShuffle $racers $random)
  $hasPassiveCatalog=@($gadgets | Where-Object name -eq 'Speed Tuner 1').Count -eq 1
  foreach($b in $blueprints){
    $availableTypes=@($standards.racingType | Sort-Object -Unique)
    $machineType=if($b.ordinal -in $boardIndexes){'BOOST'}else{$availableTypes[$random.Next($availableTypes.Count)]}
    if($ExpandedCommunity){
      $typeRotation=@($availableTypes)+@($boards | Select-Object -First 1 | ForEach-Object {'BOOST'})
      $round=[int][Math]::Floor($b.ordinal/$racers.Count)
      $machineType=$typeRotation[($b.ordinal+$round)%$typeRotation.Count]
    }
    $profile=$profiles[$b.owner]
    $pool=@(@($standards)+@($boards) | Where-Object racingType -eq $machineType)
    $preferredMachines=@($pool | Where-Object id -in @($profile.machines.id))
    $frontPool=if($preferredMachines.Count -and $random.NextDouble() -lt .78){$preferredMachines}else{$pool}
    $fm=$frontPool[$random.Next($frontPool.Count)];$mixed=$random.NextDouble() -lt .38;$rm=if($mixed -and $pool.Count -gt 1){@($pool | Where-Object id -ne $fm.id)[$random.Next($pool.Count-1)]}else{$fm};$tm=if($machineType -ne 'BOOST' -and $mixed -and $pool.Count -gt 1 -and $random.NextDouble() -lt .5){$pool[$random.Next($pool.Count)]}else{$fm}
    if($ExpandedCommunity -and $pool.Count -gt 1 -and (($b.ordinal+$round)%4 -ne 0 -or $b.key -in @('sonic-speed','community-blaze-1','community-metalhead-1'))){
      $rm=@($pool | Where-Object id -ne $fm.id)[$random.Next($pool.Count-1)]
      $tm=$pool[$random.Next($pool.Count)]
    }
    $front=@($byMachine[[string]$fm.id] | Where-Object type -eq 'FRONT')[0];$rear=@($byMachine[[string]$rm.id] | Where-Object type -eq 'REAR')[0];$tire=if($machineType -ne 'BOOST'){@($byMachine[[string]$tm.id] | Where-Object type -eq 'TIRE')[0]}else{$null}
    $group=if($b.ordinal -lt [Math]::Round($BuildCount*.60)){$main}elseif($b.ordinal -lt [Math]::Round($BuildCount*.90)){$other}else{$guests}
    $fav=@($favorites[$b.owner] | ForEach-Object{$wanted=$_;$group | Where-Object name -ceq $wanted} | Where-Object{$_});$racer=if($fav.Count -and $random.NextDouble() -lt .82){$fav[$random.Next($fav.Count)]}else{$group[$random.Next($group.Count)]}
    if($ExpandedCommunity){$racer=$racerRotation[$b.ordinal%$racerRotation.Count]}
    if($b.key -eq 'sonic-speed') {
      $sonic=@($racers | Where-Object name -CEQ 'Sonic the Hedgehog')
      if($sonic.Count -ne 1){throw 'Featured fixture sonic-speed requires one canonical Sonic the Hedgehog racer.'}
      $racer=$sonic[0]
    }
    $preferred=@($gadgets | Where-Object name -in $profile.theme.names)
    $candidates=@(Invoke-DemoShuffle $preferred $random)+@(Invoke-DemoShuffle @($gadgets | Where-Object name -notin $profile.theme.names) $random)
    $selected=@();foreach($g in $candidates){$trial=@($selected+$g);if($trial.Count -le 4 -and (Test-GadgetPlateFit @($trial.slotCost))){$selected=$trial};if($selected.Count -ge 2 -and $random.NextDouble() -lt .45){break}}
    $version=if($b.ordinal -in $olderIndexes){$older[$random.Next($older.Count)]}else{$latest}
    if($rankingFixtures.Contains($b.key)){$version=if($rankingFixtures[$b.key].patch -eq 'oldest'){$versions[-1]}else{$latest}}
    $gadgetDemo=$null
    if($ExpandedCommunity -and $hasPassiveCatalog -and $version.version -eq '1.4.1'){
      $typeName=(Get-Culture).TextInfo.ToTitleCase($machineType.ToLowerInvariant())
      $racerTypeName=(Get-Culture).TextInfo.ToTitleCase(([string]$racer.racingType).ToLowerInvariant())
      $scenario=($b.ordinal+[int][Math]::Floor($b.ordinal/$racers.Count))%9
      if($b.key -eq 'sonic-speed'){$scenario=1}
      if($b.key -eq 'community-blaze-1'){$scenario=2}
      if($b.key -eq 'community-metalhead-1'){$scenario=3}
      $wantedNames=@(switch($scenario){
        0 {@();$gadgetDemo='Base Stats'}
        1 {@("$typeName Tuner 1","$typeName Tuner 2");$gadgetDemo='Verified Stacking'}
        2 {@("$racerTypeName Character Kit");$gadgetDemo='Racer Kit'}
        3 {@('Drift Charge Kit');$gadgetDemo='Passive Handling'}
        4 {@("$typeName Tuner 1");$gadgetDemo='Tuner Penalty'}
        5 {@("$typeName Machine Kit");$gadgetDemo='Machine Kit'}
        6 {@('Panel Combo Kit');$gadgetDemo='Passive and Conditional'}
        7 {@('Double Down');$gadgetDemo='Stat Tradeoff'}
        8 {@('Quick Starter','Ring Evolution');$gadgetDemo='Conditional Effects'}
      })
      $chosen=@($gadgets | Where-Object name -in @($wantedNames))
      if($chosen.Count -ne $wantedNames.Count){throw "Expected $($wantedNames -join ', ') for $gadgetDemo; found $($chosen.name -join ', ')."}
      $selected=$chosen
    }
    $stock=$fm.id -eq $rm.id -and ($machineType -eq 'BOOST' -or $fm.id -eq $tm.id);$parent=if($b.ordinal -ge 8 -and $b.ordinal%11 -eq 0){$builds[$b.ordinal-5].key}else{$null}
    $setup=if($machineType -eq 'BOOST'){if($stock){"A straightforward $($fm.name) Extreme Gear setup."}else{"An Extreme Gear mix using the $($fm.name) front and $($rm.name) rear."}}else{if($stock){"A straightforward stock $($fm.name) setup."}else{"A mixed $machineType setup with $($fm.name), $($rm.name), and $($tm.name) parts."}}
    $tone=@('I keep coming back to this one.','Still deciding whether this becomes my regular setup.','A small experiment I wanted to save.','Any thoughts on the gadget order?','Keeping a spare setup for next time.','Nothing ambitious today; just saving my current picks.')[$random.Next(6)]
    $title=@("$($racer.name): $($profile.theme.label)","An evening with $($racer.name)","$($fm.name), take two","$($racer.name) and a garage experiment","A spare $($machineType.ToLowerInvariant()) setup","Trying $($selected[0].name)")[$random.Next(6)]
    if($b.key -eq 'sonic-speed'){$title='Sonic — my regular garage pick'}
    $description="$setup $tone"
    if($b.ordinal % 3 -ne 0){$description+=" Keeping $($selected[0].name) with $($selected[1].name) for this version of the loadout."}
    $recommendedMaps=@()
    if($ExpandedCommunity){
      # Preferences belong only to this explicit synthetic fixture refresh, never real builds.
      if($b.ordinal % 4 -ne 0 -or $b.key -in @('sonic-speed','community-blaze-1','community-metalhead-1')){
        $mapOffset=$b.ordinal-[int][Math]::Floor($b.ordinal/4)
        $recommendedMaps=@(0..($b.ordinal%3) | ForEach-Object { $maps[($mapOffset+$_*13)%$maps.Count] } | Sort-Object id -Unique)
      }
      $shortRacer=switch($racer.name){
        'Sonic the Hedgehog' {'Sonic'}; 'Shadow the Hedgehog' {'Shadow'}; 'Knuckles the Echidna' {'Knuckles'}
        'Miles "Tails" Prower' {'Tails'}; 'Espio the Chameleon' {'Espio'}; 'Vector the Crocodile' {'Vector'}
        'Silver the Hedgehog' {'Silver'}; 'Blaze the Cat' {'Blaze'}; 'Rouge the Bat' {'Rouge'}
        'Goro Majima (Captain Majima)' {'Captain Majima'}; default {$racer.name}
      }
      $typeName=$machineType.ToLowerInvariant()
      $routeName=if($recommendedMaps.Count){$recommendedMaps[0].name}else{'the weekend lobby'}
      $partsTitle=if($stock){"Keeping $($fm.name) stock"}else{"$($fm.name) front, $($rm.name) rear"}
      $gadgetTitle=if($selected.Count){"$shortRacer and $($selected[0].name)"}else{"$shortRacer, no gadgets"}
      $testingTitle=if($selected.Count){"Testing $($selected[0].name)"}else{"Keeping $shortRacer simple"}
      $titles=@("My $shortRacer build", "$shortRacer on $($fm.name)", "$shortRacer for tonight's lobby",
        "$shortRacer, but with $typeName parts", "$shortRacer practice setup", "Back to $shortRacer",
        $gadgetTitle, "$shortRacer for $routeName", "My usual $shortRacer setup",
        "$shortRacer after work", "$shortRacer for a few casual races", "$shortRacer, one more try",
        "$shortRacer with a different setup", "$shortRacer for the group lobby", "$shortRacer - version two",
        "$shortRacer with $($fm.name) parts", "$shortRacer, current favourite", "$shortRacer for Sunday races",
        "$shortRacer - still tweaking this", "$shortRacer, my backup build", "$shortRacer and the $typeName setup",
        "Trying $($fm.name)", "Back on $($fm.name)", "$($fm.name), my current pick", $partsTitle,
        "$($fm.name) for $routeName", "$($fm.name) with $shortRacer", "A few laps with $($fm.name)",
        "$routeName practice", "Trying this on $routeName", "$shortRacer - friends' lobby",
        "$shortRacer, keeping it simple", "$shortRacer - saved before I forget", "Giving $shortRacer another go",
        "$shortRacer - small changes", "$shortRacer for a change", "$shortRacer - late-night races",
        $testingTitle, "$shortRacer - no rush tonight", "One more race with $shortRacer")
      $title=$titles[$random.Next($titles.Count)]
      $notes=@('Been using this for evening lobbies. Might change the last gadget later.',
        'I mostly picked these because I like the combination. Still figuring out the rest.',
        'Posting this so my friends can copy it without me reading out every part.',
        'Not settled on the gadgets yet. What would you swap?',
        'Keeping this as a second option for our next lobby.',
        'Not chasing records with this one, just a setup I like playing.',
        'Trying something different from my usual pick. Open to suggestions.',
        'Giving this a few more races before I start changing things again.')
      $description="$($notes[$random.Next($notes.Count)]) $setup"
      if($recommendedMaps.Count){$description+=" I have marked $($recommendedMaps[0].name) because that is where I want to try it next."}
      if($b.key -eq 'sonic-speed'){$title="$shortRacer and the $typeName setup [Top 3 Demo]"}
      elseif($b.key -eq 'community-blaze-1'){$title=if($machineType -eq 'BOOST'){"$shortRacer on Extreme Gear [Top 3 Demo]"}else{"My usual $shortRacer build [Top 3 Demo]"}}
      elseif($b.key -eq 'community-metalhead-1'){$title="$shortRacer, my weekend main [Top 3 Demo]"}
      if($b.ordinal -ge 80 -and $b.ordinal % 29 -eq 0){$title="[Remix Demo] A second take on a saved setup";$parent=$builds[$b.ordinal-5].key}
      elseif($b.ordinal -ge 80 -and $b.ordinal % 31 -eq 0){$title='[Gadget Demo] Starting with an empty plate';$selected=@();$gadgetDemo='Base Stats'}
      elseif($b.ordinal -ge 80 -and $b.ordinal % 37 -eq 0){$title='[Map Demo] Keeping every route open';$recommendedMaps=@()}
      elseif($b.ordinal -ge 80 -and $b.ordinal % 41 -eq 0){$title="[Machine Demo] My $($machineType.ToLowerInvariant()) parts combination"}
    }
    $fixtureKind=$null;$targetUpvotes=$null;$targetDownvotes=$null;$targetComments=$null;$caseTime=$null
    if($wilsonFixtures.Contains($b.key)){
      $fixture=$wilsonFixtures[$b.key];$fixtureKind='WILSON';$title=$fixture.title
      $description="$($fixture.description) $setup";$targetUpvotes=$fixture.upvotes;$targetDownvotes=$fixture.downvotes
    } elseif($rankingFixtures.Contains($b.key)) {
      $fixture=$rankingFixtures[$b.key];$fixtureKind='WILSON';$title=$fixture.title
      $description="$($fixture.description) $setup";$targetUpvotes=$fixture.upvotes;$targetDownvotes=$fixture.downvotes;$caseTime=$fixture.caseTime
    } elseif($commentFixtures.Contains($b.key)) {
      $fixture=$commentFixtures[$b.key];$fixtureKind='COMMENT';$title=$fixture.title
      $description="$($fixture.description) $setup";$targetComments=$fixture.comments
    }
    if($ExpandedCommunity){
      # Nine dedicated loadouts demonstrate the passive-rule scenarios. Ordinary
      # community builds use the same valid selections without QA labels or prose.
      if($gadgetDemo -and !$demonstratedGadgetScenarios.ContainsKey($gadgetDemo) -and $title -notmatch '\[(?:[^\]]*Demo|DEMO)\]'){
        $title="[$gadgetDemo Demo] $shortRacer - $($selected.name -join ' + ')"
        if(!$selected.Count){$title="[Base Stats Demo] $shortRacer without gadgets"}
        $demonstratedGadgetScenarios[$gadgetDemo]=$true
      }
      $isFeatureDemo=$title -match '\[(?:[^\]]*Demo|DEMO)\]'
      if($isFeatureDemo -and $gadgetDemo){$description+=" Demo focus: $gadgetDemo. Verified passive arithmetic uses this saved patch; race-event effects are not assumed active."}
      if($isFeatureDemo -and $recommendedMaps.Count){$description+=' Map selections are fictional author recommendations, not calculated course advantages.'}
    }
    $builds+=[pscustomobject]@{key=$b.key;owner=$b.owner;title=$title;legacyTitle=$b.title;description=$description;fixtureKind=$fixtureKind;targetUpvotes=$targetUpvotes;targetDownvotes=$targetDownvotes;targetComments=$targetComments;caseTime=$caseTime;racerId=$racer.id;racerName=$racer.name;machineType=$machineType;frontPartId=$front.id;frontMachine=$fm.name;rearPartId=$rear.id;rearMachine=$rm.name;tirePartId=if($tire){$tire.id}else{$null};tireMachine=if($tire){$tm.name}else{$null};gameVersionId=$version.id;version=$version.version;releasedAt=$version.releasedAt;gadgetIds=@($selected|ForEach-Object id);gadgetNames=@($selected|ForEach-Object name);recommendedMapIds=@($recommendedMaps | Sort-Object id | ForEach-Object id);stock=$stock;remixedFromKey=$parent}
  }
  $votes=@();$comments=@()
  foreach($b in $builds){
    $roll=$random.Next(100)
    $attention=if($roll -lt 22){0}elseif($roll -lt 62){$random.Next(1,5)}elseif($roll -lt 90){$random.Next(5,14)}else{$random.Next(18,36)}
    $candidates=@(Invoke-DemoShuffle @($users | Where-Object key -ne $b.owner) $random)
    if($b.fixtureKind -eq 'WILSON'){
      $targetTotal=$b.targetUpvotes+$b.targetDownvotes
      for($i=0;$i -lt $targetTotal;$i++){
        $votes+=[pscustomobject]@{key="vote/$($candidates[$i].key)/$($b.key)";user=$candidates[$i].key;build=$b.key;value=if($i -lt $b.targetUpvotes){1}else{-1}}
      }
      continue
    }
    # A visible Sonic entry is an explicit editorial fixture, not a ranking rule.
    if($b.key -eq 'sonic-speed'){$attention=65}
    if($ExpandedCommunity -and $b.key -eq 'community-blaze-1'){$attention=64}
    if($ExpandedCommunity -and $b.key -eq 'community-metalhead-1'){$attention=62}
    for($i=0;$i -lt [Math]::Min($attention,$candidates.Count);$i++){
      $down=if($roll -ge 84 -and $roll -lt 92){.45}else{.12}
      if($b.key -eq 'sonic-speed'){$down=0}
      if($ExpandedCommunity -and $b.key -in @('community-blaze-1','community-metalhead-1')){$down=0}
      $votes+=[pscustomobject]@{key="vote/$($candidates[$i].key)/$($b.key)";user=$candidates[$i].key;build=$b.key;value=if($random.NextDouble() -lt $down){-1}else{1}}
    }
    if($b.fixtureKind -eq 'COMMENT'){continue}
    $cc=if(!$attention){0}elseif($random.NextDouble() -lt .5){0}else{[Math]::Min(4,1+$random.Next([Math]::Max(1,[Math]::Min(4,$attention))))}
    for($i=0;$i -lt $cc;$i++){
      $speaker=$candidates[($i+2)%$candidates.Count]
      $texts=@("Nice to see another $($b.racerName) setup.","What made you choose the $($b.rearMachine) rear here?",'I would probably change one gadget, but the setup is interesting.',"How has $($b.gadgetNames[0]) felt with this combination?")
      $comments+=[pscustomobject]@{key="comment/$($b.key)/$i";user=$speaker.key;build=$b.key;text=$texts[$i%4]}
    }
    if($cc -ge 2 -and $random.NextDouble() -lt .55){$comments+=[pscustomobject]@{key="comment/$($b.key)/author";user=$b.owner;build=$b.key;text='Mostly curiosity. I am comparing it with my other saved setup.'}}
  }
  $discussionTexts=@(
    'I tried a similar setup last night and the handling felt predictable.',
    'The gadget order makes sense to me, especially for longer races.',
    'I would keep this saved and compare it with a stock setup next.',
    'This is a useful variation; thanks for sharing the reasoning.',
    'The machine-part combination is interesting without feeling random.',
    'I would like to see how this performs on a more technical course.'
  )
  foreach($b in @($builds|Where-Object fixtureKind -eq 'COMMENT')){
    $speakers=@($users|Where-Object key -ne $b.owner)
    for($i=0;$i -lt $b.targetComments;$i++){
      $speaker=$speakers[$i%$speakers.Count]
      $comments+=[pscustomobject]@{key="comment/$($b.key)/page-$i";user=$speaker.key;build=$b.key;text=$discussionTexts[$i%$discussionTexts.Count]}
    }
  }
  [pscustomobject]@{schemaVersion=2;seed=$RandomSeed;referenceTime=$ReferenceTime.ToUniversalTime().ToString('o');newestVersion=$latest;users=$users;builds=$builds;votes=$votes;comments=$comments}
}
