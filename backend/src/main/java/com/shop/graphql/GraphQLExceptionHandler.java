package com.shop.graphql;

import com.shop.error.BadRequestException;
import com.shop.error.ResourceNotFoundException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Maps domain exceptions thrown inside GraphQL resolvers to structured GraphQL errors
 * (design pattern: <b>Adapter</b> — adapts Spring exceptions to graphql-java error model).
 *
 * <p>Without this handler, any resolver exception becomes {@code INTERNAL_ERROR} and the
 * client cannot distinguish "not found" from a real server fault. Spring for GraphQL invokes
 * {@link DataFetcherExceptionResolverAdapter} automatically — no manual registration needed.</p>
 *
 * <p>Logging policy: expected domain errors (NOT_FOUND, BAD_REQUEST) are logged at DEBUG to
 * avoid noise; unexpected errors are logged at ERROR with a full stack trace. PII is never
 * included in log messages — only resource type and field name.</p>
 */
@Component
public class GraphQLExceptionHandler extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQLExceptionHandler.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ResourceNotFoundException e) {
            log.debug("GraphQL NOT_FOUND in field '{}': {}", env.getField().getName(), e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.NOT_FOUND)
                    .message(e.getMessage())
                    .build();
        }
        if (ex instanceof BadRequestException e) {
            log.debug("GraphQL BAD_REQUEST in field '{}': {}", env.getField().getName(), e.getMessage());
            return GraphqlErrorBuilder.newError(env)
                    .errorType(ErrorType.BAD_REQUEST)
                    .message(e.getMessage())
                    .build();
        }
        // Unknown exceptions: let Spring produce INTERNAL_ERROR, but log the full cause
        log.error("GraphQL unhandled exception in field '{}'", env.getField().getName(), ex);
        return null;
    }
}
