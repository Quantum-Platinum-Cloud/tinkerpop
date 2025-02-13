/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.jsr223;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes Gremlin strings using regex to try to detect certain properties from the script without actual
 * having to execute a {@code eval()} on it.
 */
public class GremlinScriptChecker {

    /**
     * An empty result whose properties return as empty.
     */
    public static final Result EMPTY_RESULT = new Result(null, null);

    /**
     * At least one of these tokens should be present somewhere in the Gremlin string for {@link #parse(String)} to
     * take any action at all.
     */
    private static final Set<String> tokens = new HashSet<>(Arrays.asList("evaluationTimeout", "scriptEvaluationTimeout",
                                                                          "ARGS_EVAL_TIMEOUT", "ARGS_SCRIPT_EVAL_TIMEOUT",
                                                                          "requestId", "REQUEST_ID"));
    /**
     * Matches single line comments, multi-line comments and space characters.
     * <pre>
     * From https://regex101.com/
     *
     * 	OR: match either of the followings
     * Sequence: match all of the followings in order
     * / /
     * Repeat
     * AnyCharacterExcept\n
     * zero or more times
     * EndOfLine
     * Sequence: match all of the followings in order
     * /
     * *
     * Repeat
     * CapturingGroup
     * GroupNumber:1
     * OR: match either of the followings
     * AnyCharacterExcept\n
     * AnyCharIn[ CarriageReturn NewLine]
     * zero or more times (ungreedy)
     * *
     * /
     * WhiteSpaceCharacter
     * </pre>
     */
    private static final Pattern patternClean = Pattern.compile("//.*$|/\\*(.|[\\r\\n])*?\\*/|\\s", Pattern.MULTILINE);

    /**
     * Regex fragment for the timeout tokens to look for. There are basically four:
     * <ul>
     *     <li>{@code evaluationTimeout} which is a string value and thus single or double quoted</li>
     *     <li>{@code scriptEvaluationTimeout} which is a string value and thus single or double quoted</li>
     *     <li>{@code ARGS_EVAL_TIMEOUT} which is a enum type of value which can be referenced with or without a {@code Tokens} qualifier</li>
     *     <li>{@code ARGS_SCRIPT_EVAL_TIMEOUT} which is a enum type of value which can be referenced with or without a {@code Tokens} qualifier</li>
     * </ul>
     * See {@link #patternWithOptions} for explain as this regex is embedded in there.
     */
    private static final String timeoutTokens = "[\"']evaluationTimeout[\"']|[\"']scriptEvaluationTimeout[\"']|(?:Tokens\\.)?ARGS_EVAL_TIMEOUT|(?:Tokens\\.)?ARGS_SCRIPT_EVAL_TIMEOUT";

    /**
     * Regex fragment for the timeout tokens to look for. There are basically four:
     * <ul>
     *     <li>{@code requestId} which is a string value and thus single or double quoted</li>
     *     <li>{@code REQUEST_ID} which is a enum type of value which can be referenced with or without a {@code Tokens} qualifier</li>
     * </ul>
     * See {@link #patternWithOptions} for a full explain as this regex is embedded in there.
     */
    private static final String requestIdTokens = "[\"']requestId[\"']|(?:Tokens\\.)?REQUEST_ID";

