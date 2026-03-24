package com.leafy.iotmetricscollectorservice.config;

import com.leafy.iotmetricscollectorservice.integration.mqtt.MqttInboundMessageHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MqttProperties.class)
public class MqttInboundConfig {

    private final MqttProperties mqttProperties;
    private final MqttInboundMessageHandler mqttInboundMessageHandler;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        if (mqttProperties.getUrl() == null || mqttProperties.getUrl().isBlank()) {
            throw new IllegalStateException("Missing required property app.mqtt.url");
        }

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttProperties.getUrl()});
        options.setAutomaticReconnect(mqttProperties.isAutomaticReconnect());
        options.setCleanSession(mqttProperties.isCleanSession());
        options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
        options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());

        if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isBlank()) {
            options.setUserName(mqttProperties.getUsername());
        }

        if (mqttProperties.getPassword() != null && !mqttProperties.getPassword().isBlank()) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }

        return options;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttConnectOptions mqttConnectOptions) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(mqttConnectOptions);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inboundMqttAdapter(MqttPahoClientFactory mqttClientFactory) {
        List<String> topics = mqttProperties.getTopics();
        if (topics == null || topics.isEmpty()) {
            throw new IllegalStateException("MQTT topics must not be empty");
        }

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttProperties.getClientId(),
                        mqttClientFactory,
                        topics.toArray(new String[0])
                );

        adapter.setCompletionTimeout(mqttProperties.getCompletionTimeout());
        adapter.setQos(mqttProperties.getQos());

        DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
        converter.setPayloadAsBytes(false);
        adapter.setConverter(converter);

        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttMessageHandler() {
        return mqttInboundMessageHandler;
    }
}