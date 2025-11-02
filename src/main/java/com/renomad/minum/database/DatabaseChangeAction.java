package com.renomad.minum.database;

/**
 * These are the actions that can take place on
 * data in the database.
 */
enum DatabaseChangeAction {
    /**
     * This value encompasses both creates and updates to
     * data in the database.
     */
    UPDATE,

    /**
     * Represents the notion of deleting an item from the database
     */
    DELETE
}