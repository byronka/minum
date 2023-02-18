package atqa.auth;

public record RegisterResult(RegisterResultStatus status, User newUser) {}
