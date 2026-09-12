# Run directly with PowerShell. No Pester, network, database, or application startup required.
$ErrorActionPreference = 'Stop'
$seeder = Join-Path $PSScriptRoot 'SeedDemoData.ps1'
function Assert-True($Condition, [string]$Message) {
  if (-not $Condition) { throw $Message }
}
function Assert-Fails([scriptblock]$Action, [string]$Message) {
  $failed = $false
  try { & $Action | Out-Null } catch { $failed = $true }
  Assert-True $failed $Message
}

# Every API call is intercepted, including accidental calls from preview mode.
$state = @{ requests = 0 }
function Invoke-RestMethod { throw 'Preview must not contact the API' }
$plan = & $seeder -Preview
$repeat = & $seeder -Preview
Assert-True (($plan | ConvertTo-Json -Depth 20 -Compress) -ceq ($repeat | ConvertTo-Json -Depth 20 -Compress)) 'Plan must be deterministic'
Assert-True ($plan.Users.Count -eq 100 -and $plan.Builds.Count -eq 60) 'Incorrect sample size'
Assert-True (@($plan.Builds.Owner | Sort-Object -Unique).Count -eq 20) 'Expected 20 authors'
Assert-True (@($plan.Users.Username | Sort-Object -Unique).Count -eq 100) 'Duplicate account identity'
Assert-True (@($plan.Builds.Key | Sort-Object -Unique).Count -eq 60) 'Duplicate build key'
Assert-True (@($plan.Builds | Group-Object Owner,Title | Where-Object Count -gt 1).Count -eq 0) 'Duplicate author/title identity'
$main = @('Sonic the Hedgehog', 'Shadow the Hedgehog', 'Miles "Tails" Prower', 'Amy Rose', 'Knuckles the Echidna')
$guests = @('Joker', 'Mega Man', 'SpongeBob SquarePants')
Assert-True (@($plan.Builds | Where-Object { $_.Racer -in $main }).Count -eq 36) 'Expected 60% main cast'
Assert-True (@($plan.Builds | Where-Object { $_.Racer -in $guests }).Count -eq 6) 'Expected 10% guests'
Assert-True (@($plan.Builds | Where-Object { $_.Racer -notin $main -and $_.Racer -notin $guests }).Count -eq 18) 'Expected 30% other Sonic characters'
Assert-True (@($plan.Votes | ForEach-Object { "$($_[0])/$($_[1])" } | Sort-Object -Unique).Count -eq $plan.Votes.Count) 'Duplicate user/build vote'
$buildByKey = @{}
foreach ($build in $plan.Builds) {
  $buildByKey[$build.Key] = $build
  Assert-True ($build.Owner -in $plan.Users.Key) 'Unknown build owner'
}
foreach ($vote in $plan.Votes) {
  Assert-True ($vote[0] -in $plan.Users.Key -and $buildByKey.ContainsKey($vote[1]) -and $vote[2] -in @(-1, 1)) 'Invalid vote reference/value'
  if ($vote[1] -like 'community-*') {
    Assert-True ($vote[0] -ne $buildByKey[$vote[1]].Owner) 'New builds must not self-vote'
  }
}
foreach ($build in @($plan.Builds | Where-Object Key -like 'community-*')) {
  $votes = @($plan.Votes | Where-Object { $_[1] -eq $build.Key })
  Assert-True (@($votes | Where-Object { $_[2] -eq 1 }).Count -eq $build.Upvotes) 'Wrong upvote count'
  Assert-True (@($votes | Where-Object { $_[2] -eq -1 }).Count -eq $build.Downvotes) 'Wrong downvote count'
}
foreach ($sample in @(@(40,1), @(3,0), @(1,0), @(20,40), @(0,0), @(5,5))) {
  Assert-True (@($plan.Builds | Where-Object { $_.Upvotes -eq $sample[0] -and $_.Downvotes -eq $sample[1] }).Count -gt 0) 'Missing ranking sample'
}
Assert-True (@($plan.Builds | Where-Object { $_.Racer -in $guests -and $_.Upvotes -ge 18 }).Count -gt 0) 'Guests should also have well-received builds'
foreach ($comment in $plan.Comments) {
  Assert-True ($comment[0] -in $plan.Users.Key -and $buildByKey.ContainsKey($comment[1]) -and $comment[2].Length -le 2000) 'Invalid comment'
}
Assert-True (@($plan.Comments | Where-Object { $_[1] -like 'community-*' -and $_[0] -eq $buildByKey[$_[1]].Owner }).Count -gt 0) 'Expected author replies'

