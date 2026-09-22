# Global Agent Instructions: Structural Navigation & Code Intelligence

To maximize token efficiency and code comprehension speed, all agents MUST prioritize AST and structural code navigation over reading entire files.

## 1. Structural Navigation First (MANDATORY)
- **NEVER** read an entire file blindly from top to bottom when investigating or editing code.
- **NEVER** guess or repeatedly scan multiple files using manual reads.
- Always use structural tools to locate the exact symbol, function, or class first, then do surgical line-range reads (`view_file` with `StartLine`/`EndLine`).

## 2. Tool Hierarchy & Priority

### Priority 1: `ast-grep` (`sg`) — AST & Syntax-Aware Queries
Use `ast-grep` to search codebases by Abstract Syntax Tree (AST) structure rather than plain text regex:
- **Syntax**: `ast-grep run -p '<AST_PATTERN>' -l <LANGUAGE> [PATH]`
- **Examples**:
  - Find all functions/methods: `ast-grep run -p 'fun $NAME($$$ARGS)' -l kotlin <dir>`
  - Find classes & inheritance: `ast-grep run -p 'class $NAME : $SUPER' -l kotlin <dir>`
  - Find function calls/usages: `ast-grep run -p '$OBJ.$METHOD($$$ARGS)' -l <lang> <dir>`
  - JSON output for programmatic parsing: `ast-grep run -p '<PATTERN>' -l <lang> --json=compact`

### Priority 2: Universal Ctags (`ctags`) — Instant Symbol Index & Lookup
Use `ctags` to build a fast map of all symbols (functions, classes, interfaces, fields) across the repository:
- **Index Generation**: `ctags -R .` (creates standard `tags` file)
- **JSON Stream Output**: `ctags -R --output-format=json <path>` to extract symbol hierarchies and line numbers programmatically without loading source files.
- **Find Definition**: Query the `tags` file or use `readtags` to jump directly to any symbol declaration.

### Priority 3: `repomix` — Codebase Architecture & Context Extraction
Use `repomix` when an overview of project structure or module summary is needed:
- **Tree & Token Distribution**: `repomix --token-count-tree` to see file hierarchy and identify heavy components.
- **Compressed Skeletons**: `repomix --compress --include "<target-dir>/**"` to extract only classes, function signatures, and interfaces without boilerplate.
- **Selective Packing**: Pack specific packages/modules for comprehensive multi-file context without reading raw files one by one.

### Priority 4: Ripgrep / Grep / Find
- Use `grep_search` or `find_by_name` for specific identifiers, exact string literals, or file names.

### Priority 5: Surgical Reading
- Only after identifying the exact file and line numbers using `ast-grep`, `ctags`, or `grep`, read ONLY the relevant lines (typically 30-80 lines around the target).
