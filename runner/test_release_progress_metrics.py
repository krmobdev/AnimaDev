"""No Android device required: deterministic Release metric parsing regression tests."""
import importlib.util
import pathlib
import unittest

MODULE=pathlib.Path(__file__).resolve().parents[1]/"app/src/main/assets/release/progress.py"
spec=importlib.util.spec_from_file_location("release_progress",MODULE)
progress=importlib.util.module_from_spec(spec)
spec.loader.exec_module(progress)

class ReleaseMetricsTest(unittest.TestCase):
    def test_memory_available_and_zram(self):
        raw="MemTotal: 8192000 kB\nMemAvailable: 4096000 kB\nSwapTotal: 2048000 kB\nSwapFree: 1024000 kB\n"
        self.assertEqual(progress._memory(raw),{"mem_total_mb":8000,"mem_available_mb":4000,
                                                "mem_used_mb":4000,"swap_used_mb":1000})

    def test_invalid_memory_is_not_displayed(self):
        self.assertEqual(progress._memory("MemTotal: 1024 kB\nMemAvailable: 2048 kB"),{})

    def test_cpu_normalized_by_all_cores(self):
        before=(1000,400,3)
        after=(2000,600,3)
        self.assertEqual(progress._cpu_metrics(before,after,8),
                         {"cpu_percent":20,"cpu_process_percent":160,"cpu_cores":8})

    def test_cpu_missing_processes_does_not_claim_idle(self):
        self.assertEqual(progress._cpu_metrics((10,1,0),(20,1,0),8),{})
        self.assertEqual(progress._cpu_metrics((10,1,2),(10,2,2),8),{})

    def test_linux_stat_with_spaces_in_process_name(self):
        cols=["S"]+["0"]*20
        cols[11]="23"
        cols[12]="17"
        self.assertEqual(progress._process_ticks("123 (gradle daemon) "+" ".join(cols)),40)

if __name__=="__main__":
    unittest.main()