# Small REST fake exercises the actual runner, partial-run recovery, pagination, and reruns.
$state.accounts = @{}
$state.builds = @{}
$state.votes = @{}
$state.comments = @{}
$state.writes = 0
$state.failNextComment = $true
$state.catalogRacers = @($plan.Builds.Racer | Sort-Object -Unique | ForEach-Object { [pscustomobject]@{ id = $_; name = $_ } })
$state.catalogParts = @(
  foreach ($name in @(($plan.Builds.Machine + $plan.Builds.RearMachine) | Sort-Object -Unique)) {
    foreach ($type in @('FRONT', 'REAR', 'TIRE')) { [pscustomobject]@{ id = "$name/$type"; sourceMachineName = $name; type = $type } }
  }
)
function Invoke-RestMethod {
  param($Uri, $Method, $Headers, $Body, $ContentType)
  $state.requests++
  $url = [uri]$Uri
  $path = $url.AbsolutePath
  $data = if ($Body) { $Body | ConvertFrom-Json } else { $null }
  $actor = if ($Headers.Authorization) { $Headers.Authorization.Substring(7) } else { $null }
  switch ($path) {
    '/api/racers' { return $state.catalogRacers }
    '/api/machines' { return @() }
    '/api/machine-parts' { return $state.catalogParts }
    '/api/gadgets' { return @($plan.Builds.Gadgets | Sort-Object -Unique | ForEach-Object { [pscustomobject]@{ id = $_; name = $_ } }) }
    '/api/game-versions' { return @($plan.Builds.Version | Where-Object { $_ } | Sort-Object -Unique | ForEach-Object { [pscustomobject]@{ id = $_; version = $_ } }) }
    '/api/auth/login' {
      if (-not $state.accounts.ContainsKey($data.username)) {
        $failure = [Exception]::new('Not registered')
        $failure | Add-Member NoteProperty Response ([pscustomobject]@{ StatusCode = 401 })
        throw $failure
      }
      return $state.accounts[$data.username]
    }
    '/api/auth/register' {
      Assert-True (-not $state.accounts.ContainsKey($data.username)) 'Account registered twice'
      $state.writes++
      $session = [pscustomobject]@{ token = $data.username; user = [pscustomobject]@{ id = $data.username; username = $data.username } }
      $state.accounts[$data.username] = $session
      return $session
    }
    '/api/builds' {
      if ($Method -eq 'POST') {
        $state.writes++
        $id = "build-$($state.builds.Count)"
        $data | Add-Member NoteProperty id $id
        $data | Add-Member NoteProperty authorId $actor
        $state.builds[$id] = $data
        return $data
      }
      $query = [System.Web.HttpUtility]::ParseQueryString($url.Query)
      $items = @($state.builds.Values | Where-Object authorId -eq $query['authorId'] | Sort-Object id)
      return [pscustomobject]@{ total = $items.Count; items = @($items | Select-Object -Skip ([int]$query['page'] * 50) -First 50) }
    }
  }
  if ($path -match '^/api/builds/([^/]+)/vote$') {
    $key = "$actor/$($Matches[1])"
    if ($Method -eq 'PUT') { $state.writes++; $state.votes[$key] = $data.value }
    return [pscustomobject]@{ myVote = [int]$state.votes[$key] }
  }
  if ($path -match '^/api/builds/([^/]+)/comments$') {
    $id = $Matches[1]
    if (-not $state.comments.ContainsKey($id)) { $state.comments[$id] = @() }
    if ($Method -eq 'POST') {
      if ($state.failNextComment) { $state.failNextComment = $false; throw 'Simulated interruption' }
      $state.writes++
      $comment = [pscustomobject]@{ authorId = $actor; text = $data.text }
      $state.comments[$id] += $comment
      return $comment
    }
    $query = [System.Web.HttpUtility]::ParseQueryString($url.Query)
    return [pscustomobject]@{ total = $state.comments[$id].Count; items = @($state.comments[$id] | Select-Object -Skip ([int]$query['page'] * 50) -First 50) }
  }
  throw "Unexpected request: $Method $path"
}
$requestCount = $state.requests
Assert-Fails { & $seeder -BaseUrl 'https://example.com' -Preview } 'Remote target accepted'
Assert-Fails { & $seeder -BaseUrl 'file:///tmp/demo' -Preview } 'Non-HTTP target accepted'
Assert-True ($state.requests -eq $requestCount) 'Unsafe target contacted API'
$savedRacers = $state.catalogRacers
$state.catalogRacers = @()
Assert-Fails { & $seeder -ValidateOnly } 'Missing catalog reference accepted'
Assert-True ($state.writes -eq 0) 'Catalog validation made writes'
$state.catalogRacers = $savedRacers
& $seeder -ValidateOnly
Assert-True ($state.writes -eq 0) 'ValidateOnly made writes'
Assert-Fails { & $seeder 6>$null } 'Expected simulated partial-run failure'
& $seeder 6>$null
Assert-True ($state.accounts.Count -eq 100 -and $state.builds.Count -eq 60) 'Partial rerun duplicated data'
Assert-True ($state.votes.Count -eq $plan.Votes.Count) 'Missing seeded votes'
Assert-True (@($state.comments.Values | ForEach-Object { $_ }).Count -eq $plan.Comments.Count) 'Missing/duplicate comments'

# A matching seed build can be beyond the first page after users add their own builds.
$edited = $state.builds.Values | Select-Object -First 1
$edited.description = 'My manual edit'
foreach ($number in 1..55) {
  $state.builds["aaa-$number"] = [pscustomobject]@{ id = "aaa-$number"; authorId = $edited.authorId; title = "Unrelated $number"; description = 'Keep me' }
}
$voteKey = @($state.votes.Keys)[0]
$state.votes[$voteKey] = -$state.votes[$voteKey]
$changedVote = $state.votes[$voteKey]
$writeCount = $state.writes
& $seeder 6>$null
Assert-True ($state.writes -eq $writeCount) 'Unchanged rerun wrote or duplicated data'
Assert-True ($edited.description -eq 'My manual edit' -and $state.votes[$voteKey] -eq $changedVote) 'Rerun overwrote user activity'
Assert-True ($state.builds.Count -eq 115) 'Pagination lost a seeded build or changed unrelated data'
Write-Host 'PASS: deterministic fan distribution, references, ranking samples, offline preview, catalog preflight, partial recovery, pagination and non-destructive reruns.'
