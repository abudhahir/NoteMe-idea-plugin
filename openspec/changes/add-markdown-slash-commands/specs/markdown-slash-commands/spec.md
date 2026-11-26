## Purpose

Provide discoverable, context-aware Markdown editing commands at the caret while allowing future command providers to participate in the same native editor experience.

## ADDED Requirements

### Requirement: Slash command completion is available in Markdown editors
The system SHALL offer slash-command completion in every editable Markdown file, regardless of whether the file is managed by NoteMe.

#### Scenario: Slash starts a command query in Markdown prose
- **WHEN** the user types `/` at a location in editable Markdown prose where a command token can begin
- **THEN** the system opens a command dropdown at the caret

#### Scenario: Non-Markdown editor is active
- **WHEN** the user types `/` in a file that is not recognized as Markdown
- **THEN** the system does not offer NoteMe slash commands

#### Scenario: Editor is read-only
- **WHEN** the user types or invokes completion in a read-only Markdown editor
- **THEN** the system does not offer commands that would modify the document

### Requirement: Slash command triggering is context-aware
The system SHALL distinguish slash-command queries from Markdown content in which `/` has its ordinary textual meaning.

#### Scenario: Slash appears in ordinary prose
- **WHEN** `/` begins a command-like token in Markdown prose, including after non-whitespace prose
- **THEN** the system may open and filter the slash-command dropdown

#### Scenario: Slash appears in a URL or path
- **WHEN** the user types `/` as part of a URL, URI, or file-system-like path
- **THEN** the system does not open the slash-command dropdown

#### Scenario: Slash appears in a Markdown link destination
- **WHEN** the user types `/` inside a Markdown link destination
- **THEN** the system does not open the slash-command dropdown

#### Scenario: Slash appears in code content
- **WHEN** the user types `/` inside inline code or a fenced code block
- **THEN** the system does not open the slash-command dropdown

#### Scenario: Slash is escaped
- **WHEN** the user types an escaped slash that is intended as literal content
- **THEN** the system does not open the slash-command dropdown

### Requirement: Commands support native discovery and filtering
The system SHALL present commands using the IDE's standard completion interaction and SHALL filter them using the text typed after the trigger slash.

#### Scenario: Initial command list is shown
- **WHEN** a valid slash-command query contains no filter text
- **THEN** the dropdown shows all commands available in the current context with a display name and short description

#### Scenario: User types a partial command
- **WHEN** the user types characters after the trigger slash
- **THEN** the dropdown narrows to commands whose name or aliases match the query

#### Scenario: User accepts a command
- **WHEN** the user accepts a highlighted command using Enter, Tab, or pointer selection
- **THEN** the system removes the slash query and applies the selected command

#### Scenario: User cancels completion
- **WHEN** the user presses Escape while the dropdown is open
- **THEN** the dropdown closes without applying a command or removing typed text

#### Scenario: Query has no matches
- **WHEN** no available command matches the typed query
- **THEN** the system leaves the typed Markdown text unchanged and does not apply a command

### Requirement: Basic Markdown formatting commands are provided
The system SHALL provide built-in commands for Heading 1, Heading 2, Heading 3, Bold, Italic, Bullet List, Numbered List, Task Item, Block Quote, Inline Code, Code Block, Link, and Horizontal Rule.

#### Scenario: Line-oriented command is applied
- **WHEN** the user accepts a heading, list, task-item, or block-quote command
- **THEN** the system applies the corresponding Markdown prefix to the current line and places the caret after the inserted prefix

#### Scenario: Paired inline formatting is inserted
- **WHEN** the user accepts Bold, Italic, or Inline Code without an active selection
- **THEN** the system inserts the corresponding paired Markdown delimiters and places the caret between them

#### Scenario: Link command is applied
- **WHEN** the user accepts the Link command
- **THEN** the system inserts a Markdown link template and selects or positions the caret at the next field the user must fill

#### Scenario: Code block command is applied
- **WHEN** the user accepts the Code Block command
- **THEN** the system inserts an empty fenced code block without assuming a programming language and places the caret inside it

#### Scenario: Horizontal rule command is applied
- **WHEN** the user accepts the Horizontal Rule command
- **THEN** the system inserts a valid Markdown horizontal rule as a block-level element

### Requirement: Command application integrates with editor history
The system SHALL apply each accepted slash command as one atomic, undoable editor operation.

#### Scenario: User undoes an applied command
- **WHEN** the user invokes Undo immediately after accepting a slash command
- **THEN** the document, caret, and command query return to their state before command acceptance

#### Scenario: Command cannot modify the document
- **WHEN** the document becomes unavailable or non-writable before a command is applied
- **THEN** the system leaves the document unchanged and does not report the command as successfully applied

### Requirement: Additional command providers can extend the menu
The system SHALL expose a provider contract through which additional commands can participate in the same discovery, filtering, availability, and application behavior as built-in commands.

#### Scenario: Provider contributes an available command
- **WHEN** a registered provider returns a valid command that is available in the current Markdown context
- **THEN** the command appears in the slash-command dropdown and can be applied

#### Scenario: Provider command is unavailable in the current context
- **WHEN** a registered command reports that it is unavailable at the current caret context
- **THEN** the system omits that command from the dropdown

#### Scenario: Provider contributes a duplicate command identifier
- **WHEN** multiple providers contribute commands with the same stable identifier
- **THEN** the system resolves the collision deterministically and does not show ambiguous duplicate entries

#### Scenario: Provider fails while commands are collected
- **WHEN** one provider fails to return commands
- **THEN** commands from other providers remain available and the failing provider does not break Markdown editing
