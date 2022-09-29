package atqa.web;

public record Request(Headers headers, StartLine startLine, String body){}
