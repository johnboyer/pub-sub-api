package config;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ReplayPreset;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The PubSubApiConfig class is used for setting up the configurations for running the examples.
 * The configurations can be read from a YAML file or created directly via an object. It also sets
 * default values when an optional configuration is not specified.
 */
@Data
@NoArgsConstructor
public class PubSubApiConfig {

    private PubSub pubsub;
    private Auth auth;

    public static PubSubApiConfig getPubSubApiConfig() throws IOException {
        var yaml = new Yaml(new Constructor(PubSubApiConfig.class, new LoaderOptions()));
        var classLoader = PubSubApiConfig.class.getClassLoader();
        PubSubApiConfig configuration;
        try (var inputStream = classLoader.getResourceAsStream("salesforce.yml")) {
            configuration = yaml.load(inputStream);
        }

        //Set defaults
        var pubSub = configuration.getPubsub();
        pubSub.topic = Optional.ofNullable(pubSub.getTopic()).orElse("/event/Order_Event__e");
        pubSub.publishRequestSize = Optional.ofNullable(pubSub.getPublishRequestSize()).orElse(5);
        pubSub.singlePublishRequest = Optional.ofNullable(pubSub.getSinglePublishRequest()).orElse(false);
        pubSub.subscriptionRequestSize = Optional.ofNullable(pubSub.getSubscriptionRequestSize()).orElse(5);
        pubSub.logChangedEventHeaders = Optional.ofNullable(pubSub.getLogChangedEventHeaders()).orElse(false);
        Consumer<ReplayPreset> replayPresetConsumer = getReplayPresetConsumer(pubSub);
        Optional.ofNullable(pubSub.getReplayPreset())
                .ifPresentOrElse(replayPresetConsumer, () -> pubSub.setReplayPreset(ReplayPreset.LATEST));

        //Excluded in settings file
        pubSub.plaintextChannel = Optional.ofNullable(pubSub.getPlaintextChannel()).orElse(false);
        pubSub.providedLoginUrl = Optional.ofNullable(pubSub.getProvidedLoginUrl()).orElse(false);

        return configuration;
    }

    private static Consumer<ReplayPreset> getReplayPresetConsumer(PubSub pubSub) {
        return preset -> {
            ReplayPreset replayPreset;
            ByteString replayId = null;
            switch (preset) {
                case EARLIEST:
                    replayPreset = ReplayPreset.EARLIEST;
                    break;
                case CUSTOM:
                    replayPreset = ReplayPreset.CUSTOM;
                    replayId = getByteStringFromReplayIdInputString(String.valueOf(pubSub.getReplayId()));
                    break;
                default:
                    replayPreset = ReplayPreset.LATEST;
            }
            pubSub.setReplayPreset(replayPreset);
            pubSub.setReplayId(replayId);
        };
    }

    /**
     * NOTE: replayIds are meant to be opaque (See docs: https://developer.salesforce.com/docs/platform/pub-sub-api/guide/intro.html)
     * and this is used for example purposes only. A long-lived subscription client will use the stored replay to
     * resubscribe on failure. The stored replay should be in bytes and not in any other form.
     */
    private static ByteString getByteStringFromReplayIdInputString(String input) {
        ByteString replayId;
        String[] values = input.substring(1, input.length()-2).split(",");
        byte[] b = new byte[values.length];
        int i=0;
        for (String x : values) {
            if (x.strip().length() != 0) {
                b[i++] = (byte)Integer.parseInt(x.strip());
            }
        }
        replayId = ByteString.copyFrom(b);
        return replayId;
    }


    public boolean usePlaintextChannel() {
        return pubsub.getPlaintextChannel();
    }

    public boolean useProvidedLoginUrl() {
        return pubsub.getProvidedLoginUrl();
    }

    @Data
    public static class PubSub {
        private String host;
        private Integer port;
        private String topic;
        private String loginUrl;
        private Integer publishRequestSize;//formerly NUMBER_OF_EVENTS_TO_PUBLISH
        private Boolean singlePublishRequest;
        private Integer subscriptionRequestSize;//formerly NUMBER_OF_EVENTS_IN_FETCHREQUES
        private Boolean logChangedEventHeaders;//formerly PROCESS_CHANGE_EVENT_HEADER_FIELDS
        private Boolean plaintextChannel;
        private Boolean providedLoginUrl;
        private ReplayPreset replayPreset;
        private ByteString replayId;
        private String managedSubscriptionId;
        private String managedSubscriptionDeveloperName;
    }

    @Data
    public static class Auth {
        private String username;
        private String password;
        private String tenantId;
        private String accessToken;
    }
}
