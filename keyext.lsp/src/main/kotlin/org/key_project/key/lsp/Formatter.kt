package org.key_project.key.lsp

import de.uka.ilkd.key.nparser.JavaKeYLexer.*
import de.uka.ilkd.key.nparser.ParsingFacade
import de.uka.ilkd.key.speclang.PositionedString
import io.github.wadoon.pp.*
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import org.key_project.util.parsing.Location
import java.nio.file.Path
import java.util.*

internal typealias TokenIter = PushbackIterator<Token>

class Formatter {
    fun format(code: String) =
        format(ParsingFacade.createLexer(CharStreams.fromString(code)).allTokens)

    fun format(code: String, location: Location) =
        format(ParsingFacade.createLexer(PositionedString(code, location)).allTokens)

    fun format(path: Path) =
        format(ParsingFacade.createLexer(path).allTokens)

    fun format(tokens: List<Token>, width: Int = 80, rfrac: Double = 1.0, indent: Int = 0): String {
        val doc = createDoc(tokens)
        return Engine.pretty(doc, width, rfrac, indent)
    }

    fun createDoc(code: List<Token>): Document {
        var doc: Document = empty
        val iter = PushbackIterator(code)
        while (iter.hasNext()) {
            val token = iter.next()
            doc += asDoc(token, iter)
        }
        return doc
    }

    private fun formatUntil(iter: TokenIter, pred: (Token) -> Boolean): Document {
        var doc: Document = empty
        while (iter.hasNext()) {
            val token = iter.next()
            if (pred(token)) {
                iter.back()
                break
            }
            doc += asDoc(token, iter)
        }
        return doc
    }

    private fun asDoc(iter: TokenIter): Document = if (iter.hasNext()) asDoc(iter.next(), iter) else empty

    private fun asDoc(token: Token, iter: TokenIter): Document = when (token.type) {
        MODALITY -> empty

        TERMLABEL -> keyword(token, iter)

        MODIFIABLE -> keyword(token, iter)

        GET_VARIANT,
        IS_LABELED,
        SAME_OBSERVER,
        DEPENDINGON,
        DISJOINTMODULONULL,
        GET_FREE_INVARIANT, GET_INVARIANT, HAS_INVARIANT, STORE_STMT_IN, STORE_TERM_IN, PROGRAMVARIABLES -> keyword(
            token,
            iter
        )

        DROP_EFFECTLESS_STORES,
        ENUM_CONST,
        FREELABELIN,
        FIELDTYPE,
        FINAL,
        ELEMSORT,
        HASLABEL,
        HASSUBFORMULAS,
        ISARRAY,
        ISARRAYLENGTH,
        ISCONSTANT,
        ISENUMTYPE,
        ISINDUCTVAR,
        ISLOCALVARIABLE,
        ISOBSERVER,
        METADISJOINT,
        ISTHISREFERENCE,
        DIFFERENTFIELDS,
        ISREFERENCE,
        ISREFERENCEARRAY,
        ISSTATICFIELD,
        ISMODELFIELD,
        ISINSTRICTFP,
        NEW_DEPENDING_ON,
        NEW_LOCAL_VARS,
        NEWLABEL,
        CONTAINS_ASSIGNMENT,
        NOTFREEIN,
        STATIC,
        STATICMETHODREFERENCE,
        MAXEXPANDMETHOD,
        CLASSPATH,
        BOOTCLASSPATH,
        NODEFAULTCLASSES,
        JAVASOURCE,
        CHOOSECONTRACT,
        CONTRACTS,
        INVARIANTS,
        IN_TYPE,
        IS_ABSTRACT_OR_INTERFACE,
        IS_FINAL,
        CONTAINERTYPE,
        GENERIC,
        PROXY,
        EXTENDS,
        ONEOF,
        ABSTRACT,
        ALIAS -> keyword(token, iter)

        VARIABLES, SCHEMAVARIABLES,
        SORTS -> sectionKw(token, iter)

        SCHEMAVAR,

        MODALOPERATOR,

        PROGRAM,
        FORMULA,
        TERM,
        UPDATE,
        VARIABLE,
        SKOLEMTERM,
        SKOLEMFORMULA,
        VARCOND,

        APPLY_UPDATE_ON_RIGID,

        DROP_EFFECTLESS_ELEMENTARIES,

        SIMPLIFY_IF_THEN_ELSE_UPDATE,

        HASSORT,

        DIFFERENT,

        ISSUBTYPE,

        EQUAL_UNIQUE,

        NEW,

        NEW_TYPE_OF,

        HAS_ELEMENTARY_SORT,

        DOC_COMMENT -> comment(token)

        ML_COMMENT -> comment(token)

        NOT_,

        SAME,

        STRICT,

        TYPEOF,

        INSTANTIATE_GENERIC,

        FORALL,

        EXISTS,

        SUBST,

        IF,

        IFEX,

        THEN,

        ELSE,

        INCLUDE,

        INCLUDELDTS,

        PROGRAMSOURCE,

        WITHOPTIONS,

        OPTIONSDECL,

        KEYSETTINGS,

        PROFILE,

        TRUE,

        FALSE,

        SAMEUPDATELEVEL,

        IGNOREUPDATELEVEL,

        INSEQUENTSTATE,

        ANTECEDENTPOLARITY,

        SUCCEDENTPOLARITY,

        CLOSEGOAL,

        HEURISTICSDECL,

        NONINTERACTIVE,

        DISPLAYNAME,

        HELPTEXT,

        REPLACEWITH,

        ADDRULES,

        ADDPROGVARS,

        HEURISTICS,

        FIND,

        ADD,

        ASSUMES,

        TRIGGER,

        AVOID,

        TRANSFORMERS,

        UNIQUE,

        FREE,

        FUNCTIONS, PREDICATES,
        DATATYPES, RULES, AXIOMS,
        PROBLEM, PROOFOBLIGATION, PROOF,
        PROOFSCRIPT -> sectionKw(token, iter)

        LEMMA -> hardline + keyword(token, iter)


        SEMI -> string(token) + (if (section) {
            iter.skip { it.type == WS }
            if (iter.peekClosing()) empty else hardline
        } else empty)

        COMMA -> commaSpace
        EMPTYBRACKETS -> string(token)
        PARALLEL, OR, AND, NOT, IMP, EQUALS, NOT_EQUALS, SEQARROW,
        EXP, TILDE, PERCENT, STAR, MINUS, PLUS, GREATER, GREATEREQUAL, LESS, LESSEQUAL, EQV, PRIMES,
        UTF_PRECEDES, UTF_IN, UTF_EMPTY, UTF_UNION, UTF_INTERSECT, UTF_SUBSET_EQ,
        UTF_SUBSEQ, UTF_SETMINUS, SLASH, COLON, DOUBLECOLON, ASSIGN, AT,
        DOT, DOTRANGE -> separator(token, break1, break1)

        STRING_LITERAL, CHAR_LITERAL, QUOTED_STRING_LITERAL -> string(token)
        WS -> handleWhitespace(token)

        OPENTYPEPARAMS -> groupUntil(token, CLOSETYPEPARAMS, iter)
        CLOSETYPEPARAMS -> string(token)
        LGUILLEMETS -> groupUntil(token, RGUILLEMETS, iter)
        RGUILLEMETS -> string(token)

        LPAREN -> groupUntil(token, RPAREN, iter)
        RPAREN -> string(token)

        LBRACE -> groupUntil(token, RBRACE, iter, section)

        RBRACE -> string(token)

        LBRACKET -> groupUntil(token, RBRACKET, iter)

        RBRACKET -> string(token)

        SL_COMMENT, BIN_LITERAL, HEX_LITERAL, INT_LITERAL,
        FLOAT_LITERAL, DOUBLE_LITERAL, REAL_LITERAL,
        MATCH_IDENT, IDENT -> string(token)

        else -> string(token)
    }

