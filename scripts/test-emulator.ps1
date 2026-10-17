param([string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Use a disposable emulator, never a personal device.' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
& $adb -s $Serial install -r app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE) { throw 'App install failed' }
& $adb -s $Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if ($LASTEXITCODE) { throw 'Test install failed' }
# Gradle connected tests grant permissions automatically; explicitly test denial here.
& $adb -s $Serial shell pm revoke com.valerie.meteoquote android.permission.ACCESS_COARSE_LOCATION
$result = & $adb -s $Serial shell am instrument -w com.valerie.meteoquote.test/androidx.test.runner.AndroidJUnitRunner
$result | Write-Output
if ($LASTEXITCODE -or ($result -join "`n") -notmatch 'OK \(\d+ tests?\)') { throw 'Instrumentation failed' }
