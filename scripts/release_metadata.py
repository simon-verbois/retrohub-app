#!/usr/bin/env python3
"""Canonical calendar versions and changelog rollover; no Git/network side effects."""
import argparse
from datetime import date
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
UNRELEASED = '## [Unreleased]'
DEVELOPMENT_VERSION = '0.1.0'
TAG = re.compile(r'(20[0-9]{2})\.([1-9]|[1-4][0-9]|5[0-3])\.([1-9][0-9]{0,3})')


def version_code(version):
    if version == DEVELOPMENT_VERSION:
        return 1
    match = TAG.fullmatch(version)
    if match is None:
        raise ValueError('Expected a calendar version YYYY.W.ID (2000–2099, week 1–53, ID 1–9999; no leading zeroes).')
    year, week, ident = map(int, match.groups())
    # Reserve four digits per weekly release counter. Monotonic across ISO years.
    return year * 1_000_000 + week * 10_000 + ident


def release_notes(changelog, version):
    heading = re.compile(r'^## \[' + re.escape(version) + r'\] - (\d{4}-\d{2}-\d{2})$', re.M)
    matches = list(heading.finditer(changelog))
    if len(matches) != 1:
        raise ValueError('The release must have exactly one dated changelog heading.')
    date.fromisoformat(matches[0][1])
    return re.split(r'^## ', changelog[matches[0].end():], maxsplit=1, flags=re.M)[0].strip()


def rollover(changelog, version, release_date):
    version_code(version)
    if not TAG.fullmatch(version):
        raise ValueError('Development versions cannot be released.')
    date.fromisoformat(release_date)
    lines = changelog.splitlines(keepends=True)
    indexes = [i for i, line in enumerate(lines) if line.rstrip('\r\n') == UNRELEASED]
    if len(indexes) != 1:
        raise ValueError('CHANGELOG.md must contain exactly one ## [Unreleased] placeholder.')
    start = indexes[0]
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith('## ')), len(lines))
    if not any(re.match(r'^- \S', line) for line in lines[start + 1:end]):
        raise ValueError('The Unreleased section has no change entries.')
    if re.search(r'^## \[' + re.escape(version) + r'\]', changelog, re.M):
        raise ValueError('The release already exists in CHANGELOG.md.')
    lines[start] = f'{UNRELEASED}\n\n## [{version}] - {release_date}\n'
    return ''.join(lines)


def next_version(year, week, versions):
    # Validate the requested prefix through the same native-version contract.
    version_code(f'{year}.{week}.1')
    previous = [int(match[3]) for value in versions if (match := TAG.fullmatch(value)) and int(match[1]) == year and int(match[2]) == week]
    candidate = f'{year}.{week}.{max(previous, default=0) + 1}'
    version_code(candidate)
    return candidate


def validate_release(root, tag):
    if not TAG.fullmatch(tag):
        raise ValueError('The pushed tag is not a release calendar version.')
    version = (root / 'VERSION').read_text().strip()
    code = version_code(version)
    if tag != version:
        raise ValueError('The pushed tag does not match VERSION.')
    changelog = (root / 'CHANGELOG.md').read_text()
    if changelog.splitlines().count(UNRELEASED) != 1:
        raise ValueError('Expected exactly one Unreleased placeholder.')
    notes = release_notes(changelog, version)
    if not re.search(r'^- \S', notes, re.M):
        raise ValueError('The release changelog has no change entries.')
    return version, code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('version')
    check = commands.add_parser('check')
    check.add_argument('--tag', required=True)
    following = commands.add_parser('next')
    following.add_argument('--year', required=True, type=int)
    following.add_argument('--week', required=True, type=int)
    prepare = commands.add_parser('prepare')
    prepare.add_argument('--tag', required=True)
    prepare.add_argument('--date', default=date.today().isoformat())
    notes = commands.add_parser('notes')
    notes.add_argument('--tag', required=True)
    args = parser.parse_args()
    try:
        if args.command == 'version':
            value = (ROOT / 'VERSION').read_text().strip()
            print(value)
            print(version_code(value))
        elif args.command == 'check':
            value, code = validate_release(ROOT, args.tag)
            print(json.dumps({'version': value, 'versionCode': code}))
        elif args.command == 'next':
            versions = [line.split()[-1].removeprefix('refs/tags/') for line in sys.stdin if line.strip()]
            print(next_version(args.year, args.week, versions))
        elif args.command == 'prepare':
            updated = rollover((ROOT / 'CHANGELOG.md').read_text(), args.tag, args.date)
            # Validate everything before changing either tracked file.
            (ROOT / 'CHANGELOG.md').write_text(updated)
            (ROOT / 'VERSION').write_text(args.tag + '\n')
        else:
            validate_release(ROOT, args.tag)
            print(release_notes((ROOT / 'CHANGELOG.md').read_text(), args.tag))
    except (OSError, ValueError) as error:
        parser.exit(1, f'Release metadata: {error}\n')


if __name__ == '__main__':
    main()
