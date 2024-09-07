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
 *     // ... (several more lines. See PersonName.java in the tests directory)
 *
 * }
 *
 * //------------------------
 * // Initialize the database
 * //------------------------
 *
 * Db<PersonName> db = context.getDb("names", PersonName.EMPTY);
 *
 * //---------------------
 * //  Add to the database
 * //---------------------
 *
 * db.write(new PersonName(0L, "My Name"));
 *
 * //-------------------------------------------
 * //  Get (read-only) by name from the database
 * //-------------------------------------------
 *
 * PersonName foundPerson = SearchUtils.findExactlyOne(db.values().stream(), x -> x.getFullname().equals("My Name"));
 *
 * //------------------------------------------
 * //  Get all the values (read-only) as a list
 * //------------------------------------------
 *
 * PersonName allPersons = db.values().stream().toList()
 *
 * //--------------------------------
 * //  Update a value in the database
 * //--------------------------------
 *
 * foundPerson.setName("a new name");
 * db.write(foundPerson);
 *
 * }
 * </pre>
 */
package com.renomad.minum.database;