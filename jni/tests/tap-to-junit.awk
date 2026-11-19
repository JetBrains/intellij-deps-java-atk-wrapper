# Converts the TAP log of one GLib test program into a JUnit XML report.
#
# Usage: awk -v suite=<name> -f tap-to-junit.awk <log> > TEST-native-<name>.xml
#
# "ok N name" is a passed test case, "not ok N name" a failure, "# SKIP" a skipped
# one, and "# TODO", which GLib writes for g_test_incomplete, a skipped one with a
# TODO message. The lines before a result are the output of that test case. If
# the program stops before it reports every test case of its plan, for example
# on a crash or an abort, the report gets a failed test case with the rest of the
# log, so that the CI does not show the run as passed.

function xml_escape(text) {
    gsub(/&/, "\\&amp;", text)
    gsub(/</, "\\&lt;", text)
    gsub(/>/, "\\&gt;", text)
    gsub(/"/, "\\&quot;", text)
    # Control characters other than tab and newline are not allowed in XML 1.0.
    gsub(/[\001-\010\013\014\016-\037]/, "?", text)
    return text
}

function add_case(name, kind, message, output) {
    count++
    case_name[count] = name
    case_kind[count] = kind
    case_message[count] = message
    case_output[count] = output
    if (kind == "failure") {
        failures++
    } else if (kind == "skipped") {
        skipped++
    }
}

function result_name(line, prefix_length,    rest, hash) {
    rest = substr(line, prefix_length + 1)
    sub(/^[0-9]+[ \t]*/, "", rest)
    sub(/^-[ \t]*/, "", rest)
    hash = index(rest, " # ")
    if (hash > 0) {
        rest = substr(rest, 1, hash - 1)
    }
    return rest
}

function directive(line,    hash) {
    hash = index(line, " # ")
    return hash > 0 ? substr(line, hash + 3) : ""
}

BEGIN {
    count = 0
    failures = 0
    skipped = 0
    planned = -1
    output = ""
}

/^1\.\.[0-9]+/ {
    planned = substr($0, 4) + 0
    next
}

/^ok / || /^ok$/ {
    d = directive($0)
    if (toupper(substr(d, 1, 4)) == "SKIP") {
        add_case(result_name($0, 3), "skipped", d, output)
    } else if (toupper(substr(d, 1, 4)) == "TODO") {
        # GLib writes "ok ... # TODO" when an incomplete test passes.
        add_case(result_name($0, 3), "skipped", d, output)
    } else {
        add_case(result_name($0, 3), "passed", "", output)
    }
    output = ""
    next
}

/^not ok / || /^not ok$/ {
    d = directive($0)
    if (toupper(substr(d, 1, 4)) == "TODO") {
        add_case(result_name($0, 7), "skipped", d, output)
    } else if (toupper(substr(d, 1, 4)) == "SKIP") {
        add_case(result_name($0, 7), "skipped", d, output)
    } else {
        add_case(result_name($0, 7), "failure", "test case failed", output)
    }
    output = ""
    next
}

{
    output = output $0 "\n"
}

END {
    if (count == 0 || (planned >= 0 && count < planned)) {
        add_case("(the test program stopped before it reported all test cases)", "failure",
                 "the test program crashed, aborted or stopped early", output)
    }

    printf "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
    printf "<testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" errors=\"0\" skipped=\"%d\">\n",
           xml_escape(suite), count, failures, skipped
    for (i = 1; i <= count; i++) {
        printf "  <testcase classname=\"%s\" name=\"%s\"", xml_escape(suite), xml_escape(case_name[i])
        if (case_kind[i] == "passed") {
            printf ">\n"
        } else if (case_kind[i] == "skipped") {
            printf ">\n    <skipped message=\"%s\"/>\n", xml_escape(case_message[i])
        } else {
            printf ">\n    <failure message=\"%s\">%s</failure>\n",
                   xml_escape(case_message[i]), xml_escape(case_output[i])
        }
        if (case_output[i] != "") {
            printf "    <system-out>%s</system-out>\n", xml_escape(case_output[i])
        }
        printf "  </testcase>\n"
    }
    printf "</testsuite>\n"
}
