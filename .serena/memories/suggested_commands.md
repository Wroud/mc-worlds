# mc-worlds — commands

All from the project root.

```bash
./gradlew build          # jar -> versions/latest/build/libs/worlds-<mod_version>+<mc_version>.jar
./gradlew runClient      # dev client, auto-logs in as "Wroud"
./gradlew runServer      # dev server (also the only QA harness)
./gradlew runDatagen     # tag providers
./gradlew genSources     # decompile MC after a version bump
```

## Stonecutter task scoping

The publication and several tasks exist only on the `:latest` subproject, not the root:

```bash
./gradlew :latest:publishToMavenLocal    # -> ~/.m2, dev.wroud.mc:worlds:<version>
./gradlew tasks --all | grep -i publish  # use this rather than guessing a task path
```

`:latest:publishMavenPublicationToGitHubPackagesRepository` needs `GITHUB_ACTOR`/`GITHUB_TOKEN` (or `gpr.user`/`gpr.key`).

## Dev-run hazards

- Client and server share `versions/latest/run/` — **never run both at once**.
- A bare `runServer` migrates `run/world` in place, irreversibly. Use a throwaway world: `./gradlew runServer --console=plain --args="--nogui --world world-<tag>"` (args are forwarded).

## Driving the dev server without a TTY

`runServer` reads stdin, so a FIFO feeds console commands after startup; results land in the log as `System chat:` lines.

```bash
mkfifo "$SCRATCH/mcin"; LOG="$SCRATCH/run.log"
( exec 3>"$SCRATCH/mcin"
  until grep -q 'Done (' "$LOG" 2>/dev/null; do sleep 1; done; sleep 2
  echo "worlds create test_world" >&3; sleep 8
  echo "execute in minecraft:test_world run time query time" >&3; sleep 2
  echo 'stop' >&3 ) &
./gradlew runServer --console=plain --args="--nogui --world world-tmp" < "$SCRATCH/mcin" > "$LOG" 2>&1
```

## Darwin specifics

- `sed -i` requires an explicit empty backup arg: `sed -i '' -e ...`. Prefer `python3` for in-place edits.
- BSD `find` has no `-printf`; use `-exec` or `sed`.
- `javap` comes from the system JDK and is the fastest way to confirm a mixin's descriptor/ordinal:
  `javap -p -c -cp <minecraft-common-*.jar> net.minecraft.some.Class | awk '/method/,/^$/'`
