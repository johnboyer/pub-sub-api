package utility;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import lombok.Getter;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ReplayPreset;

/**
 * The ExampleConfigurations class is used for setting up the configurations for running the examples.
 * The configurations can be read from a YAML file or created directly via an object. It also sets
 * default values when an optional configuration is not specified.
 * @deprecated Use {@link config.PubSubApiConfig} instead
 */
@Getter
@Setter
@Deprecated
public class ExampleConfigurations {
    private String username;
    private String password;
    private String loginUrl;
    private String tenantId;
    private String accessToken;
    private String pubsubHost;
    private Integer pubsubPort;
    private String topic;
    private Integer numberOfEventsToPublish;
    private Boolean singlePublishRequest;
    private Integer numberOfEventsToSubscribeInEachFetchRequest;
    private Boolean processChangedFields;
    private Boolean plaintextChannel;
    private Boolean providedLoginUrl;
    private ReplayPreset replayPreset;
    private ByteString replayId;
    private String managedSubscriptionId;
    private String developerName;

    public ExampleConfigurations() {
        this(null, null, null, null, null,
                null, null, null, 5, false,
                5, false,
                false, false, ReplayPreset.LATEST, null, null, null);
    }
    public ExampleConfigurations(String filename) throws IOException {

        Yaml yaml = new Yaml();
        InputStream inputStream = new FileInputStream("src/main/resources/"+filename);
        HashMap<String, Object> obj = yaml.load(inputStream);

        // Reading Required Parameters
        this.loginUrl = obj.get("LOGIN_URL").toString();
        this.pubsubHost = obj.get("PUBSUB_HOST").toString();
        this.pubsubPort = Integer.parseInt(obj.get("PUBSUB_PORT").toString());

        // Reading Optional Parameters
        this.username = obj.get("USERNAME") == null ? null : obj.get("USERNAME").toString();
        this.password = obj.get("PASSWORD") == null ? null : obj.get("PASSWORD").toString();
        this.topic = obj.get("TOPIC") == null ? "/event/Order_Event__e" : obj.get("TOPIC").toString();
        this.tenantId = obj.get("TENANT_ID") == null ? null : obj.get("TENANT_ID").toString();
        this.accessToken = obj.get("ACCESS_TOKEN") == null ? null : obj.get("ACCESS_TOKEN").toString();
        this.numberOfEventsToPublish = obj.get("NUMBER_OF_EVENTS_TO_PUBLISH") == null ?
                5 : Integer.parseInt(obj.get("NUMBER_OF_EVENTS_TO_PUBLISH").toString());
        this.singlePublishRequest = obj.get("SINGLE_PUBLISH_REQUEST") == null ?
                false : Boolean.parseBoolean(obj.get("SINGLE_PUBLISH_REQUEST").toString());
        this.numberOfEventsToSubscribeInEachFetchRequest = obj.get("NUMBER_OF_EVENTS_IN_FETCHREQUEST") == null ?
                5 : Integer.parseInt(obj.get("NUMBER_OF_EVENTS_IN_FETCHREQUEST").toString());
        this.processChangedFields = obj.get("PROCESS_CHANGE_EVENT_HEADER_FIELDS") == null ?
                false : Boolean.parseBoolean(obj.get("PROCESS_CHANGE_EVENT_HEADER_FIELDS").toString());
        this.plaintextChannel = obj.get("USE_PLAINTEXT_CHANNEL") != null && Boolean.parseBoolean(obj.get("USE_PLAINTEXT_CHANNEL").toString());
        this.providedLoginUrl = obj.get("USE_PROVIDED_LOGIN_URL") != null && Boolean.parseBoolean(obj.get("USE_PROVIDED_LOGIN_URL").toString());

        if (obj.get("REPLAY_PRESET") != null) {
            if (obj.get("REPLAY_PRESET").toString().equals("EARLIEST")) {
                this.replayPreset = ReplayPreset.EARLIEST;
            } else if (obj.get("REPLAY_PRESET").toString().equals("CUSTOM")) {
                this.replayPreset = ReplayPreset.CUSTOM;
                this.replayId = getByteStringFromReplayIdInputString(obj.get("REPLAY_ID").toString());
            } else {
                this.replayPreset = ReplayPreset.LATEST;
            }
        } else {
            this.replayPreset = ReplayPreset.LATEST;
        }

        this.developerName = obj.get("MANAGED_SUB_DEVELOPER_NAME") == null ? null : obj.get("MANAGED_SUB_DEVELOPER_NAME").toString();
        this.managedSubscriptionId = obj.get("MANAGED_SUB_ID") == null ? null : obj.get("MANAGED_SUB_ID").toString();
    }

    public ExampleConfigurations(String username, String password, String loginUrl,
                                 String pubsubHost, int pubsubPort, String topic) {
        this(username, password, loginUrl, null, null, pubsubHost, pubsubPort, topic,
                5, false, Integer.MAX_VALUE, false, false, false, ReplayPreset.LATEST, null, null, null);
    }

    public ExampleConfigurations(String username, String password, String loginUrl, String tenantId, String accessToken,
                                 String pubsubHost, Integer pubsubPort, String topic, Integer numberOfEventsToPublish,
                                 Boolean singlePublishRequest, Integer numberOfEventsToSubscribeInEachFetchRequest,
                                 Boolean processChangedFields, Boolean plaintextChannel, Boolean providedLoginUrl,
                                 ReplayPreset replayPreset, ByteString replayId, String devName, String managedSubId) {
        this.username = username;
        this.password = password;
        this.loginUrl = loginUrl;
        this.tenantId = tenantId;
        this.accessToken = accessToken;
        this.pubsubHost = pubsubHost;
        this.pubsubPort = pubsubPort;
        this.topic = topic;
        this.singlePublishRequest = singlePublishRequest;
        this.numberOfEventsToPublish = numberOfEventsToPublish;
        this.numberOfEventsToSubscribeInEachFetchRequest = numberOfEventsToSubscribeInEachFetchRequest;
        this.processChangedFields = processChangedFields;
        this.plaintextChannel = plaintextChannel;
        this.providedLoginUrl = providedLoginUrl;
        this.replayPreset = replayPreset;
        this.replayId = replayId;
        this.developerName = devName;
        this.managedSubscriptionId = managedSubId;
    }

    /**
     * NOTE: replayIds are meant to be opaque (See docs: https://developer.salesforce.com/docs/platform/pub-sub-api/guide/intro.html)
     * and this is used for example purposes only. A long-lived subscription client will use the stored replay to
     * resubscribe on failure. The stored replay should be in bytes and not in any other form.
     */
    public ByteString getByteStringFromReplayIdInputString(String input) {
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
        return plaintextChannel;
    }

    public boolean useProvidedLoginUrl() {
        return providedLoginUrl;
    }
}
