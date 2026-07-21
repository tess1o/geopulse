package org.github.tess1o.geopulse.importdata.service;

import io.quarkus.narayana.jta.QuarkusTransactionException;

import java.io.IOException;

final class ImportTransactionExceptions {

    private ImportTransactionExceptions() {
    }

    static IOException unwrapIOExceptionOrThrowRuntime(QuarkusTransactionException exception) {
        IOException ioException = findCause(exception, IOException.class);
        if (ioException != null) {
            return ioException;
        }

        RuntimeException runtimeException = findFirstRuntimeCause(exception);
        if (runtimeException != null) {
            throw runtimeException;
        }

        throw exception;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> expectedType) {
        Throwable current = throwable;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static RuntimeException findFirstRuntimeCause(QuarkusTransactionException exception) {
        Throwable current = exception.getCause();
        while (current != null) {
            if (current instanceof RuntimeException runtimeException
                    && !(current instanceof QuarkusTransactionException)) {
                return runtimeException;
            }
            current = current.getCause();
        }
        return null;
    }
}
