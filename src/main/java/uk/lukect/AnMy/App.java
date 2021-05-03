package uk.lukect.AnMy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.BasicDBObject;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.WriteError;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.*;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.bson.types.ObjectId;
import twitter4j.Paging;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.User;
import uk.lukect.AnMy.util.json.DateTypeAdapter;
import uk.lukect.AnMy.util.json.UserTypeAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

        // INDEXING START

        var textIndex_options = new IndexOptions().name("text");
        var createdAt_options = new IndexOptions().name("createdAt");
        var id_options_unique = new IndexOptions().name("id").unique(true);
        var id_options = new IndexOptions().name("id");

        var twitterDB = mongo.getDatabase("twitter");
        var tweets = twitterDB.getCollection("tweets");
        tweets.createIndex(Indexes.descending("createdAt"), createdAt_options);
        tweets.createIndex(Indexes.descending("id"), id_options_unique);
        tweets.createIndex(Indexes.text("text"), textIndex_options);
        tweets.createIndex(Indexes.descending("favoriteCount"), new IndexOptions().name("favoriteCount"));
        tweets.createIndex(Indexes.descending("retweetCount"), new IndexOptions().name("retweetCount"));
        tweets.createIndex(Indexes.descending("isPossiblySensitive"), new IndexOptions().name("isPossiblySensitive"));
        tweets.createIndex(Indexes.ascending("lang"), new IndexOptions().name("lang"));
        tweets.createIndex(Indexes.ascending("place"), new IndexOptions().name("place").sparse(true));
        tweets.createIndex(Indexes.descending("user"), new IndexOptions().name("user"));

        // $text index for both users and users_history
        var textIndex = new BsonDocument();
        var textVal = new BsonString("text");
        textIndex.put("username", textVal);
        textIndex.put("displayName", textVal);
        textIndex.put("description", textVal);
        textIndex.put("location", textVal);

        // username index.
        var username_index = new IndexOptions().name("username")
                .collation(Collation.builder().locale("en").collationStrength(CollationStrength.PRIMARY).build());

        var users = twitterDB.getCollection("users");
        users.createIndex(Indexes.descending("id"), id_options_unique); //only one current for each user id
        users.createIndex(Indexes.descending("createdAt"), createdAt_options);
        users.createIndex(Indexes.ascending("username"), username_index);
        users.createIndex(textIndex, textIndex_options);

        var users_history = twitterDB.getCollection("users_history");
        users_history.createIndex(Indexes.descending("id"), id_options); // multiple for each user id because recording history
        users_history.createIndex(Indexes.descending("createdAt"), createdAt_options);
        users_history.createIndex(Indexes.ascending("username"), username_index);
        users_history.createIndex(textIndex, textIndex_options);

        var user_stats = twitterDB.getCollection("user_stats");
        user_stats.createIndex(Indexes.descending("id"), id_options);
        user_stats.createIndex(Indexes.descending("followers"), new IndexOptions().name("followers"));
        user_stats.createIndex(Indexes.descending("following"), new IndexOptions().name("following"));
        user_stats.createIndex(Indexes.descending("likes"), new IndexOptions().name("likes"));
        user_stats.createIndex(Indexes.descending("tweets"), new IndexOptions().name("tweets"));
        user_stats.createIndex(Indexes.descending("listed"), new IndexOptions().name("listed"));

        // INDEXING END

        int total = 0;
        int duplicate_total = 0;

        try {
            var user = twitter.showUser(args[0]);

            var ux = new uk.lukect.AnMy.types.User(user);
            var ud = Document.parse(ux.toJSON());
            ud.put("_id", new ObjectId()); // required because replaceOne doesn't force new _id, therefore there will be duplicates in history

            var users_find = users.find(new BasicDBObject("id", ux.id)).limit(1);
            var users_find_itr = users_find.iterator();
            var last_user_doc = users_find_itr.tryNext();

            if (last_user_doc == null) {
                users.insertOne(ud);
            } else {
                var last_user = gson.fromJson(bsonToJson(last_user_doc), uk.lukect.AnMy.types.User.class);
                if (!ux.equals(last_user)) {
                    users_history.insertOne(last_user_doc);
                    users.findOneAndDelete(new BasicDBObject("id", ux.id));
                    users.insertOne(ud);
                }
            }

            var last_stat_find = user_stats.find(new BasicDBObject("id", ux.stats.id))
                    .sort(new BasicDBObject("_id", -1)).limit(1); // https://docs.mongodb.com/manual/reference/bson-types/#std-label-objectid
            var last_stat_itr = last_stat_find.iterator();
            var last_stat = last_stat_itr.tryNext();

            if (last_stat == null || Math.abs(getDifferenceInHours(new Date(), last_stat.getObjectId("_id").getDate())) >= 6) {
                user_stats.insertOne(Document.parse(ux.statsToJSON()));
            }

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

    public static long getDifferenceInHours(Date date1, Date date2) {
        var diff = date1.getTime() - date2.getTime();
        return TimeUnit.MILLISECONDS.toHours(diff);
    }

    private static final JsonWriterSettings JSW = JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build();

    public static String bsonToJson(Document document) {
        return document.toJson(JSW);
    }

}
