package com.renomad.minum.web;

/**
 * A container for the data of a request line, meant for internal use
 * @param method an HTTP method, such as GET or POST
 * @param path the path to an endpoint
 * @param protocol one of the HTTP protocols, like HTTP/1.1
 */
record RequestLineRawValues(String method, String path, String protocol){}
