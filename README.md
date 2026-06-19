# Shady CLI

Shady ist ein interaktiver Desktop-Terminal-Emulator für macOS und Zsh. Jeder Tab
besitzt eine langlebige PTY-Shell: Eingaben, `sudo`, Ctrl+C, `clear`,
Autovervollständigung und Programme wie `vim`, `ssh`, `top` oder REPLs verhalten
sich deshalb wie in einem normalen Terminal.

## Voraussetzungen

- macOS auf Apple Silicon oder Intel
- Java 21 oder neuer
- Zsh unter `/bin/zsh`

## Bauen und starten

```bash
./gradlew installDist

mkdir -p "$HOME/.local/bin"
ln -sf "$(pwd)/build/install/shady/bin/shady" "$HOME/.local/bin/shady"

Falls ~/.local/bin noch nicht im PATH ist, kann es zur Zsh-Konfiguration hinzugefügt werden:

echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.zshrc"
source "$HOME/.zshrc"
```

`shady start` öffnet den Emulator. Direkte Aufrufe bleiben im aktuellen Terminal:

```bash
shady help
shady git status
shady alias list
```

Für eine systemweite Installation kann alternativ /usr/local/bin verwendet werden:

```bash
./gradlew installDist
sudo ln -sf "$(pwd)/build/install/shady/bin/shady" /usr/local/bin/shady
```

Im Emulator darf das Präfix bei normalen Shell-Befehlen entfallen. `git status`
und `shady git status` führen denselben Shell-Befehl aus; Shady beobachtet beide
für History und Alias-Vorschläge. `shady start` wird innerhalb einer laufenden
Session abgewiesen.

## Terminalfunktionen

- Eine unabhängige PTY-/Zsh-Session pro Tab
- Native stdin-Ausgabe und interaktive Passwort-Prompts
- Ctrl+C zum Unterbrechen und Ctrl+R für die integrierte Fuzzy-Suche
- Native Zsh-Completion für Befehle, `cd`, Dateien und Pfade
- Completion für Shady-Befehle und Unterbefehle
- Persistente Arbeitsordner, Umgebungsvariablen, Funktionen und Shell-Zustände
- Wiederherstellung der zuletzt geöffneten Tabs
- ANSI-/Truecolor-Ausgabe und optionale Shady-Farbregeln

Passwörter werden von Shady weder gelesen noch gespeichert. Zsh beziehungsweise
`sudo` steuert selbst, ob eingegebene Zeichen angezeigt werden; Passwörter bleiben
wie in jedem Terminal unsichtbar.

## Konfiguration

Die globale Konfiguration liegt unter `~/.config/shady/config.json`. Eine
Projektkonfiguration kann unter `.shady/config.json` liegen; die bestehende
`shady.config.json` wird aus Kompatibilitätsgründen ebenfalls gelesen.

```bash
shady config show
shady config set security.alwaysRequestSudo false
shady config set terminal.coloredOutput true
shady config set terminal.usernameOverrideEnabled true
shady config set terminal.username neo
shady config set features.stylesCommandEnabled true
shady config set features.fuzzySearchEnabled true
```

Standardmäßig ist farbige Ausgabe aktiv. Sudo-Vorabprüfung, Username-Override
und `shady styles` sind deaktiviert. Wenn `security.alwaysRequestSudo` aktiv ist,
führt Shady beim ersten Prompt eines App-Starts `sudo -k; sudo -v` aus.

`shady styles` öffnet nach Aktivierung `~/.config/shady/styles`.

## Farbregeln

```bash
shady cc list
shady cc command ls magenta
shady cc filetype kt cyan
shady cc remove command ls
shady cc reset
```

Command-Farben gelten für unformatierte Ausgabe. Dateiendungsregeln überschreiben
die Command-Farbe. Native ANSI-Farben und Alternate-Screen-Programme bleiben
unverändert. Regeln werden beim Start jedes neuen Befehls neu eingelesen.

## Aliases und Prehooks

```bash
shady alias add lint -- npm run lint
shady alias list
shady alias suggest
shady alias accept 1 lint

shady prehook add format -- path/to/script.sh
shady prehook list
shady prehook run format
```

Aliases liegen projektweit in `aliases/shady-aliases.json`. Erfolgreiche Befehle
werden lokal beobachtet; häufig wiederholte Befehle können als Alias vorgeschlagen
werden. Prehooks akzeptieren ausschließlich Bash-Skripte mit passender Shebang.

## Installation und Updates

Releases bestehen aus plattformspezifischen Archiven und SHA-256-Prüfsummen. Bis
eine feste öffentliche Repository-URL beschlossen ist, wird sie explizit gesetzt:

```bash
curl -fsSL https://raw.githubusercontent.com/OWNER/REPO/main/scripts/install.sh \
  | SHADY_REPO_URL=https://github.com/OWNER/REPO bash
```

Der Installer prüft die Prüfsumme, installiert versioniert unter
`~/.local/share/shady/releases` und aktualisiert atomar den Link
`~/.local/bin/shady`.

```bash
shady config set updates.repositoryUrl https://github.com/OWNER/REPO
shady update
```

Beim Start prüft Shady höchstens einmal pro konfiguriertem Intervall auf Updates.
Der Check bleibt deaktiviert, solange weder `updates.repositoryUrl` noch
`SHADY_REPO_URL` gesetzt ist.

Für die Entwicklung:

```bash
scripts/install-dev.sh
```

## IntelliJ IDEA

Das Modul `intellij-plugin` stellt die Aktion **Open Shady Terminal** bereit. Sie
sucht `~/.local/bin/shady` beziehungsweise `PATH` und startet `shady --ide-shell`
als normalen Tab im integrierten IDEA-Terminal. Es wird weder ein separates
Compose-Fenster geöffnet noch die globale IDEA-Standardshell verändert.

```bash
./gradlew -p intellij-plugin test buildPlugin
```

## Release

### macOS DMG

```bash
./gradlew buildDMG
```

Erzeugt eine unsignierte DMG unter `build/compose/binaries/main/dmg/`. Die App
startet beim Doppelklick direkt den Emulator (`shady start`). Die DMG enthält
eine gebündelte Java-Runtime und benötigt kein separat installiertes JDK.

Signierung und Notarisierung sind nicht enthalten und müssen separat konfiguriert
werden.

### GitHub Release

Ein Tag `v*` startet `.github/workflows/release.yml`. Der Workflow baut und testet
macOS-x64 und macOS-aarch64, erzeugt Prüfsummen, baut das IntelliJ-Plugin und legt
eine GitHub-Release an.

Technische Details stehen in [Architecture.md](Architecture.md).
