import contextlib
import io
import inspect
import json
import os
import pathlib
import runpy
import sys
import traceback


_PRELOADED_NATIVE_LIB_NAMES = set()


def _iter_candidate_lib_dirs(roots):
    for root in roots:
        if not root:
            continue
        root = str(root)
        if not os.path.isdir(root):
            continue

        yield root

        try:
            children = os.listdir(root)
        except Exception:
            children = []

        for child in children:
            if child.endswith(".libs"):
                d = os.path.join(root, child)
                if os.path.isdir(d):
                    yield d

        for rel in (
            "numpy.libs",
            os.path.join("numpy", ".libs"),
            os.path.join("numpy", "core", ".libs"),
            ".libs",
            "libs",
            "lib",
        ):
            d = os.path.join(root, rel)
            if os.path.isdir(d):
                yield d


def _find_file_in_roots(filename: str, roots):
    for d in _iter_candidate_lib_dirs(roots):
        p = os.path.join(d, filename)
        if os.path.isfile(p):
            return p

    for root in roots:
        if not root:
            continue
        root = str(root)
        if not os.path.isdir(root):
            continue
        try:
            for dirpath, _, filenames in os.walk(root):
                if filename in filenames:
                    return os.path.join(dirpath, filename)
        except Exception:
            continue

    return None


def _preload_native_lib_by_name(
    lib_filename: str,
    roots,
    log_errors: bool = False,
    log_stream=None,
) -> bool:
    if lib_filename in _PRELOADED_NATIVE_LIB_NAMES:
        return True

    def log(msg: str):
        if not log_errors:
            return
        try:
            stream = log_stream if log_stream is not None else sys.stderr
            print(msg, file=stream)
        except Exception:
            pass

    try:
        from java.lang import System as JavaSystem
    except Exception:
        JavaSystem = None

    path = _find_file_in_roots(lib_filename, roots)
    if not path:
        log(f"[native] not found: {lib_filename}")
        return False

    # Prefer Java's System.load on Android, which loads the library into the same namespace
    # used by JNI-loaded libraries. This avoids namespace mismatch issues on newer Android
    # versions where dlopen callers may not share the same search space.
    if JavaSystem is not None:
        try:
            JavaSystem.load(path)
            _PRELOADED_NATIVE_LIB_NAMES.add(lib_filename)
            return True
        except Exception as e:
            log(f"[native] System.load failed for {lib_filename} from {path}: {e}")

    try:
        import os
        import ctypes

        mode = 0
        for mod in (os, ctypes):
            mode |= int(getattr(mod, "RTLD_NOW", 0) or 0)
            mode |= int(getattr(mod, "RTLD_GLOBAL", 0) or 0)
        ctypes.CDLL(path, mode=mode or 0)
        _PRELOADED_NATIVE_LIB_NAMES.add(lib_filename)
        return True
    except Exception as e:
        log(f"[native] ctypes.CDLL failed for {lib_filename} from {path}: {e}")
        return False


def _truncate(text: str, max_chars: int):
    if max_chars <= 0:
        return "", bool(text)
    if text is None:
        return "", False
    if len(text) <= max_chars:
        return text, False
    return text[:max_chars] + "\n... (truncated)", True


def _patch_pathlib_write_text_newline():
    """
    Backport `Path.write_text(..., newline=...)` for older Python runtimes where
    `newline` is not supported (e.g., Python < 3.10).

    Returns a list of (cls, original_write_text) for restoration.
    """
    patched = []

    def needs_newline_backport(fn):
        try:
            sig = inspect.signature(fn)
            return "newline" not in sig.parameters
        except Exception:
            return False

    def make_wrapper(orig_fn):
        def write_text(self, data, encoding=None, errors=None, newline=None):
            if newline is None:
                return orig_fn(self, data, encoding=encoding, errors=errors)
            with self.open(mode="w", encoding=encoding, errors=errors, newline=newline) as f:
                return f.write(data)

        return write_text

    for cls_name in ("Path", "PosixPath", "WindowsPath"):
        cls = getattr(pathlib, cls_name, None)
        if cls is None:
            continue
        orig = getattr(cls, "write_text", None)
        if orig is None:
            continue
        if not needs_newline_backport(orig):
            continue
        setattr(cls, "write_text", make_wrapper(orig))
        patched.append((cls, orig))

    return patched


def _coerce_sys_paths(value):
    if value is None:
        return []
    if isinstance(value, (str, bytes, bytearray)):
        return [value]

    try:
        return list(value)
    except TypeError:
        pass

    size = getattr(value, "size", None)
    get = getattr(value, "get", None)
    if callable(size) and callable(get):
        try:
            return [get(i) for i in range(size())]
        except Exception:
            return []

    try:
        n = len(value)
        return [value[i] for i in range(n)]
    except Exception:
        return []


