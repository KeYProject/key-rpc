package org.key_project.key.lsp.services

import de.uka.ilkd.key.nparser.JavaKeYParserBaseVisitor
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Range

class CodeActionVisitor(val range: Range) : JavaKeYParserBaseVisitor<List<Command>?>() {}
