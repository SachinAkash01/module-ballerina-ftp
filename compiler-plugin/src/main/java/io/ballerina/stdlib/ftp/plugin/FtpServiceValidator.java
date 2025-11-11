/*
 * Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.ftp.plugin;

import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.CheckExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;
import io.ballerina.tools.text.LineRange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.ANNOTATION_PATTERN_NOT_SUBSET;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.INVALID_ANNOTATION_USAGE;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.INVALID_REMOTE_FUNCTION;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.MULTIPLE_CONTENT_METHODS;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.MULTIPLE_GENERIC_CONTENT_METHODS;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.NO_ON_FILE_CHANGE;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.OVERLAPPING_ANNOTATION_PATTERNS;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.RESOURCE_FUNCTION_NOT_ALLOWED;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.CompilationErrors.ON_FILE_CHANGE_DEPRECATED;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_CHANGE_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_CSV_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_DELETED_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_JSON_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_TEXT_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginConstants.ON_FILE_XML_FUNC;
import static io.ballerina.stdlib.ftp.plugin.PluginUtils.getDiagnostic;
import static io.ballerina.stdlib.ftp.plugin.PluginUtils.isRemoteFunction;

/**
 * FTP service compilation validator.
 */
public class FtpServiceValidator {

    private static final Pattern FILE_NAME_PATTERN_REGEX =
            Pattern.compile("fileNamePattern\\s*:\\s*\"([^\"]+)\"");
    private static final List<String> SAMPLE_PLACEHOLDERS = List.of("sample", "data", "file");

