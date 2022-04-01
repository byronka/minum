package database.externaldb;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * This class encapsulates some actions related to
 * injecting data into a prepared SQL statement, so that
 * we are able to summarize what we want done without
 * all the annoying boilerplate.
 * <p>
 * Was necessary to suppress the nullness warnings on this class due to its
 * use of generics.
 * The generic R is the result type - if we ask for a string, R would be a String.
 * On the other hand if R might be a compound type, like Employee.
 */
final class SqlData<R> {

    /**
     * A summary description of what this SQL is doing.
     */
    final String description;

    /**
     * This is the String text of the SQL prepared statement.  We're using PostgreSQL,
     * see https://jdbc.postgresql.org/documentation/81/server-prepare.html
     */
    final String preparedStatement;

    /**
     * The data that we will inject to the SQL statement.
     */
    private final List<ParameterObject<?>> params;

    /**
     * A generic function - takes a {@link ResultSet} straight from the database,
     * and then carries out actions on it, per the user's intentions, to convert it
     * into something of type {@link R}.
     */
    public final Function<ResultSet, Optional<R>> extractor;

    SqlData(String description, String preparedStatement, Object ... params) {
        this(description, preparedStatement, (resultSet -> Optional.empty()), params);
    }

    /**
     * Creates an object that is used to avoid some of the boilerplate
     * in running database CRUD operations.
     *
     * @param description       A string that describes in plain English what this SQL does.
     * @param preparedStatement The SQL that is run on the database
     * @param extractor         see {@link #extractor} a function that is run to convert the returned {@link ResultSet} into whatever we want
     */
    SqlData(String description, String preparedStatement, Function<ResultSet, Optional<R>> extractor, Object ... params) {
        this.description = description;
        this.preparedStatement = preparedStatement;
        this.params = new ArrayList<>();
        if (params.length > 0) {
            generateParams(params);
        }
        this.extractor = extractor;
    }

    /**
     * Loads the parameters for this SQL
     */
    private void generateParams(Object[] params) {
        for (Object param:params) {
            addParameter(param, param.getClass());
        }
    }


    /**
     * A list of the parameters to a particular SQL statement.
     * Add to this list in the order of the statement.
     * For example,
     * for SELECT * FROM USERS WHERE a = ? and b = ?
     * <p>
     * first add the parameter for a, then for b.
     *
     * @param data  a particular item of data.  Any object will do.  Look at {@link #applyParametersToPreparedStatement(PreparedStatement)}
     *              to see what we can process.
     * @param clazz the class of the thing.  I would rather not use reflection, let's keep it above board for now.
     */
    <T> void addParameter(Object data, Class<T> clazz) {
        params.add(new ParameterObject<>(data, clazz));
    }

    /**
     * Loop through the parameters that have been added and
     * serially add them to the prepared statement.
     *
     * @param st a prepared statement
     */
    void applyParametersToPreparedStatement(PreparedStatement st) {
        try {
            for (int i = 1; i <= params.size(); i++) {
                ParameterObject<?> p = params.get(i - 1);
                if (p.type() == String.class) {
                    st.setString(i, (String) p.data());
                } else if (p.type() == Integer.class) {
                    st.setInt(i, (Integer) p.data());
                } else if (p.type() == Long.class) {
                    st.setLong(i, (Long) p.data());
                } else if (p.type() == Date.class) {
                    st.setDate(i, (Date) p.data());
                }
            }
        } catch (SQLException e) {
            throw new SqlRuntimeException(e);
        }
    }

    public static <T> SqlData<T> createEmpty() {
        return new SqlData<>("", "");
    }

    public boolean isEmpty() {
        return this.equals(SqlData.createEmpty());
    }

}
