# Additional teaching loadouts for SeedDemoData.ps1. No writes or scenario state.
function Add-CurrentFeatureDemos($Plan,$Catalog) {
  $optimizer=Get-OptimizerDemoPlan $Catalog
  $Plan.users+=@($optimizer.users|Where-Object key -notin $Plan.users.key)
  $Plan.builds+=@($optimizer.builds|Where-Object key -notin $Plan.builds.key)
  $version=@($Catalog.versions|Where-Object version -eq '1.4.1')[0]
  $racer=@($Catalog.racers|Where-Object name -eq 'Amy Rose')[0]
  $machine=@($Catalog.machines|Where-Object racingType -eq 'SPEED'|Sort-Object id)[0]
  $parts=@($Catalog.parts|Where-Object sourceMachineId -eq $machine.id)
  if(!$version -or !$racer -or !$machine){throw 'Current feature demos require the reviewed 1.4.1 catalog.'}
  function Id([int]$Number){'70000000-0000-4000-8000-'+$Number.ToString('000000000000')}
  $definitions=@(
    @{key='quick';name='Quick Starter + Sea Dog';ids=@((Id 45),(Id 15));note='Try Scenario Preview with Lap 1 and Water form separately, then enable both conditions together to see how multiple active effects combine. Each effect is calculated separately; their simultaneous stat combination is currently unverified and shown as partial.'},
    @{key='super';name='Super Slow Starter: final lap';ids=@((Id 48));note='In Scenario Preview compare Lap 2 with Lap 3. The reviewed final-lap bonus is +60 to each stat; displayed totals are not capped at 100.'},
    # Retain the old water fixture identity, repurposed so there is one Starter/Sea Dog demo.
    @{key='water';name='Invincible Finish — Near the finish';ids=@((Id 4));note='Try Scenario Preview within 300 metres of the finish. Invincibility becomes active without adding stat points.'},
    @{key='flight';name='Ace Pilot: flight form';ids=@((Id 17));note='Compare Normal with Flight in Scenario Preview. Flight applies +20 to each stat; ring rewards remain separate utility.'},
    @{key='all-rounder';name='All-Rounder: either transformation';ids=@((Id 18));note='Try Normal, Water and Flight. Either transformed form applies +20 to each stat; terrain utility is separate.'},
    @{key='rings';name='Ring Engine: rings held now';ids=@('5a000a58-7d7a-581c-80e3-6ae8661215b7');note='Compare 0 with 1 ring held now in Scenario Preview. Positive held rings activate a fixed +12 to each stat. This is not a rings-collected counter or speed simulation.'},
    @{key='landing';name='Perfect Landing: active utility';ids=@((Id 2));note='Set Successful landing boost to Active now. The descriptive effect becomes active without adding points to the five stats.'},
    @{key='unsupported';name='Damage Evolution: honest unknown';ids=@((Id 7));note='Open Scenario Preview to see the partial result. Accumulation limits and reset behavior remain unsupported; no damage counter or invented bonus is supplied.'},
    @{key='locks';name='Combined locks: keep several favourites';ids=@((Id 2),(Id 45));note='Remix into a draft. Lock Amy Rose, the front part, Perfect Landing and Quick Starter together. Use Strict with Speed first, then Calculate recommendation. Locks are combined; conditional effects are not part of the recommendation objective.'},
    @{key='ownership';name='Collection exclusions: another starting point';ids=@((Id 2));note='Remix into a draft and calculate a Strict recommendation. In Game Collection, uncheck a machine or gadget in that suggestion, then calculate again. Restore ownership afterward. This fixture stores no private collection settings or predetermined result.'}
  )
  foreach($d in $definitions){
    foreach($id in $d.ids){if($id -notin $Catalog.gadgets.id){throw "Missing reviewed gadget: $id"}}
    $Plan.builds+=[pscustomobject]@{
      key="feature-$($d.key)-v1";owner='optimizer';title="[Demo · $(if($d.key -eq 'ownership'){'Ownership'}elseif($d.key -eq 'locks'){'Recommendation'}else{'Scenario'})] $($d.name)";legacyTitle="[DEMO] $($d.name)"
      description=$d.note;fixtureKind=if($d.key -in @('locks','ownership')){'RECOMMENDATION'}else{'SCENARIO'}
      racerId=$racer.id;racerName=$racer.name;machineType='SPEED'
      frontPartId=($parts|Where-Object type -eq 'FRONT').id;rearPartId=($parts|Where-Object type -eq 'REAR').id
      tirePartId=($parts|Where-Object type -eq 'TIRE').id;gameVersionId=$version.id;version=$version.version
      gadgetIds=@($d.ids);recommendedMapIds=@();stock=$true;remixedFromKey=$null;caseTime=$null
    }
  }
  $Plan
}
