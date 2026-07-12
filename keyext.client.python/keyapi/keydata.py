from __future__ import annotations
import enum
import abc
import typing
from abc import abstractmethod, ABCMeta


class ExampleDesc:
    """
    This class represents a built-in example.
     TODO (weigl,rpc) Also deliver the contents of the example (files)?


    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    name: str
    """
        the name of the example, also used as an identifer
        """

    description: str
    """
        a description of the example
        """

    def __init__(self, name: str, description: str):
        self.name = name
        self.description = description


class ProofId:

    env: EnvironmentId

    proofId: str

    def __init__(self, env: EnvironmentId, proofId: str):
        self.env = env
        self.proofId = proofId


class EnvironmentId:
    """



    @author	Alexander Weigl
    @version	1 (28.10.23)
    """

    envId: str

    def __init__(self, envId: str):
        self.envId = envId


class ProofMacroDesc:
    """



    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    name: str

    category: str

    description: str

    scriptCommandName: str

    def __init__(
        self, name: str, category: str, description: str, scriptCommandName: str
    ):
        self.name = name
        self.category = category
        self.description = description
        self.scriptCommandName = scriptCommandName


class ProofScriptCommandDesc:
    """



    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    macroId: str

    documentation: str

    def __init__(self, macroId: str, documentation: str):
        self.macroId = macroId
        self.documentation = documentation


class SetTraceParams:
    """
    Values
    """

    value: TraceValue
    """
        The new value that should be assigned to the trace setting.
        """

    def __init__(self, value: TraceValue):
        self.value = value


class TraceValue(enum.Enum):
    """ """

    Off = None

    """
        An error message.
        """
    Error = None

    """
        A warning message.
        """
    Warning = None

    """
        An information message.
        """
    Info = None

    """
        A log message.
        """
    Log = None

    """
        A debug message.
        """
    Debug = None


class NodeDesc:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    nodeid: NodeId

    branchLabel: str

    scriptRuleApplication: bool

    children: typing.List[NodeDesc]

    description: str

    def __init__(
        self,
        nodeid: NodeId,
        branchLabel: str,
        scriptRuleApplication: bool,
        children: typing.List[NodeDesc],
        description: str,
    ):
        self.nodeid = nodeid
        self.branchLabel = branchLabel
        self.scriptRuleApplication = scriptRuleApplication
        self.children = children
        self.description = description


class NodeId:
    """



    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    proofId: ProofId

    nodeId: str

    def __init__(self, proofId: ProofId, nodeId: str):
        self.proofId = proofId
        self.nodeId = nodeId


class StrategyOptions:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    method: str

    dep: str

    query: str

    nonLinArith: str

    stopMode: str

    maxSteps: int

    def __init__(
        self,
        method: str,
        dep: str,
        query: str,
        nonLinArith: str,
        stopMode: str,
        maxSteps: int,
    ):
        self.method = method
        self.dep = dep
        self.query = query
        self.nonLinArith = nonLinArith
        self.stopMode = stopMode
        self.maxSteps = maxSteps


class MacroStatistic:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    proofId: ProofId

    macroId: str

    appliedRules: int

    closedGoals: int

    def __init__(
        self, proofId: ProofId, macroId: str, appliedRules: int, closedGoals: int
    ):
        self.proofId = proofId
        self.macroId = macroId
        self.appliedRules = appliedRules
        self.closedGoals = closedGoals


class ProofStatus:
    """



    @author	teuber
    """

    id: ProofId
    """
        handle of the proof
        """

    openGoals: int
    """
        open goals
        """

    closedGoals: int
    """
        closed goals
        """

    def __init__(self, id: ProofId, openGoals: int, closedGoals: int):
        self.id = id
        self.openGoals = openGoals
        self.closedGoals = closedGoals


class TreeNodeDesc:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    id: NodeId

    name: str

    def __init__(self, id: NodeId, name: str):
        self.id = id
        self.name = name


class TreeNodeId:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    id: str

    def __init__(self, id: str):
        self.id = id


