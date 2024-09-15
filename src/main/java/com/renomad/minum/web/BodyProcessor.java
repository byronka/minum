package com.renomad.minum.web;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.security.ForbiddenUseException;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This code is responsible for extracting the {@link Body} from
 * an HTTP request.
 */
final class BodyProcessor implements IBodyProcessor {

    private final ILogger logger;
    private final IInputStreamUtils inputStreamUtils;
    private final Constants constants;

    BodyProcessor(Context context) {
        this.constants = context.getConstants();
        this.logger = context.getLogger();
        this.inputStreamUtils = new InputStreamUtils(constants.maxReadLineSizeBytes);
    }

    @Override
    public Body extractData(InputStream is, Headers h) {
        final var contentType = h.contentType();

        if (h.contentLength() >= 0) {
            if (h.contentLength() >= constants.maxReadSizeBytes) {
                throw new ForbiddenUseException("It is disallowed to process a body with a length more than " + constants.maxReadSizeBytes + " bytes");
            }
        } else {
            // we don't process chunked transfer encodings.  just bail.
            List<String> transferEncodingHeaders = h.valueByKey("transfer-encoding");
            if (List.of("chunked").equals(transferEncodingHeaders)) {
                logger.logDebug(() -> "client sent chunked transfer-encoding.  Minum does not automatically read bodies of this type.");
            }
            return Body.EMPTY;
        }

        return extractBodyFromInputStream(h.contentLength(), contentType, is);
    }

    /**
     * Handles the parsing of the body data for either form-urlencoded or
     * multipart/form-data
     *
     * @param contentType a mime value which must be either application/x-www-form-urlencoded
     *                    or multipart/form-data.  Anything else will cause a new Body to
     *                    be created with the body bytes, unparsed.  There are a number of
     *                    cases where this makes sense - if the user is sending us plain text,
     *                    html, json, or css, we want to simply accept the data and not try to parse it.
     */
    Body extractBodyFromInputStream(int contentLength, String contentType, InputStream is) {
        // if the body is zero bytes long, just return
        if (contentLength == 0) {
            logger.logDebug(() -> "the length of the body was 0, returning an empty Body");
            return Body.EMPTY;
        }

        if (contentType.contains("application/x-www-form-urlencoded")) {
            return parseUrlEncodedForm(is, contentLength);
        } else if (contentType.contains("multipart/form-data")) {
            String boundaryValue = determineBoundaryValue(contentType);
            return parseMultipartForm(contentLength, boundaryValue, is);
        } else {
            logger.logDebug(() -> "did not recognize a key-value pattern content-type, returning the raw bytes for the body.  Content-Type was: " + contentType);
            // we can return the whole byte array here because we never read from it
            return new Body(Map.of(), inputStreamUtils.read(contentLength, is), List.of(), BodyType.UNRECOGNIZED);
        }
    }

    /**
     * Parse multipart/form protocol.
     * @param contentLength the length of incoming data, found in the "content-length" header
     * @param boundaryValue the randomly-generated boundary value between the partitions.  Research
     *                      multipart/form data protocol for further information.
     * @param inputStream A stream of bytes coming from the socket.
     */
    private Body parseMultipartForm(int contentLength, String boundaryValue, InputStream inputStream) {

        if (boundaryValue.isBlank()) {
            logger.logDebug(() -> "The boundary value was blank for the multipart input. Returning an empty map");
            return new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED);
        }

        List<Partition> partitions = new ArrayList<>();

