# One frozen, real catalog example. Selection discovery belongs in bounded tests, never seeding.
function Get-OptimizerDemoPlan($Catalog) {
  $fixture=Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'optimizer-miku-balanced-v1.json')|ConvertFrom-Json
  $racer=@($Catalog.racers|Where-Object id -eq $fixture.racerId)
  $version=@($Catalog.versions|Where-Object id -eq $fixture.gameVersionId)
  if($racer.Count -ne 1 -or $racer[0].name -cne 'Hatsune Miku' -or $version.Count -ne 1 -or $version[0].version -ne '1.4.1'){
    throw 'The frozen optimizer fixture requires canonical Hatsune Miku and the reviewed Ver. 1.4.1 catalog.'
  }
  $fixture|Add-Member -NotePropertyMembers @{
    owner='optimizer';legacyTitle=$fixture.title;fixtureKind='OPTIMIZER';machineType='BOOST';version='1.4.1';stock=$true
  }
  [pscustomobject]@{
    users=@([pscustomobject]@{key='optimizer';username='ringlab_demo_optimizer';email='ringlab_demo_optimizer@example.test';author=$true})
    builds=@($fixture);votes=@();comments=@();newestVersion=$version[0];referenceTime='2026-09-28T00:00:00Z'
  }
}
