## Why

Markdown authors currently have to remember syntax or leave the editor to use formatting controls. A native slash-command menu makes common formatting discoverable at the caret while establishing a stable command model that can accept additional built-in, plugin-provided, and user-defined commands over time.

## What Changes

- Show an IntelliJ-native completion dropdown when `/` starts a slash-command query in any editable Markdown file.
- Filter commands as the user types and support standard completion navigation, acceptance, and cancellation.
- Apply basic Markdown formatting and insertion commands through undoable IntelliJ document edits.
- Suppress slash-command completion in contexts where `/` is ordinary content, including URLs, paths, link destinations, escaped text, and code content.
- Introduce a command registry and provider contract so additional command sources can be added without changing completion UI or dispatch logic.
- Keep user-configured command templates out of the initial settings UI while preserving a command model that can support them in a later change.

## Capabilities

### New Capabilities

- `markdown-slash-commands`: Contextual discovery, filtering, and application of extensible Markdown editor commands.

### Modified Capabilities

None.

## Impact

- Adds IntelliJ editor integrations for Markdown completion, typed-character auto-popup behavior, lookup insertion, and write-command execution.
- Adds a Markdown slash-command domain model, registry, built-in provider, and extension point registration.
- Reuses or extracts pure Markdown transformations currently represented in `MarkdownFormatting.kt` without coupling IntelliJ editors to Compose state types.
- Adds editor-fixture and transformation tests for triggering, filtering, context suppression, command application, caret placement, and undo behavior.
- Does not change note storage, `index.md`, semantic search, or tool-window behavior.
