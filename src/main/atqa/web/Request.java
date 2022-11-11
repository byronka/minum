package atqa.web;

public record Request(Headers headers, StartLine startLine, String body){
    public Authentication getAuth() {
        // TODO: relying merely on them having a Cookie header is so insufficient.
        // we really need to check their code against a session in our database.
        final var isAuthenticated = headers.rawValues().stream().anyMatch(x -> x.startsWith("Cookie"));
        return new Authentication(isAuthenticated);
    }
}
