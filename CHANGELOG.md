# Changelog

## Unreleased

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

## 1.7.0 - 2025-10-14

### Added

- Creating worlds from presets (you can create a flat world from a normal world and vice versa now)
- By default `/worlds create name` will create a normal overworld

## 1.6.13 - 2025-10-13

### Changed

- Better stability in unexpected cases

## 1.6.12 - 2025-10-13

### Fixed

- Null pointer exception when accessing server levels before overworld initialization [#3](https://github.com/Wroud/mc-worlds/issues/3)

## 1.6.11 - 2025-10-11

### Fixed

- Include datagen in jar

## 1.6.10 - 2025-10-11

### Changed

- Consistent logging

## 1.6.9 - 2025-10-10

### Fixed

- Watchdog killing server if level initialization is too long

## 1.6.8 - 2025-10-09

### Changed

- Update fabric version

## 1.6.7 - 2025-10-08

### Fixed

- Prevent world unloading during save

## 1.6.6 - 2025-10-07

### Added

- Support for 1.21.10

## 1.6.5 - 2025-10-03

### Added

- Support for 1.21.10-rc1

## 1.6.4 - 2025-10-03

### Fixed

- Worlds unloading in sprinting mode
- Concurrency exception

## 1.6.3 - 2025-10-02

### Fixed

- Bee, End Portal, Falling block, Frosted Ice block fixes

## 1.6.2 - 2025-10-02

### Fixed

- Level migration from 1.21.8

## 1.6.1 - 2025-09-30

### Added

- Support for 1.21.9

## 1.6.0 - 2025-09-30

### Added

- Non-blocking spawn location detection for overworld-like worlds to prevent server thread blocking

## 1.5.0 - 2025-09-29

### Added

- Prevent players and entities from teleporting to unloading worlds
- Automatically unload worlds after 1 minute of being empty

### Fixed

- World deletion [#1](https://github.com/Wroud/mc-worlds/issues/1)

## 1.4.3 - 2025-09-26

### Added

- List Quilt as supported loader (not tested)
- Support for 1.21.9-rc1

## 1.4.2 - 2025-09-24

### Added

- Support for 1.21.9-pre4

## 1.4.1 - 2025-09-23

### Added

- Support for 1.21.9-pre3

## 1.4.0 - 2025-09-21

### Added

- Support for 1.21.9-pre2

## 1.3.1 - 2025-09-18

### Fixed

- Display unloaded worlds in the teleport comand list

## 1.3.0 - 2025-09-17

### Added

- Level provider API
- Worlds lazy-loaded on demand by default
- Dimensions type tags for custom dimension types

### Changed

- Worlds activated in tick instead of request

## 1.2.0 - 2025-09-05

### Fixed

- Custom spawners in overworld like worlds (cats, phantoms, patrol, wandering trader)
- Server crash during worlds deletion
- Localization

## 1.1.0 - 2025-09-04

### Added

- Support for Nether and End dimensions creation
- Proper Overworld initialization and spawn setting

### Fixed

- End world teleportation

## 1.0.1 - 2025-09-04

### Added

- Proper dimension deletion

## 1.0.0 - 2025-09-03

### Added

- Initial plugin version with simple overworld manadgement
