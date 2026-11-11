// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.macrobenchmark

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.perfetto.ExperimentalPerfettoCaptureApi
import androidx.benchmark.perfetto.PerfettoConfig

/**
 * Measures Plasma Benchmark as [MacrobenchmarkRule.measureRepeated] does, with [PLASMA_TRACE] as
 * the trace of each iteration.
 */
@OptIn(ExperimentalBenchmarkConfigApi::class, ExperimentalPerfettoCaptureApi::class)
internal fun MacrobenchmarkRule.measurePlasma(
    metrics: List<Metric>,
    iterations: Int,
    startupMode: StartupMode? = null,
    setupBlock: MacrobenchmarkScope.() -> Unit = {},
    measureBlock: MacrobenchmarkScope.() -> Unit,
) = measureRepeated(
    packageName = PLASMA,
    metrics = metrics,
    iterations = iterations,
    perfettoConfig = PerfettoConfig.Text(PLASMA_TRACE),
    startupMode = startupMode,
    setupBlock = setupBlock,
    measureBlock = measureBlock,
)

/** [PLASMA] as a regular expression, its dots escaped for the text proto's string too. */
private val PLASMA_PRODUCER = ".*" + PLASMA.replace(".", "\\\\.") + ".*"

/**
 * Macrobenchmark's own trace (benchmark 1.5.0's `PerfettoConfig.Benchmark`), with one change: its
 * `track_event` data source, which composition tracing writes to, is Plasma's alone, not every
 * app's that writes trace data of its own.
 *
 * Before an iteration starts, `perfetto --background-wait` waits for every data source to start,
 * for 30 s at most. An app Android has frozen in the background can't start its own, so the wait
 * runs out and the iteration goes ahead. When that app is woken later on (`dumpsys gfxinfo`, which
 * macrobenchmark runs to see a launch's first frame, wakes frozen apps), its data source starts,
 * and the tracing process goes to tell the command that waited for it. The command has gone, so
 * the tracing process stops, and the trace ends wherever the iteration has got to.
 */
private val PLASMA_TRACE = """
    buffers { size_kb: 32768 fill_policy: RING_BUFFER }
    buffers { size_kb: 4096 fill_policy: RING_BUFFER }
    data_sources {
      config {
        name: "linux.ftrace"
        target_buffer: 0
        ftrace_config {
          ftrace_events: "task/task_newtask"
          ftrace_events: "task/task_rename"
          ftrace_events: "sched/sched_process_exit"
          ftrace_events: "sched/sched_process_free"
          ftrace_events: "mm_event/mm_event_record"
          ftrace_events: "kmem/rss_stat"
          ftrace_events: "kmem/ion_heap_shrink"
          ftrace_events: "kmem/ion_heap_grow"
          ftrace_events: "ion/ion_stat"
          ftrace_events: "oom/oom_score_adj_update"
          ftrace_events: "disk"
          ftrace_events: "ufs/ufshcd_clk_gating"
          ftrace_events: "lowmemorykiller/lowmemory_kill"
          atrace_categories: "am"
          atrace_categories: "aidl"
          atrace_categories: "audio"
          atrace_categories: "binder_driver"
          atrace_categories: "camera"
          atrace_categories: "dalvik"
          atrace_categories: "freq"
          atrace_categories: "gfx"
          atrace_categories: "hal"
          atrace_categories: "idle"
          atrace_categories: "input"
          atrace_categories: "memreclaim"
          atrace_categories: "power"
          atrace_categories: "res"
          atrace_categories: "sched"
          atrace_categories: "sync"
          atrace_categories: "view"
          atrace_categories: "wm"
          atrace_apps: "*"
          compact_sched { enabled: true }
        }
      }
    }
    data_sources {
      config {
        name: "linux.process_stats"
        target_buffer: 1
        process_stats_config { scan_all_processes_on_start: true proc_stats_poll_ms: 10000 }
      }
    }
    data_sources { config { name: "android.packages_list" target_buffer: 1 } }
    data_sources {
      config {
        name: "linux.sys_stats"
        target_buffer: 1
        sys_stats_config {
          meminfo_period_ms: 1000
          meminfo_counters: MEMINFO_MEM_TOTAL
          meminfo_counters: MEMINFO_MEM_FREE
          meminfo_counters: MEMINFO_MEM_AVAILABLE
          meminfo_counters: MEMINFO_BUFFERS
          meminfo_counters: MEMINFO_CACHED
          meminfo_counters: MEMINFO_SWAP_CACHED
          meminfo_counters: MEMINFO_ACTIVE
          meminfo_counters: MEMINFO_INACTIVE
          meminfo_counters: MEMINFO_ACTIVE_ANON
          meminfo_counters: MEMINFO_INACTIVE_ANON
          meminfo_counters: MEMINFO_ACTIVE_FILE
          meminfo_counters: MEMINFO_INACTIVE_FILE
          meminfo_counters: MEMINFO_UNEVICTABLE
          meminfo_counters: MEMINFO_SWAP_TOTAL
          meminfo_counters: MEMINFO_SWAP_FREE
          meminfo_counters: MEMINFO_DIRTY
          meminfo_counters: MEMINFO_WRITEBACK
          meminfo_counters: MEMINFO_ANON_PAGES
          meminfo_counters: MEMINFO_MAPPED
          meminfo_counters: MEMINFO_SHMEM
        }
      }
    }
    data_sources {
      config {
        name: "android.power"
        android_power_config {
          battery_poll_ms: 250
          battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
          battery_counters: BATTERY_COUNTER_CHARGE
          battery_counters: BATTERY_COUNTER_CURRENT
          collect_power_rails: true
        }
      }
    }
    data_sources { config { name: "android.gpu.memory" } }
    data_sources { config { name: "android.surfaceflinger.frame" } }
    data_sources { config { name: "android.surfaceflinger.frametimeline" } }
    data_sources { config { name: "linux.system_info" target_buffer: 1 } }
    data_sources {
      config {
        name: "track_event"
        track_event_config { disabled_categories: "*" enabled_categories: "rendering" }
      }
      producer_name_regex_filter: "$PLASMA_PRODUCER"
    }
    write_into_file: true
    file_write_period_ms: 2500
    flush_period_ms: 5000
    data_source_stop_timeout_ms: 2500
""".trimIndent()
