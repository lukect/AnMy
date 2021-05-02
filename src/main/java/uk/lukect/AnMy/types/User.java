package uk.lukect.AnMy.types;

import uk.lukect.AnMy.App;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) { // does not include stats
        if (this == o) return true;
        if (!(o instanceof User)) return false;
        User user = (User) o;
        return id == user.id && verified == user.verified && isProtected == user.isProtected && defaultProfile == user.defaultProfile && createdAt.equals(user.createdAt) && username.equals(user.username) && Objects.equals(displayName, user.displayName) && Objects.equals(description, user.description) && Objects.equals(location, user.location) && Objects.equals(URL, user.URL) && Objects.equals(profileImageURL, user.profileImageURL) && Objects.equals(profileBannerImageURL, user.profileBannerImageURL) && Arrays.equals(withheldInCountries, user.withheldInCountries);
    }

    @Override
    public int hashCode() { // does not include stats
        int result = Objects.hash(id, createdAt, username, displayName, verified, isProtected, description, location, defaultProfile, URL, profileImageURL, profileBannerImageURL);
        result = 31 * result + Arrays.hashCode(withheldInCountries);
        return result;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Stats)) return false;
            Stats stats = (Stats) o;
            return id == stats.id && followers == stats.followers && following == stats.following && likes == stats.likes && tweets == stats.tweets && listed == stats.listed;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, followers, following, likes, tweets, listed);
        }

        public String toJSON() {
            return App.gson.toJson(this);
        }

    }

    public String statsToJSON() {
        return stats.toJSON();
    }

}
