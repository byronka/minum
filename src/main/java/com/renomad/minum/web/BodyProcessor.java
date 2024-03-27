package com.renomad.minum.web;

import com.renomad.minum.Context;
import com.renomad.minum.exceptions.ForbiddenUseException;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.InvariantException;
import com.renomad.minum.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.renomad.minum.utils.Invariants.mustBeTrue;

/**
 * This code is responsible for extracting the {@link Body} from
 * an HTTP request.
 */
final class BodyProcessor {

    private final ILogger logger;
    private final IInputStreamUtils inputStreamUtils;
    private final Context context;

    /**
     * When parsing fails, we would like to send the raw text
     * back to the user so the development team can determine
     * why parsing went awry.  But, when we are sent a huge file,
     * we would rather not include all that data in the logs.
     * So we will cap out at this value.
     */
    static final int MAX_SIZE_DATA_RETURNED_IN_EXCEPTION = 1024;

    BodyProcessor(Context context) {
        this.context = context;
        this.logger = context.getLogger();
        this.inputStreamUtils = context.getInputStreamUtils();
    }

    /**
     * read the body if one exists
     * <br>
     * There are really only two ways to read the body.
     * 1. the client tells us how many bytes to read
     * 2. the client uses "transfer-encoding: chunked"
     * <br>
     * In either case, it is absolutely critical that the client gives us
     * a way to know ahead of time how many bytes to read, so we (the server)
     * can stop reading at precisely the right point.  There's simply no
     * other way to reasonably do this.
     */
    Body extractData(InputStream is, Headers h) {
        final var contentType = h.contentType();

        byte[] bodyBytes = h.contentLength() > 0 ?
                inputStreamUtils.read(h.contentLength(), is) :
                inputStreamUtils.readChunkedEncoding(is);

        return extractBodyFromBytes(h.contentLength(), contentType, bodyBytes);
    }

    /**
     * Handles the parsing of the body data for either form-urlencoded or
     * multipart/form-data
     * @param contentType a mime value which must be either application/x-www-form-urlencoded
     *                    or multipart/form-data.  Anything else will cause a new Body to
     *                    be created with the body bytes, unparsed.  There are a number of
     *                    cases where this makes sense - if the user is sending us plain text,
     *                    html, json, or css, we want to simply accept the data and not try to parse it.
     * @param bodyBytes the full body of this HTTP message, as bytes.
     */
    Body extractBodyFromBytes(int contentLength, String contentType, byte[] bodyBytes) {
        if (contentLength > 0 && contentType.contains("application/x-www-form-urlencoded")) {
            return parseUrlEncodedForm(StringUtils.byteArrayToString(bodyBytes));
        } else if (contentType.contains("multipart/form-data")) {
            String boundaryKey = "boundary=";
            int indexOfBoundaryKey = contentType.indexOf(boundaryKey);
            if (indexOfBoundaryKey > 0) {
                // grab all the text after the key
                String boundaryValue = contentType.substring(indexOfBoundaryKey + boundaryKey.length());
                return parseMultiform(bodyBytes, boundaryValue);
            }
            String parsingError = "Did not find a valid boundary value for the multipart input. Returning an empty map and the raw bytes for the body. Header was: " + contentType;
            logger.logDebug(() -> parsingError);
            return new Body(Map.of(), bodyBytes, Map.of());
        } else {
            logger.logDebug(() -> "did not recognize a key-value pattern content-type, returning an empty map and the raw bytes for the body");
            return new Body(Map.of(), bodyBytes, Map.of());
        }
    }