class PrintOptions:
    """



    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    unicode: bool

    width: int

    indentation: int

    pure: bool

    termLabels: bool

    def __init__(
        self, unicode: bool, width: int, indentation: int, pure: bool, termLabels: bool
    ):
        self.unicode = unicode
        self.width = width
        self.indentation = indentation
        self.pure = pure
        self.termLabels = termLabels


class NodeTextDesc:
    """
    A printed sequent.


    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    id: NodeTextId
    """
        a handle identifying this print-out
        """

    sequent: str
    """
        the plain textual notation of the sequent
        """

    terms: typing.List[NodeTextSpan]
    """
        position table of the printed terms, for term selection in the UI
        """

    tacletAppInfo: str
    """
        textual description of the taclet applied at this node (may be null)
        """

    def __init__(
        self,
        id: NodeTextId,
        sequent: str,
        terms: typing.List[NodeTextSpan],
        tacletAppInfo: str,
    ):
        self.id = id
        self.sequent = sequent
        self.terms = terms
        self.tacletAppInfo = tacletAppInfo


class NodeTextId:
    """



    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    nodeId: NodeId

    nodeTextId: int

    def __init__(self, nodeId: NodeId, nodeTextId: int):
        self.nodeId = nodeId
        self.nodeTextId = nodeTextId


class NodeTextSpan:

    start: int

    end: int

    children: typing.List[NodeTextSpan]

    def __init__(self, start: int, end: int, children: typing.List[NodeTextSpan]):
        self.start = start
        self.end = end
        self.children = children


class TermActionDesc:
    """
    This class represents an action that can be executed on a term.


    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    commandId: TermActionId
    """
        Unique identification
        """

    displayName: str
    """
        A string to display to the user.
        """

    description: str
    """
        Long description of the action for the user.
        """

    category: str
    """
        A string identifying a category
        """

    kind: TermActionKind
    """
        Kind of the action
        """

    def __init__(
        self,
        commandId: TermActionId,
        displayName: str,
        description: str,
        category: str,
        kind: TermActionKind,
    ):
        self.commandId = commandId
        self.displayName = displayName
        self.description = description
        self.category = category
        self.kind = kind


class TermActionId:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    nodeTextId: NodeTextId

    pio: str

    id: str

    caretPos: int

    def __init__(self, nodeTextId: NodeTextId, pio: str, id: str, caretPos: int):
        self.nodeTextId = nodeTextId
        self.pio = pio
        self.id = id
        self.caretPos = caretPos


class TermActionKind(enum.Enum):
    """
    Possible kinds of an action applicable to position on a sequent.


    @author	Alexander Weigl
    @version	1 (29.10.23)
    """

    """
        Built-In Rule
        """
    BuiltIn = None

    """
        Proof Script
        """
    Script = None

    """
        Proof Macro
        """
    Macro = None

    """
        Taclet
        """
    Taclet = None


class FunctionDesc:
    """



    @author	Alexander Weigl
    @version	1 (15.10.23)
    """

    name: str

    sort: str

    retSort: SortDesc

    argSorts: typing.List[SortDesc]

    rigid: bool

    unique: bool

    skolemConstant: bool

    def __init__(
        self,
        name: str,
        sort: str,
        retSort: SortDesc,
        argSorts: typing.List[SortDesc],
        rigid: bool,
        unique: bool,
        skolemConstant: bool,
    ):
        self.name = name
        self.sort = sort
        self.retSort = retSort
        self.argSorts = argSorts
        self.rigid = rigid
        self.unique = unique
        self.skolemConstant = skolemConstant


