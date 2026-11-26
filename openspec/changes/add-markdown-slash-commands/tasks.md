## 1. Command Model and Transformation Foundation

- [x] 1.1 Add focused tests that define command-query ranges, replacement edits, caret placement, and line-prefix normalization for built-in Markdown transformations.
- [x] 1.2 Introduce editor-neutral command context, command descriptor, transformation result, and command provider types with stable identifiers and aliases.
- [x] 1.3 Implement the command registry with built-in-first ordering, deterministic duplicate-ID handling, availability filtering, provider-failure logging, and tests.
- [x] 1.4 Extract or adapt reusable pure Markdown transformations from `MarkdownFormatting.kt` while preserving existing Compose editor behavior with regression tests.

## 2. Built-in Markdown Commands

- [x] 2.1 Implement and test Heading 1-3, Bullet List, Numbered List, Task Item, and Block Quote commands with normalized current-line prefixes.
- [x] 2.2 Implement and test Bold, Italic, and Inline Code commands with paired delimiters and caret placement.
- [x] 2.3 Implement and test Link, language-neutral Code Block, and Horizontal Rule insertion commands.
- [x] 2.4 Assemble the built-in provider with stable names, descriptions, aliases, icons where available, and the complete required command set.

## 3. Markdown Context Recognition

- [x] 3.1 Add editor-fixture tests for valid slash-command tokens in Markdown at line starts, after whitespace, and after prose.
- [x] 3.2 Add editor-fixture tests proving completion is suppressed in non-Markdown files, read-only editors, URLs/URIs, paths, link destinations, escaped text, inline code, and fenced code blocks.
- [x] 3.3 Implement a shared context analyzer that finds the slash/query range and applies fast lexical guards plus authoritative Markdown PSI checks.

## 4. Native Completion Integration

- [x] 4.1 Implement the Markdown typed-handler delegate that schedules native completion only when the context analyzer accepts a newly typed slash.
- [x] 4.2 Implement the Markdown completion contributor that collects registry commands, filters by command names and aliases, and presents native lookup items.
- [x] 4.3 Implement lookup insertion that removes the slash query, calculates the selected command transformation, applies it as one write command, and restores the resulting caret or selection.
- [x] 4.4 Add editor-fixture tests for dropdown contents, partial-query filtering, acceptance, cancellation, no-match behavior, atomic undo, and non-writable document handling.

## 5. Extensibility and Plugin Registration

- [x] 5.1 Define and register the slash-command provider extension point with the smallest extension-facing API required by the design.
- [x] 5.2 Register the built-in provider, typed handler, and Markdown completion contributor in `plugin.xml`.
- [x] 5.3 Add a test provider fixture proving contributed commands appear, unavailable commands are omitted, duplicate IDs resolve deterministically, and provider failures do not hide healthy commands.

## 6. Documentation and Verification

- [x] 6.1 Update `README.md` with slash-command usage, built-in commands, supported Markdown scope, contextual suppression, and the distinction between provider extensibility and future user templates.
- [x] 6.2 Run targeted transformation and editor-fixture tests, then run `./gradlew test`.
- [x] 6.3 Run `./gradlew build` and `./gradlew verifyPlugin` to verify compilation, packaging, extension registration, and IntelliJ platform compatibility.
- [ ] 6.4 Manually validate the command dropdown, filtering, application, caret placement, undo, false-positive suppression, and coexistence with existing Markdown completion in `./gradlew runIde`.
