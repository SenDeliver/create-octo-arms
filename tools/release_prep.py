"""Prepare a release (docs/RELEASE.md): set mod_version in gradle.properties, move the [Unreleased]
entries of CHANGELOG.md under a new `## [X.Y.Z] - <today>`, update the compare links, print the next steps.
usage: release_prep.py X.Y.Z [--root DIR]   text edits only, no git.
       exit 1 when [Unreleased] is empty, X.Y.Z is already in the changelog or not above mod_version."""
import datetime, os, re, sys

TAGGED = re.compile(r'^(?:[-*] +)?\*\*(\d+(?:\.\d+)+) only:\*\*')
LINK_UNRELEASED = re.compile(r'^\[Unreleased\]: (?P<base>\S+)/compare/(?P<prev>\S+)\.\.\.HEAD$')
PRIMARY_MC = '1.21.1'


def fail(msg):
    print(f'release_prep: {msg}', file=sys.stderr)
    sys.exit(1)


def prop(text, key):
    m = re.search(rf'^{key}=(.*)$', text, re.M)
    if not m:
        fail(f'gradle.properties has no {key}')
    return m.group(1).strip()


def link_mc(entries):
    """The Minecraft version the section's compare link points at: the only platform when every entry
    is tagged for the same one, else the primary line. Depends on the text only, so every branch gets the same file."""
    tags = [TAGGED.match(l) for l in entries if re.match(r'^([-*] |\*\*)', l)]
    mcs = {m.group(1) if m else None for m in tags}
    return mcs.pop() if len(mcs) == 1 and None not in mcs else PRIMARY_MC


def main(argv):
    args = argv[1:]
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
    if '--root' in args:
        i = args.index('--root')
        root = args[i + 1]
        del args[i:i + 2]
    if len(args) != 1 or not re.fullmatch(r'\d+\.\d+\.\d+', args[0]):
        print(__doc__, file=sys.stderr)
        return 2
    version = args[0]
    props_path = os.path.join(root, 'gradle.properties')
    log_path = os.path.join(root, 'CHANGELOG.md')
    props = open(props_path, encoding='utf-8').read()
    log = open(log_path, encoding='utf-8').read()
    old = prop(props, 'mod_version')
    mc = prop(props, 'minecraft_version')
    if tuple(map(int, version.split('.'))) <= tuple(map(int, old.split('.'))):
        fail(f'{version} is not above mod_version {old}')
    if re.search(r'^## \[' + re.escape(version) + r'\]', log, re.M):
        fail(f'CHANGELOG.md already has [{version}]')

    lines = log.split('\n')
    try:
        u = lines.index('## [Unreleased]')
    except ValueError:
        fail('CHANGELOG.md has no "## [Unreleased]" line')
    n = next((i for i in range(u + 1, len(lines))
              if lines[i].startswith('## ') or re.match(r'^\[[^\]]+\]: ', lines[i])), len(lines))
    entries = lines[u + 1:n]
    if not any(l.strip() and not l.startswith('#') for l in entries):
        fail('[Unreleased] is empty, nothing to release')
    while entries and not entries[0].strip():
        entries.pop(0)
    lines[u:n] = ['## [Unreleased]', '', f'## [{version}] - {datetime.date.today().isoformat()}', ''] + entries

    tag = f'v{version}%2Bmc{link_mc(entries)}'
    for i, l in enumerate(lines):
        m = LINK_UNRELEASED.match(l)
        if m:
            lines[i:i + 1] = [f'[Unreleased]: {m["base"]}/compare/{tag}...HEAD',
                              f'[{version}]: {m["base"]}/compare/{m["prev"]}...{tag}']
            break

    open(log_path, 'w', encoding='utf-8').write('\n'.join(lines))
    open(props_path, 'w', encoding='utf-8').write(re.sub(r'^mod_version=.*$', f'mod_version={version}', props, flags=re.M))

    print(f"""Prepared {version} (was {old}): gradle.properties mod_version, CHANGELOG.md [{version}].
Next, on branch {mc}:
  1. Review CHANGELOG.md [{version}]: short, user-facing, `**<mc> only:**` tags, packet changes under Compatibility.
  2. ./gradlew runData && python3 tools/lang_check.py
  3. ./gradlew build   (build/libs/octo-arms-{version}+mc{mc}.jar)
  4. tools/run_all.sh <tag>
  5. python3 tools/release_notes.py {version} {mc} > notes.md
  6. Commit "Release {version}", push; bring the same CHANGELOG.md (and the bump, for a shared release) to the other branch.
  7. Owner only, after the in-game test:
     gh release create v{version}+mc{mc} --target {mc} --title "Octo Arms {version} for {mc}" --notes-file notes.md""")
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
