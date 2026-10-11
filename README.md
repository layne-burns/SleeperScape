# SleeperScape
A personal RuneLite plugin that shows your Sleeper fantasy football matchups while you play: every play that moves the score, the live scores and win probability, a live red-zone alert, and your character reacting to the big moments. See `SPEC.md`.

## Running
Not on the Plugin Hub, so it runs in a developer-mode client:

```
./gradlew run        (Windows: gradlew.bat run)
```

Then in the plugin's config set **Sleeper username**, and turn on **Use ESPN data** for the red zone, game clock, play descriptions and win probability. The side panel (pink football) has the delay buttons, mute, and the league switches.

The plugin will join the combined AnkiScape jar (see `../ankiscape`), like Port Tasks.

## Tests
`./gradlew test` runs the core tests (`src/test/java/.../core`), which need no client.
