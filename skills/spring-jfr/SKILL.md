---
name: spring-jfr
description: |
  **JDK Flight Recorder (JFR) Profiling Skill**: Production-safe JVM profiling using JFR for Java 21 banking platform. Covers enabling JFR, recording configurations, programmatic recording via JFR API, custom JFR events, JMC analysis workflows, Async Profiler, heap dump analysis, virtual thread profiling, and interpreting flamegraphs.

  MANDATORY TRIGGERS: JFR, Java Flight Recorder, JDK Flight Recorder, JMC, Java Mission Control, jcmd, jfr file, FlightRecorder, RecordingStream, RecordingConfiguration, profile recording, continuous recording, flamegraph, Async Profiler, heap dump, jmap, memory leak, CPU profiling, thread profiling, GC analysis, virtual threads, ZGC, lock contention, hot method, allocation profiling, jstack, thread dump, perf regression, profiling, jfr event.
---

# JFR Profiling Skill — JDK Flight Recorder · JMC · Async Profiler

You are profiling a **Java 21 + Spring Boot 3.3 banking platform** running ZGC (`-XX:+UseZGC -XX:+ZGenerational`) with virtual threads. JFR is always-on in production at low overhead — targeted recordings activate during incidents or performance investigations.

**Key principle**: profile in production with JFR's low-overhead continuous recording, then use JMC or Async Profiler for targeted deep dives.

---

## JVM Startup Flags (Dockerfile / Kubernetes)

```bash
# Add to JAVA_OPTS — safe for production (< 1% overhead)
JAVA_OPTS="\
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+UnlockExperimentalVMOptions \
  \
  # JFR — always-on continuous recording (max 250MB ring buffer, 6h age)
  -XX:StartFlightRecording=\
    name=continuous,\
    settings=default,\
    maxsize=250m,\
    maxage=6h,\
    dumponexit=true,\
    filename=/tmp/jfr/continuous.jfr \
  \
  # Enable JFR dump on OutOfMemoryError for post-mortem analysis
  -XX:FlightRecorderOptions=stackdepth=128 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/heap-dump.hprof \
  \
  # Expose JMX for jcmd attach (not exposed externally)
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.local.only=true"
```

---

## Triggering JFR Recordings via jcmd

```bash
# 1. Find the PID of the Spring Boot process
jcmd                          # lists all JVM PIDs
# or inside Kubernetes pod:
jcmd $(pgrep -f BankingPlatformApplication)

PID=<pid>

# 2. Start a targeted 2-minute profile recording (higher overhead)
jcmd $PID JFR.start \
  name=profile-run \
  settings=profile \
  duration=120s \
  filename=/tmp/jfr/profile-$(date +%s).jfr

# 3. Dump the continuous recording on demand (during an incident)
jcmd $PID JFR.dump \
  name=continuous \
  filename=/tmp/jfr/incident-$(date +%s).jfr

# 4. Check active recordings
jcmd $PID JFR.check

# 5. Stop a recording
jcmd $PID JFR.stop name=profile-run

# 6. Thread dump (quick — no JFR required)
jcmd $PID Thread.print > /tmp/threaddump-$(date +%s).txt

# 7. Heap histogram (no full heap dump — much faster)
jcmd $PID GC.heap_info
jcmd $PID VM.native_memory summary

# 8. Full heap dump (use carefully — STW pause for non-ZGC)
jcmd $PID GC.heap_dump /tmp/heap-$(date +%s).hprof
```

---

## Kubernetes Pod — JFR in a Running Container

```bash
# 1. Exec into the pod
kubectl exec -it -n banking \
  $(kubectl get pod -n banking -l app=banking-platform -o jsonpath='{.items[0].metadata.name}') \
  -- /bin/sh

# 2. Trigger profile recording
PID=$(pgrep -f BankingPlatformApplication)
jcmd $PID JFR.start name=k8s-profile settings=profile duration=60s filename=/tmp/k8s-profile.jfr

# 3. Copy JFR file out of the pod
kubectl cp banking/<pod-name>:/tmp/k8s-profile.jfr ./k8s-profile.jfr

# 4. Open in JMC locally (download from jdk.java.net/jmc)
```

---

## Programmatic JFR via RecordingStream (Spring Boot)

