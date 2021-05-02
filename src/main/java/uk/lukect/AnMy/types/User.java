package uk.lukect.AnMy.types;

import uk.lukect.AnMy.App;

import java.io.Serializable;
import java.util.Date;

public class User implements Serializable {

    public long id;
    public Date createdAt;
    public String username;
    public String displayName;
    public boolean verified;
    public boolean isProtected;
    public String description;
    public String location;
    public boolean defaultProfile;
    public String URL;
    public String profileImageURL;
    public String profileBannerImageURL;
    public String[] withheldInCountries;

    public transient Stats stats;

    private User() {
        // GSON
    }

    public User(twitter4j.User user) {
        this.id = user.getId();
        this.createdAt = user.getCreatedAt();
        this.username = user.getScreenName();
        this.displayName = user.getName();
        this.verified = user.isVerified();
        this.isProtected = user.isProtected();

        var description = user.getDescription();
        var urls = user.getDescriptionURLEntities();
        for (var u : urls) {
            description = description.replace(u.getURL(), u.getDisplayURL()); // get what the user actually typed (without https:// added by twitter or shortend t.co link)
        }
        this.description = description;

        this.location = user.getLocation();
        this.defaultProfile = user.isDefaultProfile();
        this.URL = user.getURLEntity().getDisplayURL(); // what the user actually typed (without https:// added by twitter or shortend t.co link)
        this.profileImageURL = user.getOriginalProfileImageURLHttps();
        this.profileBannerImageURL = user.getProfileBackgroundImageUrlHttps();
        this.withheldInCountries = user.getWithheldInCountries();

        this.stats = new Stats(user);
    }

    public String toJSON() {
        return App.gson.toJson(this);
    }

    public String statsToJSON() {
        return stats.toJSON();
    }

    public static class Stats {

        public long id;
        public int followers;
        public int following;
        public int likes;
        public int tweets;
        public int listed;

        public Stats(twitter4j.User user) {
            this.id = user.getId();
            this.followers = user.getFollowersCount();
            this.following = user.getFriendsCount();
            this.likes = user.getFavouritesCount();
            this.tweets = user.getStatusesCount();
            this.listed = user.getListedCount();
        }

        private Stats() {
            // GSON
        }

        public String toJSON() {
            return App.gson.toJson(this);
        }

    }

}