    private fun string(s: Token): Document = string(s.text)

    private fun separator(token: Token, before: Document = break1, after: Document = break1) =
        before + string(token) + after

    private fun handleWhitespace(token: Token): Document {
        val hl = token.text.count { it == '\n' }
        return if (hl != 0) {
            hardline + hardline
        } else {
            breakableSpace
        }
    }

    var section = false
    private fun groupUntil(
        token: Token,
        untilType: Int,
        iter: PushbackIterator<Token>,
        forceLine: Boolean = false
    ): Document {
        if (section) {
            val s = if (forceLine) {
                iter.skipWS()
                hardline
            } else if (iter.peekWS()) {
                iter.skipWS()
                breakableSpace
            } else {
                empty
            }
            iter.skipWS()

            val doc = string(token) + nest(4, s + formatUntil(iter) { it.type == untilType }) + s + asDoc(iter)
            section = false
            return doc
        } else {
            return string(token) + breakableSpace + nest(4, formatUntil(iter) { it.type == untilType })
        }
    }


    private fun comment(token: Token): Document = StringTokenizer(token.text, " \t\n\r", true).asIterator().asSequence()
        .map { it.toString() }
        .map { if (it.isBlank()) breakableSpace else string(it) }
        .reduce { acc, unit -> acc + unit }

    private fun keyword(token: Token, iter: PushbackIterator<Token>): Document = keyword(token, iter.peek())
    private fun keyword(token: Token, peek: Token): Document = string(token.text)
    private fun sectionKw(token: Token, iter: PushbackIterator<Token>): Document {
        section = true
        iter.skipWS()
        return clearline + string(token.text) + space
    }
}

private fun TokenIter.peekClosing(): Boolean = peek().type in arrayOf(RPAREN, RBRACE, RBRACE, RGUILLEMETS)
private fun TokenIter.peekWS(): Boolean = peek().type == WS
private fun TokenIter.skipWS() = skip { it.type == WS }

private fun String.weightedCount(value: (Int) -> Int): Int = chars().map { value(it) }.sum()

private operator fun Document.times(count: Int): Document =
    if (count <= 0) {
        empty
    } else {
        (0 until count).toList().concatMap { hardline }
    }

internal class PushbackIterator<T>(val tokens: List<T>) : Iterator<T> {
    var pos = 0

    override fun next() = tokens[pos++]
    fun back() = pos--

    override fun hasNext(): Boolean = pos < tokens.size

    fun peek() = tokens[pos]

    fun seek(a: Int) {
        pos += a
        require(0 <= pos)
    }

    fun mark(): Int = pos
    fun skip(pred: (T) -> Boolean) {
        while (hasNext()) {
            if (pred(peek())) {
                next()
            } else {
                break
            }
        }
    }
}

val clearline: Document = hardline
