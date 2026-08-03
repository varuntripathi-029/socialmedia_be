package com.socialmedia.app.exception;

/**
 * Authenticated, but not allowed to perform this action.
 *
 * Distinct from {@link UnauthorizedException} (401, "we don't know who you are") — this is 403,
 * "we know who you are and the answer is still no". The existing ownership checks signal refusal
 * with {@code IllegalStateException}, which the handler maps to 400 and which reads to a client as
 * "your request was malformed" rather than "you are not permitted".
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
