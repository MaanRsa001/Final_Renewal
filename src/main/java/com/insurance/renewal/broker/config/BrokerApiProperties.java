package com.insurance.renewal.broker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Broker API clients, bound from application.properties:
 *
 *   broker.api.clients[0].broker-code=BRK001
 *   broker.api.clients[0].name=ABC Insurance Brokers
 *   broker.api.clients[0].api-key=${BROKER_BRK001_KEY:}
 *
 * A client with a blank api-key is disabled. Keys should come from environment variables / a secret store.
 */
@ConfigurationProperties(prefix = "broker.api")
public class BrokerApiProperties {

    private List<Client> clients = new ArrayList<>();

    public List<Client> getClients() { return clients; }
    public void setClients(List<Client> clients) { this.clients = clients; }

    public static class Client {
        private String brokerCode;
        private String name;
        private String apiKey;

        public String getBrokerCode() { return brokerCode; }
        public void setBrokerCode(String brokerCode) { this.brokerCode = brokerCode; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
