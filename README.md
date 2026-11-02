# Wepons

Paper 1.21.x plugin (Java 21). Converted from the customswords Skript.

## Build
    mvn clean package
Jar: `target/Wepons-1.0.0.jar` -> put in `plugins/`. Remove the Skript file and AltarSMP is not needed.

## Commands
- `/wepons`  - one GUI with every weapon, no holder names. Click to take one. (op by default: `wepons.use`)
- `/holder`  - every weapon with its holders, green = online, gray = offline. (everyone by default: `wepons.holder`)

Holders are saved in `plugins/Wepons/holders.yml`, so offline players still show.
