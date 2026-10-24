"""Release notes for one Minecraft version from CHANGELOG.md (docs/RELEASE.md).
usage: release_notes.py <version> <mc> [changelog]   prints the body of `## [<version>]`: entries tagged
       `**<other mc> only:**` dropped, the `**<mc> only:**` prefix stripped, empty `###` headings dropped.
       exit 1 when the section is missing or nothing is left for <mc>.
       release_notes.py --selftest"""
import os, re, sys

TAGGED = re.compile(r'^([-*] +)?\*\*(\d+(?:\.\d+)+) only:\*\* *')
BULLET = re.compile(r'^[-*] ')
LINK_REF = re.compile(r'^\[[^\]]+\]: ')


def section(text, version):
    head = re.compile(r'^## \[' + re.escape(version) + r'\]( |$)')
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if head.match(line):
            body = []
            for line in lines[i + 1:]:
                if line.startswith('## ') or LINK_REF.match(line):
                    break
                body.append(line)
            return body
    return None


def blocks(body):
    out = []
    for line in body:
        if not line.strip():
            out.append(('blank', []))
        elif line.startswith('#'):
            out.append(('head', [line]))
        elif out and out[-1][0] == 'entry' and not BULLET.match(line):
            out[-1][1].append(line)
        else:
            out.append(('entry', [line]))
    return out


def notes(text, version, mc):
    body = section(text, version)
    if body is None:
        return None
    kept = []
    for kind, lines in blocks(body):
        if kind == 'entry':
            m = TAGGED.match(lines[0])
            if m and m.group(2) != mc:
                continue
            if m:
                lines = [(m.group(1) or '') + lines[0][m.end():]] + lines[1:]
        kept.append((kind, lines))
    def empty_heading(i):
        if not kept[i][1][0].startswith('### '):
            return False
        nxt = next((k for k, _ in kept[i + 1:] if k != 'blank'), None)
        return nxt in (None, 'head')
    out = []
    for i, (kind, lines) in enumerate(kept):
        if kind == 'blank':
            if out and out[-1] != '':
                out.append('')
        elif not (kind == 'head' and empty_heading(i)):
            out.extend(lines)
    while out and out[-1] == '':
        out.pop()
    return '\n'.join(out)


SAMPLE = """# Changelog

## [Unreleased]

- Pending.

## [2.0.0] - 2026-01-02

Intro.

### Added

- Shared thing.
- **1.20.1 only:** Forge thing,
  second line.
- **1.21.1 only:** Neo thing.

### Platforms

- **1.20.1 only:** First Forge build.

## [1.0.0] - 2026-01-01

- Old.

[Unreleased]: https://example.org/compare/v2.0.0...HEAD
[1.0.0]: https://example.org/releases/tag/v1.0.0
"""


def selftest():
    cases = [
        (('2.0.0', '1.21.1'), 'Intro.\n\n### Added\n\n- Shared thing.\n- Neo thing.'),
        (('2.0.0', '1.20.1'), 'Intro.\n\n### Added\n\n- Shared thing.\n- Forge thing,\n  second line.\n\n### Platforms\n\n- First Forge build.'),
        (('1.0.0', '1.21.1'), '- Old.'),
        (('Unreleased', '1.20.1'), '- Pending.'),
        (('2.0', '1.21.1'), None),
        (('3.0.0', '1.21.1'), None),
    ]
    bad = 0
    for (version, mc), want in cases:
        got = notes(SAMPLE, version, mc)
        if got != want:
            bad += 1
            print(f'FAIL {version} {mc}: got {got!r}, want {want!r}')
    print('PASS' if not bad else 'FAIL')
    return 1 if bad else 0


def main(argv):
    if argv[1:] == ['--selftest']:
        return selftest()
    if len(argv) not in (3, 4):
        print(__doc__, file=sys.stderr)
        return 2
    version, mc = argv[1], argv[2]
    path = argv[3] if len(argv) == 4 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'CHANGELOG.md')
    with open(path, encoding='utf-8') as f:
        text = f.read()
    out = notes(text, version, mc)
    if out is None:
        print(f'CHANGELOG.md has no ## [{version}] section', file=sys.stderr)
        return 1
    if not out:
        print(f'CHANGELOG.md [{version}] has nothing for Minecraft {mc}', file=sys.stderr)
        return 1
    print(out)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
