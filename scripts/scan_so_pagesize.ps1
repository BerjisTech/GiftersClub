Param()

$ErrorActionPreference = "Stop"

# Root dir is repo root (one up from scripts)
$rootDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)

Write-Host "Scanning native libs for page size info..."

function Pick-Readelf {
  if (Get-Command llvm-readelf -ErrorAction SilentlyContinue) { return (Get-Command llvm-readelf).Path }
  if (Get-Command readelf -ErrorAction SilentlyContinue) { return (Get-Command readelf).Path }

  $ndkVars = @("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT")
  foreach ($var in $ndkVars) {
    $ndk = (Get-Item -Path "Env:$var" -ErrorAction SilentlyContinue).Value
    if ($ndk) {
      $cand = Join-Path $ndk "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
      if (Test-Path $cand) { return $cand }
    }
  }

  $ndkVersion = "26.3.11579264"
  $sdkVars = @("ANDROID_SDK_ROOT", "ANDROID_HOME")
  foreach ($var in $sdkVars) {
    $sdk = (Get-Item -Path "Env:$var" -ErrorAction SilentlyContinue).Value
    if ($sdk) {
      $cand = Join-Path $sdk "ndk\$ndkVersion\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
      if (Test-Path $cand) { return $cand }

      $ndkDir = Join-Path $sdk "ndk"
      if (Test-Path $ndkDir) {
        $latest = Get-ChildItem $ndkDir -Directory | Sort-Object Name | Select-Object -Last 1
        if ($latest) {
          $cand = Join-Path $latest.FullName "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
          if (Test-Path $cand) { return $cand }
        }
      }
    }
  }
  return $null
}

$readelf = Pick-Readelf
if (-not $readelf) {
  Write-Host "Could not find readelf/llvm-readelf."
  Write-Host "Install Android NDK r26+ via Android Studio (SDK Manager > SDK Tools > NDK),"
  Write-Host "or add llvm-readelf to PATH, or set ANDROID_NDK_HOME/ANDROID_SDK_ROOT."
}

$patterns = @(
  (Join-Path $rootDir "app\build\intermediates\merged_native_libs\*\merge*NativeLibs\out\lib\*\*.so"),
  (Join-Path $rootDir "app\build\intermediates\stripped_native_libs\*\strip*DebugSymbols\out\lib\*\*.so")
)

$found = $false
foreach ($glob in $patterns) {
  $files = Get-ChildItem -Path $glob -File -ErrorAction SilentlyContinue
  foreach ($so in $files) {
    $found = $true
    Write-Host "==== $($so.FullName)"
    if ($readelf) {
      try {
        & $readelf -l $so.FullName 2>$null |
          Select-String -Pattern "MaxPageSize|Page size" |
          ForEach-Object { $_.Line }
      } catch {
        Write-Host "  (Failed to run llvm-readelf)"
      }
    } else {
      Write-Host "  (readelf/llvm-readelf not available on this system)"
    }
  }
}

if (-not $found) {
  Write-Host "No .so files found in intermediates. Build a release first: ./gradlew clean :app:assembleRelease"
}

Write-Host "Done."

