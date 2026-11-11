# Бутстрап для Windows БЕЗ Java: принимает файлы от proxy-bridge по COM-порту.
#
# Запуск:
#   powershell -NoProfile -ExecutionPolicy Bypass -File bootstrap.ps1 settings.txt
#
# Или, если политика разрешает скрипты:
#   .\bootstrap.ps1 settings.txt
#
# Настройки — тот же settings.txt (key=value).
#
# Протокол (общий с Bootstrap.java):
#   START_FILE (1 байт)
#   длина имени (4 байта, big-endian)
#   имя файла (UTF-8)
#   размер файла (4 байта, big-endian)
#   содержимое файла
#
# Почему это возможно без Java: System.IO.Ports.SerialPort есть в .NET,
# который встроен в Windows. Java-версия нужна там, где JRE уже стоит и
# хочется один файл на все ОС; эта — для машин вообще без Java.

param(
    [string]$SettingsPath = "settings.txt"
)

$ErrorActionPreference = 'Stop'

# Constrained Language Mode (AppLocker/WDAC) запрещает .NET-типы, которыми
# этот скрипт читает порт. Лучше сказать об этом сразу и понятно.
if ($ExecutionContext.SessionState.LanguageMode -ne 'FullLanguage') {
    Write-Host "This script needs FullLanguage mode, current mode is:" `
        $ExecutionContext.SessionState.LanguageMode
    Write-Host "The machine has hardened PowerShell (AppLocker/WDAC)."
    Write-Host "Use the C# bootstrap instead: ..\csharp\build.bat and Bootstrap.exe"
    exit 2
}

function Read-Settings([string]$Path) {
    $result = @{ port = ""; baud = 115200; workdir = "incoming" }
    if (-not (Test-Path $Path)) {
        Write-Host "Settings file not found: $Path (using defaults)"
        return $result
    }
    foreach ($raw in Get-Content -Path $Path -Encoding UTF8) {
        $line = $raw.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith('#')) { continue }
        $idx = $line.IndexOf('=')
        if ($idx -le 0) { continue }
        $key = $line.Substring(0, $idx).Trim().ToLower()
        $value = $line.Substring($idx + 1).Trim()
        if ($key -eq 'port') { $result.port = $value }
        elseif ($key -eq 'baud') { $result.baud = [int]$value }
        elseif ($key -eq 'workdir') { $result.workdir = $value }
    }
    return $result
}

function Read-Exactly([System.IO.Stream]$Stream, [int]$Count) {
    $buffer = New-Object byte[] $Count
    $offset = 0
    while ($offset -lt $Count) {
        $read = $Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -le 0) { throw "Port closed while reading" }
        $offset += $read
    }
    return $buffer
}

function Read-Int32([System.IO.Stream]$Stream) {
    $b = Read-Exactly $Stream 4
    return (([int]$b[0] -shl 24) -bor ([int]$b[1] -shl 16) -bor ([int]$b[2] -shl 8) -bor [int]$b[3])
}

$settings = Read-Settings $SettingsPath

$portName = $settings.port
if ([string]::IsNullOrWhiteSpace($portName)) {
    $available = [System.IO.Ports.SerialPort]::GetPortNames()
    Write-Host ("Available ports: " + ($(if ($available) { $available -join ', ' } else { '(none)' })))
    $portName = Read-Host "Enter COM port (e.g. COM3)"
    if ([string]::IsNullOrWhiteSpace($portName)) {
        Write-Host "No port given - nothing to do."
        exit 1
    }
}
$portName = $portName.Trim()

$workDir = [System.IO.Path]::GetFullPath($settings.workdir)
if (-not (Test-Path $workDir)) { New-Item -ItemType Directory -Path $workDir | Out-Null }

Write-Host "Bootstrap: port=$portName baud=$($settings.baud)"
Write-Host "Bootstrap: work dir = $workDir"
Write-Host "Waiting for files... (Ctrl+C to stop)"

$port = New-Object System.IO.Ports.SerialPort($portName, $settings.baud, 'None', 8, 'One')
$port.ReadTimeout = 60000
$port.Open()

try {
    while ($true) {
        try {
            # Синхронизация по маркеру: после шума/обрыва читаем дальше.
            while ($true) {
                $code = $port.ReadByte()
                if ($code -lt 0) { throw "Port closed" }
                if ($code -eq 1) { break }
            }

            $stream = $port.BaseStream
            $nameLength = Read-Int32 $stream
            if ($nameLength -le 0 -or $nameLength -gt 4096) { throw "Bad name length: $nameLength" }
            $nameBytes = Read-Exactly $stream $nameLength
            $name = [System.Text.Encoding]::UTF8.GetString($nameBytes)

            $size = Read-Int32 $stream
            if ($size -lt 0 -or $size -gt 536870912) { throw "Bad file size: $size" }

            $safeName = [System.IO.Path]::GetFileName($name)
            if ([string]::IsNullOrEmpty($safeName)) { $safeName = "received.bin" }
            $target = Join-Path $workDir $safeName

            Write-Host "Receiving $safeName ($size bytes)..."
            $output = [System.IO.File]::Create($target)
            try {
                $buffer = New-Object byte[] 65536
                $remaining = $size
                $printed = 0
                while ($remaining -gt 0) {
                    $want = [Math]::Min($buffer.Length, $remaining)
                    $read = $stream.Read($buffer, 0, $want)
                    if ($read -le 0) { throw "Port closed while receiving $safeName" }
                    $output.Write($buffer, 0, $read)
                    $remaining -= $read
                    $percent = [int]((($size - $remaining) * 100) / $size)
                    if ($percent -ge ($printed + 10)) {
                        $printed = $percent - ($percent % 10)
                        Write-Host "  $printed%"
                    }
                }
            } finally {
                $output.Close()
            }
            Write-Host "Saved $target ($size bytes)"
        } catch {
            Write-Host ("Error: " + $_.Exception.Message + " - retrying")
            Start-Sleep -Milliseconds 1000
        }
    }
} finally {
    $port.Close()
}
