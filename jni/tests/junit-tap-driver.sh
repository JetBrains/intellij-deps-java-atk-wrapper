#! /bin/sh
# The automake test driver of the native unit tests.
#
# Runs the TAP driver of automake with the same arguments, then converts the log
# of the test program into $JUNIT_REPORTS_DIR/TEST-native-<test name>.xml. The
# conversion runs here and not in check-local: when a test fails, make stops
# after check-TESTS and never reaches check-local, and the report is needed most
# then.
#
# Environment: TAP_DRIVER (the path of tap-driver.sh), TAP_TO_JUNIT (the path of
# tap-to-junit.awk), JUNIT_REPORTS_DIR, AWK. JUNIT_REPORT_SUFFIX, for example
# "-asan", is added to the name of the report and of its test suite, so that a
# second run of the same tests does not replace the reports of the first one.

test_name=
log_file=
previous=
for argument in "$@"; do
    case "$previous" in
        --test-name) test_name=$argument ;;
        --log-file) log_file=$argument ;;
    esac
    test "$argument" = "--" && break
    previous=$argument
done

# TAP_DRIVER_SHELL is the shell of make. SHELL is not: make gives a recipe the
# SHELL of the user, which can be a shell that cannot run tap-driver.sh.
${TAP_DRIVER_SHELL:-/bin/sh} "$TAP_DRIVER" "$@"
status=$?

if test -n "$JUNIT_REPORTS_DIR" && test -n "$test_name" && test -f "$log_file"; then
    report_name=`echo "$test_name" | sed 's|[/ ]|_|g'`
    mkdir -p "$JUNIT_REPORTS_DIR" &&
        ${AWK:-awk} -v suite="native.$report_name${JUNIT_REPORT_SUFFIX:-}" -f "$TAP_TO_JUNIT" "$log_file" \
            > "$JUNIT_REPORTS_DIR/TEST-native-$report_name${JUNIT_REPORT_SUFFIX:-}.xml" ||
        echo "$0: could not write the JUnit report of $test_name" >&2
fi

exit $status
