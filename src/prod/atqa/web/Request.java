package atqa.web;

public record Request(HeaderInformation hi, StartLine sl, String body){}
