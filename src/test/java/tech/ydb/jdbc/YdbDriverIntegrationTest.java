package tech.ydb.jdbc;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import tech.ydb.jdbc.exception.YdbRetryableException;
import tech.ydb.jdbc.impl.YdbConnectionImpl;
import tech.ydb.table.SchemeClient;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Aleksandr Gorshenin
 */
@DisabledIfSystemProperty(named = YdbIntegrationTest.SKIP_DOCKER_TESTS, matches = YdbIntegrationTest.TRUE)
public class YdbDriverIntegrationTest extends YdbIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(YdbDriverIntegrationTest.class);

    private YdbDriver driver;

    @BeforeEach
    public void beforeEach() {
        driver = new YdbDriver();
    }

    @Test
    void connect() throws SQLException {
        try ( YdbConnection connection = driver.connect(jdbcURl(), new Properties())) {
            Assertions.assertTrue(connection instanceof YdbConnectionImpl);

            LOGGER.info("Session opened: {}", connection.getYdbSession());

            SchemeClient schemeClient = connection.getYdbScheme();
            LOGGER.info("Scheme client: {}", schemeClient);

            Assertions.assertSame(schemeClient, connection.getYdbScheme());
        }
    }

    @Test
    public void connectAndCloseMultipleTimes() throws SQLException {
        try ( YdbConnection ydbConnection = driver.connect(jdbcURl(), new Properties())) {
            ydbConnection.getYdbSession().close();
            ydbConnection.getYdbSession().close(); // multiple close is ok

            TestHelper.assertThrowsMsgLike(YdbRetryableException.class,
                    () -> ydbConnection.createStatement().executeQuery("select 2 + 2"),
                    "Session not found");
        }
    }

    @Test
    public void connectMultipleTimes() throws SQLException {
        YdbDriver.getConnectionsCache().close();
        String url = jdbcURl();
        try ( YdbConnection connection1 = driver.connect(url, new Properties())) {
            LOGGER.info("Session 1 opened: {}", connection1.getYdbSession());
            try ( YdbConnection connection2 = driver.connect(url, new Properties())) {
                LOGGER.info("Session 2 opened: {}", connection2.getYdbSession());

                // Expect only single connection
                Assertions.assertEquals(1, YdbDriver.getConnectionsCache().getConnectionCount());
            }
        }
    }

    @Test
    public void testYdb() throws SQLException {
        try (YdbConnection connection = (YdbConnection) DriverManager.getConnection(jdbcURl())) {
            try {
                connection.createStatement()
                        .execute("--jdbc:SCHEME\n" +
                                "drop table table_sample");
            } catch (SQLException e) {
                //
            }
            connection.createStatement()
                    .execute("--jdbc:SCHEME\n" +
                            "create table table_sample(id Int32, value Utf8, primary key (id))");

            YdbPreparedStatement ps = connection
                    .prepareStatement("" +
                            "declare $p1 as Int32;\n" +
                            "declare $p2 as Utf8;\n" +
                            "upsert into table_sample (id, value) values ($p1, $p2)");
            ps.setInt(1, 1);
            ps.setString(2, "value-1");
            ps.executeUpdate();

            ps.setInt("p1", 2);
            ps.setString("p2", "value-2");
            ps.executeUpdate();

            connection.commit();


            YdbPreparedStatement select = connection
                    .prepareStatement("select count(1) as cnt from table_sample");
            ResultSet rs = select.executeQuery();
            rs.next();
            Assertions.assertEquals(2, rs.getLong("cnt"));

            YdbPreparedStatement psBatch = connection
                    .prepareStatement("" +
                            "declare $values as List<Struct<id:Int32,value:Utf8>>;\n" +
                            "upsert into table_sample select * from as_table($values)");
            psBatch.setInt("id", 3);
            psBatch.setString("value", "value-3");
            psBatch.addBatch();

            psBatch.setInt("id", 4);
            psBatch.setString("value", "value-4");
            psBatch.addBatch();

            psBatch.executeBatch();

            connection.commit();

            rs = select.executeQuery();
            rs.next();
            Assertions.assertEquals(4, rs.getLong("cnt"));
        }
    }
}