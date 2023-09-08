package tech.ydb.jdbc.query;

public enum QueryType {
    // DDL
    SCHEME_QUERY,

    // DML
    DATA_QUERY,
    SCAN_QUERY,

    // EXPLAIN
    EXPLAIN_QUERY;
}