    /**
     * Matches {@code .with({timeout-token},{timeout})} with a matching group on the {@code timeout}.
     *
     * <pre>
     * From https://regex101.com/
     *
     * \.with\((?:(?:["']evaluationTimeout["']|["']scriptEvaluationTimeout["']|(?:Tokens\.)?ARGS_EVAL_TIMEOUT|(?:Tokens\.)?ARGS_SCRIPT_EVAL_TIMEOUT),(?<to>\d*)(:?L|l)?)|(?:(?:["']requestId["']|(?:Tokens\.)?REQUEST_ID),["'](?<rid>.*?))["']\)
     *
     * gm
     * 1st Alternative \.with\((?:(?:["']evaluationTimeout["']|["']scriptEvaluationTimeout["']|(?:Tokens\.)?ARGS_EVAL_TIMEOUT|(?:Tokens\.)?ARGS_SCRIPT_EVAL_TIMEOUT),(?<to>\d*)(:?L|l)?)
     * \. matches the character . with index 4610 (2E16 or 568) literally (case sensitive)
     * with matches the characters with literally (case sensitive)
     * \( matches the character ( with index 4010 (2816 or 508) literally (case sensitive)
     * Non-capturing group (?:(?:["']evaluationTimeout["']|["']scriptEvaluationTimeout["']|(?:Tokens\.)?ARGS_EVAL_TIMEOUT|(?:Tokens\.)?ARGS_SCRIPT_EVAL_TIMEOUT),(?<to>\d*)(:?L|l)?)
     * Non-capturing group (?:["']evaluationTimeout["']|["']scriptEvaluationTimeout["']|(?:Tokens\.)?ARGS_EVAL_TIMEOUT|(?:Tokens\.)?ARGS_SCRIPT_EVAL_TIMEOUT)
     * 1st Alternative ["']evaluationTimeout["']
     * Match a single character present in the list below ["']
     * "' matches a single character in the list "' (case sensitive)
     * evaluationTimeout matches the characters evaluationTimeout literally (case sensitive)
     * Match a single character present in the list below ["']
     * "' matches a single character in the list "' (case sensitive)
     * 2nd Alternative ["']scriptEvaluationTimeout["']
     * Match a single character present in the list below ["']
     * "' matches a single character in the list "' (case sensitive)
     * scriptEvaluationTimeout matches the characters scriptEvaluationTimeout literally (case sensitive)
     * Match a single character present in the list below ["']
     * "' matches a single character in the list "' (case sensitive)
     * 3rd Alternative (?:Tokens\.)?ARGS_EVAL_TIMEOUT
     * Non-capturing group (?:Tokens\.)?
     * ? matches the previous token between zero and one times, as many times as possible, giving back as needed (greedy)
     * Tokens matches the characters Tokens literally (case sensitive)
     * \. matches the character . with index 4610 (2E16 or 568) literally (case sensitive)
     * ARGS_EVAL_TIMEOUT matches the characters ARGS_EVAL_TIMEOUT literally (case sensitive)
     * 4th Alternative (?:Tokens\.)?ARGS_SCRIPT_EVAL_TIMEOUT
     * Non-capturing group (?:Tokens\.)?
     * ? matches the previous token between zero and one times, as many times as possible, giving back as needed (greedy)
     * Tokens matches the characters Tokens literally (case sensitive)
     * \. matches the character . with index 4610 (2E16 or 568) literally (case sensitive)
     * ARGS_SCRIPT_EVAL_TIMEOUT matches the characters ARGS_SCRIPT_EVAL_TIMEOUT literally (case sensitive)
     * , matches the character , with index 4410 (2C16 or 548) literally (case sensitive)
     * Named Capture Group to (?<to>\d*)
     * \d matches a digit (equivalent to [0-9])
     * * matches the previous token between zero and unlimited times, as many times as possible, giving back as needed (greedy)
     * 2nd Capturing Group (:?L|l)?
     * ? matches the previous token between zero and one times, as many times as possible, giving back as needed (greedy)
     * 1st Alternative :?L
     * : matches the character : with index 5810 (3A16 or 728) literally (case sensitive)
     * ? matches the previous token between zero and one times, as many times as possible, giving back as needed (greedy)
     * L matches the character L with index 7610 (4C16 or 1148) literally (case sensitive)
     * 2nd Alternative l
     * l matches the character l with index 10810 (6C16 or 1548) literally (case sensitive)
     * 2nd Alternative (?:(?:["']requestId["']|(?:Tokens\.)?REQUEST_ID),["'](?<rid>.*?))["']\)
     * Non-capturing group (?:(?:["']requestId["']|(?:Tokens\.)?REQUEST_ID),["'](?<rid>.*?))
     * Non-capturing group (?:["']requestId["']|(?:Tokens\.)?REQUEST_ID)
     * 1st Alternative ["']requestId["']
     * 2nd Alternative (?:Tokens\.)?REQUEST_ID
     * , matches the character , with index 4410 (2C16 or 548) literally (case sensitive)
     * Match a single character present in the list below ["']
     * "' matches a single character in the list "' (case sensitive)
     * Named Capture Group rid (?<rid>.*?)
     * . matches any character (except for line terminators)
     * *? matches the previous token between zero and unlimited times, as few times as possible, expanding as needed (lazy)
     * Match a single character present in the list below ["']
     * "' matches a single character in the list "' (case sensitive)
     * \) matches the character ) with index 4110 (2916 or 518) literally (case sensitive)
     * </pre>
     */
    private static final Pattern patternWithOptions =
            Pattern.compile("\\.with\\(((?:" + timeoutTokens + "),(?<to>\\d*)(:?L|l)?)|((?:" + requestIdTokens + "),[\"'](?<rid>.*?))[\"']\\)");

    /**
     * Parses a Gremlin script and extracts a {@code Result} containing properties that are relevant to the checker.
     */
    public static Result parse(final String gremlin) {
        if (gremlin.isEmpty()) return EMPTY_RESULT;

        // do a cheap check for tokens we care about - no need to parse unless one of these tokens is present in
        // the string.
        if (tokens.stream().noneMatch(gremlin::contains)) return EMPTY_RESULT;

        // kill out comments/whitespace. for whitespace, ignoring the need to keep string literals together as that
        // isn't currently a requirement
        final String cleanGremlin = patternClean.matcher(gremlin).replaceAll("");

        final Matcher m = patternWithOptions.matcher(cleanGremlin);
        if (!m.find()) return EMPTY_RESULT;

        // arguments given to Result class as null mean they weren't assigned (or the parser didn't find them somehow - eek!)
        Long timeout = null;
        String requestId = null;
        do {
            // timeout is added up across all scripts
            final String to = m.group("to");
            if (to != null) {
                if (null == timeout) timeout = 0L;
                timeout += Long.parseLong(to);
            }

            // request id just uses the last one found
            final String rid = m.group("rid");
            if (rid != null) requestId = rid;
        } while (m.find());

        return new Result(timeout, requestId);
    }

    /**
     * A result returned from a {@link #parse(String)} of a Gremlin string.
     */
    public static class Result {
        private final Long timeout;
        private final String requestId;

        private Result(final Long timeout, final String requestId) {
            this.timeout = timeout;
            this.requestId = requestId;
        }

        /**
         * Gets the value of the timeouts that were set using the {@code with()} source step. If there are multiple
         * commands using this step, the timeouts are summed together.
         */
        public final Optional<Long> getTimeout() {
            return null == timeout ? Optional.empty() : Optional.of(timeout);
        }

        /**
         * Gets the value of the request identifier supplied using the {@code with()} source step. If there are
         * multiple commands using this step, the last usage should represent the id returned here.
         */
        public Optional<String> getRequestId() {
            return null == requestId ? Optional.empty() : Optional.of(requestId);
        }

        @Override
        public String toString() {
            return "GremlinScriptChecker.Result{" +
                    "timeout=" + timeout +
                    ", requestId='" + requestId + '\'' +
                    '}';
        }
    }
}
