<#
.SYNOPSIS
  TicketRush 핫시트 부하 테스트를 한 번에 실행한다: 스택 확인 → 시드 → k6 → 정합성 검증

.EXAMPLE
  ./loadtest/run.ps1                                # 운영 기본 설정(비관적 락)으로 1회 측정
  ./loadtest/run.ps1 -Locking none -Warmup          # 잠금 없음(기준선)으로 앱을 다시 띄우고 워밍업 후 측정
  ./loadtest/run.ps1 -HotSeats 10 -Label hot10      # 10석에 1,000명 (경합 극대화)
#>
param(
    [int]$Users = 1000,
    [int]$HotSeats = 100,
    [ValidateSet('pessimistic', 'none')]
    [string]$Locking = 'pessimistic',
    [string]$Label = '',
    [int]$StartDelay = 20,
    [switch]$Warmup
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Set-Location (Join-Path $PSScriptRoot '..')

$compose = @('compose', '-f', 'deploy/local/compose.yaml')
$psql = @('exec', '-T', 'postgres', 'psql', '-U', 'ticketrush', '-d', 'ticketrush', '-v', 'ON_ERROR_STOP=1', '-f')
if ($Label -eq '') { $Label = "lock-$Locking" }

function Invoke-Step([string]$Title, [scriptblock]$Body) {
    Write-Host ''
    Write-Host "==> $Title" -ForegroundColor Cyan
    & $Body
}

function Invoke-Seed {
    docker @compose @psql /loadtest/seed/seed.sql
    if ($LASTEXITCODE -ne 0) { throw '시드 실패' }
}

function Invoke-K6([string]$RunLabel) {
    docker @compose run --rm `
        -e USERS=$Users -e HOT_SEATS=$HotSeats -e RUN_LABEL=$RunLabel -e START_DELAY=$StartDelay `
        k6 run --tag testid=$RunLabel -o experimental-prometheus-rw /scripts/scenarios/hot-seat.js |
        Out-Host  # 함수 반환값에 출력이 섞이지 않게 화면으로 바로 보낸다
    return $LASTEXITCODE
}

Invoke-Step "[1/5] 스택 기동 확인 (좌석 잠금 방식: $Locking, app healthy까지 대기)" {
    # compose.yaml이 HOLD_LOCKING 환경 변수를 앱에 넘긴다. 값이 바뀌면 app 컨테이너만 새로 만들어진다
    $env:HOLD_LOCKING = $Locking
    # --build: 코드가 바뀌었으면 앱 이미지를 다시 만든다. 바뀐 게 없으면 Docker 캐시로 몇 초 안에 끝난다
    docker @compose up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw '스택 기동 실패: docker compose -f deploy/local/compose.yaml logs app 확인' }
    $info = Invoke-RestMethod -Uri 'http://localhost:8080/actuator/info'
    $actual = $info.ticketrush.'hold-locking'
    Write-Host "앱이 보고한 잠금 방식: $actual"
    if ($actual -ne $Locking) { throw "요청한 잠금 방식($Locking)과 앱 설정($actual)이 다릅니다." }
}

if ($Warmup) {
    Invoke-Step '[2/5] 워밍업: 시드 후 같은 시나리오 1회 (결과는 비교에 쓰지 않음)' {
        Invoke-Seed
        $null = Invoke-K6 "$Label-warmup"
    }
} else {
    Write-Host ''
    Write-Host '==> [2/5] 워밍업 생략 (-Warmup으로 켤 수 있음)' -ForegroundColor DarkGray
}

Invoke-Step '[3/5] 시드: 모든 테이블 초기화 후 사용자 1,000명 · 좌석 100석 생성' {
    Invoke-Seed
}

$k6Exit = 0
Invoke-Step "[4/5] k6 측정: 사용자 $Users 명 / 목표 좌석 $HotSeats 석 / 라벨 $Label" {
    $script:k6Exit = Invoke-K6 $Label
}

Invoke-Step '[5/5] DB 정합성 검증' {
    docker @compose @psql /loadtest/seed/verify.sql
    if ($LASTEXITCODE -ne 0) { throw '검증 SQL 실행 실패' }
}

Write-Host ''
switch ($k6Exit) {
    0  { Write-Host 'k6: 모든 threshold 통과' -ForegroundColor Green }
    99 { Write-Host 'k6: 일부 threshold 실패 (어떤 항목인지 위 요약의 ✗ 표시 확인)' -ForegroundColor Yellow }
    default { Write-Host "k6: 실행 오류 (exit $k6Exit)" -ForegroundColor Red }
}
Write-Host 'Grafana: http://localhost:3000 → TicketRush 개요 (시간 범위 Last 15 minutes)'
Write-Host 'JSON 결과: loadtest/results/'
