# Griefercheck

**Griefercheck** is a server-side moderation and investigation mod for **Minecraft 1.18.2** on **Forge**.

It logs player actions such as block changes, container interaction, and item transfers into structured `.jsonl` files and provides in-game lookup commands for admins and moderators.

The mod is designed to make grief investigation faster and more practical directly in-game, without relying entirely on external log parsers.

---

## Main Features

### Block and interaction logging
- Logs **block placement**
- Logs **block breaking**
- Logs **block interaction**
- Logs **container opens**
- Logs **container item transfers**

### In-game investigation tools
- Inspect the exact block at your position
- Scan a nearby area
- Scan the current chunk
- Scan a custom radius
- Browse paged results directly in chat
- Inspect container transfer history without opening the inventory

### Extra compatibility
- Optional **FTB Chunks** integration for claim-wide scans
- Optional **Corpse** integration for corpse interaction / despawn logging

### Time display improvements
- Relative timestamps in chat

---

## Supported Environment

- **Minecraft:** 1.18.2
- **Mod Loader:** Forge
- **Primary Use Case:** Server moderation / admin review

---

## Server / Client

Griefercheck is intended to be used primarily as a **server-side utility**.

In most setups, only the server needs the mod installed.

---

## Commands

All commands require permission level **2**.

### Query commands
- `/gc block`  
  Show logged events for the exact block at your current position.

- `/gc 3x3`  
  Show logged events in the nearby area around your current position.

- `/gc chunk`  
  Show logged events for the current chunk.

- `/gc radius <blocks>`  
  Show logged events in a configurable radius around your position.

- `/gc page <n>`  
  Open another page of the current query result.

### Investigation mode
- `/gc inspect`  
  Toggle inspect mode for container history lookup.

### Claim integration
- `/gc claim`  
  Show logged events inside the current **FTB Chunks** team claim.  
  This command is only available when **FTB Chunks** is installed.

---

## Inspect Mode

Inspect mode is intended for quick container investigations.

### How it works
1. Run `/gc inspect`
2. Right-click a supported container
3. Instead of opening the inventory, Griefercheck shows the logged **container transfer history**
4. Hover the relative timestamp to see the **exact date and time**

This is especially useful for checking who inserted or removed items from chests and other storage blocks.

---

## Logged Event Types

Depending on config, Griefercheck can log entries such as:

- `block_place`
- `block_break`
- `block_interact`
- `container_open`
- `container_transfer`
- `corpse_open`
- `corpse_emptied`
- `corpse_despawn`

---

## Log File Format

Logs are written as JSON lines into the `griefercheck` folder:

```text
griefercheck/events-000001.jsonl
griefercheck/events-000002.jsonl
griefercheck/events-000003.jsonl
...