    public void validate(SyntaxNodeAnalysisContext context) {
        ServiceDeclarationNode serviceDeclarationNode = (ServiceDeclarationNode) context.node();
        NodeList<Node> memberNodes = serviceDeclarationNode.members();
        SyntaxTree syntaxTree = serviceDeclarationNode.syntaxTree();

        boolean hasRemoteFunction = serviceDeclarationNode.members().stream().anyMatch(child ->
                child.kind() == SyntaxKind.OBJECT_METHOD_DEFINITION &&
                        isRemoteFunction(context, (FunctionDefinitionNode) child));

        if (serviceDeclarationNode.members().isEmpty() || !hasRemoteFunction) {
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo(
                    PluginConstants.CompilationErrors.TEMPLATE_CODE_GENERATION_HINT.getErrorCode(),
                    PluginConstants.CompilationErrors.TEMPLATE_CODE_GENERATION_HINT.getError(),
                    DiagnosticSeverity.INTERNAL);
            context.reportDiagnostic(DiagnosticFactory.createDiagnostic(diagnosticInfo,
                    serviceDeclarationNode.location()));
        }

        FunctionDefinitionNode onFileChange = null;
        FunctionDefinitionNode onFileDeleted = null;
        List<FunctionDefinitionNode> contentMethods = new ArrayList<>();
        List<String> contentMethodNames = new ArrayList<>();
        List<AnnotationDetail> functionConfigAnnotations = new ArrayList<>();

        for (Node node : memberNodes) {
            if (node.kind() == SyntaxKind.OBJECT_METHOD_DEFINITION) {
                FunctionDefinitionNode functionDefinitionNode = (FunctionDefinitionNode) node;
                MethodSymbol methodSymbol = PluginUtils.getMethodSymbol(context, functionDefinitionNode);
                String funcName = methodSymbol.getName().orElse(null);

                if (funcName != null) {
                    if (funcName.equals(ON_FILE_CHANGE_FUNC)) {
                        onFileChange = functionDefinitionNode;
                    } else if (funcName.equals(ON_FILE_DELETED_FUNC)) {
                        onFileDeleted = functionDefinitionNode;
                    } else if (isContentMethod(funcName)) {
                        contentMethods.add(functionDefinitionNode);
                        contentMethodNames.add(funcName);
                    } else if (isRemoteFunction(context, functionDefinitionNode)) {
                        context.reportDiagnostic(getDiagnostic(INVALID_REMOTE_FUNCTION,
                                DiagnosticSeverity.ERROR, functionDefinitionNode.location()));
                    }
                }

                getFunctionConfigAnnotation(context, functionDefinitionNode, funcName)
                        .ifPresent(functionConfigAnnotations::add);
            } else if (node.kind() == SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
                context.reportDiagnostic(PluginUtils.getDiagnostic(RESOURCE_FUNCTION_NOT_ALLOWED,
                        DiagnosticSeverity.ERROR, node.location()));
            }
        }

        // Validate method exclusivity rules
        validateMethodExclusivity(context, serviceDeclarationNode, onFileChange, onFileDeleted,
                contentMethods, contentMethodNames);

        // Validate parameters based on which method type is present
        if (onFileChange != null && contentMethods.isEmpty() && onFileDeleted == null) {
            context.reportDiagnostic(getDiagnostic(ON_FILE_CHANGE_DEPRECATED,
                    DiagnosticSeverity.WARNING, onFileChange.location()));
            // Traditional onFileChange validation only
            new FtpFunctionValidator(context, onFileChange).validate();
        } else if (onFileChange == null && (!contentMethods.isEmpty() || onFileDeleted != null)) {
            // New content method validation
            for (int i = 0; i < contentMethods.size(); i++) {
                new FtpContentFunctionValidator(context, contentMethods.get(i),
                        contentMethodNames.get(i)).validate();
            }

            // Validate onFileDeleted if present
            if (onFileDeleted != null) {
                new FtpFileDeletedValidator(context, onFileDeleted).validate();
            }
        } else if (onFileChange == null && contentMethods.isEmpty() && onFileDeleted == null) {
            // No valid method found - maintain backward compatibility by reporting NO_ON_FILE_CHANGE
            context.reportDiagnostic(getDiagnostic(NO_ON_FILE_CHANGE,
                    DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
        }

        Set<String> listenerPatterns = collectListenerPatterns(context, serviceDeclarationNode, syntaxTree);
        validateFunctionConfigAnnotations(context, functionConfigAnnotations, listenerPatterns);
    }

    private boolean isContentMethod(String methodName) {
        return methodName != null && (methodName.equals(ON_FILE_FUNC) ||
                methodName.equals(ON_FILE_TEXT_FUNC) ||
                methodName.equals(ON_FILE_JSON_FUNC) ||
                methodName.equals(ON_FILE_XML_FUNC) ||
                methodName.equals(ON_FILE_CSV_FUNC));
    }

    private void validateMethodExclusivity(SyntaxNodeAnalysisContext context,
                                           ServiceDeclarationNode serviceDeclarationNode,
                                           FunctionDefinitionNode onFileChange,
                                           FunctionDefinitionNode onFileDeleted,
                                           List<FunctionDefinitionNode> contentMethods,
                                           List<String> contentMethodNames) {
        // Rule 1: Cannot mix onFileChange with content methods or onFileDeleted
        if (onFileChange != null && (!contentMethods.isEmpty() || onFileDeleted != null)) {
            context.reportDiagnostic(getDiagnostic(MULTIPLE_CONTENT_METHODS,
                    DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
            return;
        }

        // Rule 2: If using content methods, validate strategy
        if (!contentMethods.isEmpty()) {
            // Cannot have multiple generic onFile methods
            long onFileCount = contentMethodNames.stream().filter(name -> name.equals(ON_FILE_FUNC)).count();
            if (onFileCount > 1) {
                context.reportDiagnostic(getDiagnostic(MULTIPLE_GENERIC_CONTENT_METHODS,
                        DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
            }
        }
    }

    private Optional<AnnotationDetail> getFunctionConfigAnnotation(SyntaxNodeAnalysisContext context,
                                                                   FunctionDefinitionNode functionDefinitionNode,
                                                                   String methodName) {
        Optional<MetadataNode> metadataNode = functionDefinitionNode.metadata();
        if (metadataNode.isEmpty()) {
            return Optional.empty();
        }

        for (AnnotationNode annotationNode : metadataNode.get().annotations()) {
            if (!isFunctionConfigAnnotation(context, annotationNode)) {
                continue;
            }

            if (!isContentMethod(methodName)) {
                context.reportDiagnostic(getDiagnostic(INVALID_ANNOTATION_USAGE,
                        DiagnosticSeverity.ERROR, annotationNode.location()));
                return Optional.empty();
            }

            Optional<String> patternOpt = extractPatternFromAnnotation(annotationNode);
            if (patternOpt.isEmpty()) {
                context.reportDiagnostic(getDiagnostic(INVALID_ANNOTATION_USAGE,
                        DiagnosticSeverity.ERROR, annotationNode.location()));
                return Optional.empty();
            }

            String pattern = patternOpt.get();
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                context.reportDiagnostic(getDiagnostic(INVALID_ANNOTATION_USAGE,
                        DiagnosticSeverity.ERROR, annotationNode.location()));
                return Optional.empty();
            }

            return Optional.of(new AnnotationDetail(pattern, annotationNode.location()));
        }
        return Optional.empty();
    }

    private boolean isFunctionConfigAnnotation(SyntaxNodeAnalysisContext context, AnnotationNode annotationNode) {
        Optional<Symbol> symbolOpt = context.semanticModel().symbol(annotationNode.annotReference());
        if (symbolOpt.isEmpty() || !(symbolOpt.get() instanceof AnnotationSymbol annotationSymbol)) {
            return false;
        }

        Optional<ModuleSymbol> module = annotationSymbol.getModule();
        if (module.isEmpty() || !PluginUtils.validateModuleId(module.get())) {
            return false;
        }

        return annotationSymbol.getName().map(name -> name.equals("FunctionConfig")).orElse(false);
    }

    private Optional<String> extractPatternFromAnnotation(AnnotationNode annotationNode) {
        if (annotationNode.annotValue().isEmpty()) {
            return Optional.empty();
        }

        if (!(annotationNode.annotValue().get() instanceof MappingConstructorExpressionNode mappingNode)) {
            return Optional.empty();
        }

        for (MappingFieldNode fieldNode : mappingNode.fields()) {
            if (fieldNode.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificFieldNode = (SpecificFieldNode) fieldNode;
            String fieldName = specificFieldNode.fieldName().toString().trim();
            if (!fieldName.equals("fileNamePattern")) {
                continue;
            }

            Optional<ExpressionNode> valueExprOpt = specificFieldNode.valueExpr();
            if (valueExprOpt.isEmpty()) {
                return Optional.empty();
            }

            ExpressionNode valueExpr = valueExprOpt.get();
            if (valueExpr.kind() != SyntaxKind.STRING_LITERAL) {
                return Optional.empty();
            }

            BasicLiteralNode literalNode = (BasicLiteralNode) valueExpr;
            String literalText = literalNode.literalToken().text();
            return Optional.of(unescapeStringLiteral(literalText));
        }
        return Optional.empty();
    }

    private String unescapeStringLiteral(String literalText) {
        String value = literalText;
        if (literalText.length() >= 2 && literalText.charAt(0) == '"' &&
                literalText.charAt(literalText.length() - 1) == '"') {
            value = literalText.substring(1, literalText.length() - 1);
        }
        value = value.replace("\\\"", "\"");
        value = value.replace("\\\\", "\\");
        value = value.replace("\\n", "\n");
        value = value.replace("\\t", "\t");
        value = value.replace("\\r", "\r");
        return value;
    }

    private Set<String> collectListenerPatterns(SyntaxNodeAnalysisContext context,
                                                ServiceDeclarationNode serviceDeclarationNode,
                                                SyntaxTree syntaxTree) {
        Set<String> patterns = new HashSet<>();
        serviceDeclarationNode.expressions()
                .forEach(expr -> extractListenerPattern(context, expr, syntaxTree).ifPresent(patterns::add));
        return patterns;
    }

    private Optional<String> extractListenerPattern(SyntaxNodeAnalysisContext context, Node expressionNode,
                                                    SyntaxTree syntaxTree) {
        if (expressionNode == null) {
            return Optional.empty();
        }

        SyntaxKind kind = expressionNode.kind();
        if (kind == SyntaxKind.CHECK_EXPRESSION) {
            CheckExpressionNode checkExpressionNode = (CheckExpressionNode) expressionNode;
            return extractListenerPattern(context, checkExpressionNode.expression(), syntaxTree);
        } else if (kind == SyntaxKind.IMPLICIT_NEW_EXPRESSION || kind == SyntaxKind.EXPLICIT_NEW_EXPRESSION) {
            return findPatternInNodeText(expressionNode);
        } else if (kind == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            SimpleNameReferenceNode simpleNameReferenceNode = (SimpleNameReferenceNode) expressionNode;
            Optional<Symbol> symbolOpt = context.semanticModel().symbol(simpleNameReferenceNode);
            if (symbolOpt.isPresent() &&
                    symbolOpt.get() instanceof io.ballerina.compiler.api.symbols.VariableSymbol variableSymbol) {
                Location location = variableSymbol.getLocation();
                if (location != null) {
                    LineRange lineRange = location.lineRange();
                    Node node = PluginUtils.findNode(syntaxTree, lineRange);
                    if (node != null) {
                        return findPatternInNodeText(node);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> findPatternInNodeText(Node node) {
        String nodeText = node.toString();
        Matcher matcher = FILE_NAME_PATTERN_REGEX.matcher(nodeText);
        if (matcher.find()) {
            return Optional.of(unescapeStringLiteral(matcher.group(1)));
        }
        return Optional.empty();
    }

    private void validateFunctionConfigAnnotations(SyntaxNodeAnalysisContext context,
                                                   List<AnnotationDetail> annotations,
                                                   Set<String> listenerPatterns) {
        if (annotations.isEmpty()) {
            return;
        }

        for (AnnotationDetail detail : annotations) {
            if (!listenerPatterns.isEmpty()) {
                boolean matchesAny = false;
                for (String listenerPattern : listenerPatterns) {
                    if (isPatternSubset(listenerPattern, detail.pattern)) {
                        matchesAny = true;
                        break;
                    }
                }
                if (!matchesAny) {
                    context.reportDiagnostic(getDiagnostic(ANNOTATION_PATTERN_NOT_SUBSET,
                            DiagnosticSeverity.ERROR, detail.location));
                }
            }
        }

        for (int i = 0; i < annotations.size(); i++) {
            for (int j = i + 1; j < annotations.size(); j++) {
                AnnotationDetail first = annotations.get(i);
                AnnotationDetail second = annotations.get(j);
                if (patternsOverlap(first.pattern, second.pattern)) {
                    context.reportDiagnostic(getDiagnostic(OVERLAPPING_ANNOTATION_PATTERNS,
                            DiagnosticSeverity.ERROR, second.location));
                }
            }
        }
    }

    private boolean isPatternSubset(String listenerPattern, String annotationPattern) {
        try {
            Pattern listener = Pattern.compile(listenerPattern);
            Pattern annotation = Pattern.compile(annotationPattern);
            List<String> samples = generateSamples(annotationPattern);
            for (String sample : samples) {
                if (!annotation.matcher(sample).matches()) {
                    continue;
                }
                if (!listener.matcher(sample).matches()) {
                    return false;
                }
            }
            return true;
        } catch (PatternSyntaxException e) {
            return true;
        }
    }

    private boolean patternsOverlap(String patternA, String patternB) {
        try {
            Pattern compiledA = Pattern.compile(patternA);
            Pattern compiledB = Pattern.compile(patternB);
            List<String> samplesA = generateSamples(patternA);
            for (String sample : samplesA) {
                if (compiledA.matcher(sample).matches() && compiledB.matcher(sample).matches()) {
                    return true;
                }
            }
            List<String> samplesB = generateSamples(patternB);
            for (String sample : samplesB) {
                if (compiledA.matcher(sample).matches() && compiledB.matcher(sample).matches()) {
                    return true;
                }
            }
            return false;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private List<String> generateSamples(String pattern) {
        List<String> samples = new ArrayList<>();
        for (String placeholder : SAMPLE_PLACEHOLDERS) {
            String sample = buildSample(pattern, placeholder);
            if (!sample.isEmpty()) {
                samples.add(sample);
            }
        }
        if (samples.isEmpty()) {
            samples.add("sample");
        }
        return samples;
    }

    private String buildSample(String pattern, String placeholder) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\\') {
                if (i + 1 < pattern.length()) {
                    char next = pattern.charAt(i + 1);
                    switch (next) {
                        case 'd':
                        case 'D':
                        case 'w':
                        case 'W':
                        case 's':
                        case 'S':
                            sb.append('0');
                            break;
                        case '.':
                            sb.append('.');
                            break;
                        default:
                            sb.append(next);
                            break;
                    }
                    i++;
                }
            } else if (ch == '.') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    sb.append(placeholder);
                    i++;
                } else {
                    sb.append('.');
                }
            } else if (ch == '*' || ch == '+' || ch == '?') {
                // ignore quantifiers
            } else if (ch == '{') {
                int end = findClosing(pattern, i, '{', '}');
                if (end > i) {
                    i = end;
                }
            } else if (ch == '[') {
                int end = findClosing(pattern, i, '[', ']');
                if (end > i + 1) {
                    sb.append(pattern.charAt(i + 1));
                    i = end;
                }
            } else if (ch == '(') {
                int end = findClosing(pattern, i, '(', ')');
                if (end > i) {
                    String inner = pattern.substring(i + 1, end);
                    String firstAlternative = selectFirstAlternative(inner);
                    sb.append(buildSample(firstAlternative, placeholder));
                    i = end;
                }
            } else if (ch == '|') {
                break;
            } else if (ch == '^' || ch == '$') {
                // ignore anchors
            } else {
                sb.append(ch);
            }
        }
        if (sb.length() == 0) {
            return placeholder;
        }
        return sb.toString();
    }

    private int findClosing(String text, int start, char open, char close) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String selectFirstAlternative(String pattern) {
        int depth = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == '|' && depth == 0) {
                return pattern.substring(0, i);
            }
        }
        return pattern;
    }

    private static final class AnnotationDetail {
        private final String pattern;
        private final Location location;

        private AnnotationDetail(String pattern, Location location) {
            this.pattern = pattern;
            this.location = location;
        }
    }
}
