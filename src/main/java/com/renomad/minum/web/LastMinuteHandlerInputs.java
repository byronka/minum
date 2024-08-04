package com.renomad.minum.web;


/**
 * The parameters required to set up a handler that is run after everything else in the
 * web framework.  See {@link WebFramework#registerLastMinuteHandler(ThrowingFunction)}
 * @param request a HTTP request from a user
 * @param response This is the response previously calculated.  For context: this method is intended
 *                 to be run as a *special situation*, when the programmer wants a handler to run
 *                 at the "last minute" after previous processing, based on the response code. For
 *                 example, perhaps it is desired to override the response for 400 or 500 errors.
 *                 <br>
 *                 It is valuable to get the previously-calculated response data, in case there is
 *                 something useful - like valuable error messages.
 */
public record LastMinuteHandlerInputs(IRequest request, IResponse response){}