#!/usr/bin/env python3
"""인스턴스 사양 측정의 한 구간을 Prometheus와 docker stats 기록에서 요약한다. scripts/spec.sh가 부른다.

  spec-report.py PROFILE START MEASURE_START END STATS_FILE   초 단위 epoch. [START, MEASURE_START]는 워밍업, [MEASURE_START, END]가 측정 구간
"""
import json
import os
import re
import sys
import urllib.parse
import urllib.request

PROM = os.environ.get("LOADTEST_PROMETHEUS_URL", f"http://localhost:{os.environ.get('LOADTEST_PROMETHEUS_PORT', '9091')}")
APP = 'application="dozy-catalog-api"'


def query(expr, at):
    params = {"query": expr, "time": str(at)}
    with urllib.request.urlopen(f"{PROM}/api/v1/query?" + urllib.parse.urlencode(params)) as response:
        return json.load(response)["data"]["result"]


def per_instance(expr, at, scale=1.0, unit="", digits=2):
    result = query(expr, at)
    if not result:
        return "-"
    return " / ".join(f"{float(x['value'][1]) * scale:.{digits}f}{unit}" for x in sorted(result, key=lambda x: x["metric"].get("instance", "")))


def to_mib(text):
    match = re.match(r"([\d.]+)\s*([KMG]i?B)", text)
    value, unit = float(match.group(1)), match.group(2)
    return value * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "kB": 1 / 1024, "MB": 1, "GB": 1024}[unit]


def container_memory(stats_file, start, end):
    """docker stats 기록에서 컨테이너별 최대 메모리(MiB, 캐시 제외 사용량)와 한도."""
    peak, limit = {}, {}
    for line in open(stats_file):
        parts = line.split()
        if len(parts) < 5:
            continue
        ts, name, used, _, cap = int(parts[0]), parts[1], parts[2], parts[3], parts[4]
        if not start <= ts <= end:
            continue
        peak[name] = max(peak.get(name, 0), to_mib(used))
        limit[name] = to_mib(cap)
    return peak, limit


def main():
    profile, start, measure_start, end, stats_file = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), sys.argv[5]
    window = f"{end - measure_start}s"
    lines = []

    def row(label, value):
        lines.append(f"  {label:<34} {value}")

    row("JVM 힙 사용 최대 (MiB)", per_instance(f'max_over_time(sum by (instance) (jvm_memory_used_bytes{{{APP},area="heap"}})[{window}:5s])', end, 1 / 1048576, digits=0))
    row("JVM 힙 한도 (MiB)", per_instance(f'max(jvm_memory_max_bytes{{{APP},area="heap",id!~"CodeHeap.*"}}) by (instance)', end, 1 / 1048576, digits=0))
    row("JVM 힙 밖 사용 최대 (MiB)", per_instance(f'max_over_time(sum by (instance) (jvm_memory_used_bytes{{{APP},area="nonheap"}})[{window}:5s])', end, 1 / 1048576, digits=0))
    row("GC 일시 정지 비율 (구간 평균, %)", per_instance(f'sum by (instance) (increase(jvm_gc_pause_seconds_sum{{{APP}}}[{window}])) / {end - measure_start}', end, 100, digits=2))
    row("GC 일시 정지 단건 최대 (ms)", per_instance(f'max by (instance) (max_over_time(jvm_gc_pause_seconds_max{{{APP}}}[{window}]))', end, 1000, digits=0))
    gc = sorted({x["metric"].get("gc", "?") for x in query(f'jvm_gc_pause_seconds_count{{{APP}}}', end)})
    row("GC 종류", ", ".join(gc) or "-")
    row("프로세스 CPU 최대 (코어 한도 대비 %)", per_instance(f'max_over_time(process_cpu_usage{{{APP}}}[{window}])', end, 100, digits=0))
    row("프로세스 CPU 평균 (코어 한도 대비 %)", per_instance(f'avg_over_time(process_cpu_usage{{{APP}}}[{window}])', end, 100, digits=0))
    row("풀 사용 중 연결 최대", per_instance(f'max_over_time(r2dbc_pool_acquired_connections{{{APP}}}[{window}])', end, digits=0))
    row("풀 대기 최대", per_instance(f'max_over_time(r2dbc_pool_pending_connections{{{APP}}}[{window}])', end, digits=0))
    five = query(f'sum(increase(http_server_requests_seconds_count{{{APP},status=~"5.."}}[{window}]))', end)
    row("5xx 응답 수", f"{float(five[0]['value'][1]):.0f}" if five else "0")

    if measure_start > start:
        warm = f"{measure_start - start}s"
        row("(워밍업) 프로세스 CPU 최대 (%)", per_instance(f'max_over_time(process_cpu_usage{{{APP}}}[{warm}])', measure_start, 100, digits=0))
        row("(워밍업) 풀 대기 최대", per_instance(f'max_over_time(r2dbc_pool_pending_connections{{{APP}}}[{warm}])', measure_start, digits=0))

    peak, limit = container_memory(stats_file, start, end)
    for name in sorted(peak):
        row(f"컨테이너 메모리 최대, 워밍업 포함 ({name})", f"{peak[name]:.0f} MiB / 한도 {limit[name]:.0f} MiB ({peak[name] / limit[name] * 100:.0f}%)")
    print(f"[{profile}]")
    print("\n".join(lines))


main()