    /**
     * Parse data formatted by application/x-www-form-urlencoded
     * See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST">...</a>
     * <p>
     * See here for the encoding: <a href="https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding">...</a>
     * <p>
     * for example, {@code valuea=3&valueb=this+is+something}
     */
    Body parseUrlEncodedForm(String input) {
        if (input.isEmpty()) return Body.EMPTY;
        final var postedPairs = new HashMap<String, byte[]>();

        try {
            final var splitByAmpersand = tokenizer(input, '&', context.getConstants().maxTokenizerPartitions);

            for (final var s : splitByAmpersand) {
                final var pair = splitKeyAndValue(s);
                mustBeTrue(!pair[0].isBlank(), "The key must not be blank");
                final var value = StringUtils.decode(pair[1]);
                final var convertedValue = value == null ? "".getBytes(StandardCharsets.UTF_8) : value.getBytes(StandardCharsets.UTF_8);
                final var result = postedPairs.put(pair[0], convertedValue);

                if (result != null) {
                    throw new InvariantException(pair[0] + " was duplicated in the post body - had values of " + StringUtils.byteArrayToString(result) + " and " + pair[1]);
                }
            }
        } catch (Exception ex) {
            String dataToReturn = input;
            if (input.length() > MAX_SIZE_DATA_RETURNED_IN_EXCEPTION) {
                dataToReturn = input.substring(0, MAX_SIZE_DATA_RETURNED_IN_EXCEPTION) + " ... (remainder of data trimmed)";
            }
            logger.logDebug(() -> "Unable to parse this body. returning an empty map and the raw bytes for the body.  Exception message: " + ex.getMessage());
            return new Body(Map.of(), dataToReturn.getBytes(StandardCharsets.UTF_8), Map.of());
        }
        return new Body(postedPairs, input.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /**
     * This splits a key from its value, following the HTTP pattern
     * of "key=value". (that is, a key string, concatenated to an "equals"
     * character, concatenated to the value, with no spaces [and the key
     * and value are URL-encoded])
     * @param formInputValue a string like "key=value"
     */
    private String[] splitKeyAndValue(String formInputValue) {
        final var locationOfEqual = formInputValue.indexOf("=");
        return new String[] {
                formInputValue.substring(0, locationOfEqual),
                formInputValue.substring(locationOfEqual+1)};
    }

    /**
     * A regex used to extract the name value from the headers in multipart/form
     * For example, in the following code, you can see that the name is "image_uploads"
     * <pre>
     * {@code
     * --i_am_a_boundary
     * Content-Type: text/plain
     * Content-Disposition: form-data; name="text1"
     *
     * I am a value that is text
     * --i_am_a_boundary
     * Content-Type: application/octet-stream
     * Content-Disposition: form-data; name="image_uploads"; filename="photo_preview.jpg"
     * }
     * </pre>
     */
    private static final Pattern multiformNameRegex = Pattern.compile("\\bname\\b=\"(?<namevalue>.*?)\"");

    /**
     * Extract multipart/form data from a body.  See docs/http_protocol/returning_values_from_multipart_rfc_7578.txt
     */
    Body parseMultiform(byte[] body, String boundaryValue) {
        // how to split this up? It's a mix of strings and bytes.
        List<byte[]> partitions = split(body, "--" + boundaryValue);
        // What we can bear in mind is that once we've read the headers, and gotten
        // past the single blank line, *everything else* is pure data.
        final var result = new HashMap<String, byte[]>();
        final var partitionHeaders = new HashMap<String, Headers>();
        for (var df : partitions) {
            final var is = new ByteArrayInputStream(df);

            Headers headers = Headers.make(context).extractHeaderInformation(is);

            List<String> cds = headers.valueByKey("Content-Disposition");
            String contentDisposition = String.join(";", cds == null ? List.of("") : cds);

            Matcher matcher = multiformNameRegex.matcher(contentDisposition);

            if (matcher.find()) {
                String name = matcher.group("namevalue");
                // at this point our inputstream pointer is at the beginning of the
                // body data.  From here until the end it's pure data.
                var data = inputStreamUtils.readUntilEOF(is);
                result.put(name, data);
                partitionHeaders.put(name, headers);
            }
            else {
                String returnedData;
                if (body.length > MAX_SIZE_DATA_RETURNED_IN_EXCEPTION) {
                    returnedData = StringUtils.byteArrayToString(Arrays.copyOf(body, MAX_SIZE_DATA_RETURNED_IN_EXCEPTION)) + " ... (remainder of data trimmed)";
                } else {
                    returnedData = StringUtils.byteArrayToString(body);
                }
                logger.logDebug(() -> "No name value found in the headers of a partition. Data: " + returnedData);
                return new Body(Map.of(), body, Map.of());
            }

        }
        return new Body(result, body, partitionHeaders);
    }

    /**
     * Given a multipart-formatted data, return a list of byte arrays
     * between the boundary values
     */
    List<byte[]> split(byte[] body, String boundaryValue) {
        List<byte[]> result = new ArrayList<>();
        List<Integer> indexesOfEndsOfBoundaries = determineEndsOfBoundaries(body, boundaryValue);
        // now we know where the boundaries are in this hunk of data.  Partition time!
        int indexInBody = 0;
        for (int endOfBoundaryIndex : indexesOfEndsOfBoundaries) {
            // the minus one plus two silliness is to make clear that we're calculating for two extra chars after the boundary
            if (indexInBody != endOfBoundaryIndex-(boundaryValue.length() + 2 - 1)) {
                result.add(Arrays.copyOfRange(body, indexInBody, endOfBoundaryIndex-(boundaryValue.length() + 2 -1)));
            }
            indexInBody = endOfBoundaryIndex+1;
        }
        return result;
    }

    private static List<Integer> determineEndsOfBoundaries(byte[] body, String boundaryValue) {
        List<Integer> indexesOfEndsOfBoundaries = new ArrayList<>();
        byte[] boundaryValueBytes = boundaryValue.getBytes(StandardCharsets.UTF_8);
        int indexIntoBoundaryValue = 0;
        for (int i = 0; i < body.length; i++) {
            if (body[i] == boundaryValueBytes[indexIntoBoundaryValue]) {
                if (indexIntoBoundaryValue == boundaryValueBytes.length - 1) {
                    // have to add two to account for *either* CR+LF or two dashes.  Multipart
                    // form is so complicated!
                    indexesOfEndsOfBoundaries.add(i + 2);
                    indexIntoBoundaryValue = 0;
                }
                indexIntoBoundaryValue += 1;
            } else {
                indexIntoBoundaryValue = 0;
            }
        }
        return indexesOfEndsOfBoundaries;
    }


    /**
     * Splits up a string into tokens.
     * @param serializedText the string we are splitting up
     * @param delimiter the character acting as a boundary between sections
     * @param maxTokenizerPartitions the maximum partitions of text we'll allow.  e.g. "a,b,c" might create 3 partitions.
     * @return a list of strings.  If the delimiter is not found, we will just return the whole string
     */
    static List<String> tokenizer(String serializedText, char delimiter, int maxTokenizerPartitions) {
        final var resultList = new ArrayList<String>();
        var currentPlace = 0;
        // when would we have a need to tokenize anything into more than MAX_TOKENIZER_PARTITIONS partitions?
        for(int i = 0;; i++) {
            if (i >= maxTokenizerPartitions) {
                throw new ForbiddenUseException("Request made for too many partitions in the tokenizer.  Current max: " + maxTokenizerPartitions);
            }
            final var nextDelimiterIndex = serializedText.indexOf(delimiter, currentPlace);
            if (nextDelimiterIndex == -1) {
                // if we don't see any delimiter symbols ahead, grab the rest of the text from our current place
                resultList.add(serializedText.substring(currentPlace));
                break;
            }
            resultList.add(serializedText.substring(currentPlace, nextDelimiterIndex));
            currentPlace = nextDelimiterIndex + 1;
        }

        return resultList;
    }

}
