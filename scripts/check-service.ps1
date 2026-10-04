param([Parameter(Mandatory=$true)][uri]$BaseUrl)
$ErrorActionPreference='Stop'
if($BaseUrl.Scheme -ne 'https'){throw 'Use the production HTTPS address.'}
$health=Invoke-RestMethod -Uri ([uri]::new($BaseUrl,'/actuator/health')) -TimeoutSec 10
if($health.status -ne 'UP'){throw 'Service health check failed.'}
Write-Output 'Service health: UP'
# Run under your chosen monitoring scheduler; a nonzero exit must trigger its alert policy.
