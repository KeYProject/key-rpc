# KeY Language Server Test Workspace

This directory contains sample `.key` files used for testing the KeY Language Server.

## Files

### simple.key
A basic KeY file with minimal declarations including:
- Sort declarations
- Function declarations  
- Simple program
- Basic axioms

### complex.key
A more comprehensive KeY file demonstrating:
- Multiple sort declarations
- Various function types (arithmetic, list operations, tree operations)
- Complete Java-like program
- Multiple axiom categories
- Rule declarations

### syntax_error.key
Intentionally malformed KeY file for testing error handling:
- Missing closing braces
- Incomplete function declarations
- Invalid syntax

### program.key
KeY file with Java program verification example:
- Counter class with methods
- Main class with example usage
- Associated functions and axioms

## Usage

These files are automatically copied to the test temporary directory during test execution.
Tests use JUnit's `@TempDir` annotation to create isolated test environments.

## Creating Additional Test Files

When adding new test cases, create corresponding `.key` files here following these guidelines:

1. **Keep files focused** - Each file should test a specific feature or scenario
2. **Include comments** - Document what the file is testing
3. **Use meaningful names** - File names should indicate their purpose
4. **Validate syntax** - Ensure valid `.key` files parse correctly in KeY

## File Structure Example

```key
// Comment describing the file's purpose
\sorts {
    // Sort declarations
}

\functions {
    // Function declarations
}

\program {
    // Program code (optional)
}

\axioms {
    // Axiom declarations
}

\rules {
    // Rule declarations (optional)
}
```
