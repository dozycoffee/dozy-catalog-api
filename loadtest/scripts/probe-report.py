#!/usr/bin/env python3
"""probe 결과를 Prometheus에서 계산해 출력한다. scripts/probe.sh가 부른다.

  probe-report.py idle                          유휴 CPU(코어)만 출력한다
  probe-report.py report NAME END SECONDS IDLE  [END-SECONDS, END] 구간의 처리량, 지연, 요청당 CPU

요청당 CPU = (부하 중 앱 CPU 코어 - 유휴 CPU 코어) / 처리량. CPU는 컨테이너 제한(LOADTEST_APP_CPUS)으로 정규화된
process_cpu_usage에 그 제한을 곱한 값(코어)이다. 두 인스턴스를 합친다.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

PROM = os.environ.get("LOADTEST_PROMETHEUS_URL", f"http://localhost:{os.environ.get('LOADTEST_PROMETHEUS_PORT', '9091')}")
CPUS = float(os.environ.get("LOADTEST_APP_CPUS", "2"))
APP = 'application="dozy-catalog-api"'

# 요청 이름 -> 서버 URI 패턴(Prometheus 정규식, 전체 일치)
URIS = {
    "catalog_info": r"/api/v1/internal/products",
    "catalog_options": r"/api/v1/internal/products/\{productId\}/effective-options",
    "catalog_categories": r"/api/v1/internal/categories",
    "state_list": r"/api/v1/internal/product-states",
    "admin_search": r"/api/v1/admin/products",
    "admin_toggle": r"/api/v1/admin/products/\{productId\}/(activate|discontinue)",
}


def query(expr, at=None):
    params = {"query": expr}
    if at is not None:
        params["time"] = str(at)
    with urllib.request.urlopen(f"{PROM}/api/v1/query?" + urllib.parse.urlencode(params)) as response:
        return json.load(response)["data"]["result"]


def scalar(expr, at=None, default=float("nan")):
    result = query(expr, at)
    return float(result[0]["value"][1]) if result else default


def cores(window, at=None):
    """앱 인스턴스 전체가 쓴 CPU(코어)."""
    return scalar(f"sum(avg_over_time(process_cpu_usage{{{APP}}}[{window}s]))", at) * CPUS


def main():
    mode = sys.argv[1]
    if mode == "idle":
        print(f"{cores(10):.4f}")
        return

    name, end, seconds, idle = sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), float(sys.argv[5])
    # PromQL 문자열 안에서 정규식의 역슬래시는 한 번 더 이스케이프해야 한다
    uri = URIS[name].replace("\\", "\\\\")
    selector = f'{APP},uri=~"{uri}"'
    window = f"[{seconds}s]"
    rps = scalar(f"sum(rate(http_server_requests_seconds_count{{{selector}}}{window}))", end)
    mean = scalar(
        f"sum(rate(http_server_requests_seconds_sum{{{selector}}}{window})) / sum(rate(http_server_requests_seconds_count{{{selector}}}{window}))",
        end,
    ) * 1000

    def quantile(q):
        return scalar(f"histogram_quantile({q}, sum by (le) (rate(http_server_requests_seconds_bucket{{{selector}}}{window})))", end) * 1000

    busy = cores(seconds, end)
    per_request = (busy - idle) / rps * 1000 if rps else float("nan")
    errors = scalar(f'sum(rate(http_server_requests_seconds_count{{{selector},status=~"5.."}}{window}))', end, 0.0)

    print(f"요청: {name}")
    print(f"  처리량 {rps:6.1f} rps   5xx {errors:.2f} rps")
    print(f"  서버 지연  평균 {mean:6.1f} ms   p95 {quantile(0.95):6.1f} ms   p99 {quantile(0.99):6.1f} ms")
    print(f"  앱 CPU  유휴 {idle:.3f} 코어 -> 부하 중 {busy:.3f} 코어   =>  요청당 CPU {per_request:6.1f} ms")
    print(f"  (CPU 한도 {CPUS:g}코어 x 2대 = {CPUS * 2:g}코어 중 {busy / (CPUS * 2) * 100:.0f}% 사용)")


if __name__ == "__main__":
    main()