def run_script(
    script_path: str,
    input_json,
    work_dir: str,
    max_stdout_chars: int = 20000,
    max_stderr_chars: int = 20000,
    extra_sys_paths=None,
) -> str:
    """
    Executes a Python script file and calls its `run(input: dict)` function.
    Returns a JSON string containing {ok, result, stdout, stderr, error}.
    """
    ok = False
    result = None
    error = None
    stdout_text = ""
    stderr_text = ""
    stdout_truncated = False
    stderr_truncated = False

    try:
        input_obj = json.loads(input_json) if input_json else {}
        if input_obj is None:
            input_obj = {}
        if not isinstance(input_obj, dict):
            input_obj = {"_value": input_obj}
    except Exception:
        input_obj = {}

    argv = None
    raw_argv = input_obj.get("argv") or input_obj.get("_argv")
    if isinstance(raw_argv, list) and all(isinstance(x, str) for x in raw_argv):
        argv = raw_argv

    extra_paths = _coerce_sys_paths(extra_sys_paths)

    out_buf = io.StringIO()
    err_buf = io.StringIO()

    prev_cwd = os.getcwd()
    prev_argv = list(sys.argv)
    prev_sys_path = list(sys.path)
    try:
        os.makedirs(work_dir, exist_ok=True)
        os.chdir(work_dir)

        with contextlib.redirect_stdout(out_buf), contextlib.redirect_stderr(err_buf):
            patched_pathlib = []
            try:
                sys.argv = [script_path]

                script_dir = os.path.dirname(os.path.abspath(script_path))
                skill_root_dir = os.path.dirname(script_dir) if script_dir else ""

                prefix_paths = []
                if skill_root_dir:
                    prefix_paths.append(skill_root_dir)
                if script_dir:
                    prefix_paths.append(script_dir)

                for p in extra_paths:
                    if p is None:
                        continue
                    p = str(p)
                    if p and os.path.isdir(p):
                        prefix_paths.append(p)

                if prefix_paths:
                    seen = set()
                    ordered_prefix = []
                    for p in prefix_paths:
                        if p not in seen:
                            seen.add(p)
                            ordered_prefix.append(p)

                    sys.path[:] = ordered_prefix + [p for p in prev_sys_path if p not in seen]

                _preload_native_lib_by_name("libc++_shared.so", extra_paths, log_errors=True)
                _preload_native_lib_by_name("libgfortran.so.3", extra_paths, log_errors=True)
                _preload_native_lib_by_name("libopenblas.so", extra_paths, log_errors=True)

                patched_pathlib = _patch_pathlib_write_text_newline()

                scope = runpy.run_path(script_path, run_name="__skill__")
                fn = scope.get("run")
                if callable(fn):
                    result = fn(input_obj)
                    ok = True
                else:
                    if argv is None:
                        raise RuntimeError(
                            "脚本未提供 run(input: dict) 函数。若该脚本为命令行脚本，请传入 argv（或 input.argv）参数列表，例如 ['--help'] 查看用法。"
                        )
                    sys.argv = [script_path] + argv
                    runpy.run_path(script_path, run_name="__main__")
                    result = None
                    ok = True
            finally:
                for cls, orig in reversed(patched_pathlib):
                    try:
                        setattr(cls, "write_text", orig)
                    except Exception:
                        pass
                sys.argv = prev_argv
                sys.path[:] = prev_sys_path
    except BaseException as e:
        if isinstance(e, SystemExit) and e.code in (0, None):
            ok = True
            error = None
        else:
            ok = False
            if isinstance(e, SystemExit) and e.code == 2:
                error = "SystemExit: 2（可能是 argparse 参数错误：请检查 argv（或 input.argv），或改用 run(input) 入口）"
            else:
                error = str(e) or e.__class__.__name__

            formatted_tb = ""
            try:
                formatted_tb = traceback.format_exc()
            except Exception:
                formatted_tb = ""

            err_buf.write("\n")
            err_buf.write(formatted_tb)

            missing_markers = (
                "libopenblas.so",
                "libgfortran.so",
                "libc++_shared.so",
            )
            if any(m in formatted_tb for m in missing_markers) or any(m in (error or "") for m in missing_markers):
                err_buf.write("\n[native] dependency diagnostics (NumPy)\n")
                _preload_native_lib_by_name("libc++_shared.so", extra_paths, log_errors=True, log_stream=err_buf)
                _preload_native_lib_by_name("libgfortran.so.3", extra_paths, log_errors=True, log_stream=err_buf)
                _preload_native_lib_by_name("libopenblas.so", extra_paths, log_errors=True, log_stream=err_buf)
                err_buf.write(
                    "[native] hint: 请导入并启用 chaquopy-libcxx、chaquopy-openblas、chaquopy-libgfortran（ABI 匹配），并重启 App。\n"
                )
    finally:
        try:
            os.chdir(prev_cwd)
        except Exception:
            pass

    stdout_text, stdout_truncated = _truncate(out_buf.getvalue(), int(max_stdout_chars))
    stderr_text, stderr_truncated = _truncate(err_buf.getvalue(), int(max_stderr_chars))

    if ok:
        try:
            json.dumps(result)
        except Exception:
            result = {"_repr": repr(result)}

    payload = {
        "ok": ok,
        "result": result,
        "stdout": stdout_text,
        "stderr": stderr_text,
        "error": error,
        "truncated": {"stdout": stdout_truncated, "stderr": stderr_truncated},
    }
    return json.dumps(payload, ensure_ascii=False)
