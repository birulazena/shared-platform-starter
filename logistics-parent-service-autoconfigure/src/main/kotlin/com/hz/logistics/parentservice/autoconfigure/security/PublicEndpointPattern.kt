package com.hz.logistics.parentservice.autoconfigure.security

import org.springframework.http.server.PathContainer
import org.springframework.http.server.RequestPath
import org.springframework.web.util.pattern.PathPattern
import org.springframework.web.util.pattern.PathPatternParser

/**
 * The deliberately small, permit-only path grammar shared by the Servlet and
 * reactive security branches.
 *
 * Spring's parsed [PathPattern] implementation supplies the matching
 * semantics. Validation takes place before parsing so that features outside
 * the published grammar cannot become public merely because Spring supports a
 * richer pattern syntax.
 */
class PublicEndpointPattern private constructor(
    /** De-duplicated patterns in configuration encounter order. */
    val patterns: List<PathPattern>,
) {

    /** Returns true when any configured permit pattern matches an application path. */
    fun matches(path: String): Boolean {
        val applicationPath = applicationPath(path) ?: return false
        val parsedPath = PathContainer.parsePath(applicationPath)
        return patterns.any { pattern -> pattern.matches(parsedPath) }
    }

    /** Matches a parsed application path without introducing stack-specific matchers. */
    fun matches(path: PathContainer): Boolean = matches(path.value())

    /** Matches only the application path, excluding a Servlet context path. */
    fun matches(path: RequestPath): Boolean = matches(path.pathWithinApplication())

    companion object {

        private val parser = PathPatternParser.defaultInstance
        private val encodedSeparator = Regex("%(?:2f|5c)", RegexOption.IGNORE_CASE)

        /**
         * Validates and compiles the configured permit list once at startup.
         * Duplicate strings are intentionally collapsed; permit rules are a
         * union, so duplicates and declaration order cannot alter the result.
         */
        @JvmStatic
        fun compileAll(configuredPatterns: Collection<String>): PublicEndpointPattern {
            val uniquePatterns = LinkedHashSet<String>()
            configuredPatterns.forEach { pattern ->
                validate(pattern)
                uniquePatterns += pattern
            }

            val parsedPatterns = uniquePatterns.map { pattern ->
                try {
                    parser.parse(pattern)
                } catch (exception: RuntimeException) {
                    throw IllegalArgumentException("Invalid public endpoint pattern '$pattern'", exception)
                }
            }
            return PublicEndpointPattern(parsedPatterns)
        }

        private fun validate(pattern: String) {
            require(pattern.isNotBlank()) { "Public endpoint patterns must not be blank" }
            require(pattern.startsWith('/') && pattern != "/") {
                "Public endpoint patterns must be absolute non-root application paths"
            }
            require(!pattern.startsWith("//") && !pattern.endsWith('/')) {
                "Public endpoint patterns must not contain empty path segments"
            }
            require(!encodedSeparator.containsMatchIn(pattern) && !pattern.contains('\\')) {
                "Public endpoint patterns must not contain encoded path separators"
            }
            require(!pattern.contains('{') && !pattern.contains('}')) {
                "URI-template variables and inline regular expressions are not supported"
            }

            val segments = pattern.removePrefix("/").split('/')
            segments.forEachIndexed { index, segment ->
                require(segment.isNotEmpty() && segment != "." && segment != "..") {
                    "Public endpoint patterns must not contain empty or dot path segments"
                }

                if (segment.contains("**")) {
                    require(segment == "**" && index == segments.lastIndex) {
                        "'**' is allowed only as a terminal complete path segment"
                    }
                }
            }
        }

        /** Drops query and fragment data; neither is part of path authorization. */
        private fun applicationPath(value: String): String? {
            val suffixIndex = value.indexOfFirst { it == '?' || it == '#' }
            val path = if (suffixIndex >= 0) value.substring(0, suffixIndex) else value
            if (path.isEmpty() || !path.startsWith('/')) return null
            if (encodedSeparator.containsMatchIn(path) || path.contains('\\')) return null
            return path
        }
    }
}
