from pathlib import Path
import runpy
import sys
import traceback

workspace_dir = Path(__file__).resolve().parent
verify_runtime_path = workspace_dir / "verify_runtime.py"
output_path = workspace_dir / "verification_results.txt"

# Open the required evidence file first. This is the only artifact the launcher is allowed to create.
with output_path.open("w", encoding="utf-8") as evidence:
    evidence.write("=== VERIFICATION LAUNCHER STARTED ===\n")
    evidence.write(f"workspace_dir={workspace_dir}\n")
    evidence.write(f"verify_runtime_path={verify_runtime_path}\n")
    evidence.write(f"output_path={output_path}\n")
    evidence.flush()

exit_status = "unavailable"
traceback_text = ""

try:
    runpy.run_path(str(verify_runtime_path), run_name="__main__")
except SystemExit as exc:
    exit_status = int(exc.code) if isinstance(exc.code, int) else str(exc.code)
    traceback_text = traceback.format_exc()
except Exception as exc:
    exit_status = type(exc).__name__
    traceback_text = traceback.format_exc()
except BaseException as exc:
    exit_status = type(exc).__name__
    traceback_text = traceback.format_exc()
finally:
    try:
        with output_path.open("a", encoding="utf-8") as evidence:
            if "HARNESS EXECUTION FAILED" in traceback_text:
                evidence.write("HARNESS EXECUTION FAILED\n")
            if traceback_text:
                evidence.write("=== TRACEBACK ===\n")
                evidence.write(traceback_text)
                evidence.write("\n")
            evidence.write(f"=== EXIT STATUS: {exit_status} ===\n")
            evidence.write("=== VERIFICATION LAUNCHER FINISHED ===\n")
            evidence.flush()
    except Exception as launcher_exc:
        try:
            with output_path.open("a", encoding="utf-8") as evidence:
                evidence.write("=== VERIFICATION LAUNCHER FINISHED ===\n")
                evidence.write("HARNESS EXECUTION FAILED\n")
                evidence.write(traceback.format_exc())
                evidence.write("\n")
                evidence.write(f"LAUNCHER_WRITE_FAILURE: {launcher_exc}\n")
                evidence.flush()
        except Exception:
            pass
