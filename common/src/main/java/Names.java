import io.vertx.core.Future;
import io.vertx.ext.web.client.WebClient;

import java.security.SecureRandom;

public class Names {
    public static Future<String> getName(WebClient client) {
        var useNameAPI = Commons.getBoolEnv("USE_NAME_API", true);
        if (useNameAPI) {
            return client.get(443, "random-data-api.com", "/api/name/random_name")
                    .ssl(true)
                    .timeout(3000)
                    .send()
                    .onFailure(Throwable::printStackTrace)
                    .map(it -> it.bodyAsJsonObject().getString("two_word_name", "Anonymous Missing"));
        } else {
            var names = Commons.getStringEnv("NAMES", "Goat,Sheep,Cow,Chicken,Pig,Lamb");
            var arrNames = names.split(",");
            var i = new SecureRandom().nextInt(arrNames.length);
            return Future.succeededFuture(arrNames[i]);
        }
    }
}
