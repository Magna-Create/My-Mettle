@echo off
set EXPECTED_GRADLE=9.1.0
for /f "tokens=2" %%v in ('gradle --version ^| findstr /B "Gradle "') do set ACTUAL_GRADLE=%%v
if not "%ACTUAL_GRADLE%"=="%EXPECTED_GRADLE%" (
  echo LAB-2B toolchain mismatch: expected Gradle %EXPECTED_GRADLE%, got %ACTUAL_GRADLE%.
  echo The accepted Qualcomm reference app ships no wrapper; do not modernise the matrix.
  exit /b 2
)
gradle %*
