# Deploy the demo to OpenShift with the active `oc` login. Windows (PowerShell 5.1+ or pwsh).
# macOS/Linux: deploy.sh
#
# Usage: .\deploy\scripts\deploy.ps1 <sandbox|gpu> [-Namespace <ns>] [-LocalSource] [-Delete] [-Force] [-NoWait]
#   sandbox       decision gateway + app, using the Developer Sandbox's shared models
#   gpu           DiffusionGemma (needs a >= 40 GB GPU) + decision gateway + app
#   -Namespace    target project (default: the current `oc project`)
#   -LocalSource  build the app from this working tree instead of Git
#   -Delete       remove everything the overlay created
#   -Force        apply the gpu overlay even when the namespace has no GPU quota
#   -NoWait       do not wait for builds, rollouts and the model
[CmdletBinding()]
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet("sandbox", "gpu")]
    [string]$Overlay,
    [string]$Namespace = "",
    [switch]$LocalSource,
    [switch]$Delete,
    [switch]$Force,
    [switch]$NoWait
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Manifests = Join-Path $RepoRoot "deploy\openshift\overlays\$Overlay"
$App = "quarkus-ai-styles-demo"

function Fail([string]$Message) {
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

function Invoke-Oc {
    & oc @args
    if ($LASTEXITCODE -ne 0) { Fail "oc $($args -join ' ') failed (exit $LASTEXITCODE)." }
}

if (-not (Get-Command oc -ErrorAction SilentlyContinue)) {
    Fail "'oc' not found on PATH. Install the OpenShift CLI first."
}
$UserName = (oc whoami 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $UserName) {
    Fail "No active oc session. Run: oc login --token=<token> --server=<api url>"
}
if (-not $Namespace) {
    $Namespace = (oc project -q 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $Namespace) {
        Fail "No current project. Pass -Namespace <namespace> or run: oc project <namespace>"
    }
}
oc get project $Namespace 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "Project '$Namespace' does not exist or is not visible to $UserName." }

Write-Host "Cluster:   $(oc whoami --show-server)"
Write-Host "User:      $UserName"
Write-Host "Namespace: $Namespace"
Write-Host "Overlay:   $Overlay"

if ($Delete) {
    Invoke-Oc delete -k $Manifests -n $Namespace --ignore-not-found
    exit 0
}

if ($Overlay -eq "gpu") {
    $KServe = (oc api-resources --api-group=serving.kserve.io -o name 2>$null) | Where-Object { $_ -like "inferenceservices*" }
    if (-not $KServe) { Fail "KServe (OpenShift AI model serving) is not installed on this cluster." }
    # Every ResourceQuota that caps GPUs must leave room for one.
    $GpuLimits = (oc get resourcequota -n $Namespace -o 'jsonpath={range .items[*]}{.spec.hard.requests\.nvidia\.com/gpu}{"\n"}{end}' 2>$null)
    if (@($GpuLimits) -contains "0") {
        Write-Host "WARNING: namespace '$Namespace' has a GPU quota of 0 (requests.nvidia.com/gpu=0)." -ForegroundColor Yellow
        Write-Host "         DiffusionGemma needs one GPU with >= 40 GB; its pod would never start." -ForegroundColor Yellow
        Write-Host "         On the Developer Sandbox, use the 'sandbox' overlay." -ForegroundColor Yellow
        if (-not $Force) { Fail "Aborting. Re-run with -Force to apply anyway." }
    }
}

Invoke-Oc apply -k $Manifests -n $Namespace

if ($LocalSource) {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Fail "-LocalSource needs git (it uploads the files git tracks or would track)."
    }
    $SourceDir = Join-Path ([System.IO.Path]::GetTempPath()) ("quarkus-ai-styles-demo-" + [guid]::NewGuid())
    Write-Host "Copying the working tree (minus .gitignore'd files) to $SourceDir ..."
    $Files = (git -C $RepoRoot ls-files --cached --others --exclude-standard)
    foreach ($File in $Files) {
        $From = Join-Path $RepoRoot $File
        if (-not (Test-Path $From -PathType Leaf)) { continue }
        $To = Join-Path $SourceDir $File
        New-Item -ItemType Directory -Force -Path (Split-Path $To -Parent) | Out-Null
        Copy-Item $From $To
    }
    oc cancel-build "bc/$App" -n $Namespace 2>$null | Out-Null
    Invoke-Oc start-build $App -n $Namespace "--from-dir=$SourceDir" --follow --wait
    Remove-Item $SourceDir -Recurse -Force -ErrorAction SilentlyContinue
}

if (-not $NoWait) {
    Invoke-Oc rollout status deployment/decision-gateway -n $Namespace --timeout=5m
    if (-not $LocalSource) {
        Write-Host "Waiting for the app image (Git build, a few minutes; oc logs -f bc/$App to follow)..."
        for ($i = 0; $i -lt 180; $i++) {
            oc get istag "${App}:latest" -n $Namespace 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { break }
            $Phase = (oc get builds -n $Namespace -l "buildconfig=$App" -o 'jsonpath={.items[-1:].status.phase}' 2>$null)
            if ($Phase -in @("Failed", "Error", "Cancelled")) { Fail "Build ${Phase}: oc logs bc/$App -n $Namespace" }
            Start-Sleep -Seconds 10
        }
    }
    Invoke-Oc rollout status "deployment/$App" -n $Namespace --timeout=15m
    if ($Overlay -eq "gpu") {
        Write-Host "Waiting for InferenceService/diffusiongemma (weights are ~26 GB)..."
        Invoke-Oc wait inferenceservice/diffusiongemma -n $Namespace --for=condition=Ready --timeout=45m
    }
}

$RouteHost = (oc get route $App -n $Namespace -o 'jsonpath={.spec.host}' 2>$null)
Write-Host ""
Write-Host "App:  https://$RouteHost"
Write-Host "Try:  curl.exe -s -X POST https://$RouteHost/ai/decision -H 'Content-Type: text/plain' -d 'Something milky, not too sweet'"
