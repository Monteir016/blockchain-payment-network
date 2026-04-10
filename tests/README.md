# System test harness

## Layout

```
tests/
├── inputs/
│   ├── input01.txt
│   ├── input02.txt
│   ├── input03.txt
│   └── input04.txt
├── outputs/
│   ├── out01.txt
│   ├── out02.txt
│   ├── out03.txt
│   └── out04.txt
├── README.md
└── run_tests.sh
```

## How it works

Each `inputs/input*.txt` file is a sequence of client commands. The matching `outputs/out*.txt` contains the expected console output.

Tests run in order (`input01`, `input02`, …) until the next index is missing.

The shell script only launches the client (a new client process per test). Nodes and the sequencer are assumed to be running already.

## Usage

1. Edit `run_tests.sh` and set the *PATHS* section to match your machine.

2. Build the project, e.g. from the repo root: `mvn clean install -DskipTests`.

3. Start node(s) and the sequencer.

4. Run `./run_tests.sh` from this directory.

5. On failure, compare generated logs under the script’s output directory with the expected `outputs/` files.

## Test cases (summary)

- **input01.txt**: Single-wallet create/delete.
- **input02.txt**: Two wallets, create/delete.
- **input03.txt**: Transfers between wallets.
- **input04.txt**: Transfer with insufficient balance.

Wallet identifiers in inputs may use the `XXXX` placeholder; the script substitutes a unique value per run so cases stay isolated while nodes keep running.
