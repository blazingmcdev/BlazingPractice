# FFA mode-selection update

This source update changes the FFA entry flow:

1. Right-click the FFA hotbar item, or choose FFA from the lobby mode menu.
2. A `Choose an FFA Mode` inventory opens.
3. The menu lists enabled ladder definitions (for example Sword, Crystal, Axe, Sumo).
4. Clicking one enters the existing FFA flow and records that ladder ID for the active session.
5. `/ffa join` opens the picker. `/ffa join sword` enters the selected mode directly.
6. `/ffa leave` restores the saved pre-FFA inventory using the existing snapshot logic.

## Important limitation

This change implements the mode picker and tracks the selected ladder. It does **not** yet load ladder-specific kits or implement special mode mechanics. The bundled ladder YAML files are still definitions/catalog entries, not proof that all modes are playable. The selected ladder is not yet used to alter the player's inventory.

## Build status

This is an edited source ZIP, not a compiled plugin JAR. The current build environment has Java 21 and does not have Maven installed, while this project targets Java 25 and Paper 1.21.11. Compilation and live Paper testing have therefore not been completed here.
