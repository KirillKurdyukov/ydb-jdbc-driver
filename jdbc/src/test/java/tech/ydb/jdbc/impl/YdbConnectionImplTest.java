package tech.ydb.jdbc.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.ydb.jdbc.YdbConnection;
import tech.ydb.jdbc.YdbConst;
import tech.ydb.jdbc.YdbDatabaseMetaData;
import tech.ydb.jdbc.impl.helper.ExceptionAssert;
import tech.ydb.jdbc.impl.helper.JdbcConnectionExtention;
import tech.ydb.jdbc.impl.helper.SqlQueries;
import tech.ydb.jdbc.impl.helper.TableAssert;
import tech.ydb.test.junit5.YdbHelperExtension;

public class YdbConnectionImplTest {
    @RegisterExtension
    private static final YdbHelperExtension ydb = new YdbHelperExtension();

    @RegisterExtension
    private static final JdbcConnectionExtention jdbc = new JdbcConnectionExtention(ydb);

    private static final SqlQueries QUERIES = new SqlQueries("ydb_connection_test");
    private static final String SELECT_2_2 = "select 2 + 2";
    private static final String SIMPLE_UPSERT = QUERIES
            .withTableName("upsert into #tableName (key, c_Text) values (1, '2')");

    @BeforeAll
    public static void createTable() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement()) {
            // create simple tables
            statement.execute(QUERIES.createTableSQL());
        }
    }

    @AfterAll
    public static void dropTable() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement();) {
            // create test table
            statement.execute(QUERIES.dropTableSQL());
        }
    }

    @BeforeEach
    public void checkTransactionState() throws SQLException {
        Assertions.assertNull(getTxId(jdbc.connection()), "Transaction must be empty before test");
    }

    @AfterEach
    public void checkTableIsEmpty() throws SQLException {
        if (jdbc.connection().isClosed()) {
            return;
        }

        try (Statement statement = jdbc.connection().createStatement()) {
            try (ResultSet result = statement.executeQuery(QUERIES.selectAllSQL())) {
                Assertions.assertFalse(result.next(), "Table must be empty after test");
            }
        }
        jdbc.connection().close();
    }

    private void cleanTable() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(QUERIES.deleteAllSQL());
        }
    }

    private String getTxId(Connection connection) throws SQLException {
        return connection.unwrap(YdbConnection.class).getYdbTxId();
    }

    @Test
    public void connectionUnwrapTest() throws SQLException {
        Assertions.assertTrue(jdbc.connection().isWrapperFor(YdbConnection.class));
        Assertions.assertSame(jdbc.connection(), jdbc.connection().unwrap(YdbConnection.class));

        Assertions.assertFalse(jdbc.connection().isWrapperFor(YdbDatabaseMetaData.class));

        SQLException ex = Assertions.assertThrows(SQLException.class,
                () -> jdbc.connection().unwrap(YdbDatabaseMetaData.class));
        Assertions.assertEquals("Cannot unwrap to interface tech.ydb.jdbc.YdbDatabaseMetaData", ex.getMessage());
    }

    @Test
    public void createStatement() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement()) {
            TableAssert.assertSelectInt(4, statement.executeQuery("select 1 + 3"));
        }
        try (Statement statement = jdbc.connection().createStatement(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            TableAssert.assertSelectInt(5, statement.executeQuery("select 2 + 3"));
        }
        try (Statement statement = jdbc.connection().createStatement(
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            TableAssert.assertSelectInt(3, statement.executeQuery("select 2 + 1"));
        }
        try (Statement statement = jdbc.connection().createStatement(
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
            TableAssert.assertSelectInt(2, statement.executeQuery("select 1 + 1"));
        }
    }

    @Test
    public void createStatementInvalid() {
        ExceptionAssert.sqlFeatureNotSupported(
                "resultSetType must be ResultSet.TYPE_FORWARD_ONLY or ResultSet.TYPE_SCROLL_INSENSITIVE",
                () -> jdbc.connection().createStatement(
                        ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT
                )
        );

        ExceptionAssert.sqlFeatureNotSupported(
                "resultSetConcurrency must be ResultSet.CONCUR_READ_ONLY",
                () -> jdbc.connection().createStatement(
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE, ResultSet.HOLD_CURSORS_OVER_COMMIT
                )
        );

        ExceptionAssert.sqlFeatureNotSupported(
                "resultSetHoldability must be ResultSet.HOLD_CURSORS_OVER_COMMIT",
                () -> jdbc.connection().createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT
                )
        );
    }

    @Test
    public void prepareStatement() throws SQLException {
        try (PreparedStatement statement = jdbc.connection().prepareStatement("select 1 + 3")) {
            TableAssert.assertSelectInt(4, statement.executeQuery());
        }
        try (PreparedStatement statement = jdbc.connection().prepareStatement("select 2 + 3",
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            TableAssert.assertSelectInt(5, statement.executeQuery());
        }
        try (PreparedStatement statement = jdbc.connection().prepareStatement("select 2 + 1",
                ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
            TableAssert.assertSelectInt(3, statement.executeQuery());
        }
        try (PreparedStatement statement = jdbc.connection().prepareStatement("select 1 + 1",
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT)) {
            TableAssert.assertSelectInt(2, statement.executeQuery());
        }
    }

    @Test
    public void prepareStatementInvalid() throws SQLException {
        ExceptionAssert.sqlFeatureNotSupported("Auto-generated keys are not supported",
                () -> jdbc.connection().prepareStatement(SELECT_2_2, new int[] {})
        );

        ExceptionAssert.sqlFeatureNotSupported("Auto-generated keys are not supported",
                () -> jdbc.connection().prepareStatement(SELECT_2_2, new String[] {})
        );

        ExceptionAssert.sqlFeatureNotSupported("Auto-generated keys are not supported",
                () -> jdbc.connection().prepareStatement(SELECT_2_2, Statement.RETURN_GENERATED_KEYS)
        );

        ExceptionAssert.sqlFeatureNotSupported(
                "resultSetType must be ResultSet.TYPE_FORWARD_ONLY or ResultSet.TYPE_SCROLL_INSENSITIVE",
                () -> jdbc.connection().prepareStatement(SELECT_2_2,
                        ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT
                )
        );

        ExceptionAssert.sqlFeatureNotSupported(
                "resultSetConcurrency must be ResultSet.CONCUR_READ_ONLY",
                () -> jdbc.connection().prepareStatement(SELECT_2_2,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE, ResultSet.HOLD_CURSORS_OVER_COMMIT
                )
        );

        ExceptionAssert.sqlFeatureNotSupported(
                "resultSetHoldability must be ResultSet.HOLD_CURSORS_OVER_COMMIT",
                () -> jdbc.connection().prepareStatement(SELECT_2_2,
                        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT
                )
        );
    }

    @Test
    public void prepareCallNotSupported() {
        ExceptionAssert.sqlFeatureNotSupported("Prepared calls are not supported",
                () -> jdbc.connection().prepareCall(SELECT_2_2)
        );

        ExceptionAssert.sqlFeatureNotSupported("Prepared calls are not supported",
                () -> jdbc.connection().prepareCall(SELECT_2_2,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        );

        ExceptionAssert.sqlFeatureNotSupported("Prepared calls are not supported",
                () -> jdbc.connection().prepareCall(SELECT_2_2,
                        ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT)
        );
    }

    @Test
    public void nativeSQL() throws SQLException {
        String nativeSQL = jdbc.connection().nativeSQL("select ? + ?");
        Assertions.assertEquals("-- DECLARE 2 PARAMETERS\nselect $jp1 + $jp2", nativeSQL);
    }

    @Test
    public void clientInfo() throws SQLException {
        Assertions.assertEquals(new Properties(), jdbc.connection().getClientInfo());

        Properties properties = new Properties();
        properties.setProperty("key", "value");
        jdbc.connection().setClientInfo(properties);

        Assertions.assertEquals(new Properties(), jdbc.connection().getClientInfo());
        jdbc.connection().setClientInfo("key", "value");

        Assertions.assertEquals(new Properties(), jdbc.connection().getClientInfo());
        Assertions.assertNull(jdbc.connection().getClientInfo("key"));
    }

    @Test
    public void invalidSQLCancelTransaction() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(SELECT_2_2);
            String txId = getTxId(jdbc.connection());
            Assertions.assertNotNull(txId);

            ExceptionAssert.ydbException("Column reference 'x' (S_ERROR)", () -> statement.execute("select 2 + x"));

            statement.execute(SELECT_2_2);
            Assertions.assertNotNull(getTxId(jdbc.connection()));
            Assertions.assertNotEquals(txId, getTxId(jdbc.connection()));
        } finally {
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void autoCommit() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(SELECT_2_2);
            String txId = getTxId(jdbc.connection());
            Assertions.assertNotNull(txId);

            Assertions.assertFalse(jdbc.connection().getAutoCommit());

            jdbc.connection().setAutoCommit(false);
            Assertions.assertFalse(jdbc.connection().getAutoCommit());
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            jdbc.connection().setAutoCommit(true);
            Assertions.assertTrue(jdbc.connection().getAutoCommit());
            Assertions.assertNull(getTxId(jdbc.connection()));

            statement.execute(SELECT_2_2);
            Assertions.assertNull(getTxId(jdbc.connection()));

            statement.execute(SELECT_2_2);
            Assertions.assertNull(getTxId(jdbc.connection()));

            jdbc.connection().setAutoCommit(false);
            Assertions.assertFalse(jdbc.connection().getAutoCommit());
            Assertions.assertNull(getTxId(jdbc.connection()));
        } finally {
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void commit() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            Assertions.assertTrue(statement.execute(SELECT_2_2));
            String txId = getTxId(jdbc.connection());
            Assertions.assertNotNull(txId);

            Assertions.assertTrue(statement.execute(SELECT_2_2));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertTrue(statement.execute(QUERIES.selectAllSQL()));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertFalse(statement.execute(SIMPLE_UPSERT));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertTrue(statement.execute(SELECT_2_2));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            jdbc.connection().commit();
            Assertions.assertNull(getTxId(jdbc.connection()));

            jdbc.connection().commit(); // does nothing
            Assertions.assertNull(getTxId(jdbc.connection()));

            try (ResultSet result = statement.executeQuery(QUERIES.selectAllSQL())) {
                Assertions.assertTrue(result.next());
            }
        } finally {
            cleanTable();
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void schemeQueryInTx() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            Assertions.assertTrue(statement.execute(SELECT_2_2));
            String txId = getTxId(jdbc.connection());
            Assertions.assertNotNull(txId);

            Assertions.assertTrue(statement.execute(SELECT_2_2));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertTrue(statement.execute(QUERIES.selectAllSQL()));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertFalse(statement.execute(SIMPLE_UPSERT));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertTrue(statement.execute(SELECT_2_2));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            ExceptionAssert.sqlException(YdbConst.SCHEME_QUERY_INSIDE_TRANSACTION,
                    () -> statement.execute("CREATE TABLE tmp_table (id Int32, primary key (id))"));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            ExceptionAssert.sqlException(YdbConst.SCHEME_QUERY_INSIDE_TRANSACTION,
                    () -> statement.execute("DROP TABLE tmp_table"));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            try (ResultSet result = statement.executeQuery(QUERIES.selectAllSQL())) {
                Assertions.assertTrue(result.next());
            }
        } finally {
            cleanTable();
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void schemeQueryWithShadowCommit() throws SQLException {
        try (Connection connection = jdbc.createCustomConnection("schemeQueryTxMode", "SHADOW_COMMIT")) {
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {
                Assertions.assertTrue(statement.execute(SELECT_2_2));
                String txId = getTxId(connection);
                Assertions.assertNotNull(txId);

                Assertions.assertTrue(statement.execute(SELECT_2_2));
                Assertions.assertEquals(txId, getTxId(connection));

                Assertions.assertTrue(statement.execute(QUERIES.selectAllSQL()));
                Assertions.assertEquals(txId, getTxId(connection));

                Assertions.assertFalse(statement.execute(SIMPLE_UPSERT));
                Assertions.assertEquals(txId, getTxId(connection));

                Assertions.assertTrue(statement.execute(SELECT_2_2));
                Assertions.assertEquals(txId, getTxId(connection));

                // DDL  - equals to commit
                statement.execute("CREATE TABLE tmp_table (id Int32, primary key (id))");
                Assertions.assertNull(getTxId(connection));

                statement.execute("DROP TABLE tmp_table");
                Assertions.assertNull(getTxId(connection));

                try (ResultSet result = statement.executeQuery(QUERIES.selectAllSQL())) {
                    Assertions.assertTrue(result.next());
                }
            }
        } finally {
            cleanTable();
        }
    }

    @Test
    public void schemeQueryInFakeTx() throws SQLException {
        try (Connection connection = jdbc.createCustomConnection("schemeQueryTxMode", "FAKE_TX")) {
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {
                Assertions.assertTrue(statement.execute(SELECT_2_2));
                String txId = getTxId(connection);
                Assertions.assertNotNull(txId);

                Assertions.assertTrue(statement.execute(SELECT_2_2));
                Assertions.assertEquals(txId, getTxId(connection));

                Assertions.assertTrue(statement.execute(QUERIES.selectAllSQL()));
                Assertions.assertEquals(txId, getTxId(connection));

                Assertions.assertFalse(statement.execute(SIMPLE_UPSERT));
                Assertions.assertEquals(txId, getTxId(connection));

                Assertions.assertTrue(statement.execute(SELECT_2_2));
                Assertions.assertEquals(txId, getTxId(connection));

                // FAKE TX - DDL will be executed outside current transaction
                statement.execute("CREATE TABLE tmp_table (id Int32, primary key (id))");
                Assertions.assertEquals(txId, getTxId(connection));

                statement.execute("DROP TABLE tmp_table");
                Assertions.assertEquals(txId, getTxId(connection));

                try (ResultSet result = statement.executeQuery(QUERIES.selectAllSQL())) {
                    Assertions.assertTrue(result.next());
                }
            }
        } finally {
            cleanTable();
        }
    }

    @Test
    public void rollback() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            Assertions.assertTrue(statement.execute(SELECT_2_2));
            String txId = getTxId(jdbc.connection());
            Assertions.assertNotNull(txId);

            Assertions.assertTrue(statement.execute(SELECT_2_2));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertTrue(statement.execute(QUERIES.selectAllSQL()));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertFalse(statement.execute(SIMPLE_UPSERT));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            Assertions.assertTrue(statement.execute(SELECT_2_2));
            Assertions.assertEquals(txId, getTxId(jdbc.connection()));

            jdbc.connection().rollback();
            Assertions.assertNull(getTxId(jdbc.connection()));

            jdbc.connection().rollback(); // does nothing
            Assertions.assertNull(getTxId(jdbc.connection()));

            try (ResultSet result = statement.executeQuery(QUERIES.selectAllSQL())) {
                Assertions.assertFalse(result.next());
            }
        } finally {
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void commitInvalidTx() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(SIMPLE_UPSERT);
            statement.execute(SIMPLE_UPSERT);

            ExceptionAssert.ydbException("Member not found: key2. Did you mean key?",
                    () -> statement.executeQuery(QUERIES.wrongSelectSQL()));

            Assertions.assertNull(getTxId(jdbc.connection()));

            jdbc.connection().commit(); // Nothing to commit, transaction was rolled back already
            Assertions.assertNull(getTxId(jdbc.connection()));
        } finally {
            cleanTable();
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void rollbackInvalidTx() throws SQLException {
        jdbc.connection().setAutoCommit(false);
        try (Statement statement = jdbc.connection().createStatement()) {
            ResultSet result = statement.executeQuery(QUERIES.selectAllSQL());
            Assertions.assertFalse(result.next());

            statement.execute(SIMPLE_UPSERT);

            ExceptionAssert.ydbException("Member not found: key2. Did you mean key?",
                    () -> statement.executeQuery(QUERIES.wrongSelectSQL()));

            Assertions.assertNull(getTxId(jdbc.connection()));
            jdbc.connection().rollback();
            Assertions.assertNull(getTxId(jdbc.connection()));
        } finally {
            cleanTable();
            jdbc.connection().setAutoCommit(true);
        }
    }

    @Test
    public void closeTest() throws SQLException {
        Assertions.assertFalse(jdbc.connection().isClosed());
        jdbc.connection().close();
        Assertions.assertTrue(jdbc.connection().isClosed());
        jdbc.connection().close(); // no effect
    }

    @Test
    public void getMetaData() throws SQLException {
        DatabaseMetaData metaData = jdbc.connection().getMetaData();
        Assertions.assertNotNull(metaData);
    }

    @Test
    public void readOnly() throws SQLException {
        Assertions.assertFalse(jdbc.connection().isReadOnly());
        Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE, jdbc.connection().getTransactionIsolation());

        jdbc.connection().setReadOnly(true);

        Assertions.assertTrue(jdbc.connection().isReadOnly());
        Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE, jdbc.connection().getTransactionIsolation());

        jdbc.connection().setReadOnly(false);
        Assertions.assertFalse(jdbc.connection().isReadOnly());
        Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE, jdbc.connection().getTransactionIsolation());
    }

    @Test
    public void catalog() throws SQLException {
        Assertions.assertNull(jdbc.connection().getCatalog());
        jdbc.connection().setCatalog("any"); // catalogs are not supported
        Assertions.assertNull(jdbc.connection().getCatalog());
    }

    @Test
    public void schema() throws SQLException {
        Assertions.assertNull(jdbc.connection().getSchema());
        jdbc.connection().setSchema("test"); // schemas are not supported
        Assertions.assertNull(jdbc.connection().getSchema());
    }

    @ParameterizedTest(name = "Check supported isolation level {0}")
    @ValueSource(ints = {
        Connection.TRANSACTION_SERIALIZABLE,    // 8
        YdbConst.ONLINE_CONSISTENT_READ_ONLY,   // 16
        YdbConst.ONLINE_INCONSISTENT_READ_ONLY, // 17
        YdbConst.STALE_CONSISTENT_READ_ONLY     // 32
    })
    public void supportedTransactionIsolations(int level) throws SQLException {
        jdbc.connection().setTransactionIsolation(level);
        Assertions.assertEquals(level, jdbc.connection().getTransactionIsolation());

        try (Statement statement = jdbc.connection().createStatement()) {
            TableAssert.assertSelectInt(4, statement.executeQuery(SELECT_2_2));
        }
    }

    @ParameterizedTest(name = "Check supported isolation level {0}")
    @ValueSource(ints = { 0, 1, 2, 3, 4, 5, 6, 7, /*8,*/ 9, 10 })
    public void unsupportedTransactionIsolations(int level) throws SQLException {
        ExceptionAssert.sqlException("Unsupported transaction level: " + level,
                () -> jdbc.connection().setTransactionIsolation(level)
        );
    }

    @Test
    public void clearWarnings() throws SQLException {
        // TODO: generate warnings
        Assertions.assertNull(jdbc.connection().getWarnings());
        jdbc.connection().clearWarnings();
        Assertions.assertNull(jdbc.connection().getWarnings());
    }

    @Test
    public void typeMap() throws SQLException {
        Assertions.assertTrue(jdbc.connection().getTypeMap().isEmpty());

        Map<String, Class<?>> newMap = new HashMap<>();
        newMap.put("type1", String.class);
        jdbc.connection().setTypeMap(newMap);

        // not implemented
        Assertions.assertTrue(jdbc.connection().getTypeMap().isEmpty());
    }

    @Test
    public void holdability() throws SQLException {
        Assertions.assertEquals(ResultSet.HOLD_CURSORS_OVER_COMMIT, jdbc.connection().getHoldability());
        jdbc.connection().setHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT);

        ExceptionAssert.sqlFeatureNotSupported("resultSetHoldability must be ResultSet.HOLD_CURSORS_OVER_COMMIT",
                () -> jdbc.connection().setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT));
    }

    @Test
    public void networkTimeout() throws SQLException {
        Assertions.assertEquals(0, jdbc.connection().getNetworkTimeout());
        ExceptionAssert.sqlFeatureNotSupported("Set network timeout is not supported yet",
                () -> jdbc.connection().setNetworkTimeout(null, 1));
    }

    @Test
    public void savepoints() {
        ExceptionAssert.sqlFeatureNotSupported("Savepoints are not supported", () -> jdbc.connection().setSavepoint());
        ExceptionAssert.sqlFeatureNotSupported("Savepoints are not supported", () -> jdbc.connection().setSavepoint("name"));
        ExceptionAssert.sqlFeatureNotSupported("Savepoints are not supported", () -> jdbc.connection().releaseSavepoint(null));
    }

    @Test
    public void unsupportedTypes() {
        ExceptionAssert.sqlFeatureNotSupported("Clobs are not supported", () -> jdbc.connection().createClob());
        ExceptionAssert.sqlFeatureNotSupported("Blobs are not supported", () -> jdbc.connection().createBlob());
        ExceptionAssert.sqlFeatureNotSupported("NClobs are not supported", () -> jdbc.connection().createNClob());
        ExceptionAssert.sqlFeatureNotSupported("SQLXMLs are not supported", () -> jdbc.connection().createSQLXML());
        ExceptionAssert.sqlFeatureNotSupported("Arrays are not supported",
                () -> jdbc.connection().createArrayOf("type", new Object[] { })
        );
        ExceptionAssert.sqlFeatureNotSupported("Structs are not supported",
                () -> jdbc.connection().createStruct("type", new Object[] { })
        );
    }

    @Test
    public void abort() {
        ExceptionAssert.sqlFeatureNotSupported("Abort operation is not supported yet",  () -> jdbc.connection().abort(null));
    }

    @Test
    public void testDDLInsideTransaction() throws SQLException {
        String createTempTable = QUERIES.withTableName(
                "create table temp_#tableName(id Int32, value Int32, primary key(id))"
        );
        String dropTempTable = QUERIES.withTableName("drop table temp_#tableName");

        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(SIMPLE_UPSERT);

            // call autocommit
            statement.execute(createTempTable);

            statement.executeQuery(QUERIES.selectAllSQL());
            statement.execute(QUERIES.deleteAllSQL());

            // call autocommit
            statement.execute(dropTempTable);
        }
    }

    @Test
    public void testWarningsInQuery() throws SQLException {
        String createTempTable = QUERIES.withTableName(
                "CREATE TABLE #tableName_idx(id Int32, value Int32, PRIMARY KEY(id), INDEX idx_value GLOBAL ON(value))"
        );
        String dropTempTable = QUERIES.withTableName("DROP TABLE #tableName_idx");

        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(createTempTable);

            try {
                String query = QUERIES.withTableName("SELECT * FROM #tableName_idx VIEW idx_value WHERE id = 1;");
                try (ResultSet rs = statement.executeQuery(query)) {
                    Assertions.assertFalse(rs.next());

                    SQLWarning warnings = statement.getWarnings();
                    Assertions.assertNotNull(warnings);

                    Assertions.assertEquals("#1060 Execution (S_WARNING)\n  "
                            + "1:1 - 1:1: #2503 Given predicate is not suitable for used index: idx_value (S_WARNING)",
                            warnings.getMessage());
                    Assertions.assertNull(warnings.getNextWarning());
                }
            } finally {
                statement.execute(dropTempTable);
            }
        }
    }

    @Test
    public void testLiteralQuery() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement()) {
            String query = "SELECT '1' AS p1, 123 AS p2, NULL AS p3";
            try (ResultSet rs = statement.executeQuery(query)) {
                Assertions.assertTrue(rs.next());

                Assertions.assertEquals("1", rs.getString("p1"));
                Assertions.assertArrayEquals(new byte[] { '1' }, rs.getBytes("p1"));
                Assertions.assertFalse(rs.wasNull());

                Assertions.assertEquals(123, rs.getByte("p2"));
                Assertions.assertEquals(123, rs.getShort("p2"));
                Assertions.assertEquals(123, rs.getInt("p2"));
                Assertions.assertEquals(123, rs.getLong("p2"));
                Assertions.assertFalse(rs.wasNull());

                Assertions.assertNull(rs.getObject("p3"));
                Assertions.assertNull(rs.getString("p3"));
                Assertions.assertNull(rs.getBytes("p3"));

                Assertions.assertEquals(0, rs.getByte("p3"));
                Assertions.assertEquals(0, rs.getShort("p3"));
                Assertions.assertEquals(0, rs.getInt("p3"));
                Assertions.assertEquals(0, rs.getLong("p3"));

                Assertions.assertTrue(rs.wasNull());

                Assertions.assertFalse(rs.next());
            }
        }
    }

    @Test
    public void testAnsiLexer() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement()) {
            ResultSet rs = statement.executeQuery("--!ansi_lexer\n" +
                    "select 'string value' as \"name with space\"");
            Assertions.assertTrue(rs.next());
            Assertions.assertEquals("string value", rs.getString("name with space"));
        }
    }

    @Test
    public void testAnsiLexerForIdea() throws SQLException {
        try (Statement statement = jdbc.connection().createStatement()) {
            statement.execute(SIMPLE_UPSERT);
            jdbc.connection().commit();

            // TODO: Must be "unit_1" t, see YQL-12618
            try (ResultSet rs = statement.executeQuery("--!ansi_lexer\n" +
                    QUERIES.withTableName("select t.c_Text from #tableName as t where t.key = 1"))) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals("2", rs.getString("c_Text"));
            }

            try (ResultSet rs = statement.executeQuery("--!ansi_lexer\n" +
                    QUERIES.withTableName("select t.c_Text from #tableName t where t.key = 1"))) {
                Assertions.assertTrue(rs.next());
                Assertions.assertEquals("2", rs.getString("c_Text"));
            }
        } finally {
            cleanTable();
        }
    }

    @DisplayName("Check unsupported by storage type {arguments}")
    @ParameterizedTest()
    @ValueSource(strings = {
        "TzDate",
        "TzDatetime",
        "TzTimestamp",
    })
    public void testUnsupportedByStorageTableTypes(String type) throws SQLException {
        String tableName = "unsupported_" + type;
        String sql = "create table " + tableName + " (key Int32, payload " + type + ", primary key(key))";

        try (Statement statement = jdbc.connection().createStatement()) {
            ExceptionAssert.ydbException("is not supported by storage", () -> statement.execute(sql));
        }
    }

    @ParameterizedTest(name = "Check unsupported complex type {0}")
    @ValueSource(strings = {
        "List<Int32>",
        "Struct<name:Int32>",
        "Tuple<Int32>",
        "Dict<Text,Int32>",
    })
    public void testUnsupportedComplexTypes(String type) throws SQLException {
        String tableName = "unsupported_" + type.replaceAll("[^a-zA-Z0-9]", "");
        String sql = "create table " + tableName + " (key Int32, payload " + type + ", primary key(key))";

        try (Statement statement = jdbc.connection().createStatement()) {
            ExceptionAssert.ydbException("Invalid type for column: payload.",
                    () -> statement.execute(sql));
        }
    }
}
