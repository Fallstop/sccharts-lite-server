/*
 * KIELER - Kiel Integrated Environment for Layout Eclipse RichClient
 *
 * http://rtsys.informatik.uni-kiel.de/kieler
 *
 * Copyright 2026 by
 * + Kiel University
 *   + Department of Computer Science
 *     + Real-Time and Embedded Systems Group
 *
 * This code is provided under the terms of the Eclipse Public License (EPL).
 */
package de.cau.cs.kieler.language.server.simulation.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * A watch or breakpoint condition over the simulation's data pool, written in SCCharts expression
 * syntax: {@code count >= 3 && !done}, {@code pre(x) != x}, {@code names[0] == "a"}. Parsed once when
 * the user sets it, evaluated against every tick.
 *
 * Supported: identifiers (with {@code .} and {@code [i]} access), int, float, bool and string literals,
 * {@code pre(x)}, unary {@code ! -}, {@code * / %}, {@code + -}, comparisons, {@code == !=},
 * {@code && ||} and parentheses. The KExpressions parser is not used because its references are
 * cross-references into a model, while these names resolve against the running simulation's values.
 */
public final class DebugExpression {

    /** Where identifiers get their values from. */
    public interface Scope {
        /** Value of {@code name} in the current tick, or null when the pool has no such entry. */
        JsonElement value(String name);

        /** Value of {@code name} in the previous tick, or null before the first tick. */
        JsonElement previous(String name);

        /** True when the pool knows {@code name} (used to reject typos when a watch is set). */
        boolean known(String name);
    }

    /** A malformed expression, or a runtime failure such as a type mismatch. */
    public static final class EvaluationException extends Exception {
        private static final long serialVersionUID = 1L;

        public EvaluationException(String message) {
            super(message);
        }
    }

    private final Node root;
    private final String text;
    private final List<String> identifiers = new ArrayList<>();

    private DebugExpression(String text, Node root) {
        this.text = text;
        this.root = root;
        root.collect(identifiers);
    }

    public static DebugExpression parse(String text) throws EvaluationException {
        if (text == null || text.trim().isEmpty()) throw new EvaluationException("The expression is empty.");
        Parser parser = new Parser(text);
        Node node = parser.parseOr();
        parser.skipSpace();
        if (parser.pos < parser.src.length()) {
            throw new EvaluationException("Unexpected '" + parser.src.charAt(parser.pos) + "' at position " + (parser.pos + 1) + ".");
        }
        return new DebugExpression(text, node);
    }

    /** The plain variable names the expression reads (without indices); pre() arguments included. */
    public List<String> identifiers() {
        return identifiers;
    }

    public String text() {
        return text;
    }

    public JsonElement evaluate(Scope scope) throws EvaluationException {
        return root.eval(scope);
    }

