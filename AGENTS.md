# Repository Guidelines

## Project Structure & Module Organization

This Eclipse Java Card project contains one applet. Source lives under `src/com/nhom11/smartcard/`; `EmployeeCardApplet.java` is the entry point. Conversion settings are in `configurations/EmployeeSmartCard.conf`. Treat `.gen/`, `bin/`, and `deliverables/` as generated.

## System Scope & Architecture

The project implements a smart employee card and attendance system. The card must authenticate by PIN; store employee ID, name, department, and job title; record shift check-in/check-out data; and enforce area access by department or position. It must also support temporary grants and card revocation when employment ends. Protect sensitive commands and data with AES and cryptographic authentication to prevent forged cards.

Keep card-side code focused on secure storage, authentication, APDU commands, and access decisions. Attendance calculation, administration, and report export belong in the host application. Document new APDU formats, authorization rules, status words, and host behavior.

## Build, Test, and Development Commands

The project expects Oracle Java Card Development Kit tooling and the Eclipse plug-in. Define `JC_TOOLS_HOME` so `.classpath` resolves its API libraries.

- Use **Project > Clean**, then **Project > Build Project** in Eclipse to regenerate deliverables.
- `javac -source 10 -target 10 -cp "$JC_TOOLS_HOME/lib/api_classic-preview.jar" -d bin $(find src -name '*.java')` compiles the applet from a shell when the kit is configured.
- From `configurations/`, run the kit converter with `EmployeeSmartCard.conf` to regenerate CAP/JCA/EXP files.

Do not hand-edit generated `.class`, `.cap`, `.jca`, or `.exp` files.

## Coding Style & Naming Conventions

Use four spaces and same-line opening braces. Follow Java naming: `PascalCase` classes, `camelCase` members, and `UPPER_SNAKE_CASE` constants. Keep packages lowercase under `com.nhom11.smartcard`. Prefer fixed-size data structures and avoid allocation during APDU processing. Document APDU methods with concise Javadoc.

## Testing Guidelines

No automated tests are included. Add simulator tests named `*Test` when feasible. At minimum, rebuild the CAP and test installation, PIN retry/lock behavior, authorized and denied access, temporary grants, revocation, malformed APDUs, cryptographic verification, and expected status words.

## Commit & Pull Request Guidelines

Git history is unavailable, so use short imperative subjects such as `apdu: reject invalid employee records`. Keep tracked generated artifacts synchronized with source. Pull requests should explain protocol changes, affected INS/P1/P2 values, authorization impact, status words, validation performed, and linked issues.

## Work Log Requirements

Every change to source code, configuration, documentation, tests, or generated deliverables must include a corresponding entry in `WORKLOG.md`. Add the entry in the same work session using the existing daily table format: member, task, status, output, and time. Do not mark work complete until the log is updated. Group related edits in one entry; never record PINs, keys, employee data, or other secrets.

## Security & Configuration Tips

Never commit real employee data, PINs, production keys, or card secrets. Do not log sensitive APDU payloads. Coordinate AID, key-management, and package-version changes because they affect card issuance and host integrations.
