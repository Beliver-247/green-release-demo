# Selective Build Script
<!-- Test 1: doc-only change -->


This repository includes a Python helper, `selective_build.py`, that decides which Maven modules need to be built and tested after the most recent commit.

The goal is to avoid running a full multi-module build when only a small part of the codebase changed.

## What It Does

The script compares `HEAD~1` with `HEAD`, collects the changed files, and then:

1. Detects which top-level Maven module each file belongs to.
2. Expands the impact through the internal module dependency graph.
3. Skips work for documentation-only changes.
4. Runs a targeted Maven build and test pass for the affected modules.

In this project, the module dependency chain is:

`core -> service -> api -> app`

So a change in a lower-level module triggers rebuilds of the modules that depend on it.

## Repository Modules

- `core`: shared business logic
- `service`: service layer built on `core`
- `api`: REST layer built on `service`
- `app`: Spring Boot entry point built on `api`

## How Impact Is Calculated

The script uses a few simple rules:

- Changes inside a module folder mark that module as directly affected.
- Maven `pom.xml` changes are treated as build-relevant and may widen the affected set.
- Documentation files such as `.md`, `.txt`, `.rst`, `.adoc`, and common image formats are ignored for build selection.
- Changes in a shared module name such as `common-lib` trigger a full module rebuild.

After the direct impact is identified, the script walks the reverse dependency graph so that dependent modules are included automatically.

## Usage

Run the script from the repository root:

```bash
python3 selective_build.py
```

Preview the actions without executing Maven:

```bash
python3 selective_build.py --dry-run
```

## What It Runs

For the final affected module set, the script executes:

```bash
mvn -pl <modules> -am clean install
mvn -pl <modules> -am test
```

`-pl` selects the affected modules, and `-am` asks Maven to build any required upstream modules too.

## Example Outcomes

- If `core` changes, the affected set becomes `core`, `service`, `api`, and `app`.
- If `service` changes, the affected set becomes `service`, `api`, and `app`.
- If `api` changes, the affected set becomes `api` and `app`.
- If only documentation changes, the script skips Maven entirely.

## Notes

- The script only looks at the most recent commit delta by default.
- It prints the changed files, the affected modules, and the actions it takes.
- If no build-relevant files changed, it exits without running Maven.

## Related Files

- [selective_build.py](selective_build.py)
- [test_selective_build.py](test_selective_build.py)
- [SUMMARY.md](SUMMARY.md)