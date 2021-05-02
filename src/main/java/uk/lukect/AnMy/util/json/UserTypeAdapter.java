package uk.lukect.AnMy.util.json;

import com.google.gson.*;
import twitter4j.TwitterException;
import twitter4j.User;
import uk.lukect.AnMy.App;

import java.lang.reflect.Type;

public class UserTypeAdapter implements JsonSerializer<User>, JsonDeserializer<User> {

    @Override
    public JsonElement serialize(User user, Type type, JsonSerializationContext jsonSerializationContext) {
        return new JsonPrimitive(user.getId());
    }

    @Override
    public User deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        try {
            return App.twitter.showUser(jsonElement.getAsLong());
        } catch (TwitterException e) {
            e.printStackTrace();
            return null;
        }
    }

}
