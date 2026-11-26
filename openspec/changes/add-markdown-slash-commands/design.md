## Context

See `proposal.md` for motivation and `specs/markdown-slash-commands/spec.md` for observable behavior.

NoteMe depends on the bundled IntelliJ Markdown plugin and currently exposes editor actions but no code-completion integrations. Formatting logic exists in `MarkdownFormatting.kt`, but it is coupled to Compose's `String` and `TextRange` model while normal IntelliJ Markdown files are edited through `Editor`, `Document`, PSI, and write-command APIs.

The feature spans typed-character handling, completion contribution, context analysis, command discovery, document transformation, extension registration, and editor tests. The design must avoid interfering with existing Markdown completion and with ordinary slashes in URLs, paths, links, or code.

## Goals / Non-Goals

**Goals:**

- Use native IntelliJ completion behavior instead of maintaining a custom popup.
- Keep command definitions independent from completion rendering and document mechanics.
- Share pure Markdown transformations between the Compose rich editor and IntelliJ editor where behavior matches.
- Make command application atomic, undoable, caret-aware, and testable.
- Provide a provider boundary that can later host user-defined template commands without redesigning completion.

**Non-Goals:**

- Adding a settings UI or persistence format for user-defined commands in this change.
- Executing arbitrary scripts or shell commands from slash commands.
- Replacing standard Markdown completion, live templates, intentions, or keyboard shortcuts.
- Supporting slash commands in the Compose `RichTextEditor`; its existing toolbar remains unchanged.
- Changing note storage, index parsing, or synchronization behavior.

## Decisions

### Use native completion plus typed-character auto-popup

Register a Markdown completion contributor to supply lookup elements and a typed-handler delegate to schedule completion when `/` can begin a command query. The contributor remains responsible for revalidating context because completion can also be invoked manually and editor state can change between typing and collection.

Each lookup element carries the command's stable identifier, presentation, aliases, and insert handler. The insert handler determines the trigger/query range, removes it, and dispatches the selected command.

**Alternatives considered:**

- A custom `JBPopup` would provide complete visual control but would duplicate keyboard navigation, filtering, accessibility, positioning, and lifecycle behavior already supplied by completion.
- Live templates work well for known abbreviations but do not provide the desired discoverable, provider-backed menu.
- Replacing the editor's slash action globally would be invasive and difficult to compose with other plugins.

### Model commands separately from providers and editor adapters

Introduce an immutable command descriptor with:

- stable ID;
- display name, description, icon, and aliases;
- availability predicate based on a read-only Markdown command context;
- execution behavior expressed as a transformation or editor operation.

A registry collects commands from ordered providers, removes duplicate IDs deterministically, evaluates availability, and returns presentation-ready commands. Built-ins are one provider. An IntelliJ extension point supplies additional providers. A future user-template service can become another provider without changing the completion contributor.

Provider order and collision policy will prefer the built-in provider, then extension registration order. Duplicate IDs are logged and ignored after the first accepted command.

**Alternatives considered:**

- Keeping a list inside the completion contributor is simpler initially but couples command growth to UI code and blocks future command sources.
- Exposing lookup elements directly from providers leaks IntelliJ completion mechanics into the command API and makes non-completion use difficult.

### Separate pure transformations from IntelliJ document execution

Represent built-in formatting as transformations over a small editor-neutral input containing text, command range, caret, optional selection, and line boundaries. A transformation returns replacement edits plus the resulting caret or selection.

The IntelliJ adapter applies the result through one write command. Existing `MarkdownFormatting` behavior can be migrated to shared transformation helpers where semantics match, while Compose-specific `TextRange` handling remains in its adapter.

Line-oriented commands normalize an existing equivalent prefix rather than blindly stacking repeated markers. Inline commands insert paired delimiters at the command location. Code block insertion uses an empty language slot rather than hard-coding `kotlin`.

**Alternatives considered:**

- Mutating `Document` directly in every command would duplicate range and caret logic and make unit testing harder.
- Calling current Compose utilities from completion handlers would couple the platform editor to Compose types and whole-document string replacement.

### Treat "anywhere" as command-token availability, not every slash keystroke

The typed handler performs a fast lexical guard before scheduling completion. The contributor then performs authoritative PSI/text checks. Completion is suppressed in inline code, fenced code, Markdown link destinations, escaped content, recognized URL/URI sequences, and path-like tokens.

The query range starts at the slash and continues through command-name characters to the caret. Prose before the slash does not have to be whitespace, allowing command usage anywhere that the slash begins a distinct command-like token. Boundaries that form URLs or path segments are rejected.

This two-stage approach keeps typing responsive while centralizing correctness in a context analyzer shared by triggering and tests.

**Alternatives considered:**

- Restricting commands to line starts would avoid most false positives but conflicts with the selected product behavior.
- Triggering on every slash and filtering later would create disruptive popups in common Markdown content.

### Keep the first built-in set deterministic

The initial provider supplies Heading 1-3, Bold, Italic, Bullet List, Numbered List, Task Item, Block Quote, Inline Code, Code Block, Link, and Horizontal Rule. Names and aliases are stable inputs to filtering; locale-sensitive presentation can be added later without changing stable IDs.

Selection-based wrapping is not introduced through slash typing because typing `/` normally replaces an active editor selection. Existing keyboard and toolbar formatting remain the appropriate selection-first interactions. Provider APIs may still receive selection information for commands invoked through future entry points.

### Isolate provider failures

Registry collection catches failures at each provider boundary, reports them through the repository-standard IntelliJ logger, and continues with remaining providers. Command execution errors leave the document unchanged by calculating transformations before entering the write operation wherever possible.

The API does not silently convert failures into successful no-ops. Invalid or duplicate contributions are logged with provider and command identifiers.

## Risks / Trade-offs

- **[Markdown PSI boundaries differ across IDE/Markdown plugin versions]** → Keep lexical fallback checks, use the bundled Markdown dependency in tests, and target the repository's declared 2025.2 platform.
- **[Aggressive anywhere-triggering produces noisy popups]** → Require a command-token boundary and suppress known URL, path, link, escape, and code contexts before scheduling.
- **[Native completion may merge unrelated Markdown suggestions]** → Use a slash-prefixed result set and contributor patterns that activate only for a validated slash query; do not stop unrelated contributors outside that context.
- **[Public provider API becomes a compatibility commitment]** → Keep the contract small, immutable, and editor-neutral; mark only the provider interface and command descriptor as extension-facing.
- **[Provider code can be slow or faulty]** → Require synchronous lightweight command enumeration, isolate failures, and avoid index-dependent work so the contributor remains available during indexing.
- **[Shared transformation extraction changes Compose behavior]** → Preserve existing Compose-visible behavior with focused transformation tests before switching adapters.

## Migration Plan

1. Add the command model, registry, built-in transformations, and tests without registering editor extensions.
2. Register the completion contributor, typed handler, and provider extension point in `plugin.xml`.
3. Validate native Markdown editing, completion coexistence, command application, undo, and false-positive suppression in the sandbox IDE.
4. If the feature must be rolled back, remove the editor extension registrations; the registry and pure transformations are inert without those entry points.
