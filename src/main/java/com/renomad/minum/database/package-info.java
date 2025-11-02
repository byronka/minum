/**
 * This package contains classes for data persistence capabilities.
 *
 * <pre>
 * {@code
 * //------------------------
 * // Define the data type
 * //------------------------
 *
 * public class PersonName extends DbData<PersonName> {
 *
 *     private long index;
 *     private final String fullname;
 *
 *     public PersonName(Long index, String fullname) {
 *         this.index = index;
 *         this.fullname = fullname;
 *     }
 *
 *     public static final PersonName EMPTY = new PersonName(0L, "");
 *
 *     // ... (several more lines. For an example see Inmate.java)
 *
 * }
 *
 * //------------------------
 * // Initialize the database
 * //------------------------
 *
 * For the following database instantiations, it is also possible to set their
 * type to AbstractDb
 *
 * // This will start a database using "DbEngine2", which is a fast database, and
 * // is the preferred one to use.
 * DbEngine2<PersonName> db = context.getDb2("names", PersonName.EMPTY);
 *
 * // This will start a database of the "classic" type.
 * Db<PersonName> db = context.getDb("names", PersonName.EMPTY);
 *
 *
 * //--------------------------------------
 * //  Writing and updating to the database
 * //--------------------------------------
 *
 * // to create a new item in the database, use an index of zero.
 * //
 * // Otherwise, when an item has a positive, non-zero index, it is understood
 * // as an update to an existing item in the database.
 *
 * PersonName myName = db.write(new PersonName(0, "original name");
 * myName.setName("a new name");
 * db.write(myName);
 *
 * //------------------------------------------
 * // Register an index, and search by value
 * //------------------------------------------
 *
 * // Registering indexes has to happen immediately after database creation, as shown here,
 * // because if it happens after the data is loaded from disk, then the opportunity is
 * // missed to register each piece of data with this tool.
 *
 * Db<PersonName> db = context.getDb("names", PersonName.EMPTY);
 * db.registerIndex("myIndex", x -> x.getName());
 * PersonName name = db.findExactlyOne("myIndex", "Alice");
 *
 *
 * //------------------------------------------
 * //  Get all the values (read-only) as a list
 * //------------------------------------------
 *
 * PersonName allPersons = db.values().stream().toList()
 *
 * }
 * </pre>
 */
package com.renomad.minum.database;