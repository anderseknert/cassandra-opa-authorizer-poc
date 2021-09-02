package com.eknert.cassandra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.auth.DataResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.auth.RoleResource;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unused") // Suppress unused getters warnings
public class OpaClient {

    private final static ObjectMapper objectMapper = new ObjectMapper();

    public String query(AuthenticatedUser user, DataResource resource) throws IOException {
        String input;
        try {
            Payload payload = new Payload(new Input(new User(user), new Resource(resource)));
            input = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            String opaUrl = "http://localhost:8181/v1/data/cassandra/allow";
            HttpPost httpPost = new HttpPost(opaUrl);
            httpPost.setEntity(new StringEntity(input, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
                HttpEntity entity = response.getEntity();
                try {
                    String content = EntityUtils.toString(entity);
                    EntityUtils.consume(entity);
                    return content;
                } catch (ParseException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    static class Payload {
        private final Input input;

        public Payload(Input input) {
            this.input = input;
        }

        public Input getInput() {
            return this.input;
        }
    }

    static class Input {
        private final User user;
        private final Resource resource;

        public Input(User user, Resource resource) {
            this.user = user;
            this.resource = resource;
        }

        public User getUser() {
            return user;
        }

        public Resource getResource() {
            return resource;
        }
    }

    static class User {
        private final AuthenticatedUser user;

        public User(AuthenticatedUser user) {
           this.user = user;
        }

        public String getName() {
            return user.getName();
        }

        public Set<String> getRoles() {
            return user.getRoles().stream().map(RoleResource::getName).collect(Collectors.toSet());
        }
    }

    static class Resource {
        private final DataResource resource;

        public Resource(DataResource resource) {
            this.resource = resource;
        }

        public String getName() {
            return resource.getName();
        }

        public String getLevel() {
            if (resource.isRootLevel()) {
                return "root";
            } else if (resource.isKeyspaceLevel()) {
                return "keyspace";
            } else if (resource.isTableLevel()) {
                return "table";
            }

            throw new RuntimeException("Unknown resource level");
        }

        @Nullable
        public Resource getParent() {
            if (resource.hasParent()) {
                return new Resource((DataResource) resource.getParent());
            }
            return null;
        }

        public Set<Permission> applicablePermissions() {
            return resource.applicablePermissions();
        }
    }

}
