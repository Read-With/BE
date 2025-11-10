#!/bin/bash
# ============================================================
# JVM 메모리 및 GC 설정 (t2.micro 환경 최적화)
# ============================================================
# 
# t2.micro 스펙:
#   - RAM: 1GB
#   - CPU: 1 vCPU (버스트 크레딧)
#
# 설정 목표:
#   - 명시적 힙 메모리 제한 (OOM 방지)
#   - G1GC로 일시정지 시간 최소화
#   - GC 로그 활성화 (문제 분석용)
# ============================================================

echo "=== JVM 설정 시작 ==="

# JAVA_TOOL_OPTIONS 환경변수 설정
cat >> /etc/profile.d/jvm.sh <<'EOF'
# JVM Heap 메모리 설정
export JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UseStringDeduplication \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:/var/log/gc.log"
EOF

# 파일 권한 설정
chmod 644 /etc/profile.d/jvm.sh

# 설정 적용
source /etc/profile.d/jvm.sh

echo "=== JVM 설정 완료 ==="
echo "Heap: 256MB~512MB"
echo "GC: G1GC (MaxPause: 200ms)"

