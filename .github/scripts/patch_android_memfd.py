#!/usr/bin/env python3
"""ZERO Native rev2 v1.4: narrow Android memfd_create portability correction.

Only modifies the *public* WiiCompiled source checkout. Does not read game files.
Requires the exact pinned source fragment and preserves Linux/desktop behavior.
"""
from __future__ import annotations
import argparse
import difflib
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

SOURCE_REL = Path('runtime/src/guest_flat_memory.cpp')
HEADER_OLD = '#include <fcntl.h>\n#include <sys/mman.h>\n#include <unistd.h>'
HEADER_NEW = '''#include <fcntl.h>
#if defined(__ANDROID__)
// Android libc declares memfd_create only for API >= 30 with GNU extensions.
// Use the kernel syscall so the runtime can target older Android API levels.
#include <linux/memfd.h>
#include <sys/syscall.h>
#endif
#include <sys/mman.h>
#include <unistd.h>'''
CALL_OLD = '        section.fd = memfd_create("wiicompiled-guest-ram", MFD_CLOEXEC);'
CALL_NEW = '''#if defined(__ANDROID__)
        section.fd = static_cast<int>(
            syscall(__NR_memfd_create, "wiicompiled-guest-ram", MFD_CLOEXEC));
#else
        section.fd = memfd_create("wiicompiled-guest-ram", MFD_CLOEXEC);
#endif'''
PINNED_COMMIT = '69ee62be4a28b14ae69c2a03443e3e376f5c3344'


def patch_source(data: str) -> tuple[str, bool]:
    """Return (patched_source, changed). Reject unexpected or partial source state."""
    header_new = HEADER_NEW in data
    call_new = CALL_NEW in data
    if header_new or call_new:
        if header_new and call_new and data.count(HEADER_NEW) == 1 and data.count(CALL_NEW) == 1:
            return data, False
        raise ValueError('partially patched or ambiguous Android memfd source: refusing modification')
    if data.count(HEADER_OLD) != 1 or data.count(CALL_OLD) != 1:
        raise ValueError('source differs from reviewed pinned version: refusing modification')
    return data.replace(HEADER_OLD, HEADER_NEW, 1).replace(CALL_OLD, CALL_NEW, 1), True


def apply(repo: Path, dest: Path, *, enforce_commit: bool = True) -> None:
    repo = repo.expanduser().resolve()
    source = repo / SOURCE_REL
    if enforce_commit:
        head = subprocess.check_output(['git', '-C', str(repo), 'rev-parse', 'HEAD'], text=True).strip()
        if head != PINNED_COMMIT:
            raise ValueError(f'repository HEAD is {head}, expected pinned {PINNED_COMMIT}')
    if not source.is_file():
        raise FileNotFoundError(f'missing source file: {source}')
    before = source.read_text(encoding='utf-8')
    after, changed = patch_source(before)
    dest.mkdir(parents=True, exist_ok=True)
    diff_path = dest / 'android_memfd_v14.patch'
    if not diff_path.exists():
        oldbase = before if changed else before.replace(HEADER_NEW, HEADER_OLD, 1).replace(CALL_NEW, CALL_OLD, 1)
        diff_text = ''.join(difflib.unified_diff(
            oldbase.splitlines(keepends=True), after.splitlines(keepends=True),
            fromfile=f'a/{SOURCE_REL}', tofile=f'b/{SOURCE_REL}'))
        diff_path.write_text(diff_text, encoding='utf-8')
    if changed:
        backup = dest / 'guest_flat_memory.cpp.before-v14'
        if not backup.exists():
            shutil.copy2(source, backup)
        temp = source.with_suffix(source.suffix + '.zero14.tmp')
        try:
            temp.write_text(after, encoding='utf-8')
            os.replace(temp, source)
        finally:
            temp.unlink(missing_ok=True)
    actual = source.read_text(encoding='utf-8')
    assert actual == after
    print('[ZERO Native] Android memfd portability patch:', 'applied' if changed else 'already present')
    print('[ZERO Native] Reviewed source SHA256:', hashlib.sha256(actual.encode()).hexdigest())
    print('[ZERO Native] Patch review:', diff_path)
    print('[ZERO Native] No game-derived binaries modified or uploaded.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path.home() / 'zero-native-src')
    parser.add_argument('--out', type=Path, default=Path.home() / 'zero-native-private/rev2-runtime-core/patches')
    args = parser.parse_args()
    try:
        apply(args.repo, args.out)
    except (ValueError, FileNotFoundError, subprocess.CalledProcessError, OSError) as exc:
        parser.exit(2, f'STOP: {exc}\n')