```java
package com.banking.platform.monitoring;

import jdk.jfr.consumer.RecordingStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Streams JFR events in real-time and logs anomalies.
 * Replaces polling — reacts to events as they happen.
 * Useful for: detecting slow methods, lock contention, allocation spikes.
 */
@Slf4j
@Component
public class JfrEventMonitor {

    @EventListener(ApplicationReadyEvent.class)
    public void startMonitoring() {
        Thread.ofVirtual().name("jfr-monitor").start(() -> {
            try (var rs = new RecordingStream()) {
                // Alert on methods slower than 500ms
                rs.enable("jdk.MethodTiming")
                  .withThreshold(Duration.ofMillis(500));

                // Log long GC pauses (should be rare with ZGC)
                rs.enable("jdk.GCPhasePauseLevel1")
                  .withThreshold(Duration.ofMillis(100));

                // Detect lock contention
                rs.enable("jdk.JavaMonitorWait")
                  .withThreshold(Duration.ofMillis(200));

                // Detect thread allocation rate spikes
                rs.enable("jdk.ThreadAllocationStatistics")
                  .withPeriod(Duration.ofSeconds(30));

                rs.onEvent("jdk.GCPhasePauseLevel1", event -> {
                    var duration = event.getDuration("duration");
                    log.warn("gc.pause.detected durationMs={} gcId={}",
                            duration.toMillis(), event.getLong("gcId"));
                });

                rs.onEvent("jdk.JavaMonitorWait", event -> {
                    var durationMs = event.getDuration("duration").toMillis();
                    log.warn("lock.contention.detected class={} durationMs={}",
                            event.getClass("monitorClass"), durationMs);
                });

                rs.start();  // Blocks — runs until stream is closed
            }
        });
    }
}
```

---

## Custom JFR Events

```java
package com.banking.platform.monitoring.jfr;

import jdk.jfr.*;

/**
 * Custom JFR event for tracking slow transaction processing.
 * Appears as "com.banking.TransactionProcessed" in JMC.
 */
@Name("com.banking.TransactionProcessed")
@Label("Transaction Processed")
@Category({"Banking", "Transactions"})
@Description("Records when a transaction is processed, with latency and outcome")
@StackTrace(false)     // Disable stack trace — reduces overhead
public class TransactionProcessedEvent extends Event {

    @Label("Transaction ID")
    public String transactionId;

    @Label("Account ID")
    public String accountId;

    @Label("Amount")
    @DataAmount                         // JMC will show human-readable units
    public long amountCents;

    @Label("Type")
    public String type;

    @Label("Outcome")
    public String outcome;              // SUCCESS | FAILED | INSUFFICIENT_FUNDS
}

// Usage in TransactionService:
public TransactionResponse processTransaction(CreateTransactionRequest req) {
    var jfrEvent = new TransactionProcessedEvent();
    jfrEvent.begin();

    try {
        var result = doProcess(req);
        jfrEvent.transactionId = result.transactionId().toString();
        jfrEvent.accountId     = req.accountId().toString();
        jfrEvent.amountCents   = req.amount().movePointRight(2).longValue();
        jfrEvent.type          = req.type();
        jfrEvent.outcome       = "SUCCESS";
        return result;
    } catch (Exception ex) {
        jfrEvent.outcome = ex.getClass().getSimpleName();
        throw ex;
    } finally {
        jfrEvent.end();
        jfrEvent.commit();  // Only committed if shouldCommit() — threshold filtering
    }
}
```

---

## Async Profiler (Flamegraphs)

Async Profiler is the best tool for CPU and allocation flamegraphs — bypasses safepoint bias that JFR has for CPU profiling.

```bash
# 1. Download Async Profiler into the container image
# In Dockerfile (dev/perf image only — not prod):
RUN wget https://github.com/async-profiler/async-profiler/releases/download/v3.0/async-profiler-3.0-linux-x64.tar.gz \
 && tar -xf async-profiler-3.0-linux-x64.tar.gz -C /opt/

# 2. Attach and record 30s CPU flamegraph
PID=$(pgrep -f BankingPlatformApplication)
/opt/async-profiler-3.0-linux-x64/bin/asprof \
  -d 30 \           # 30 second duration
  -e cpu \          # CPU event (use 'alloc' for allocation flamegraph)
  -f /tmp/flamegraph-cpu.html \
  $PID

# 3. Allocation flamegraph (find what's allocating most)
/opt/async-profiler-3.0-linux-x64/bin/asprof \
  -d 30 \
  -e alloc \
  -f /tmp/flamegraph-alloc.html \
  $PID

# 4. Wall-clock profiling (includes I/O wait — great for finding blocking calls)
/opt/async-profiler-3.0-linux-x64/bin/asprof \
  -d 30 \
  -e wall \
  -t \              # Thread-per-frame mode
  -f /tmp/flamegraph-wall.html \
  $PID

# Copy out
kubectl cp banking/<pod>:/tmp/flamegraph-cpu.html ./flamegraph-cpu.html
```