class SortDesc:
    """



    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    string: str

    documentation: str

    extendsSorts: typing.List[SortDesc]

    anAbstract: bool

    s: str

    def __init__(
        self,
        string: str,
        documentation: str,
        extendsSorts: typing.List[SortDesc],
        anAbstract: bool,
        s: str,
    ):
        self.string = string
        self.documentation = documentation
        self.extendsSorts = extendsSorts
        self.anAbstract = anAbstract
        self.s = s


class ContractDesc:
    """
    Description of a loadable contract in KeY.

     Contracts can be arbitrary objects, representing a functional, dependency, information flow, etc.
     contract.
     Contracts can be loaded into proofs.


    @author	Alexander Weigl
    @version	1 (13.10.23)
    """

    contractId: ContractId
    """
        an identifier of the contract
        """

    name: str
    """
        Name of the contract
        """

    displayName: str
    """
        Showable name
        """

    typeName: str
    """
        the typename which is associated with this contract
        """

    htmlText: str
    """
        content of the contract as html text
        """

    plainText: str
    """
        content of the contract as plain text
        """

    def __init__(
        self,
        contractId: ContractId,
        name: str,
        displayName: str,
        typeName: str,
        htmlText: str,
        plainText: str,
    ):
        self.contractId = contractId
        self.name = name
        self.displayName = displayName
        self.typeName = typeName
        self.htmlText = htmlText
        self.plainText = plainText


class ContractId:
    """



    @author	Alexander Weigl
    @version	1 (28.10.23)
    """

    envId: EnvironmentId

    contractId: str

    def __init__(self, envId: EnvironmentId, contractId: str):
        self.envId = envId
        self.contractId = contractId


class LoadParams:
    """ """

    problemFile: Uri
    """
        URI to the problem file to be loaded
        """

    classPath: typing.List[Uri]
    """
        optional
        """

    bootClassPath: Uri
    """
        xxx
        """

    includes: typing.List[Uri]
    """
        xxx
        """

    def __init__(
        self,
        problemFile: Uri,
        classPath: typing.List[Uri],
        bootClassPath: Uri,
        includes: typing.List[Uri],
    ):
        self.problemFile = problemFile
        self.classPath = classPath
        self.bootClassPath = bootClassPath
        self.includes = includes


class Uri:
    """
    Value class of a uniform resource identifier


    @author	Alexander Weigl
    @version	1 (11/30/25)
    """

    uri: str

    def __init__(self, uri: str):
        self.uri = uri


class ProblemDefinition:
    """



    @author	Alexander Weigl
    @version	1 (15.10.23)
    """

    sorts: typing.List[SortDesc]

    functions: typing.List[FunctionDesc]

    predicates: typing.List[PredicateDesc]

    antecTerms: typing.List[str]

    succTerms: typing.List[str]

    def __init__(
        self,
        sorts: typing.List[SortDesc],
        functions: typing.List[FunctionDesc],
        predicates: typing.List[PredicateDesc],
        antecTerms: typing.List[str],
        succTerms: typing.List[str],
    ):
        self.sorts = sorts
        self.functions = functions
        self.predicates = predicates
        self.antecTerms = antecTerms
        self.succTerms = succTerms


class PredicateDesc:
    """



    @author	Alexander Weigl
    @version	1 (15.10.23)
    """

    name: str

    argSorts: typing.List[SortDesc]

    def __init__(self, name: str, argSorts: typing.List[SortDesc]):
        self.name = name
        self.argSorts = argSorts


class LogTraceParams:
    """ """

    message: str
    """
        The message to be logged.
        """

    level: str
    """
        Additional information that can be computed if the `trace` configuration is set to
                `'verbose'`
        """

    def __init__(self, message: str, level: str):
        self.message = message
        self.level = level


class ShowMessageParams:
    """ """

    type: MessageType
    """
        The message type. See {@link MessageType MessageType}.
        """

    message: str
    """
        the actual message
        """

    def __init__(self, type: MessageType, message: str):
        self.type = type
        self.message = message


class MessageType(enum.Enum):
    """ """

    Unused = None

    """
        An error message.
        """
    Error = None

    """
        A warning message.
        """
    Warning = None

    """
        An information message.
        """
    Info = None

    """
        A log message.
        """
    Log = None

    """
        A debug message.
        
        
        @proposed	
        @since	3.18.0
        """
    Debug = None


class ShowMessageRequestParams:
    """ """

    type: MessageType
    """
        The message type. See {@link MessageType MessageType}.
        """

    message: str
    """
        the actual message
        """

    actions: typing.List[MessageActionItem]

    def __init__(
        self, type: MessageType, message: str, actions: typing.List[MessageActionItem]
    ):
        self.type = type
        self.message = message
        self.actions = actions


class MessageActionItem:
    """ """

    title: str
    """
        A short title like 'Retry', 'Open Log' etc.
        """

    def __init__(self, title: str):
        self.title = title


class ShowDocumentParams:
    """
    Information to show a document on the client side.
    """

    uri: str
    """
        The uri to show.
        """

    external: bool
    """
        Indicates to show the resource in an external program.
                To show, for example, `https://code.visualstudio.com/`
                in the default WEB browser set `external` to `true`.
        """

    takeFocus: bool
    """
        An optional property to indicate whether the editor
                showing the document should take focus or not.
                Clients might ignore this property if an external
                program is started.
        """

    selection: TextRange
    """
        An optional selection range if the document is a text
                document. Clients might ignore the property if an
                external program is started or the file is not a text
                file.
        """

    def __init__(self, uri: str, external: bool, takeFocus: bool, selection: TextRange):
        self.uri = uri
        self.external = external
        self.takeFocus = takeFocus
        self.selection = selection


class TextRange:
    """
    TextRange specifies a range of integer numbers e.g. character positions.
    """

    start: int
    """
        this range's (included) start position.
        """

    end: int
    """
        this range's (excluded) end position.
        """

    def __init__(self, start: int, end: int):
        self.start = start
        self.end = end


class ShowDocumentResult:
    """ """

    success: bool
    """
        A boolean indicating if the show was successful.
        """

    def __init__(self, success: bool):
        self.success = success


class TaskFinishedInfo:

    time: int

    appliedRules: int

    closedGoals: int

    def __init__(self, time: int, appliedRules: int, closedGoals: int):
        self.time = time
        self.appliedRules = appliedRules
        self.closedGoals = closedGoals


class TaskStartedInfo:

    message: str

    size: int

    kind: TaskKind

    def __init__(self, message: str, size: int, kind: TaskKind):
        self.message = message
        self.size = size
        self.kind = kind


class TaskKind(enum.Enum):

    Strategy = None

    Macro = None

    UserInterface = None

    Loading = None

    Other = None


KEY_DATA_CLASSES = {
    "int": int,
    "int": int,
    "int": int,
    "int": int,
    "long": long,
    "long": long,
    "bool": bool,
    "bool": bool,
    "bool": bool,
    "bool": bool,
    "string": string,
    "string": string,
    "double": double,
    "double": double,
    "org.keyproject.key.api.data.ExampleDesc": ExampleDesc,
    "org.keyproject.key.api.data.KeyIdentifications$ProofId": ProofId,
    "org.keyproject.key.api.data.KeyIdentifications$EnvironmentId": EnvironmentId,
    "org.keyproject.key.api.data.ProofMacroDesc": ProofMacroDesc,
    "org.keyproject.key.api.data.ProofScriptCommandDesc": ProofScriptCommandDesc,
    "org.keyproject.key.api.remoteapi.ServerManagement$SetTraceParams": SetTraceParams,
    "org.keyproject.key.api.data.TraceValue": TraceValue,
    "org.keyproject.key.api.data.NodeDesc": NodeDesc,
    "org.keyproject.key.api.data.KeyIdentifications$NodeId": NodeId,
    "org.keyproject.key.api.data.StrategyOptions": StrategyOptions,
    "org.keyproject.key.api.data.MacroStatistic": MacroStatistic,
    "org.keyproject.key.api.data.ProofStatus": ProofStatus,
    "org.keyproject.key.api.data.TreeNodeDesc": TreeNodeDesc,
    "org.keyproject.key.api.data.KeyIdentifications$TreeNodeId": TreeNodeId,
    "org.keyproject.key.api.data.PrintOptions": PrintOptions,
    "org.keyproject.key.api.data.NodeTextDesc": NodeTextDesc,
    "org.keyproject.key.api.data.KeyIdentifications$NodeTextId": NodeTextId,
    "org.keyproject.key.api.data.NodeTextSpan": NodeTextSpan,
    "org.keyproject.key.api.data.TermActionDesc": TermActionDesc,
    "org.keyproject.key.api.data.KeyIdentifications$TermActionId": TermActionId,
    "org.keyproject.key.api.data.TermActionKind": TermActionKind,
    "org.keyproject.key.api.data.FunctionDesc": FunctionDesc,
    "org.keyproject.key.api.data.SortDesc": SortDesc,
    "org.keyproject.key.api.data.ContractDesc": ContractDesc,
    "org.keyproject.key.api.data.KeyIdentifications$ContractId": ContractId,
    "org.keyproject.key.api.data.LoadParams": LoadParams,
    "org.keyproject.key.api.data.Uri": Uri,
    "org.keyproject.key.api.data.ProblemDefinition": ProblemDefinition,
    "org.keyproject.key.api.data.PredicateDesc": PredicateDesc,
    "org.keyproject.key.api.remoteclient.LogTraceParams": LogTraceParams,
    "org.keyproject.key.api.remoteclient.ShowMessageParams": ShowMessageParams,
    "org.keyproject.key.api.remoteclient.MessageType": MessageType,
    "org.keyproject.key.api.remoteclient.ShowMessageRequestParams": ShowMessageRequestParams,
    "org.keyproject.key.api.remoteclient.MessageActionItem": MessageActionItem,
    "org.keyproject.key.api.remoteclient.ShowDocumentParams": ShowDocumentParams,
    "org.keyproject.key.api.data.TextRange": TextRange,
    "org.keyproject.key.api.remoteclient.ShowDocumentResult": ShowDocumentResult,
    "org.keyproject.key.api.data.TaskFinishedInfo": TaskFinishedInfo,
    "org.keyproject.key.api.data.TaskStartedInfo": TaskStartedInfo,
    "org.key_project.prover.engine.TaskStartedInfo$TaskKind": TaskKind,
}

KEY_DATA_CLASSES_REV = {
    "int": "int",
    "int": "int",
    "int": "int",
    "int": "int",
    "long": "long",
    "long": "long",
    "bool": "bool",
    "bool": "bool",
    "bool": "bool",
    "bool": "bool",
    "string": "string",
    "string": "string",
    "double": "double",
    "double": "double",
    "ExampleDesc": "org.keyproject.key.api.data.ExampleDesc",
    "ProofId": "org.keyproject.key.api.data.KeyIdentifications$ProofId",
    "EnvironmentId": "org.keyproject.key.api.data.KeyIdentifications$EnvironmentId",
    "ProofMacroDesc": "org.keyproject.key.api.data.ProofMacroDesc",
    "ProofScriptCommandDesc": "org.keyproject.key.api.data.ProofScriptCommandDesc",
    "SetTraceParams": "org.keyproject.key.api.remoteapi.ServerManagement$SetTraceParams",
    "TraceValue": "org.keyproject.key.api.data.TraceValue",
    "NodeDesc": "org.keyproject.key.api.data.NodeDesc",
    "NodeId": "org.keyproject.key.api.data.KeyIdentifications$NodeId",
    "StrategyOptions": "org.keyproject.key.api.data.StrategyOptions",
    "MacroStatistic": "org.keyproject.key.api.data.MacroStatistic",
    "ProofStatus": "org.keyproject.key.api.data.ProofStatus",
    "TreeNodeDesc": "org.keyproject.key.api.data.TreeNodeDesc",
    "TreeNodeId": "org.keyproject.key.api.data.KeyIdentifications$TreeNodeId",
    "PrintOptions": "org.keyproject.key.api.data.PrintOptions",
    "NodeTextDesc": "org.keyproject.key.api.data.NodeTextDesc",
    "NodeTextId": "org.keyproject.key.api.data.KeyIdentifications$NodeTextId",
    "NodeTextSpan": "org.keyproject.key.api.data.NodeTextSpan",
    "TermActionDesc": "org.keyproject.key.api.data.TermActionDesc",
    "TermActionId": "org.keyproject.key.api.data.KeyIdentifications$TermActionId",
    "TermActionKind": "org.keyproject.key.api.data.TermActionKind",
    "FunctionDesc": "org.keyproject.key.api.data.FunctionDesc",
    "SortDesc": "org.keyproject.key.api.data.SortDesc",
    "ContractDesc": "org.keyproject.key.api.data.ContractDesc",
    "ContractId": "org.keyproject.key.api.data.KeyIdentifications$ContractId",
    "LoadParams": "org.keyproject.key.api.data.LoadParams",
    "Uri": "org.keyproject.key.api.data.Uri",
    "ProblemDefinition": "org.keyproject.key.api.data.ProblemDefinition",
    "PredicateDesc": "org.keyproject.key.api.data.PredicateDesc",
    "LogTraceParams": "org.keyproject.key.api.remoteclient.LogTraceParams",
    "ShowMessageParams": "org.keyproject.key.api.remoteclient.ShowMessageParams",
    "MessageType": "org.keyproject.key.api.remoteclient.MessageType",
    "ShowMessageRequestParams": "org.keyproject.key.api.remoteclient.ShowMessageRequestParams",
    "MessageActionItem": "org.keyproject.key.api.remoteclient.MessageActionItem",
    "ShowDocumentParams": "org.keyproject.key.api.remoteclient.ShowDocumentParams",
    "TextRange": "org.keyproject.key.api.data.TextRange",
    "ShowDocumentResult": "org.keyproject.key.api.remoteclient.ShowDocumentResult",
    "TaskFinishedInfo": "org.keyproject.key.api.data.TaskFinishedInfo",
    "TaskStartedInfo": "org.keyproject.key.api.data.TaskStartedInfo",
    "TaskKind": "org.key_project.prover.engine.TaskStartedInfo$TaskKind",
}
