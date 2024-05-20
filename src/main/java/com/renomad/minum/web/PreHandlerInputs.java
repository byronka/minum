package com.renomad.minum.web;

/**
 * @param clientRequest the raw {@link Request} from the user
 * @param endpoint the endpoint that was properly chosen for the combination
 *                 of path and verb.
 */
public record PreHandlerInputs(Request clientRequest, ThrowingFunction<Request, Response> endpoint) {
}