---

## JMC Analysis Workflow

When opening a `.jfr` file in JMC:

```
1. Overview tab
   └── Check: GC pressure, VM info, CPU load, thread count trends

2. Java Application → Method Profiling
   └── Sort by "Total Time" — find hot methods
   └── Look for: serialization, reflection, JDBC, crypto

3. Java Application → Lock Instances
   └── Identify: high contention monitors
   └── Common culprits: ConcurrentHashMap, HikariCP lock, Kafka producer

4. JVM Internals → Garbage Collection
   └── Check: GC cause, pause times, allocation rate (MB/s)
   └── With ZGC: pauses > 10ms are unusual — investigate

5. Java Application → Exceptions
   └── Find: repeated exceptions (even caught ones have overhead)

6. Java Application → Thread Dump (periodic)
   └── Find: thread states — BLOCKED = contention, WAITING = pool starvation

7. Memory → Object Statistics
   └── Find: top allocators, retained heap, suspected leaks
   └── Follow with heap dump + Eclipse MAT if needed
```

---

## Common Bottlenecks & Fixes

| Symptom in JFR | Root Cause | Fix |
|----------------|-----------|-----|
| High CPU in `java.io.ObjectOutputStream` | Java serialization | Switch to JSON/Avro |
| Hot method: `Class.forName()` | Excessive reflection | Cache `Class` references |
| DB pool blocked (HikariCP) | Pool too small or long queries | Increase pool size, add index |
| GC pressure: short-lived objects | Large allocations in hot path | Use object pooling, reduce allocation |
| `sun.misc.Unsafe.park` everywhere | Thread pool starvation | Increase pool size or use virtual threads |
| High allocation in Kafka consumer | JSON deserialization per message | Use Avro + Schema Registry |
| Long `MonitorEnter` waits | `synchronized` block contention | Replace with `ReentrantLock` or lock-free |

---

## Virtual Thread JFR Monitoring (Java 21)

```bash
# Virtual threads show as "VirtualThread-N" — check pinning events
jcmd $PID JFR.start name=vt-profile settings=profile duration=60s filename=/tmp/vt.jfr

# In JMC — look for:
# jdk.VirtualThreadPinned — virtual thread blocked on native/synchronized
# jdk.VirtualThreadSubmitFailed — virtual thread pool saturated

# Pinned virtual threads are the #1 virtual thread performance issue
# Fix: replace synchronized blocks with ReentrantLock in pinned code paths
```

---

## Actuator JFR Endpoint (Spring Boot)

```yaml
# application.yml — expose JFR dump via Actuator (restrict to internal only!)
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, threaddump, heapdump
  endpoint:
    heapdump:
      enabled: true    # GET /actuator/heapdump — triggers heap dump download
    threaddump:
      enabled: true    # GET /actuator/threaddump — JSON thread dump
```

```bash
# Trigger heap dump via actuator (useful in K8s without kubectl exec)
curl -O http://pod-ip:8080/api/actuator/heapdump
# Analyze with Eclipse MAT or JMC
```

---

## Critical Rules

1. **Always enable JFR's continuous recording in production** — overhead is < 1%, value is enormous.
2. **Set `dumponexit=true`** so JFR data is captured on crashes/OOM.
3. **Use `profile` settings for targeted investigations** — not `default` (too low detail).
4. **Never use `jmap -histo:live` in production** — triggers a full GC pause; use `GC.heap_info` instead.
5. **Always set `stackdepth=128`** — default (64) often truncates important frames.
6. **Use Async Profiler for CPU flamegraphs** — JFR's CPU sampling has safepoint bias.
7. **Do NOT include Async Profiler in the production image** — it's a debug tool only.
8. **Restrict `/actuator/heapdump` to internal networks** — heap dumps contain sensitive data.
9. **Custom JFR events add near-zero overhead** when the event threshold is not met (`shouldCommit()`).
10. **Correlate JFR with Grafana metrics** — JFR shows *what*, Prometheus shows *when*.