        try {
            int countOfPartitions = 0;
            for (StreamingMultipartPartition p : getMultiPartIterable(inputStream, boundaryValue, contentLength)) {
                countOfPartitions += 1;
                if (countOfPartitions >= MAX_BODY_KEYS_URL_ENCODED) {
                    throw new WebServerException("Error: body had excessive number of partitions (" + countOfPartitions + ").  Maximum allowed: " + MAX_BODY_KEYS_URL_ENCODED);
                }
                partitions.add(new Partition(p.getHeaders(), p.readAllBytes(), p.getContentDisposition()));
            }


        } catch (Exception ex) {
            logger.logDebug(() -> "Unable to parse this body. returning what we have so far.  Exception message: " + ex.getMessage());
            // we have to return nothing for the raw bytes, because at this point we are halfway through
            // reading the inputstream and don't want to return broken data
            return new Body(Map.of(), new byte[0], partitions, BodyType.MULTIPART);
        }
        if (partitions.isEmpty()) {
            return new Body(Map.of(), new byte[0], List.of(), BodyType.UNRECOGNIZED);
        } else {
            return new Body(Map.of(), new byte[0], partitions, BodyType.MULTIPART);
        }
    }

    /**
     * Given the "content-type" header, determine the boundary value.  A typical
     * multipart content-type header might look like this: <pre>Content-Type: multipart/form-data; boundary=i_am_a_boundary</pre>
     */
    private static String determineBoundaryValue(String contentType) {
        String boundaryKey = "boundary=";
        String boundaryValue = "";
        int indexOfBoundaryKey = contentType.indexOf(boundaryKey);
        if (indexOfBoundaryKey > 0) {
            // grab all the text after the key to obtain the boundary value
            boundaryValue = contentType.substring(indexOfBoundaryKey + boundaryKey.length());
        }
        return boundaryValue;
    }


    /**
     * Parse data formatted by application/x-www-form-urlencoded
     * See <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/POST">...</a>
     * <p>
     * See here for the encoding: <a href="https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding">...</a>
     * <p>
     * for example, {@code valuea=3&valueb=this+is+something}
     */
    Body parseUrlEncodedForm(InputStream is, int contentLength) {
        if (contentLength == 0) {
            return Body.EMPTY;
        }
        final var postedPairs = new HashMap<String, byte[]>();

        try {
            int countOfPartitions = 0;
            for (final var keyValue : getUrlEncodedDataIterable(is, contentLength)) {
                countOfPartitions += 1;
                if (countOfPartitions >= MAX_BODY_KEYS_URL_ENCODED) {
                    throw new WebServerException("Error: body had excessive number of partitions ("+countOfPartitions+").  Maximum allowed: " + MAX_BODY_KEYS_URL_ENCODED);
                }
                String value = new String(keyValue.getUedg().readAllBytes(), StandardCharsets.US_ASCII);
                String key = keyValue.getKey();
                final var decodedValue = StringUtils.decode(value);
                final var convertedValue = decodedValue == null ? "".getBytes(StandardCharsets.UTF_8) : decodedValue.getBytes(StandardCharsets.UTF_8);

                final var result = postedPairs.put(key, convertedValue);

                if (result != null) {
                    throw new WebServerException("Error: key (" +key + ") was duplicated in the post body - previous version was " + new String(result, StandardCharsets.US_ASCII) + " and recent data was " + decodedValue);
                }
            }
        } catch (Exception ex) {
            logger.logDebug(() -> "Unable to parse this body. returning what we have so far.  Exception message: " + ex.getMessage());
            // we have to return nothing for the raw bytes, because at this point we are halfway through
            // reading the inputstream and don't want to return broken data
            return new Body(postedPairs, new byte[0], List.of(), BodyType.UNRECOGNIZED);
        }
        // we return nothing for the raw bytes because the code for parsing the streaming data
        // doesn't begin with a fully-read byte array - it pulls data off the stream one byte
        // at a time.
        return new Body(postedPairs, new byte[0], List.of(), BodyType.FORM_URL_ENCODED);
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
    private static final Pattern multiformFilenameRegex = Pattern.compile("\\bfilename\\b=\"(?<namevalue>.*?)\"");

    @Override
    public Iterable<UrlEncodedKeyValue> getUrlEncodedDataIterable(InputStream inputStream, long contentLength) {
        return () -> new Iterator<>() {

            final CountBytesRead countBytesRead = new CountBytesRead();

            @Override
            public boolean hasNext() {
                return countBytesRead.getCount() < contentLength;
            }

            @Override
            public UrlEncodedKeyValue next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                String key = "";
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                while(true) {
                    int result = 0;
                    try {
                        result = inputStream.read();
                        countBytesRead.increment();
                    } catch (IOException e) {
                        throw new WebServerException(e);
                    }
                    // if this is true, the inputstream is closed
                    if (result == -1) break;
                    byte myByte = (byte) result;
                    // if this is true, we're done with the key
                    if (myByte == '=') {
                        // URL encoding is in ASCII only.
                        key = byteArrayOutputStream.toString(StandardCharsets.US_ASCII);
                        break;
                    } else {
                        if (byteArrayOutputStream.size() >= MAX_KEY_SIZE_BYTES) {
                            throw new WebServerException("Maximum size for name attribute is " + MAX_KEY_SIZE_BYTES + " ascii characters");
                        }
                        byteArrayOutputStream.write(myByte);
                    }
                }

                if (key.isBlank()) {
                    throw new WebServerException("Unable to parse this body. no key found during parsing");
                } else if (countBytesRead.getCount() == contentLength) {
                    // if the only thing sent was the key and there's no further data, return the key with a null input stream
                    // that will immediately return
                    return new UrlEncodedKeyValue(key, new UrlEncodedDataGetter(InputStream.nullInputStream(), countBytesRead, contentLength));
                } else {
                    return new UrlEncodedKeyValue(key, new UrlEncodedDataGetter(inputStream, countBytesRead, contentLength));
                }
            }
        };
    }


    @Override
    public Iterable<StreamingMultipartPartition> getMultiPartIterable(InputStream inputStream, String boundaryValue, int contentLength) {
        return () -> new Iterator<>() {

            final CountBytesRead countBytesRead = new CountBytesRead();
            boolean hasReadFirstPartition = false;

            @Override
            public boolean hasNext() {
                // determining if we have more to read is a little tricky because we have a buffer
                // filled by reading ahead, looking for the boundary value
                return (contentLength - countBytesRead.getCount()) > boundaryValue.length();
            }

            @Override
            public StreamingMultipartPartition next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                // confirm that the boundary value is as expected, as a sanity check,
                // and avoid including the boundary value in the first set of headers
                if (! hasReadFirstPartition) {
                    String s;
                    try {
                        s = inputStreamUtils.readLine(inputStream);
                        countBytesRead.incrementBy(s.length() + 2);
                        hasReadFirstPartition = true;
                        if (!s.contains(boundaryValue)) {
                            throw new IOException("Error: First line must contain the expected boundary value. Expected to find: "+ boundaryValue + " in: " + s);
                        }
                    } catch (IOException e) {
                        throw new WebServerException(e);
                    }
                }
                List<String> allHeaders = Headers.getAllHeaders(inputStream, inputStreamUtils);
                int lengthOfHeaders = allHeaders.stream().map(String::length).reduce(0, Integer::sum);
                // each line has a CR + LF (that's two bytes) and the headers end with a second pair of CR+LF.
                int extraCrLfs = (2 * allHeaders.size()) + 2;
                countBytesRead.incrementBy(lengthOfHeaders + extraCrLfs);

                Headers headers = new Headers(allHeaders);

                List<String> cds = headers.valueByKey("Content-Disposition");
                if (cds == null) {
                    throw new WebServerException("Error: no Content-Disposition header on partition in Multipart/form data");
                }
                String contentDisposition = String.join(";", cds);

                Matcher nameMatcher = multiformNameRegex.matcher(contentDisposition);
                Matcher filenameMatcher = multiformFilenameRegex.matcher(contentDisposition);

                String name = "";
                if (nameMatcher.find()) {
                    name = nameMatcher.group("namevalue");
                } else {
                    throw new WebServerException("Error: No name value set on multipart partition");
                }
                String filename = "";
                if (filenameMatcher.find()) {
                    filename = filenameMatcher.group("namevalue");
                }

                // at this point our inputstream pointer is at the beginning of the
                // body data.  From here until the end it's pure data.

                return new StreamingMultipartPartition(headers, inputStream, new ContentDisposition(name, filename), boundaryValue, countBytesRead, contentLength);
            }


        };
    }

}