    /** True for a boolean result or a number other than 0; strings and arrays are an error. */
    public boolean evaluateCondition(Scope scope) throws EvaluationException {
        JsonElement value = evaluate(scope);
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsDouble() != 0;
        }
        throw new EvaluationException("The condition evaluates to " + value + ", not to a boolean.");
    }

    // ---------------------------------------------------------------------------------------------
    // Syntax tree

    private abstract static class Node {
        abstract JsonElement eval(Scope scope) throws EvaluationException;

        void collect(List<String> names) {
        }
    }

    private static final class Literal extends Node {
        final JsonElement value;

        Literal(JsonElement value) {
            this.value = value;
        }

        @Override
        JsonElement eval(Scope scope) {
            return value;
        }
    }

    private static final class Reference extends Node {
        final String name;
        final List<Node> indices;
        final boolean previous;

        Reference(String name, List<Node> indices, boolean previous) {
            this.name = name;
            this.indices = indices;
            this.previous = previous;
        }

        @Override
        void collect(List<String> names) {
            if (!names.contains(name)) names.add(name);
            for (Node index : indices) index.collect(names);
        }

        @Override
        JsonElement eval(Scope scope) throws EvaluationException {
            JsonElement value = previous ? scope.previous(name) : scope.value(name);
            if (value == null) {
                throw new EvaluationException(previous
                    ? "pre(" + name + ") has no value yet: the simulation has not completed a tick."
                    : "Unknown variable '" + name + "'.");
            }
            for (Node indexNode : indices) {
                JsonElement index = indexNode.eval(scope);
                if (!value.isJsonArray()) throw new EvaluationException("'" + name + "' is not an array.");
                JsonArray array = value.getAsJsonArray();
                int i = (int) asNumber(index, "array index");
                if (i < 0 || i >= array.size()) {
                    throw new EvaluationException("Index " + i + " is out of bounds for '" + name + "' (size " + array.size() + ").");
                }
                value = array.get(i);
            }
            return value;
        }
    }

    private static final class Unary extends Node {
        final char op;
        final Node operand;

        Unary(char op, Node operand) {
            this.op = op;
            this.operand = operand;
        }

        @Override
        void collect(List<String> names) {
            operand.collect(names);
        }

        @Override
        JsonElement eval(Scope scope) throws EvaluationException {
            JsonElement value = operand.eval(scope);
            if (op == '!') return new JsonPrimitive(!asBoolean(value, "operand of !"));
            double number = asNumber(value, "operand of unary -");
            return isIntegral(value) ? new JsonPrimitive((long) -number) : new JsonPrimitive(-number);
        }
    }

    private static final class Binary extends Node {
        final String op;
        final Node left, right;

        Binary(String op, Node left, Node right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        void collect(List<String> names) {
            left.collect(names);
            right.collect(names);
        }

        @Override
        JsonElement eval(Scope scope) throws EvaluationException {
            if (op.equals("&&")) return new JsonPrimitive(asBoolean(left.eval(scope), "operand of &&") && asBoolean(right.eval(scope), "operand of &&"));
            if (op.equals("||")) return new JsonPrimitive(asBoolean(left.eval(scope), "operand of ||") || asBoolean(right.eval(scope), "operand of ||"));
            JsonElement a = left.eval(scope), b = right.eval(scope);
            switch (op) {
                case "==": return new JsonPrimitive(same(a, b));
                case "!=": return new JsonPrimitive(!same(a, b));
                case "<": return new JsonPrimitive(compare(a, b) < 0);
                case "<=": return new JsonPrimitive(compare(a, b) <= 0);
                case ">": return new JsonPrimitive(compare(a, b) > 0);
                case ">=": return new JsonPrimitive(compare(a, b) >= 0);
                default: break;
            }
            if (op.equals("+") && (isString(a) || isString(b))) {
                return new JsonPrimitive(text(a) + text(b));
            }
            double x = asNumber(a, "left operand of " + op), y = asNumber(b, "right operand of " + op);
            boolean integral = isIntegral(a) && isIntegral(b);
            switch (op) {
                case "+": return integral ? new JsonPrimitive((long) x + (long) y) : new JsonPrimitive(x + y);
                case "-": return integral ? new JsonPrimitive((long) x - (long) y) : new JsonPrimitive(x - y);
                case "*": return integral ? new JsonPrimitive((long) x * (long) y) : new JsonPrimitive(x * y);
                case "/":
                    if (y == 0) throw new EvaluationException("Division by zero.");
                    return integral ? new JsonPrimitive((long) x / (long) y) : new JsonPrimitive(x / y);
                case "%":
                    if (y == 0) throw new EvaluationException("Division by zero.");
                    return integral ? new JsonPrimitive((long) x % (long) y) : new JsonPrimitive(x % y);
                default: throw new EvaluationException("Unsupported operator " + op);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Value helpers

    private static boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private static boolean isIntegral(JsonElement value) {
        if (!value.isJsonPrimitive()) return false;
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return true;
        if (!primitive.isNumber()) return false;
        String text = primitive.getAsNumber().toString();
        return !text.contains(".") && !text.contains("e") && !text.contains("E");
    }

    private static String text(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : value.toString();
    }

    private static double asNumber(JsonElement value, String what) throws EvaluationException {
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isNumber()) return primitive.getAsDouble();
            if (primitive.isBoolean()) return primitive.getAsBoolean() ? 1 : 0;
        }
        throw new EvaluationException("The " + what + " is " + value + ", not a number.");
    }

    private static boolean asBoolean(JsonElement value, String what) throws EvaluationException {
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) return primitive.getAsDouble() != 0;
        }
        throw new EvaluationException("The " + what + " is " + value + ", not a boolean.");
    }

    private static boolean same(JsonElement a, JsonElement b) throws EvaluationException {
        if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive x = a.getAsJsonPrimitive(), y = b.getAsJsonPrimitive();
            if (x.isString() || y.isString()) return x.isString() && y.isString() && x.getAsString().equals(y.getAsString());
            return asNumber(a, "operand") == asNumber(b, "operand");
        }
        return a.equals(b);
    }

    private static int compare(JsonElement a, JsonElement b) throws EvaluationException {
        if (isString(a) && isString(b)) return a.getAsString().compareTo(b.getAsString());
        return Double.compare(asNumber(a, "left operand of a comparison"), asNumber(b, "right operand of a comparison"));
    }

    // ---------------------------------------------------------------------------------------------
    // Recursive-descent parser

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) {
            this.src = src;
        }

        void skipSpace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        boolean take(String token) {
            skipSpace();
            if (src.startsWith(token, pos)) {
                // Do not read "==" as "=" or "<=" as "<".
                int end = pos + token.length();
                if (isOperatorChar(token.charAt(token.length() - 1)) && end < src.length() && isOperatorChar(src.charAt(end))) {
                    return false;
                }
                pos = end;
                return true;
            }
            return false;
        }

        static boolean isOperatorChar(char c) {
            return c == '=' || c == '!' || c == '<' || c == '>' || c == '&' || c == '|';
        }

        Node parseOr() throws EvaluationException {
            Node node = parseAnd();
            while (take("||") || takeWord("or")) node = new Binary("||", node, parseAnd());
            return node;
        }

        Node parseAnd() throws EvaluationException {
            Node node = parseEquality();
            while (take("&&") || takeWord("and")) node = new Binary("&&", node, parseEquality());
            return node;
        }

        Node parseEquality() throws EvaluationException {
            Node node = parseComparison();
            while (true) {
                if (take("==")) node = new Binary("==", node, parseComparison());
                else if (take("!=")) node = new Binary("!=", node, parseComparison());
                else return node;
            }
        }

        Node parseComparison() throws EvaluationException {
            Node node = parseAdditive();
            while (true) {
                if (take("<=")) node = new Binary("<=", node, parseAdditive());
                else if (take(">=")) node = new Binary(">=", node, parseAdditive());
                else if (take("<")) node = new Binary("<", node, parseAdditive());
                else if (take(">")) node = new Binary(">", node, parseAdditive());
                else return node;
            }
        }

        Node parseAdditive() throws EvaluationException {
            Node node = parseMultiplicative();
            while (true) {
                if (take("+")) node = new Binary("+", node, parseMultiplicative());
                else if (take("-")) node = new Binary("-", node, parseMultiplicative());
                else return node;
            }
        }

        Node parseMultiplicative() throws EvaluationException {
            Node node = parseUnary();
            while (true) {
                if (take("*")) node = new Binary("*", node, parseUnary());
                else if (take("/")) node = new Binary("/", node, parseUnary());
                else if (take("%")) node = new Binary("%", node, parseUnary());
                else return node;
            }
        }

        Node parseUnary() throws EvaluationException {
            if (take("!") || takeWord("not")) return new Unary('!', parseUnary());
            if (take("-")) return new Unary('-', parseUnary());
            return parsePrimary();
        }

        Node parsePrimary() throws EvaluationException {
            skipSpace();
            if (pos >= src.length()) throw new EvaluationException("The expression ends unexpectedly.");
            char c = src.charAt(pos);
            if (c == '(') {
                pos++;
                Node inner = parseOr();
                if (!take(")")) throw new EvaluationException("Missing ')' at position " + (pos + 1) + ".");
                return inner;
            }
            if (c == '"' || c == '\'') return new Literal(new JsonPrimitive(parseString(c)));
            if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
                return new Literal(parseNumber());
            }
            if (Character.isLetter(c) || c == '_') {
                String word = parseWord();
                if (word.equals("true")) return new Literal(new JsonPrimitive(true));
                if (word.equals("false")) return new Literal(new JsonPrimitive(false));
                if (word.equals("pre")) {
                    if (!take("(")) throw new EvaluationException("pre needs a variable in parentheses: pre(x).");
                    skipSpace();
                    if (pos >= src.length() || !(Character.isLetter(src.charAt(pos)) || src.charAt(pos) == '_')) {
                        throw new EvaluationException("pre needs a variable name: pre(x).");
                    }
                    String name = parseName();
                    List<Node> indices = parseIndices();
                    if (!take(")")) throw new EvaluationException("Missing ')' after pre(" + name + ".");
                    return new Reference(name, indices, true);
                }
                pos -= word.length();
                String name = parseName();
                return new Reference(name, parseIndices(), false);
            }
            throw new EvaluationException("Unexpected '" + c + "' at position " + (pos + 1) + ".");
        }

        List<Node> parseIndices() throws EvaluationException {
            List<Node> indices = new ArrayList<>();
            while (take("[")) {
                indices.add(parseOr());
                if (!take("]")) throw new EvaluationException("Missing ']' at position " + (pos + 1) + ".");
            }
            return indices;
        }

        boolean takeWord(String word) {
            skipSpace();
            if (src.startsWith(word, pos)) {
                int end = pos + word.length();
                if (end == src.length() || !(Character.isLetterOrDigit(src.charAt(end)) || src.charAt(end) == '_')) {
                    pos = end;
                    return true;
                }
            }
            return false;
        }

        String parseWord() {
            int start = pos;
            while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
            return src.substring(start, pos);
        }

        /** An identifier, possibly dotted (struct members and region-qualified names). */
        String parseName() {
            int start = pos;
            parseWord();
            while (pos + 1 < src.length() && src.charAt(pos) == '.' && (Character.isLetter(src.charAt(pos + 1)) || src.charAt(pos + 1) == '_')) {
                pos++;
                parseWord();
            }
            return src.substring(start, pos);
        }

        JsonElement parseNumber() throws EvaluationException {
            int start = pos;
            boolean floating = false;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                if (src.charAt(pos) == '.') floating = true;
                pos++;
            }
            String literal = src.substring(start, pos);
            try {
                return floating ? new JsonPrimitive(Double.parseDouble(literal)) : new JsonPrimitive(Long.parseLong(literal));
            } catch (NumberFormatException e) {
                throw new EvaluationException("'" + literal + "' is not a number.");
            }
        }

        String parseString(char quote) throws EvaluationException {
            StringBuilder text = new StringBuilder();
            pos++;
            while (pos < src.length() && src.charAt(pos) != quote) {
                char c = src.charAt(pos++);
                if (c == '\\' && pos < src.length()) {
                    char escaped = src.charAt(pos++);
                    text.append(escaped == 'n' ? '\n' : escaped == 't' ? '\t' : escaped);
                } else {
                    text.append(c);
                }
            }
            if (pos >= src.length()) throw new EvaluationException("Unterminated string literal.");
            pos++;
            return text.toString();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Scopes over JSON pools

    /**
     * Resolves a name in a data pool: exact key first, then the unique shortest key that ends in
     * {@code _name} (compilation prefixes locals with their region), ignoring pre/register copies.
     */
    public static String resolveKey(JsonObject pool, String name) {
        if (pool == null) return null;
        if (pool.has(name)) return name;
        String best = null;
        for (Map.Entry<String, JsonElement> entry : pool.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_pre_") || key.startsWith("_reg_")) continue;
            if (key.endsWith("_" + name) && (best == null || key.length() < best.length())) best = key;
        }
        return best;
    }

    /** A scope over the current and previous pool of a simulation. */
    public static Scope scope(JsonObject current, JsonObject previous) {
        Function<JsonObject, Function<String, JsonElement>> lookup = pool -> name -> {
            String key = resolveKey(pool, name);
            if (key == null) return null;
            JsonElement value = pool.get(key);
            return value == null ? JsonNull.INSTANCE : value;
        };
        Function<String, JsonElement> now = lookup.apply(current);
        Function<String, JsonElement> before = previous == null ? name -> null : lookup.apply(previous);
        return new Scope() {
            @Override
            public JsonElement value(String name) {
                return now.apply(name);
            }

            @Override
            public JsonElement previous(String name) {
                return before.apply(name);
            }

            @Override
            public boolean known(String name) {
                return resolveKey(current, name) != null;
            }
        };
    }
}
