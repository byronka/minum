package database;

import org.h2.jdbcx.JdbcConnectionPool;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static utils.Invariants.mustBeTrue;
import static utils.Invariants.stringMustNotBeNullOrBlank;
import static utils.StringUtils.makeNotNull;

public class Database {

    /*
     * ==========================================================
     * ==========================================================
     *
     *  Class construction - details of making this class
     *
     * ==========================================================
     * ==========================================================
     */

    private final DataSource dataSource;

    public Database() {
        this(obtainConnectionPool());
    }

    Database(DataSource ds) {
        dataSource = ds;
    }

    private static JdbcConnectionPool obtainConnectionPool() {
        return JdbcConnectionPool.create(
                "jdbc:h2:./build/db/training;AUTO_SERVER=TRUE;MODE=PostgreSQL", "", "");
    }

    /*
     * ==========================================================
     * ==========================================================
     *
     *  Micro ORM
     *    Demo has a simplistic Object Relational Mapper (ORM)
     *    implementation.  These are the methods that comprise
     *    the mechanisms for that.
     *
     *    In comparison, a gargantuan project like Hibernate
     *    would consist of a heckuva-lot-more than this.  That's
     *    why this one is termed, "micro"
     *
     * ==========================================================
     * ==========================================================
     */


    /**
     * This command provides a template to execute updates (including inserts) on the database
     */
    void executeUpdateTemplate(String description, String preparedStatement, Object ... params) {
        final SqlData<Object> sqlData = new SqlData<>(description, preparedStatement, params);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement st = prepareStatementWithKeys(sqlData, connection)) {
                executeUpdateOnPreparedStatement(sqlData, st);
            }
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }
    }


    public long executeInsertTemplate(
            String description,
            String preparedStatement,
            Object ... params) {
        final SqlData<Object> sqlData = new SqlData<>(description, preparedStatement, params);
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement st = prepareStatementWithKeys(sqlData, connection)) {
                return executeInsertOnPreparedStatement(sqlData, st);
            }
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }
    }


    <T> long executeInsertOnPreparedStatement(SqlData<T> sqlData, PreparedStatement st) throws SQLException {
        sqlData.applyParametersToPreparedStatement(st);
        st.executeUpdate();
        try (ResultSet generatedKeys = st.getGeneratedKeys()) {
            long newId;
            if (generatedKeys.next()) {
                newId = generatedKeys.getLong(1);
                assert (newId > 0);
            } else {
                throw new SqlRuntimeException("failed Sql.  Description: " + sqlData.description + " SQL code: " + sqlData.preparedStatement);
            }
            return newId;
        }
    }


    private <T> void executeUpdateOnPreparedStatement(SqlData<T> sqlData, PreparedStatement st) throws SQLException {
        sqlData.applyParametersToPreparedStatement(st);
        st.executeUpdate();
    }


    /**
     * A helper method.  Simply creates a prepared statement that
     * always returns the generated keys from the database, like
     * when you insert a new row of data in a table with auto-generating primary key.
     *
     * @param sqlData    see {@link SqlData}
     * @param connection a typical {@link Connection}
     */
    private <T> PreparedStatement prepareStatementWithKeys(SqlData<T> sqlData, Connection connection) throws SQLException {
        return connection.prepareStatement(
                sqlData.preparedStatement,
                Statement.RETURN_GENERATED_KEYS);
    }


    <R> Optional<R> runQuery(SqlData<R> sqlData) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement st =
                         connection.prepareStatement(sqlData.preparedStatement)) {
                sqlData.applyParametersToPreparedStatement(st);
                try (ResultSet resultSet = st.executeQuery()) {
                    return sqlData.extractor.apply(resultSet);
                }
            }
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }

    }


    /**
     * This is an interface to a wrapper around {@link Function} so we can catch exceptions
     * in the generic function.
     *
     * @param <R> The return type
     * @param <E> The type of the exception
     */
    @FunctionalInterface
    private interface ThrowingFunction<R, E extends Exception> {
        R apply(ResultSet resultSet) throws E;
    }


    /**
     * This wraps the throwing function, so that we are not forced to
     * catch an exception in our ordinary code - it's caught and handled
     * here.
     * @param throwingFunction a lambda that throws a checked exception we have to handle.
     *                         specifically in this case that's a SqlRuntimeException
     * @param <R> the type of value returned
     * @return returns a function that runs and returns a function wrapped with an exception handler
     */
    static <R> Function<ResultSet, R> throwingFunctionWrapper(
            ThrowingFunction<R, Exception> throwingFunction) {

        return resultSet -> {
            try {
                return throwingFunction.apply(resultSet);
            } catch (Exception ex) {
                throw new SqlRuntimeException(ex);
            }
        };
    }


    /**
     * Accepts a function to extract data from a {@link ResultSet} and
     * removes some boilerplate with handling its response.
     * Works in conjunction with {@link #throwingFunctionWrapper}
     * @param extractorFunction a function that extracts data from a {@link ResultSet}
     * @param <T> the type of data we'll retrieve from the {@link ResultSet}
     * @return either the type of data wrapped with an optional, or {@link Optional#empty}
     */
    private <T> Function<ResultSet, Optional<T>> createExtractor(
            ThrowingFunction<Optional<T>, Exception> extractorFunction) {
        return throwingFunctionWrapper(rs -> {
            if (rs.next()) {
                return extractorFunction.apply(rs);
            } else {
                return Optional.empty();
            }
        });
    }


    /*
     * ==========================================================
     * ==========================================================
     *
     *  Business functions
     *     loaning out books, registering users, etc
     *
     * ==========================================================
     * ==========================================================
     */


    // Library functions

    public long saveNewBorrower(String borrowerName) {
        stringMustNotBeNullOrBlank(borrowerName);
        return executeInsertTemplate(
                "adds a new library borrower",
                "INSERT INTO library.borrower (name) VALUES (?);", borrowerName);
    }



    public void updateBorrower(long id, String borrowerName) {
        mustBeTrue(id > 0, "The id must be positive");
        stringMustNotBeNullOrBlank(borrowerName);
        executeUpdateTemplate(
                "Updates the borrower's data",
                "UPDATE library.borrower SET name = ? WHERE id = ?;", borrowerName, id);
    }


    public Optional<String> getBorrowerName(long id) {
        mustBeTrue(id > 0, "The id must be positive");
        Function<ResultSet, Optional<String>> extractor =
                createExtractor(rs -> Optional.of(makeNotNull(rs.getString(1))));

        return runQuery(new SqlData<>(
                "get a borrower's name by their id",
                "SELECT name FROM library.borrower WHERE id = ?;",
                extractor, id));
    }



    /*
     * ==========================================================
     * ==========================================================
     *
     *  General utility methods
     *
     * ==========================================================
     * ==========================================================
     */

    public boolean isEmpty() {
        return this.dataSource.getClass().equals(EmptyDataSource.class);
    }

    public void runBackup(String backupFileName) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement st = connection.prepareStatement("SCRIPT TO ?")) {
                st.setString(1, backupFileName);
                st.execute();
            }
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }
    }

    public void runRestore(String backupFileName) {
        String dbScriptsDirectory="src/integration_test/resources/db_sample_files/";
        String fullPathToBackup = dbScriptsDirectory + backupFileName;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement st = connection.prepareStatement(
                    "DROP SCHEMA IF EXISTS ADMINISTRATIVE CASCADE;" +
                            "DROP SCHEMA IF EXISTS AUTH CASCADE;" +
                            "DROP SCHEMA IF EXISTS LIBRARY CASCADE;")) {
                st.execute();
            }
            try (PreparedStatement st = connection.prepareStatement("RUNSCRIPT FROM ?")) {
                st.setString(1, fullPathToBackup);
                st.execute();
            }
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }
    }


    /*
     * ==========================================================
     * ==========================================================
     *
     *  Database migration code - using FlywayDb
     *
     * ==========================================================
     * ==========================================================
     */

    public void cleanDatabase_new() {

    }


    public void cleanAndMigrateDatabase() {
        cleanDatabase();
        migrateDatabase();
    }

    public void cleanDatabase() {
        // TODO
    }

    public void migrateDatabase() {
        // TODO
    }
}
