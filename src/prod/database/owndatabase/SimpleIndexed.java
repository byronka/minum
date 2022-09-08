package database.owndatabase;

public interface SimpleIndexed {
    /**
     * We need an index so that each piece of data is distinct, even if it has the same data.
     * @return an index for this data, used to name a file for this particular instance of data
     */
    Long getIndex();
}