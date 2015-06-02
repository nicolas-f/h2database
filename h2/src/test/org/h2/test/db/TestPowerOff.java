/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.h2.api.ErrorCode;
import org.h2.engine.Database;
import org.h2.jdbc.JdbcConnection;
import org.h2.test.TestBase;
import org.h2.util.JdbcUtils;

import javax.swing.*;

/**
 * Tests simulated power off conditions.
 */
public class TestPowerOff extends TestBase {

    private static final String DB_NAME = "powerOff";
    private String dir, url;

    private int maxPowerOffCount;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws SQLException, InterruptedException {
        if (config.memory) {
            return;
        }
        if (config.big || config.googleAppEngine) {
            dir = getBaseDir();
            url = DB_NAME;
        } else {
            dir = "memFS:";
            url = "memFS:/" + DB_NAME;
        }
        url += ";FILE_LOCK=NO;TRACE_LEVEL_FILE=0";
        testLobCrash();
        testSummaryCrash();
        testCrash();
        testShutdown();
        testMemoryTables();
        testPersistentTables();
        testThreadKill();
        deleteDb(dir, DB_NAME);
    }

    private void testLobCrash() throws SQLException {
        if (config.networked) {
            return;
        }
        deleteDb(dir, DB_NAME);
        Connection conn = getConnection(url);
        Statement stat = conn.createStatement();
        stat.execute("create table test(id identity, data clob)");
        conn.close();
        conn = getConnection(url);
        stat = conn.createStatement();
        stat.execute("set write_delay 0");
        ((JdbcConnection) conn).setPowerOffCount(Integer.MAX_VALUE);
        stat.execute("insert into test values(null, space(11000))");
        int max = Integer.MAX_VALUE - ((JdbcConnection) conn).getPowerOffCount();
        for (int i = 0; i < max + 10; i++) {
            conn.close();
            conn = getConnection(url);
            stat = conn.createStatement();
            stat.execute("insert into test values(null, space(11000))");
            stat.execute("set write_delay 0");
            ((JdbcConnection) conn).setPowerOffCount(i);
            try {
                stat.execute("insert into test values(null, space(11000))");
            } catch (SQLException e) {
                // ignore
            }
            JdbcUtils.closeSilently(conn);
        }
    }

    private void testSummaryCrash() throws SQLException {
        if (config.networked) {
            return;
        }
        deleteDb(dir, DB_NAME);
        Connection conn = getConnection(url);
        Statement stat = conn.createStatement();
        for (int i = 0; i < 10; i++) {
            stat.execute("CREATE TABLE TEST" + i +
                    "(ID INT PRIMARY KEY, NAME VARCHAR)");
            for (int j = 0; j < 10; j++) {
                stat.execute("INSERT INTO TEST" + i +
                        " VALUES(" + j + ", 'Hello')");
            }
        }
        for (int i = 0; i < 10; i += 2) {
            stat.execute("DROP TABLE TEST" + i);
        }
        stat.execute("SET WRITE_DELAY 0");
        stat.execute("CHECKPOINT");
        for (int j = 0; j < 10; j++) {
            stat.execute("INSERT INTO TEST1 VALUES(" + (10 + j) + ", 'World')");
        }
        stat.execute("SHUTDOWN IMMEDIATELY");
        JdbcUtils.closeSilently(conn);
        conn = getConnection(url);
        stat = conn.createStatement();
        for (int i = 1; i < 10; i += 2) {
            ResultSet rs = stat.executeQuery(
                    "SELECT * FROM TEST" + i + " ORDER BY ID");
            for (int j = 0; j < 10; j++) {
                rs.next();
                assertEquals(j, rs.getInt(1));
                assertEquals("Hello", rs.getString(2));
            }
            if (i == 1) {
                for (int j = 0; j < 10; j++) {
                    rs.next();
                    assertEquals(j + 10, rs.getInt(1));
                    assertEquals("World", rs.getString(2));
                }
            }
            assertFalse(rs.next());
        }
        conn.close();
    }

