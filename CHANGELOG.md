# Changelog

## Unreleased

### Added

### Changed

### Deprecated

### Removed

### Fixed

### Security

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
