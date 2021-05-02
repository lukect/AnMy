package uk.lukect.AnMy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.WriteError;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.Document;
import twitter4j.Paging;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.User;
import uk.lukect.AnMy.util.json.DateTypeAdapter;
import uk.lukect.AnMy.util.json.UserTypeAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class App {

    public static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateTypeAdapter())
            .registerTypeAdapter(User.class, new UserTypeAdapter())
            .setPrettyPrinting().create();
    public static final Twitter twitter = new TwitterFactory().getInstance();
    public static MongoClient mongo;

    final static int MAX_PAGE_SIZE = 200;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("java -server -jar AnMy.jar <twitter_username> <page size (max 200)> [seconds delay between pages] [a=scan all pages]");
            System.exit(-1);
        }

        mongo = MongoClients.create();

        var twitterDB = mongo.getDatabase("twitter");
        var tweets = twitterDB.getCollection("tweets");
        tweets.createIndex(Indexes.descending("createdAt"));
        tweets.createIndex(Indexes.descending("id"), new IndexOptions().name("id").unique(true));
        tweets.createIndex(Indexes.text("text"));
        tweets.createIndex(Indexes.descending("favoriteCount"));
        tweets.createIndex(Indexes.descending("retweetCount"));
        tweets.createIndex(Indexes.descending("isPossiblySensitive"));
        tweets.createIndex(Indexes.ascending("lang"));
        tweets.createIndex(Indexes.ascending("place"), new IndexOptions().name("place").sparse(true));
        tweets.createIndex(Indexes.descending("user"));

        var users = twitterDB.getCollection("users");
        users.createIndex(Indexes.descending("id"), new IndexOptions().name("id").unique(true));
        users.createIndex(Indexes.descending("createdAt"));
        users.createIndex(Indexes.ascending("username"));
        BsonDocument textIndex = new BsonDocument();
        var textVal = new BsonString("text");
        textIndex.put("displayName", textVal);
        textIndex.put("description", textVal);
        textIndex.put("location", textVal);
        users.createIndex(textIndex);

        int total = 0;
        int duplicate_total = 0;

        try {
            var user = twitter.showUser(args[0]);

            var ux = new uk.lukect.AnMy.types.User(user);
            var ud = Document.parse(ux.toJSON());
            users.replaceOne(new BsonDocument("id", new BsonInt64(ux.id)), ud, new ReplaceOptions().upsert(true));

            int pageSize = MAX_PAGE_SIZE;

            try {
                pageSize = Integer.parseInt(args[1]);
                if (pageSize > MAX_PAGE_SIZE) pageSize = MAX_PAGE_SIZE;
            } catch (Exception e) {
            }

            var paging = new Paging(1, pageSize);
            final int pages = (int) Math.ceil((double) user.getStatusesCount() / pageSize);

            long delay = 100L;
            boolean scan_all = false;

            if (args.length >= 3) {
                try {
                    delay = Long.parseLong(args[2]) * 1000L;
                } catch (Exception e) {
                    if (args[2].equalsIgnoreCase("a")) {
                        scan_all = true;
                    }
                }

                if (args.length >= 4) {
                    if (args[3].equalsIgnoreCase("a")) {
                        scan_all = true;
                    }
                }
            }

            for (int i = 1; i <= pages; i++) {
                Thread.sleep(delay);

                paging.setPage(i);
                var statuses = twitter.getUserTimeline(user.getId(), paging);

                if (statuses.size() < 1) {
                    System.out.println("Received an empty response from Twitter! Stopping now.");
                    break;
                }

                List<Document> docList = new ArrayList<>();

                for (var status : statuses) {
                    docList.add(Document.parse(gson.toJson(status)));
                }

                int duplicate = 0;

                try {
                    tweets.insertMany(docList, new InsertManyOptions().ordered(false));
                } catch (MongoBulkWriteException e) {
                    for (WriteError we : e.getWriteErrors()) {
                        if (we.getCategory() == ErrorCategory.DUPLICATE_KEY) {
                            duplicate++;
                        } else {
                            e.printStackTrace();
                        }
                    }
                }

                final int added = docList.size() - duplicate;

                System.out.println("Inserted " + added + " tweet(s) [" + duplicate + " duplicate(s)]");
                total += added;
                duplicate_total += duplicate;

                if (!scan_all && added < 1) break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Total added = " + total + " tweet(s) [" + duplicate_total + " duplicate(s)]");

        mongo.close();
    }

    private static String userToJson(User user) {
        var obj = gson.toJsonTree(user).getAsJsonObject();
        obj.remove("status"); // saved in tweets collections anyway
        return gson.toJson(obj);
    }

}