    private void testCrash() throws SQLException {
        if (config.networked) {
            return;
        }
        deleteDb(dir, DB_NAME);
        Random random = new Random(1);
        int repeat = getSize(1, 20);
        for (int i = 0; i < repeat; i++) {
            Connection conn = getConnection(url);
            conn.close();
            conn = getConnection(url);
            Statement stat = conn.createStatement();
            stat.execute("SET WRITE_DELAY 0");
            ((JdbcConnection) conn).setPowerOffCount(random.nextInt(100));
            try {
                stat.execute("DROP TABLE IF EXISTS TEST");
                stat.execute("CREATE TABLE TEST" +
                        "(ID INT PRIMARY KEY, NAME VARCHAR(255))");
                conn.setAutoCommit(false);
                int len = getSize(3, 100);
                for (int j = 0; j < len; j++) {
                    stat.execute("INSERT INTO TEST VALUES(" + j + ", 'Hello')");
                    if (random.nextInt(5) == 0) {
                        conn.commit();
                    }
                    if (random.nextInt(10) == 0) {
                        stat.execute("DROP TABLE IF EXISTS TEST");
                        stat.execute("CREATE TABLE TEST" +
                                "(ID INT PRIMARY KEY, NAME VARCHAR(255))");
                    }
                }
                stat.execute("DROP TABLE IF EXISTS TEST");
                conn.close();
            } catch (SQLException e) {
                if (!e.getSQLState().equals("90098")) {
                    TestBase.logError("power", e);
                }
            }
        }
    }

    private void testShutdown() throws SQLException {
        deleteDb(dir, DB_NAME);
        Connection conn = getConnection(url);
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST" +
                "(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello')");
        stat.execute("SHUTDOWN");
        conn.close();

