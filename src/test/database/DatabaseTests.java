package database;

import logging.TestLogger;

import static framework.TestFramework.assertEquals;
import static framework.TestFramework.assertTrue;

public record DatabaseTests(TestLogger logger) {

    public void databaseTests() {

        logger.test("Connect to a database and create a schema, then delete it");
        {
            var db = new Database();
            db.createSchema("foo");
            db.deleteSchema("foo");
        }

        /*
         * Just like how the popular database migration tools do it, we'll change the
         * schema of our database in some way (maybe new tables, updating constraints,
         * all that sort of thing) and then update a separate table in the database
         * with the current version.  We can use that other table to indicate which
         * updates need to run or have already been run.
         */
        logger.test("Update the version of our database by changing its schema");
        {
            var db = new Database();
            db.deleteSchema("foo");
            db.deleteSchema("version");
            db.createSchema("foo");
            db.updateSchema(1, "create a users table", """
                    create table foo.users (
                        id serial PRIMARY KEY,
                        name VARCHAR(100) NOT NULL
                    );""");
            var id = db.saveNewUser("alice");
            var name = db.getUser(id);
            assertTrue(name.isPresent());
            assertEquals(name.get(), "alice");
            db.deleteSchema("foo");
        }
    }
}
