package database;

import org.h2.jdbcx.JdbcConnectionPool;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import java.util.function.Function;

import static utils.Invariants.mustBeTrue;
import static utils.Invariants.stringMustNotBeNullOrBlank;
import static utils.StringUtils.makeNotNull;

/**
 * This code relates to databases that exist outside of our application.
 * For example, perhaps it refers to the H2 or Postgres databases.
 */
public class ExternalDatabase {

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

    public ExternalDatabase() {
        this(obtainConnectionPool());
    }

    ExternalDatabase(DataSource ds) {
        dataSource = ds;
    }

    private static JdbcConnectionPool obtainConnectionPool() {
        return JdbcConnectionPool.create(
                "jdbc:h2:./out/db/training;AUTO_SERVER=TRUE;MODE=PostgreSQL", "", "");
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

    public long saveNewUser(String userName) {
        stringMustNotBeNullOrBlank(userName);
        return executeInsertTemplate(
                "adds a new user",
                "INSERT INTO foo.users (name) VALUES (?);", userName);
    }



    public void updateUser(long id, String userName) {
        mustBeTrue(id > 0, "The id must be positive");
        stringMustNotBeNullOrBlank(userName);
        executeUpdateTemplate(
                "Updates the borrower's data",
                "UPDATE foo.users SET name = ? WHERE id = ?;", userName, id);
    }


    public Optional<String> getUser(long id) {
        mustBeTrue(id > 0, "The id must be positive");
        Function<ResultSet, Optional<String>> extractor =
                createExtractor(rs -> Optional.of(makeNotNull(rs.getString(1))));

        return runQuery(new SqlData<>(
                "get a user by their id",
                "SELECT name FROM foo.users WHERE id = ?;",
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

    public void createSchema(String schemaName) {
        try (Connection connection = dataSource.getConnection()) {
            var st = connection.createStatement();
            st.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }
    }

    public void deleteSchema(String schemaName) {
        try (Connection connection = dataSource.getConnection()) {
            var st = connection.createStatement();
            st.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        } catch (SQLException ex) {
            throw new SqlRuntimeException(ex);
        }
    }

    /**
     * Call this command to change the schema.  It updates the version of
     * the database, and if you run the command twice it shouldn't do anything
     * the second time, since it will see that the command / version has already
     * been run.
     *
     * Note that since these commands are expected to come solely from the programmer,
     * we are not using any techniques to prevent SQL injection - we are
     * trusting the developer not to attack their own program.
     *
     * @param versionNumber The new version of the database
     * @param description a short description of the change
     * @param sql the SQL commands to run to adjust the schema
     */
    public void updateSchema(int versionNumber, String description, String sql) {
        createSchema("version");
        try (Connection connection = dataSource.getConnection()) {
            var st = connection.createStatement();
            st.execute("""
                CREATE SCHEMA IF NOT EXISTS version;
                
                CREATE TABLE IF NOT EXISTS version.versions(
                    version int PRIMARY KEY,
                    description VARCHAR(100) NOT NULL,
                    run_on TIMESTAMP
                );
                """);
            var result = st.executeQuery("select COUNT(*) = 1 from version.versions where version = " + versionNumber);
            mustBeTrue(result.next(), "If we don't get a returned value, something is seriously wrong");
            if (! result.getBoolean(1)) {
                st.execute(String.format("INSERT INTO version.versions (version, description, run_on) VALUES (%d, '%s', localtimestamp());", versionNumber, description));
                st.execute(sql);
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
