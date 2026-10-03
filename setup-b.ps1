# Provisions the Sender app as device owner on Phone B so it can read IMEI.
#
# Prerequisites:
#   1. Remove ALL Google/personal accounts on the phone (Settings > Accounts).
#   2. No secondary users / work profiles.
#   3. USB debugging enabled.
#   4. Sender APK installed (debug build).
#
# Usage: .\setup-b.ps1 [-Serial <device-serial>]

param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"

$adb = "adb"
if ($Serial) { $adb = "adb -s $Serial" }

$package = "com.tanbread.nfcdevicecheck.sender"
$admin = "$package/.admin.SenderDeviceAdmin"

Write-Host "==> Checking device..."
& $adb get-state | Out-Null

Write-Host "==> Checking for existing accounts (device owner requires NONE)..."
$accounts = & $adb shell dumpsys account | Select-String "Accounts:"
if ($accounts -and $accounts -notmatch "Accounts:\s*0") {
    Write-Warning "Accounts may exist on this device. Remove them in Settings > Accounts, then re-run."
    $confirm = Read-Host "Continue anyway? (y/N)"
    if ($confirm -ne "y") { exit 1 }
}

Write-Host "==> Setting device owner..."
& $adb shell dpm set-device-owner $admin
if ($LASTEXITCODE -ne 0) {
    Write-Error "dpm set-device-owner failed. Common causes: accounts present, existing admin, or app not installed."
    exit 1
}

Write-Host "==> Granting READ_PHONE_STATE..."
& $adb shell pm grant $package android.permission.READ_PHONE_STATE

Write-Host "==> Done. Open NFC Sender on the phone; status card should show green checks."