        conn = getConnection(url);
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST");
        assertTrue(rs.next());
        assertFalse(rs.next());
        conn.close();
    }

    private void testMemoryTables() throws SQLException {
        if (config.networked) {
            return;
        }
        deleteDb(dir, DB_NAME);

        Connection conn = getConnection(url);
        Statement stat = conn.createStatement();
        stat.execute("CREATE MEMORY TABLE TEST" +
                "(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello')");
        stat.execute("CHECKPOINT");
        ((JdbcConnection) conn).setPowerOffCount(1);
        try {
            stat.execute("INSERT INTO TEST VALUES(2, 'Hello')");
            stat.execute("INSERT INTO TEST VALUES(3, 'Hello')");
            stat.execute("CHECKPOINT");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }

        ((JdbcConnection) conn).setPowerOffCount(0);
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
        conn = getConnection(url);
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT COUNT(*) FROM TEST");
        rs.next();
        assertEquals(1, rs.getInt(1));
        conn.close();
    }

    private void testPersistentTables() throws SQLException {
        if (config.networked) {
            return;
        }
        if (config.cipher != null) {
            // this would take too long (setLength uses
            // individual writes, many thousand operations)
            return;
        }
        deleteDb(dir, DB_NAME);

        // ((JdbcConnection)conn).setPowerOffCount(Integer.MAX_VALUE);
        testRun(true);
        int max = maxPowerOffCount;
        trace("max=" + max);
        runTest(0, max, true);
        recoverAndCheckConsistency();
        runTest(0, max, false);
        recoverAndCheckConsistency();
    }

    private void runTest(int min, int max, boolean withConsistencyCheck)
            throws SQLException {
        for (int i = min; i < max; i++) {
            deleteDb(dir, DB_NAME);
            Database.setInitialPowerOffCount(i);
            int expect = testRun(false);
            if (withConsistencyCheck) {
                int got = recoverAndCheckConsistency();
                trace("test " + i + " of " + max + " expect=" + expect + " got=" + got);
            } else {
                trace("test " + i + " of " + max + " expect=" + expect);
            }
        }
        Database.setInitialPowerOffCount(0);
    }

    private int testRun(boolean init) throws SQLException {
        if (init) {
            Database.setInitialPowerOffCount(Integer.MAX_VALUE);
        }
        int state = 0;
        Connection conn = null;
        try {
            conn = getConnection(url);
            Statement stat = conn.createStatement();
            stat.execute("SET WRITE_DELAY 0");
            stat.execute("CREATE TABLE IF NOT EXISTS TEST" +
                    "(ID INT PRIMARY KEY, NAME VARCHAR(255))");
            state = 1;
            conn.setAutoCommit(false);
            stat.execute("INSERT INTO TEST VALUES(1, 'Hello')");
            stat.execute("INSERT INTO TEST VALUES(2, 'World')");
            conn.commit();
            state = 2;
            stat.execute("UPDATE TEST SET NAME='Hallo' WHERE ID=1");
            stat.execute("UPDATE TEST SET NAME='Welt' WHERE ID=2");
            conn.commit();
            state = 3;
            stat.execute("DELETE FROM TEST WHERE ID=1");
            stat.execute("DELETE FROM TEST WHERE ID=2");
            conn.commit();
            state = 1;
            stat.execute("DROP TABLE TEST");
            state = 0;
            if (init) {
                maxPowerOffCount = Integer.MAX_VALUE -
                        ((JdbcConnection) conn).getPowerOffCount();
            }
            conn.close();
        } catch (SQLException e) {
            if (e.getSQLState().equals("" + ErrorCode.DATABASE_IS_CLOSED)) {
                // this is ok
            } else {
                throw e;
            }
        }
        JdbcUtils.closeSilently(conn);
        return state;
    }

    private int recoverAndCheckConsistency() throws SQLException {
        int state;
        Database.setInitialPowerOffCount(0);
        Connection conn = getConnection(url);
        assertEquals(0, ((JdbcConnection) conn).getPowerOffCount());
        Statement stat = conn.createStatement();
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs = meta.getTables(null, null, "TEST", null);
        if (!rs.next()) {
            state = 0;
        } else {
            // table does not exist
            rs = stat.executeQuery("SELECT * FROM TEST ORDER BY ID");
            if (!rs.next()) {
                state = 1;
            } else {
                assertEquals(1, rs.getInt(1));
                String name1 = rs.getString(2);
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
                String name2 = rs.getString(2);
                assertFalse(rs.next());
                if ("Hello".equals(name1)) {
                    assertEquals("World", name2);
                    state = 2;
                } else {
                    assertEquals("Hallo", name1);
                    assertEquals("Welt", name2);
                    state = 3;
                }
            }
        }
        conn.close();
        return state;
    }

    public void testThreadKill() throws SQLException, InterruptedException {
        // Error may be raised only when thread kill a write operation.
        deleteDb("threadkill");

        AtomicBoolean started = new AtomicBoolean(false);
        KilledThread killedThread = new KilledThread(this, started);
        killedThread.execute();
        while(!started.get()) {
            Thread.sleep(5);
        }
        Thread.sleep(150);
        killedThread.cancel(true);
        Thread.sleep(150);

        // Now check if database is corrupted
        Connection conn = getConnection("threadkill");
        try {
            Statement st = conn.createStatement();
            st.execute("SET WRITE_DELAY 0");
            st.execute("DROP TABLE IF EXISTS TESTTABLE");
            st.execute("CREATE TABLE TESTTABLE(id serial, X int) as select null, X from system_range(0, 10);");
            ResultSet rs = st.executeQuery("SELECT XV FROM BIGTABLE");
            assertTrue(rs.next());
            rs.close();
        } finally {
            conn.close();
        }
    }

    /**
     * It says swing but it does not request active display to work.
     * This thread must run a long query that write data on disk.
     */
    private static class KilledThread extends SwingWorker {
        private TestBase testBase;
        private AtomicBoolean startExec;

        public KilledThread(TestBase testBase, AtomicBoolean startExec) {
            this.testBase = testBase;
            this.startExec = startExec;
        }

        @Override
        protected Object doInBackground() throws Exception {
            Connection connection = testBase.getConnection("threadkill");
            try {
                Statement st = connection.createStatement();
                st.execute("SET WRITE_DELAY 0");
                st.execute("DROP TABLE IF EXISTS BIGTABLE");
                startExec.set(true);
                st.execute("CREATE TABLE BIGTABLE AS SELECT (X*X)::varchar xv FROM SYSTEM_RANGE(0, 200000)");
            } finally {
                connection.close();
                startExec.set(false);
            }
            return null;
        }
    }
}
