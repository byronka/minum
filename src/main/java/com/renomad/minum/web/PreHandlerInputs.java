package com.renomad.minum.web;

/**
 * The input parameters that are required to add a pre-handler.  See {@link WebFramework#registerPreHandler(ThrowingFunction)}
 * @param clientRequest the raw {@link Request} from the user
 * @param endpoint      the endpoint that was properly chosen for the combination
 *                      of path and verb.
 */
public record PreHandlerInputs(IRequest clientRequest, ThrowingFunction<IRequest, IResponse> endpoint, ISocketWrapper sw) {
}
