package database.owndatabase;

public interface SimpleIndexed {
    /**
     * @return an index for this data, used to name a file for this particular instance of data
     */
    Long getIndex();
}