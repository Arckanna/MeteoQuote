param([string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe")
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$output = Join-Path $repo 'app/build/ui-review'
New-Item -ItemType Directory -Force -Path $output | Out-Null
function Invoke-Adb {
    & $Adb -s emulator-5554 @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
}
$originalSize = Invoke-Adb shell wm size
$originalDensity = Invoke-Adb shell wm density
$originalFont = (Invoke-Adb shell settings get system font_scale).Trim()
$packageState = (Invoke-Adb shell dumpsys package com.valerie.meteoquote) -join "`n"
$hadCoarse = $packageState -match 'android.permission.ACCESS_COARSE_LOCATION: granted=true'
$hadFine = $packageState -match 'android.permission.ACCESS_FINE_LOCATION: granted=true'
try {
    Invoke-Adb install -r "$repo/app/build/outputs/apk/debug/app-debug.apk"
    Invoke-Adb install -r "$repo/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if ($hadFine) { Invoke-Adb shell pm revoke com.valerie.meteoquote android.permission.ACCESS_FINE_LOCATION }
    if ($hadCoarse) { Invoke-Adb shell pm revoke com.valerie.meteoquote android.permission.ACCESS_COARSE_LOCATION }
    $profiles = @(
        @{ Name='compact'; Size='320x640'; Font='1.0' },
        @{ Name='standard'; Size='411x891'; Font='1.0' },
        @{ Name='tablet'; Size='800x1280'; Font='1.0' },
        @{ Name='large-font'; Size='320x640'; Font='2.0' }
    )
    foreach ($profile in $profiles) {
        Invoke-Adb shell wm size $profile.Size
        Invoke-Adb shell wm density 160
        Invoke-Adb shell settings put system font_scale $profile.Font
        $result = Invoke-Adb shell am instrument -w -e profile $profile.Name com.valerie.meteoquote.test/androidx.test.runner.AndroidJUnitRunner
        $result | Set-Content (Join-Path $output "$($profile.Name)-tests.txt")
        $result
        if (($result -join "`n") -notmatch 'OK \(') { throw "Instrumentation failed for $($profile.Name)" }
        Invoke-Adb shell am start -W -n com.valerie.meteoquote/.MainActivity
        Start-Sleep -Milliseconds 800
        Invoke-Adb shell screencap -p "/sdcard/ui-$($profile.Name).png"
        Invoke-Adb pull "/sdcard/ui-$($profile.Name).png" "$output/viewport-$($profile.Name).png"
    }
} finally {
    if ($hadCoarse) { Invoke-Adb shell pm grant com.valerie.meteoquote android.permission.ACCESS_COARSE_LOCATION }
    if ($hadFine) { Invoke-Adb shell pm grant com.valerie.meteoquote android.permission.ACCESS_FINE_LOCATION }
    if (($originalSize -join ' ') -match 'Override size: (\d+x\d+)') { Invoke-Adb shell wm size $Matches[1] } else { Invoke-Adb shell wm size reset }
    if (($originalDensity -join ' ') -match 'Override density: (\d+)') { Invoke-Adb shell wm density $Matches[1] } else { Invoke-Adb shell wm density reset }
    if ($originalFont -eq 'null') { Invoke-Adb shell settings delete system font_scale } else { Invoke-Adb shell settings put system font_scale $originalFont }
    Invoke-Adb pull /sdcard/Android/data/com.valerie.meteoquote/files/ui-review $output
}
