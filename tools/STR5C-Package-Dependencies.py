#!/usr/bin/env python3
"""Package real vcpkg Android ABI dependencies for trusted Windows import.
Not a compiler or source of fake libraries. Fail if upstream build is missing.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import zipfile

ABI_TO_TRIPLET = {
    'arm64-v8a': 'pm-arm64-android',
    'armeabi-v7a': 'pm-arm-neon-android',
    'x86': 'pm-x86-android',
    'x86_64': 'pm-x64-android',
}


def validate_prefix(prefix: Path) -> dict:
    required = [prefix / 'include' / 'libtorrent' / 'session.hpp',
                prefix / 'lib' / 'libssl.a', prefix / 'lib' / 'libcrypto.a']
    torrent = sorted((prefix / 'lib').glob('*torrent*.a'))
    configs = [x for x in (prefix / 'share').rglob('*')
               if x.is_file() and x.name.lower() == 'libtorrentrasterbarconfig.cmake'] if (prefix / 'share').exists() else []
    if not torrent or not configs or any(not p.is_file() for p in required):
        raise ValueError('Missing libtorrent header, static .a archives or CMake package config')
    for library in [*torrent, prefix / 'lib' / 'libssl.a', prefix / 'lib' / 'libcrypto.a']:
        if library.open('rb').read(8) not in (b'!<arch>\n', b'!<thin>\n'):
            raise ValueError(f'{library.name}: not a real static archive')
    return {'torrent_libraries': [x.name for x in torrent],
            'cmake_configs': [str(x.relative_to(prefix)).replace('\\', '/') for x in configs]}


def package(args):
    prefix = Path(args.prefix).resolve(strict=True)
    if ABI_TO_TRIPLET.get(args.abi) != args.triplet or prefix.name != args.triplet:
        raise ValueError('ABI/triplet/prefix mismatch')
    if args.api != 26 or args.ndk != '27.0.12077973':
        raise ValueError('This workflow is pinned to API 26 and NDK 27.0.12077973')
    validation = validate_prefix(prefix)
    manifest = {
        'artifact_schema': 1, 'project': 'PersonalMedia-STR5C',
        'abi': args.abi, 'triplet': args.triplet, 'android_api': args.api,
        'android_ndk': args.ndk, 'cxx_stl': 'c++_static',
        'dependency_source': 'microsoft/vcpkg', 'vcpkg_commit': args.vcpkg_commit,
        **validation,
    }
    dest = Path(args.out_dir).resolve()
    dest.mkdir(parents=True, exist_ok=True)
    filename = f'pm-libtorrent-{args.abi}.zip'
    output = dest / filename
    # Include debug as well as release archives: CMake Debug imports may require it.
    # Keep entire vcpkg package prefix (including share/ configs and licenses).
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as z:
        z.writestr(f'{args.triplet}/PM_STR5C_BUILD_INFO.json', json.dumps(manifest, indent=2, sort_keys=True) + '\n')
        count = 0
        for path in sorted(prefix.rglob('*')):
            if not path.is_file():
                continue
            relative = path.relative_to(prefix)
            # Prevent accidental packaging of host-specific absolute symlink targets.
            if path.is_symlink():
                target = path.resolve(strict=True)
                if prefix != target and prefix not in target.parents:
                    raise ValueError(f'External symlink in package: {relative}')
            if relative.name == 'PM_STR5C_BUILD_INFO.json':
                continue
            z.write(path, f'{args.triplet}/{relative.as_posix()}')
            count += 1
    if count < 10:
        raise ValueError('vcpkg package unexpectedly small')
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (dest / (filename + '.sha256')).write_text(f'{digest}  {filename}\n', encoding='ascii')
    print(f'Created {output} ({output.stat().st_size:,} bytes, {count} files)')
    print(f'SHA256 {digest}')
    print(json.dumps(manifest, sort_keys=True))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--prefix', required=True)
    p.add_argument('--abi', required=True, choices=list(ABI_TO_TRIPLET))
    p.add_argument('--triplet', required=True)
    p.add_argument('--ndk', required=True)
    p.add_argument('--api', required=True, type=int)
    p.add_argument('--vcpkg-commit', required=True)
    p.add_argument('--out-dir', required=True)
    try:
        package(p.parse_args())
    except (OSError, ValueError, zipfile.BadZipFile) as e:
        print(f'STR-5C packaging refused: {e}', file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()
