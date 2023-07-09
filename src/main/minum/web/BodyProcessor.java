package minum.web;

import minum.Constants;
import minum.Context;
import minum.htmlparsing.ParsingException;
import minum.logging.ILogger;
import minum.utils.ByteUtils;
import minum.utils.InvariantException;
import minum.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static minum.utils.Invariants.mustBeTrue;

/**
 * This code is responsible for extracting the {@link Body} from
 * an HTTP request.
 */
public class BodyProcessor {

    private final ILogger logger;
    private final InputStreamUtils inputStreamUtils;
    private final Context context;
    private final StringUtils stringUtils;

    /**
     * When parsing fails, we would like to send the raw text
     * back to the user so the development team can determine
     * why parsing went awry.  But, when we are sent a huge file,
     * we would rather not include all that data in the logs.
     * So we will cap out at this value.
     */
    public final static int MAX_SIZE_DATA_RETURNED_IN_EXCEPTION = 1024;

    public BodyProcessor(Context context) {
        this.context = context;
        this.logger = context.getLogger();
        this.stringUtils = new StringUtils(context);
        this.inputStreamUtils = new InputStreamUtils(context);
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
    public Body extractData(InputStream is, Headers h) throws IOException {
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
        try {
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
                throw new ParsingException("Did not find a valid boundary value for the multipart input. Header was: " + contentType);
            } else {
                logger.logDebug(() -> "Did not find a recognized content-type, returning an empty map and the raw bytes for the body");
                return new Body(Map.of(), bodyBytes, context);
            }
        } catch (Throwable ex) {
            throw new ParsingException("Unable to parse this body", ex);
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
    public Body parseUrlEncodedForm(String input) {
        if (input.isEmpty()) return Body.EMPTY(context);
        final var postedPairs = new HashMap<String, byte[]>();

        try {
            final var splitByAmpersand = stringUtils.tokenizer(input, '&');

            for (final var s : splitByAmpersand) {
                final var pair = splitKeyAndValue(s);
                mustBeTrue(pair.length == 2, "Splitting on = should return 2 values.  Input was " + s);
                mustBeTrue(!pair[0].isBlank(), "The key must not be blank");
                final var value = StringUtils.decode(pair[1]);
                final var convertedValue = value == null ? "".getBytes(StandardCharsets.UTF_8) : value.getBytes(StandardCharsets.UTF_8);
                final var result = postedPairs.put(pair[0], convertedValue);

                if (result != null) {
                    throw new InvariantException(pair[0] + " was duplicated in the post body - had values of " + StringUtils.byteArrayToString(result) + " and " + pair[1]);
                }
            }
        } catch (Throwable ex) {
            String dataToReturn = input;
            if (input.length() > MAX_SIZE_DATA_RETURNED_IN_EXCEPTION) {
                dataToReturn = input.substring(0, MAX_SIZE_DATA_RETURNED_IN_EXCEPTION) + " ... (remainder of data trimmed)";
            }
            throw new ParsingException("Unable to parse this body as application/x-www-form-urlencoded. Data: " + dataToReturn, ex);
        }
        return new Body(postedPairs, input.getBytes(StandardCharsets.UTF_8), context);
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
    private final static Pattern multiformNameRegex = Pattern.compile("\\bname\\b=\"(?<namevalue>.*?)\"");

    /**
     * Extract multipart/form data from a body.  See docs/http_protocol/returning_values_from_multipart_rfc_7578.txt
     */
    public Body parseMultiform(byte[] body, String boundaryValue) {
        // how to split this up? It's a mix of strings and bytes.
        List<byte[]> partitions = split(body, "--" + boundaryValue);
        final String nameEquals = "name=";
        // What we can bear in mind is that once we've read the headers, and gotten
        // past the single blank line, *everything else* is pure data.
        final var result = new HashMap<String, byte[]>();
        for (var df : partitions) {
            final var is = new ByteArrayInputStream(df);

            Headers headers = null;
            try {
                headers = Headers.make(context, inputStreamUtils).extractHeaderInformation(is);
            } catch (IOException e) {
                // This is an InputStream we built ourselves from local data.
                // there's no reason why it should fail.
                throw new RuntimeException(e);
            }

            String contentDisposition = headers.getHeaderStrings().stream()
                    .filter(x -> x.toLowerCase().contains("form-data") && x.contains(nameEquals)).collect(Collectors.joining());

            Matcher matcher = multiformNameRegex.matcher(contentDisposition);

            if (matcher.find()) {
                String name = matcher.group("namevalue");
                // at this point our inputstream pointer is at the beginning of the
                // body data.  From here until the end it's pure data.
                byte[] data = new byte[0];
                try {
                    data = inputStreamUtils.readUntilEOF(is);
                } catch (IOException e) {
                    // This is an InputStream we built ourselves from local data.
                    // there's no reason why it should fail.
                    throw new RuntimeException(e);
                }
                result.put(name, data);
            }
            else {
                String returnedData = "";
                if (body.length > MAX_SIZE_DATA_RETURNED_IN_EXCEPTION) {
                    returnedData = StringUtils.byteArrayToString(Arrays.copyOf(body, MAX_SIZE_DATA_RETURNED_IN_EXCEPTION)) + " ... (remainder of data trimmed)";
                } else {
                    returnedData = StringUtils.byteArrayToString(body);
                }
                throw new ParsingException("No name value found in the headers of a partition. Data: " + returnedData);
            }

        }
        return new Body(result, body, context);
    }

    /**
     * Given a multipart-formatted data, return a list of byte arrays
     * between the boundary values
     */
    public List<byte[]> split(byte[] body, String boundaryValue) {
        List<byte[]> result = new ArrayList<>();
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

}
