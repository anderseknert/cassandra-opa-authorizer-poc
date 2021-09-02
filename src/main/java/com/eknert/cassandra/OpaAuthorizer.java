package com.eknert.cassandra;

import com.google.common.collect.ImmutableSet;
import org.apache.cassandra.auth.*;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.schema.SchemaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class OpaAuthorizer implements IAuthorizer {

    private static final Logger logger = LoggerFactory.getLogger(OpaAuthorizer.class);

    private OpaClient opaClient;

    @Override
    public Set<Permission> authorize(AuthenticatedUser user, IResource resource) {
        if (user.isSuper()) {
            return resource.applicablePermissions();
        }

        if (!(resource instanceof DataResource)) {
            // Only deal with data resources
            return Permission.ALL;
        }

        String response;
        try {
            response = opaClient.query(user, (DataResource) resource);

            // TODO: Deserialize the response into a set of permissions

            logger.info("Response: {}", response);

            return Permission.ALL;
        } catch (IOException e) {
            logger.warn("Failed to authorize request", e);
            return Permission.NONE;
        }
    }

    @Override
    public void grant(AuthenticatedUser performer, Set<Permission> permissions, IResource resource, RoleResource grantee) throws RequestValidationException, RequestExecutionException {
        throw new UnsupportedOperationException("GRANT operation is not supported by OpaAuthorizer");
    }

    @Override
    public void revoke(AuthenticatedUser performer, Set<Permission> permissions, IResource resource, RoleResource revokee) throws RequestValidationException, RequestExecutionException {
        throw new UnsupportedOperationException("REVOKE operation is not supported by OpaAuthorizer");
    }

    @Override
    public Set<PermissionDetails> list(AuthenticatedUser performer, Set<Permission> permissions, IResource resource, RoleResource grantee) throws RequestValidationException, RequestExecutionException {
        // TODO: This could potentially be supported
        throw new UnsupportedOperationException("LIST PERMISSIONS operation is not supported by OpaAuthorizer");
    }

    @Override
    public void revokeAllFrom(RoleResource revokee) {
        throw new UnsupportedOperationException("Revoking permissions from role not supported by OpaAuthorizer");
    }

    @Override
    public void revokeAllOn(IResource droppedResource) {
        throw new UnsupportedOperationException("Revoking permissions on resource not supported by OpaAuthorizer");
    }

    @Override
    public Set<? extends IResource> protectedResources() {
        return ImmutableSet.of(DataResource.table(SchemaConstants.AUTH_KEYSPACE_NAME, AuthKeyspace.ROLE_PERMISSIONS));
    }

    @Override
    public void validateConfiguration() throws ConfigurationException {
    }

    @Override
    public void setup() {
        opaClient = new OpaClient();
    }
}
